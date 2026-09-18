package dev.funtime.pe.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;

public final class RakNetTransport implements AutoCloseable {
    private static final byte[] MAGIC = new byte[]{
            0x00, (byte) 0xff, (byte) 0xff, 0x00, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfe, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, 0x12, 0x34, 0x56, 0x78
    };
    private final DatagramChannel channel;
    private final InetSocketAddress address;
    private final long clientGuid;
    private int mtu = 1492;
    private int sendSequence;

    private RakNetTransport(DatagramChannel channel, InetSocketAddress address, long clientGuid) {
        this.channel = channel;
        this.address = address;
        this.clientGuid = clientGuid;
    }

    public static CompletableFuture<RakNetTransport> connect(String host, int port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final DatagramChannel channel = DatagramChannel.open();
                channel.configureBlocking(false);
                final InetSocketAddress address = new InetSocketAddress(host, port);
                final RakNetTransport transport = new RakNetTransport(
                        channel, address, ThreadLocalRandom.current().nextLong());
                transport.handshake();
                return transport;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    public synchronized void sendReliable(byte[] payload) throws IOException {
        final BedrockBuffer frame = new BedrockBuffer()
                .writeByte(0xc0)
                .writeShortBigEndian(payload.length * 8)
                .writeBytes(triad(sendSequence++))
                .writeBytes(payload);
        send(frame.toByteArray());
    }

    public byte[] receive(long timeoutMillis) throws IOException {
        final long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        final ByteBuffer buffer = ByteBuffer.allocate(2048);
        while (System.nanoTime() < deadline) {
            buffer.clear();
            if (channel.receive(buffer) != null) {
                final byte[] packet = Arrays.copyOf(buffer.array(), buffer.position());
                if (packet.length > 4 && (packet[0] & 0xff) >= 0x80) {
                    return extractFramePayload(packet);
                }
                return packet;
            }
            Thread.onSpinWait();
        }
        throw new IOException("Bedrock server did not answer within " + timeoutMillis + " ms");
    }

    private void handshake() throws IOException {
        final BedrockBuffer ping = new BedrockBuffer()
                .writeByte(0x01)
                .writeLongBigEndian(System.currentTimeMillis())
                .writeBytes(MAGIC)
                .writeLongBigEndian(clientGuid);
        send(ping.toByteArray());
        receiveUntil(0x1c, 2500);

        final BedrockBuffer open1 = new BedrockBuffer()
                .writeByte(0x05)
                .writeBytes(MAGIC)
                .writeByte(10)
                .writeShortBigEndian(mtu);
        send(open1.toByteArray());
        final byte[] response1 = receiveUntil(0x06, 2500);
        if (response1.length >= 29) {
            mtu = ((response1[response1.length - 3] & 0xff) << 8)
                    | (response1[response1.length - 2] & 0xff);
        }

        final BedrockBuffer open2 = new BedrockBuffer()
                .writeByte(0x07)
                .writeBytes(MAGIC)
                .writeByte(4)
                .writeBytes(new byte[]{0, 0, 0, 0})
                .writeShortBigEndian(address.getPort())
                .writeShortBigEndian(mtu)
                .writeLongBigEndian(clientGuid);
        send(open2.toByteArray());
        receiveUntil(0x08, 2500);

        final BedrockBuffer request = new BedrockBuffer()
                .writeByte(0x09)
                .writeLongBigEndian(clientGuid)
                .writeLongBigEndian(System.currentTimeMillis())
                .writeByte(0);
        send(request.toByteArray());
        receiveUntil(0x13, 2500);
    }

    private byte[] receiveUntil(int packetId, long timeoutMillis) throws IOException {
        final long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        final ByteBuffer buffer = ByteBuffer.allocate(2048).order(ByteOrder.BIG_ENDIAN);
        while (System.nanoTime() < deadline) {
            buffer.clear();
            if (channel.receive(buffer) == null) {
                Thread.onSpinWait();
                continue;
            }
            byte[] packet = Arrays.copyOf(buffer.array(), buffer.position());
            if ((packet[0] & 0xff) == packetId) {
                return packet;
            }
        }
        throw new IOException("RakNet handshake packet 0x"
                + Integer.toHexString(packetId) + " was not received");
    }

    private void send(byte[] payload) throws IOException {
        channel.send(ByteBuffer.wrap(payload), address);
    }

    private static byte[] triad(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16)};
    }

    private static byte[] extractFramePayload(byte[] packet) throws IOException {
        if (packet.length < 4) {
            throw new IOException("Short RakNet frame");
        }
        final int bitLength = ((packet[2] & 0xff) << 8) | (packet[3] & 0xff);
        final int payloadOffset = 4 + 3;
        final int payloadLength = (bitLength + 7) / 8;
        if (payloadOffset + payloadLength > packet.length) {
            throw new IOException("Invalid RakNet frame length");
        }
        return Arrays.copyOfRange(packet, payloadOffset, payloadOffset + payloadLength);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}