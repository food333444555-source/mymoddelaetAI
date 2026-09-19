package dev.funtime.pe.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Bedrock login encryption for modern protocol versions.
 *
 * <p>Bedrock derives a 32-byte key with ECDH and SHA-256. The encrypted
 * payload uses the AES-256-CTR stream and carries the protocol's own
 * eight-byte checksum. A fresh stream counter is used per packet so a single
 * reused IV cannot silently corrupt the session.</p>
 */
final class BedrockCrypto {
    private static final byte[] CLIENT_SALT = "🧂".getBytes(StandardCharsets.UTF_8);

    private final byte[] secretKey;
    private final int compressionAlgorithm;
    private long sendCounter;
    private long receiveCounter;

    private BedrockCrypto(byte[] secretKey, int compressionAlgorithm) throws IOException {
        this.secretKey = secretKey;
        this.compressionAlgorithm = compressionAlgorithm;
    }

    static BedrockCrypto fromServerToken(String token, PrivateKey clientPrivateKey,
                                         int compressionAlgorithm)
            throws IOException {
        try {
            final String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IOException("Повреждённый Bedrock server handshake JWT");
            }
            final JsonObject header = JsonParser.parseString(new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            final JsonObject payload = JsonParser.parseString(new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            final byte[] serverPublicKey = Base64.getDecoder().decode(
                    header.get("x5u").getAsString());
            final byte[] salt = Base64.getDecoder().decode(
                    payload.get("salt").getAsString());

            final PublicKey serverKey = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(serverPublicKey));
            final Signature verifier = Signature.getInstance("SHA384withECDSA");
            verifier.initVerify(serverKey);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (parts.length < 3 || !verifier.verify(fromJwsSignature(
                    Base64.getUrlDecoder().decode(parts[2])))) {
                throw new IOException("Недействительная подпись Bedrock server handshake");
            }
            final KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(clientPrivateKey);
            agreement.doPhase(serverKey, true);
            final byte[] sharedSecret = agreement.generateSecret();

            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            final byte[] secretKey = digest.digest(sharedSecret);
            return new BedrockCrypto(secretKey, compressionAlgorithm);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Не удалось вычислить Bedrock shared secret", exception);
        }
    }

    byte[] encryptBatch(byte[] rawBatch) throws IOException {
        final byte[] compressed = compress(rawBatch);
        final byte[] withChecksum = appendChecksum(compressed, sendCounter);
        final byte[] result = crypt(withChecksum, sendCounter);
        sendCounter++;
        return result;
    }

