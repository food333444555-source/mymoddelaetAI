package dev.funtime.pe.protocol;

import dev.funtime.pe.PeAccountState;
import dev.funtime.pe.PeServerEntry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class BedrockClient implements AutoCloseable {
    private final PeServerEntry server;
    private final PeAccountState account;
    private final Consumer<String> status;
    private RakNetTransport transport;
    private volatile boolean closed;

    private BedrockClient(PeServerEntry server, PeAccountState account, Consumer<String> status) {
        this.server = server;
        this.account = account;
        this.status = status;
    }

    public static CompletableFuture<BedrockClient> connect(
            PeServerEntry server, PeAccountState account, Consumer<String> status) {
        final BedrockClient client = new BedrockClient(server, account, status);
        status.accept("Подключение к RakNet " + server.host() + ":" + server.port() + "...");
        return RakNetTransport.connect(server.host(), server.port())
                .thenCompose(transport -> {
                    client.transport = transport;
                    return client.login();
                })
                .thenApply(ignored -> client);
    }

    private CompletableFuture<Void> login() {
        return CompletableFuture.runAsync(() -> {
            try {
                status.accept("RakNet готов, отправляю Bedrock login...");
                final byte[] requestNetworkSettings = new BedrockBuffer()
                        .writeByte(0xc1)
                        .writeVarInt(server.protocolVersion())
                        .toByteArray();
                sendBatch(List.of(requestNetworkSettings), false);

                final byte[] login = BedrockLoginFactory.createLogin(server, account);
                sendBatch(List.of(login), false);
                status.accept("Login отправлен. Ожидаю StartGame и registry...");
                readInitialPackets();
            } catch (IOException | java.security.GeneralSecurityException exception) {
                closeQuietly();
                throw new RuntimeException(exception);
            }
        });
    }

    private void readInitialPackets() throws IOException {
        final long deadline = System.nanoTime() + 10_000_000_000L;
        while (!closed && System.nanoTime() < deadline) {
            final byte[] payload = transport.receive(1500);
            if (payload.length == 0) {
                continue;
            }
            if ((payload[0] & 0xff) != 0xfe) {
                continue;
            }
            final List<byte[]> packets = BedrockBatchCodec.decode(
                    java.util.Arrays.copyOfRange(payload, 1, payload.length), false);
            for (byte[] packet : packets) {
                handlePacket(packet);
            }
        }
        if (!closed) {
            status.accept("Bedrock transport подключён; ожидание мира продолжается.");
        }
    }

    private void handlePacket(byte[] packet) {
        try {
            final BedrockBuffer.Reader reader = BedrockBuffer.reader(packet);
            final int packetId = reader.readVarInt();
            if (packetId == 0x02 && reader.remaining() > 0) {
                status.accept("Bedrock сервер принял login.");
            } else if (packetId == 0x0b) {
                status.accept("StartGame получен; можно строить слой мира.");
            } else if (packetId == 0x05) {
                status.accept("Bedrock disconnect: " + safeString(reader.readString()));
            }
        } catch (IOException exception) {
            status.accept("Пропущен повреждённый Bedrock packet: " + exception.getMessage());
        }
    }

    private static String safeString(String value) {
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private void sendBatch(List<byte[]> packets, boolean compressed) throws IOException {
        final byte[] batch = BedrockBatchCodec.encode(packets, compressed);
        final byte[] payload = new BedrockBuffer().writeByte(0xfe).writeBytes(batch).toByteArray();
        transport.sendReliable(payload);
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        closed = true;
        if (transport != null) {
            try {
                transport.close();
            } catch (IOException ignored) {
                // Closing a failed connection is best effort.
            }
        }
    }
}