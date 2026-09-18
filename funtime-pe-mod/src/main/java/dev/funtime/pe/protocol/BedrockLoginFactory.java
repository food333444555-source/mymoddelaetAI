package dev.funtime.pe.protocol;

import dev.funtime.pe.MicrosoftSession;
import dev.funtime.pe.PeAccountState;
import dev.funtime.pe.PeServerEntry;
import dev.funtime.pe.BedrockVersion;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;

public final class BedrockLoginFactory {
    private BedrockLoginFactory() {
    }

    public static byte[] createLogin(PeServerEntry server, PeAccountState account)
            throws GeneralSecurityException {
        return createLoginMaterial(server, account).loginPacket();
    }

    public static LoginMaterial createLoginMaterial(PeServerEntry server,
                                                     PeAccountState account)
            throws GeneralSecurityException {
        final String name = account.isMicrosoftConnected()
                ? account.microsoftSession().gamertag()
                : account.guestName();
        final MicrosoftSession microsoft = account.microsoftSession();

        final KeyPair guestKeyPair = microsoft == null ? generateGuestKey() : null;
        final PrivateKey privateKey = microsoft == null ? guestKeyPair.getPrivate() : microsoft.privateKey();
        final String chain = microsoft == null
                ? createOfflineChain(name, privateKey, guestKeyPair.getPublic().getEncoded())
                : microsoft.minecraftChain();
        final byte[] publicKey = microsoft == null ? guestKeyPair.getPublic().getEncoded()
                : microsoft.publicKey();
        final String clientData = createClientData(server, name, privateKey, publicKey);
        final int authenticationType = microsoft == null ? 2 : 0;
        final String identity = "{"
                + "\"Certificate\":" + quoteJson(chain) + ","
                + "\"AuthenticationType\":" + authenticationType + ","
                + "\"Token\":\"\""
                + "}";

        final byte[] login = new BedrockBuffer()
                .writeByte(0x01)
                .writeIntLittleEndian(server.protocolVersion())
                .writeString(identity)
                .writeString(clientData)
                .toByteArray();
        return new LoginMaterial(login, privateKey, publicKey);
    }

    private static String createClientData(PeServerEntry server, String name, PrivateKey privateKey,
                                           byte[] publicKey)
            throws GeneralSecurityException {
        final String skin = "{"
                + "\"ClientRandomId\":" + Math.abs(UUID.randomUUID().getMostSignificantBits()) + ","
                + "\"ServerAddress\":\"" + escape(server.host() + ":" + server.port()) + "\","
                + "\"SkinId\":\"Standard_Custom\","
                + "\"SkinData\":\"\","
                + "\"DeviceOS\":7,"
                + "\"DeviceModel\":\"FUNTIME PE\","
                + "\"DeviceId\":\"" + UUID.randomUUID() + "\","
                + "\"ClientVersion\":\""
                + BedrockVersion.displayName(server.protocolVersion()) + "\","
                + "\"LanguageCode\":\"ru_RU\","
                + "\"ThirdPartyName\":\"" + escape(name) + "\","
                + "\"ThirdPartyNameOnly\":true,"
                + "\"CurrentInputMode\":2,"
                + "\"DefaultInputMode\":2,"
                + "\"UIProfile\":0"
                + "}";
        return signJwt(skin, privateKey, publicKey);
    }

    private static String createOfflineChain(String name, PrivateKey privateKey, byte[] publicKeyBytes)
            throws GeneralSecurityException {
        final String publicKey = Base64.getEncoder().encodeToString(publicKeyBytes);
        final String payload = "{\"extraData\":{\"displayName\":\"" + escape(name)
                + "\",\"identity\":\"" + UUID.randomUUID() + "\"},"
                + "\"identityPublicKey\":\"" + publicKey + "\"}";
        return "{\"chain\":[\"" + signJwt(payload, privateKey, publicKeyBytes) + "\"]}";
    }

    private static String signJwt(String payload, PrivateKey privateKey, byte[] publicKey)
            throws GeneralSecurityException {
        final String header = "{\"alg\":\"ES384\",\"typ\":\"JWT\",\"x5u\":\""
                + Base64.getEncoder().encodeToString(publicKey) + "\"}";
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        final String unsigned = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        final Signature signature = Signature.getInstance("SHA384withECDSA");
        signature.initSign(privateKey);
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + encoder.encodeToString(toJwsSignature(signature.sign()));
    }

    private static KeyPair generateGuestKey() throws GeneralSecurityException {
        final java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp384r1"));
        return generator.generateKeyPair();
    }

    private static byte[] toJwsSignature(byte[] der) throws GeneralSecurityException {
        if (der.length < 8 || der[0] != 0x30) {
            throw new GeneralSecurityException("Invalid ECDSA signature");
        }
        int cursor = 2;
        if ((der[1] & 0xff) >= 0x80) {
            cursor += (der[1] & 0x7f) - 1;
        }
        if (der[cursor++] != 0x02) {
            throw new GeneralSecurityException("Invalid ECDSA r");
        }
        int rLength = der[cursor++] & 0xff;
        byte[] r = java.util.Arrays.copyOfRange(der, cursor, cursor + rLength);
        cursor += rLength;
        if (der[cursor++] != 0x02) {
            throw new GeneralSecurityException("Invalid ECDSA s");
        }
        int sLength = der[cursor++] & 0xff;
        byte[] s = java.util.Arrays.copyOfRange(der, cursor, cursor + sLength);
        byte[] result = new byte[96];
        copyUnsignedInteger(r, result, 0, 48);
        copyUnsignedInteger(s, result, 48, 48);
        return result;
    }

    private static void copyUnsignedInteger(byte[] source, byte[] target, int targetOffset,
                                            int targetLength) {
        int sourceOffset = source.length > 1 && source[0] == 0 ? 1 : 0;
        int length = Math.min(source.length - sourceOffset, targetLength);
        System.arraycopy(source, source.length - length, target, targetOffset + targetLength - length, length);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String quoteJson(String value) {
        return "\"" + escape(value) + "\"";
    }

    public record LoginMaterial(byte[] loginPacket, PrivateKey privateKey,
                                byte[] publicKey) {
    }
}