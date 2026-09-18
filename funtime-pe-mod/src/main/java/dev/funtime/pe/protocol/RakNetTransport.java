package dev.funtime.pe.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * RakNet session used by Bedrock.
 *
 * <p>This class deliberately owns the reliability layer instead of treating UDP
 * as if every datagram were a complete packet. It implements the parts needed
 * by a Bedrock client: connection handshake, reliable ordered frames, ACK/NAK,
 * retransmission, fragmentation and reassembly.</p>
 */
public final class RakNetTransport implements AutoCloseable {
    private static final byte[] MAGIC = new byte[]{
            0x00, (byte) 0xff, (byte) 0xff, 0x00, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfe, (byte) 0xfe, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
            (byte) 0xfd, 0x12, 0x34, 0x56, 0x78
    };

    private static final int DATA_PACKET = 0x80;
    private static final int ACK_PACKET = 0xc0;
    private static final int NAK_PACKET = 0xa0;
    private static final int RELIABLE_ORDERED = 3;
    private static final int RETRY_LIMIT = 10;
    private static final long RETRY_MILLIS = 250L;

    private final DatagramChannel channel;
    private final InetSocketAddress address;
    private final long clientGuid;
    private final ScheduledExecutorService retryExecutor;
    private final Map<Integer, PendingFrame> pending = new ConcurrentHashMap<>();
    private final Map<Integer, SplitFrame> splitFrames = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> orderedIncoming = new ConcurrentHashMap<>();
    private final Deque<byte[]> incoming = new ArrayDeque<>();
    private final Object incomingLock = new Object();
    private int mtu = 1492;
    private int datagramSequence;
    private int reliableMessageIndex;
    private int orderedMessageIndex;
    private int nextIncomingOrderIndex;
    private int splitId;
    private boolean hasIncomingSequence;
    private int highestIncomingSequence;
    private volatile boolean closed;