    byte[] decryptBatch(byte[] encrypted) throws IOException {
        final byte[] plain = crypt(encrypted, receiveCounter);
        if (plain.length < 8) {
            throw new IOException("Слишком короткий зашифрованный Bedrock batch");
        }
        final int bodyLength = plain.length - 8;
        final byte[] body = java.util.Arrays.copyOf(plain, bodyLength);
        final byte[] expected = java.util.Arrays.copyOfRange(plain, bodyLength, plain.length);
        final byte[] actual = checksum(body, receiveCounter);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IOException("Bedrock encryption checksum mismatch");
        }
        receiveCounter++;
        return decompress(body);
    }

    static String createClientHandshake(BedrockLoginFactory.LoginMaterial material)
            throws IOException {
        try {
            final String publicKey = Base64.getEncoder().encodeToString(material.publicKey());
            final String header = "{\"alg\":\"ES384\",\"typ\":\"JWT\",\"x5u\":\""
                    + publicKey + "\"}";
            final String payload = "{\"salt\":\""
                    + Base64.getEncoder().encodeToString(CLIENT_SALT)
                    + "\",\"signedToken\":\"" + publicKey + "\"}";
            final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            final String unsigned = encoder.encodeToString(
                    header.getBytes(StandardCharsets.UTF_8)) + "."
                    + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            final Signature signature = Signature.getInstance("SHA384withECDSA");
            signature.initSign(material.privateKey());
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "."
                    + encoder.encodeToString(toJwsSignature(signature.sign()));
        } catch (Exception exception) {
            throw new IOException("Не удалось подписать Bedrock handshake", exception);
        }
    }

    private byte[] crypt(byte[] input, long counter) throws IOException {
        try {
            final Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "AES"),
                    new javax.crypto.spec.IvParameterSpec(counterVector(counter)));
            return cipher.doFinal(input);
        } catch (Exception exception) {
            throw new IOException("Ошибка Bedrock AES stream", exception);
        }
    }

    private byte[] counterVector(long counter) {
        final byte[] vector = new byte[16];
        final byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        System.arraycopy(secretKey, 0, vector, 0, 8);
        System.arraycopy(counterBytes, 0, vector, 8, 8);
        return vector;
    }

    private byte[] compress(byte[] input) throws IOException {
        if (compressionAlgorithm != 0) {
            return input;
        }
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0);
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(
                output, new Deflater(Deflater.DEFAULT_COMPRESSION, true))) {
            deflater.write(input);
        }
        return output.toByteArray();
    }

    private byte[] decompress(byte[] input) throws IOException {
        if (input.length == 0 || compressionAlgorithm != 0) {
            return input;
        }
        if ((input[0] & 0xff) == 0xff) {
            return java.util.Arrays.copyOfRange(input, 1, input.length);
        }
        if ((input[0] & 0xff) != 0) {
            throw new IOException("Неизвестный Bedrock compressor header");
        }
        try (InflaterInputStream inflater = new InflaterInputStream(
                new ByteArrayInputStream(input, 1, input.length - 1),
                new Inflater(true));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            inflater.transferTo(output);
            return output.toByteArray();
        }
    }

    private byte[] appendChecksum(byte[] body, long counter) {
        final byte[] result = new byte[body.length + 8];
        System.arraycopy(body, 0, result, 0, body.length);
        System.arraycopy(checksum(body, counter), 0, result, body.length, 8);
        return result;
    }

    private byte[] checksum(byte[] body, long counter) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int index = 0; index < 8; index++) {
                digest.update((byte) (counter >>> (index * 8)));
            }
            digest.update(body);
            digest.update(secretKey);
            return java.util.Arrays.copyOf(digest.digest(), 8);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static byte[] toJwsSignature(byte[] der) throws IOException {
        if (der.length < 8 || der[0] != 0x30) {
            throw new IOException("Некорректная ECDSA signature");
        }
        int cursor = 2;
        if ((der[1] & 0xff) >= 0x80) {
            cursor += (der[1] & 0x7f) - 1;
        }
        if (der[cursor++] != 0x02) {
            throw new IOException("Некорректная ECDSA r");
        }
        final int rLength = der[cursor++] & 0xff;
        final byte[] r = java.util.Arrays.copyOfRange(der, cursor, cursor + rLength);
        cursor += rLength;
        if (der[cursor++] != 0x02) {
            throw new IOException("Некорректная ECDSA s");
        }
        final int sLength = der[cursor++] & 0xff;
        final byte[] s = java.util.Arrays.copyOfRange(der, cursor, cursor + sLength);
        final byte[] result = new byte[96];
        copyUnsignedInteger(r, result, 0);
        copyUnsignedInteger(s, result, 48);
        return result;
    }

    private static byte[] fromJwsSignature(byte[] raw) throws IOException {
        if (raw.length != 96) {
            throw new IOException("Некорректная ECDSA JWS signature");
        }
        final byte[] r = trimUnsigned(java.util.Arrays.copyOfRange(raw, 0, 48));
        final byte[] s = trimUnsigned(java.util.Arrays.copyOfRange(raw, 48, 96));
        final int bodyLength = 4 + r.length + s.length;
        final ByteArrayOutputStream output = new ByteArrayOutputStream(bodyLength + 2);
        output.write(0x30);
        output.write(bodyLength);
        output.write(0x02);
        output.write(r.length);
        output.writeBytes(r);
        output.write(0x02);
        output.write(s.length);
        output.writeBytes(s);
        return output.toByteArray();
    }

    private static byte[] trimUnsigned(byte[] value) {
        int offset = 0;
        while (offset < value.length - 1 && value[offset] == 0) {
            offset++;
        }
        final byte[] trimmed = java.util.Arrays.copyOfRange(value, offset, value.length);
        return (trimmed[0] & 0x80) == 0
                ? trimmed
                : concat(new byte[]{0}, trimmed);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        final byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void copyUnsignedInteger(byte[] source, byte[] target, int offset) {
        final int sourceOffset = source.length > 1 && source[0] == 0 ? 1 : 0;
        final int length = Math.min(source.length - sourceOffset, 48);
        System.arraycopy(source, source.length - length, target, offset + 48 - length, length);
    }
}