    private RakNetTransport(DatagramChannel channel, InetSocketAddress address, long clientGuid) {
        this.channel = channel;
        this.address = address;
        this.clientGuid = clientGuid;
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            final Thread thread = new Thread(task, "funtime-pe-raknet-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static CompletableFuture<RakNetTransport> connect(String host, int port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final DatagramChannel channel = DatagramChannel.open();
                channel.configureBlocking(false);
                final InetSocketAddress address = new InetSocketAddress(host, port);
                channel.connect(address);
                final RakNetTransport transport = new RakNetTransport(
                        channel, address, ThreadLocalRandom.current().nextLong());
                transport.handshake();
                transport.retryExecutor.scheduleAtFixedRate(
                        transport::retryExpiredFrames, RETRY_MILLIS, RETRY_MILLIS,
                        TimeUnit.MILLISECONDS);
                return transport;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    /**
     * Sends a reliable, ordered Bedrock batch. Large batches are split into
     * RakNet fragments and each fragment is acknowledged independently.
     */
    public synchronized void sendReliable(byte[] payload) throws IOException {
        ensureOpen();
        final int headerSize = 4 + 3 + 3 + 4 + 10;
        final int maxPayload = Math.max(256, mtu - headerSize);
        if (payload.length <= maxPayload) {
            sendFrame(payload, false, 0, 0, 0);
            return;
        }

        final int count = (payload.length + maxPayload - 1) / maxPayload;
        final int currentSplitId = splitId++ & 0xffff;
        for (int index = 0; index < count; index++) {
            final int offset = index * maxPayload;
            final int length = Math.min(maxPayload, payload.length - offset);
            sendFrame(Arrays.copyOfRange(payload, offset, offset + length),
                    true, count, currentSplitId, index);
        }
    }

    /**
     * Returns one complete encapsulated Bedrock payload. ACK/NAK packets and
     * fragments are consumed internally.
     */
    public byte[] receive(long timeoutMillis) throws IOException {
        ensureOpen();
        final long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (!closed && System.nanoTime() < deadline) {
            synchronized (incomingLock) {
                if (!incoming.isEmpty()) {
                    return incoming.removeFirst();
                }
            }

            final ByteBuffer buffer = ByteBuffer.allocate(Math.max(2048, mtu + 256));
            if (channel.receive(buffer) == null) {
                Thread.onSpinWait();
                continue;
            }
            buffer.flip();
            final byte[] packet = new byte[buffer.remaining()];
            buffer.get(packet);
            processDatagram(packet);
        }
        throw new IOException("Bedrock server did not answer within "
                + timeoutMillis + " ms");
    }

    private void handshake() throws IOException {
        send(new BedrockBuffer()
                .writeByte(0x01)
                .writeLongBigEndian(System.currentTimeMillis())
                .writeBytes(MAGIC)
                .writeLongBigEndian(clientGuid)
                .toByteArray());
        receiveUntil(0x1c, 3000);

        send(new BedrockBuffer()
                .writeByte(0x05)
                .writeBytes(MAGIC)
                .writeByte(11)
                .writeShortBigEndian(mtu)
                .writeBytes(new byte[mtu - 18])
                .toByteArray());
        final byte[] openReply = receiveUntil(0x06, 3000);
        if (openReply.length >= 28) {
            mtu = ((openReply[openReply.length - 3] & 0xff) << 8)
                    | (openReply[openReply.length - 2] & 0xff);
            mtu = Math.max(400, Math.min(1492, mtu));
        }

        send(new BedrockBuffer()
                .writeByte(0x07)
                .writeBytes(MAGIC)
                .writeByte(4)
                .writeBytes(new byte[]{0, 0, 0, 0})
                .writeShortBigEndian(address.getPort())
                .writeShortBigEndian(mtu)
                .writeLongBigEndian(clientGuid)
                .toByteArray());
        receiveUntil(0x08, 3000);

        send(new BedrockBuffer()
                .writeByte(0x09)
                .writeLongBigEndian(clientGuid)
                .writeLongBigEndian(System.currentTimeMillis())
                .writeByte(0)
                .toByteArray());
        final byte[] accepted = receiveUntil(0x10, 3000);
        sendNewIncomingConnection(accepted);
    }

    private void sendNewIncomingConnection(byte[] accepted) throws IOException {
        final BedrockBuffer packet = new BedrockBuffer()
                .writeByte(0x13)
                .writeBytes(rakNetAddress(address.getAddress().getAddress(), address.getPort()));
        for (int index = 0; index < 10; index++) {
            packet.writeBytes(rakNetAddress(new byte[]{0, 0, 0, 0}, 0));
        }
        packet.writeLongBigEndian(System.currentTimeMillis());
        packet.writeLongBigEndian(accepted.length >= 16
                ? readLongBigEndian(accepted, accepted.length - 8)
                : System.currentTimeMillis());
        send(packet.toByteArray());
    }

    private byte[] receiveUntil(int packetId, long timeoutMillis) throws IOException {
        final long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        final ByteBuffer buffer = ByteBuffer.allocate(2048);
        while (System.nanoTime() < deadline) {
            buffer.clear();
            if (channel.receive(buffer) == null) {
                Thread.onSpinWait();
                continue;
            }
            final int length = buffer.position();
            if (length == 0) {
                continue;
            }
            final byte[] packet = Arrays.copyOf(buffer.array(), length);
            if ((packet[0] & 0xff) == packetId) {
                return packet;
            }
        }
        throw new IOException("RakNet handshake packet 0x"
                + Integer.toHexString(packetId) + " was not received");
    }

    private void sendFrame(byte[] payload, boolean split, int splitCount,
                           int currentSplitId, int currentSplitIndex) throws IOException {
        final int messageIndex = reliableMessageIndex++ & 0xFFFFFF;
        final int orderIndex = orderedMessageIndex++ & 0xFFFFFF;
        final int sequence = datagramSequence++ & 0xFFFFFF;
        final BedrockBuffer frame = new BedrockBuffer()
                .writeByte((RELIABLE_ORDERED << 5) | (split ? 0x10 : 0))
                .writeShortBigEndian(payload.length * 8)
                .writeTriadLittleEndian(messageIndex)
                .writeTriadLittleEndian(orderIndex)
                .writeByte(0);
        if (split) {
            frame.writeIntBigEndian(splitCount)
                    .writeShortBigEndian(currentSplitId)
                    .writeIntBigEndian(currentSplitIndex);
        }
        frame.writeBytes(payload);

        final byte[] datagram = new BedrockBuffer()
                .writeByte(DATA_PACKET | 0x04)
                .writeTriadLittleEndian(sequence)
                .writeBytes(frame.toByteArray())
                .toByteArray();
        pending.put(sequence, new PendingFrame(sequence, datagram,
                System.nanoTime(), 0));
        send(datagram);
    }

    private void processDatagram(byte[] packet) throws IOException {
        if (packet.length == 0) {
            return;
        }
        final int id = packet[0] & 0xff;
        if (id == ACK_PACKET) {
            acknowledge(packet);
        } else if (id == NAK_PACKET) {
            resend(packet);
        } else if ((id & 0x80) != 0 && id < 0xa0) {
            processDataDatagram(packet);
        }
    }

    private void processDataDatagram(byte[] datagram) throws IOException {
        if (datagram.length < 4) {
            throw new IOException("Short RakNet data datagram");
        }
        final int sequence = triad(datagram, 1);
        sendAck(sequence);
        detectMissingDatagrams(sequence);
        int offset = 4;
        while (offset < datagram.length) {
            final int start = offset;
            final int header = datagram[offset++] & 0xff;
            final int reliability = (header & 0xe0) >>> 5;
            final boolean split = (header & 0x10) != 0;
            if (offset + 2 > datagram.length) {
                throw new IOException("Truncated RakNet encapsulated header");
            }
            final int bitLength = unsignedShort(datagram, offset);
            offset += 2;
            final boolean reliable = reliability == 2 || reliability == 3
                    || reliability == 4 || reliability == 6 || reliability == 7;
            final boolean ordered = reliability == 3 || reliability == 4
                    || reliability == 7;
            if (reliable) {
                offset += 3;
            }
            if (reliability == 1 || reliability == 4) {
                offset += 3;
            }
            int orderIndex = 0;
            if (ordered) {
                if (offset + 4 > datagram.length) {
                    throw new IOException("Truncated RakNet ordering header");
                }
                orderIndex = triad(datagram, offset);
                offset += 4;
            }
            int splitCount = 0;
            int splitNumber = 0;
            int splitIndex = 0;
            if (split) {
                if (offset + 10 > datagram.length) {
                    throw new IOException("Truncated RakNet split header");
                }
                splitCount = readIntBigEndian(datagram, offset);
                splitNumber = unsignedShort(datagram, offset + 4);
                splitIndex = readIntBigEndian(datagram, offset + 6);
                offset += 10;
            }
            final int contentLength = (bitLength + 7) / 8;
            if (contentLength < 0 || offset + contentLength > datagram.length) {
                throw new IOException("Invalid RakNet encapsulated length at " + start);
            }
            final byte[] content = Arrays.copyOfRange(datagram, offset,
                    offset + contentLength);
            offset += contentLength;
            if (split) {
                acceptSplit(splitNumber, splitCount, splitIndex, content, orderIndex);
            } else {
                enqueueOrdered(content, orderIndex, ordered);
            }
        }
    }

    /**
     * RakNet uses NAKs to tell the peer which datagrams are missing. Without
     * this, a single dropped ordered datagram can leave the peer's ordered
     * stream waiting forever. The first sequence establishes the baseline;
     * subsequent forward gaps are reported immediately.
     */
    private void detectMissingDatagrams(int sequence) throws IOException {
        if (!hasIncomingSequence) {
            hasIncomingSequence = true;
            highestIncomingSequence = sequence;
            return;
        }

        final int distance = (sequence - highestIncomingSequence) & 0xFFFFFF;
        if (distance > 0 && distance < 0x800000) {
            if (distance > 1) {
                sendNakRange((highestIncomingSequence + 1) & 0xFFFFFF,
                        (sequence - 1) & 0xFFFFFF);
            }
            highestIncomingSequence = sequence;
        }
    }

    private void acceptSplit(int id, int count, int index, byte[] part, int orderIndex) {
        if (count <= 0 || count > 4096 || index < 0 || index >= count) {
            return;
        }
        final SplitFrame split = splitFrames.computeIfAbsent(id,
                ignored -> new SplitFrame(count, orderIndex));
        split.parts[index] = part;
        split.received++;
        if (split.received != split.count) {
            return;
        }
        final int length = Arrays.stream(split.parts)
                .mapToInt(value -> value == null ? 0 : value.length).sum();
        final byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : split.parts) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        splitFrames.remove(id);
        enqueueOrdered(result, split.orderIndex, true);
    }

    private void enqueueOrdered(byte[] payload, int orderIndex, boolean ordered) {
        synchronized (incomingLock) {
            if (!ordered) {
                incoming.addLast(payload);
            } else {
                final int expected = nextIncomingOrderIndex & 0xFFFFFF;
                if (orderIndex == expected) {
                    incoming.addLast(payload);
                    nextIncomingOrderIndex = (nextIncomingOrderIndex + 1) & 0xFFFFFF;
                    drainOrderedQueue();
                } else if (isAhead(orderIndex, expected)) {
                    orderedIncoming.putIfAbsent(orderIndex, payload);
                }
            }
            incomingLock.notifyAll();
        }
    }

    private void drainOrderedQueue() {
        while (true) {
            final int expected = nextIncomingOrderIndex & 0xFFFFFF;
            final byte[] next = orderedIncoming.remove(expected);
            if (next == null) {
                return;
            }
            incoming.addLast(next);
            nextIncomingOrderIndex = (nextIncomingOrderIndex + 1) & 0xFFFFFF;
        }
    }

    private static boolean isAhead(int value, int expected) {
        final int distance = (value - expected) & 0xFFFFFF;
        return distance > 0 && distance < 0x800000;
    }

    private void sendAck(int sequence) throws IOException {
        send(new BedrockBuffer()
                .writeByte(ACK_PACKET)
                .writeShortBigEndian(1)
                .writeByte(0)
                .writeTriadLittleEndian(sequence)
                .toByteArray());
    }

    private void sendNakRange(int first, int last) throws IOException {
        send(new BedrockBuffer()
                .writeByte(NAK_PACKET)
                .writeShortBigEndian(1)
                .writeByte(0)
                .writeTriadLittleEndian(first)
                .writeTriadLittleEndian(last)
                .toByteArray());
    }

    private void acknowledge(byte[] packet) throws IOException {
        for (int[] range : readRanges(packet)) {
            for (int sequence = range[0]; sequence <= range[1]; sequence++) {
                pending.remove(sequence & 0xFFFFFF);
                if (sequence == range[1]) {
                    break;
                }
            }
        }
    }

    private void resend(byte[] packet) throws IOException {
        for (int[] range : readRanges(packet)) {
            for (int sequence = range[0]; sequence <= range[1]; sequence++) {
                final PendingFrame frame = pending.get(sequence & 0xFFFFFF);
                if (frame != null) {
                    send(frame.datagram);
                }
                if (sequence == range[1]) {
                    break;
                }
            }
        }
    }

    private static ArrayList<int[]> readRanges(byte[] packet) throws IOException {
        if (packet.length < 3) {
            throw new IOException("Short RakNet ACK/NAK packet");
        }
        final int count = unsignedShort(packet, 1);
        int offset = 3;
        final ArrayList<int[]> ranges = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (offset >= packet.length) {
                throw new IOException("Truncated RakNet ACK/NAK range");
            }
            final boolean single = packet[offset++] == 1;
            if (single) {
                if (offset + 3 > packet.length) {
                    throw new IOException("Truncated RakNet ACK/NAK sequence");
                }
                final int sequence = triad(packet, offset);
                offset += 3;
                ranges.add(new int[]{sequence, sequence});
            } else {
                if (offset + 6 > packet.length) {
                    throw new IOException("Truncated RakNet ACK/NAK range");
                }
                final int first = triad(packet, offset);
                final int last = triad(packet, offset + 3);
                offset += 6;
                ranges.add(new int[]{first, last});
            }
        }
        return ranges;
    }

    private void retryExpiredFrames() {
        if (closed) {
            return;
        }
        final long now = System.nanoTime();
        for (PendingFrame frame : pending.values()) {
            if (now - frame.lastSentNanos < Duration.ofMillis(RETRY_MILLIS).toNanos()) {
                continue;
            }
            if (frame.retries >= RETRY_LIMIT) {
                pending.remove(frame.sequence);
                continue;
            }
            try {
                send(frame.datagram);
                pending.put(frame.sequence, frame.nextRetry());
            } catch (IOException ignored) {
                // The foreground receive loop will report a closed socket.
            }
        }
    }

    private void send(byte[] payload) throws IOException {
        channel.write(ByteBuffer.wrap(payload));
    }

    private void ensureOpen() throws IOException {
        if (closed || !channel.isOpen()) {
            throw new IOException("RakNet transport is closed");
        }
    }

    private static byte[] rakNetAddress(byte[] address, int port) {
        final byte[] value = address.length == 4 ? address : new byte[]{0, 0, 0, 0};
        return new BedrockBuffer()
                .writeByte(4)
                .writeByte(~value[0])
                .writeByte(~value[1])
                .writeByte(~value[2])
                .writeByte(~value[3])
                .writeShortBigEndian(port)
                .toByteArray();
    }

    private static int unsignedShort(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 8) | (value[offset + 1] & 0xff);
    }

    private static int triad(byte[] value, int offset) {
        if (offset < 0 || offset + 3 > value.length) {
            return 0;
        }
        return (value[offset] & 0xff)
                | ((value[offset + 1] & 0xff) << 8)
                | ((value[offset + 2] & 0xff) << 16);
    }

    private static int readIntBigEndian(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }

    private static long readLongBigEndian(byte[] value, int offset) {
        long result = 0;
        for (int index = 0; index < 8; index++) {
            result = (result << 8) | (value[offset + index] & 0xffL);
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        retryExecutor.shutdownNow();
        pending.clear();
        splitFrames.clear();
        orderedIncoming.clear();
        channel.close();
    }

    private record PendingFrame(int sequence, byte[] datagram, long lastSentNanos,
                                int retries) {
        private PendingFrame nextRetry() {
            return new PendingFrame(sequence, datagram, System.nanoTime(), retries + 1);
        }
    }

    private static final class SplitFrame {
        private final int count;
        private final int orderIndex;
        private final byte[][] parts;
        private int received;

        private SplitFrame(int count, int orderIndex) {
            this.count = count;
            this.orderIndex = orderIndex;
            this.parts = new byte[count][];
        }
    }
}