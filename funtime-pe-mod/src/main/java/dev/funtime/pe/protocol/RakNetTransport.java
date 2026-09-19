package dev.funtime.pe.protocol;

import java.io.IOException;
import java.net.InetAddress;
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
 * <p>Owns the UDP handshake and reliable ordered frame layer. The transport
 * deliberately validates every variable-length header before reading it so a
 * malformed datagram cannot desynchronise the session.</p>
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
    private static final int MAX_DATAGRAM = 1492;
    private static final int MAX_SPLIT_COUNT = 4096;
    private static final int TRIAD_MASK = 0xFFFFFF;

    private final DatagramChannel channel;
    private final InetSocketAddress address;
    private final long clientGuid;
    private final ScheduledExecutorService retryExecutor;
    private final Map<Integer, PendingFrame> pending = new ConcurrentHashMap<>();
    private final Map<Integer, SplitFrame> splitFrames = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> orderedIncoming = new ConcurrentHashMap<>();
    private final Deque<byte[]> incoming = new ArrayDeque<>();
    private final Object incomingLock = new Object();
    private int mtu = MAX_DATAGRAM;
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
            DatagramChannel channel = null;
            RakNetTransport transport = null;
            try {
                if (host == null || host.isBlank() || port < 1 || port > 65535) {
                    throw new IOException("Неверный адрес Bedrock-сервера");
                }
                final InetAddress resolved = InetAddress.getByName(host);
                final InetSocketAddress address = new InetSocketAddress(resolved, port);
                channel = DatagramChannel.open();
                channel.configureBlocking(false);
                channel.connect(address);
                transport = new RakNetTransport(channel, address, ThreadLocalRandom.current().nextLong());
                transport.handshake();
                final RakNetTransport connected = transport;
                connected.retryExecutor.scheduleAtFixedRate(
                        connected::retryExpiredFrames, RETRY_MILLIS, RETRY_MILLIS, TimeUnit.MILLISECONDS);
                return connected;
            } catch (IOException | RuntimeException exception) {
                if (transport != null) {
                    try { transport.close(); } catch (IOException ignored) { }
                } else if (channel != null) {
                    try { channel.close(); } catch (IOException ignored) { }
                }
                throw new CompletionException(exception);
            }
        });
    }

    public synchronized void sendReliable(byte[] payload) throws IOException {
        ensureOpen();
        if (payload == null || payload.length == 0) {
            throw new IOException("Пустой RakNet payload");
        }
        final int maxPayload = Math.max(256, mtu - 32);
        final int orderIndex = orderedMessageIndex++ & TRIAD_MASK;
        if (payload.length <= maxPayload) {
            sendFrame(payload, false, 0, 0, 0, orderIndex);
            return;
        }
        final int count = (payload.length + maxPayload - 1) / maxPayload;
        if (count > MAX_SPLIT_COUNT) {
            throw new IOException("RakNet payload слишком большой");
        }
        final int currentSplitId = splitId++ & 0xffff;
        for (int index = 0; index < count; index++) {
            final int offset = index * maxPayload;
            final int length = Math.min(maxPayload, payload.length - offset);
            sendFrame(Arrays.copyOfRange(payload, offset, offset + length), true,
                    count, currentSplitId, index, orderIndex);
        }
    }

    public byte[] receive(long timeoutMillis) throws IOException {
        ensureOpen();
        final long deadline = System.nanoTime() + Duration.ofMillis(Math.max(1, timeoutMillis)).toNanos();
        while (!closed && System.nanoTime() < deadline) {
            synchronized (incomingLock) {
                if (!incoming.isEmpty()) return incoming.removeFirst();
            }
            final ByteBuffer buffer = ByteBuffer.allocate(Math.max(2048, mtu + 256));
            if (channel.receive(buffer) == null) {
                pauseReceiver();
                continue;
            }
            buffer.flip();
            final byte[] packet = new byte[buffer.remaining()];
            buffer.get(packet);
            processDatagram(packet);
        }
        throw new IOException("Bedrock server did not answer within " + timeoutMillis + " ms");
    }

    private void handshake() throws IOException {
        send(new BedrockBuffer().writeByte(0x01).writeLongBigEndian(System.currentTimeMillis())
                .writeBytes(MAGIC).writeLongBigEndian(clientGuid).toByteArray());
        receiveUntil(0x1c, 5000);

        send(new BedrockBuffer().writeByte(0x05).writeBytes(MAGIC).writeByte(11)
                .writeShortBigEndian(mtu).writeBytes(new byte[mtu - 18]).toByteArray());
        final byte[] openReply = receiveUntil(0x06, 5000);
        final int negotiated = readMtu(openReply);
        if (negotiated >= 400) mtu = Math.min(MAX_DATAGRAM, negotiated);

        send(new BedrockBuffer().writeByte(0x07).writeBytes(MAGIC).writeByte(4)
                .writeBytes(new byte[]{0, 0, 0, 0}).writeShortBigEndian(address.getPort())
                .writeShortBigEndian(mtu).writeLongBigEndian(clientGuid).toByteArray());
        receiveUntil(0x08, 5000);

        send(new BedrockBuffer().writeByte(0x09).writeLongBigEndian(clientGuid)
                .writeLongBigEndian(System.currentTimeMillis()).writeByte(0).toByteArray());
        final byte[] accepted = receiveUntil(0x10, 5000);
        sendNewIncomingConnection(accepted);
    }

    private static int readMtu(byte[] reply) {
        if (reply.length < 2) return 0;
        for (int offset = reply.length - 2; offset >= 0; offset--) {
            final int value = ((reply[offset] & 0xff) << 8) | (reply[offset + 1] & 0xff);
            if (value >= 400 && value <= MAX_DATAGRAM) return value;
        }
        return 0;
    }

    private void sendNewIncomingConnection(byte[] accepted) throws IOException {
        final BedrockBuffer packet = new BedrockBuffer().writeByte(0x13)
                .writeBytes(rakNetAddress(address.getAddress().getAddress(), address.getPort()));
        for (int index = 0; index < 10; index++) {
            packet.writeBytes(rakNetAddress(new byte[]{0, 0, 0, 0}, 0));
        }
        packet.writeLongBigEndian(System.currentTimeMillis())
                .writeLongBigEndian(accepted.length >= 8
                        ? readLongBigEndian(accepted, accepted.length - 8) : System.currentTimeMillis());
        send(packet.toByteArray());
    }

    private byte[] receiveUntil(int packetId, long timeoutMillis) throws IOException {
        final long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        final ByteBuffer buffer = ByteBuffer.allocate(2048);
        while (System.nanoTime() < deadline) {
            buffer.clear();
            if (channel.receive(buffer) == null) { pauseReceiver(); continue; }
            final int length = buffer.position();
            if (length > 0 && (buffer.get(0) & 0xff) == packetId) {
                return Arrays.copyOf(buffer.array(), length);
            }
        }
        throw new IOException("RakNet handshake packet 0x" + Integer.toHexString(packetId) + " was not received");
    }

    private void sendFrame(byte[] payload, boolean split, int splitCount, int currentSplitId,
                           int currentSplitIndex, int orderIndex) throws IOException {
        final int messageIndex = reliableMessageIndex++ & TRIAD_MASK;
        final int sequence = datagramSequence++ & TRIAD_MASK;
        final BedrockBuffer frame = new BedrockBuffer().writeByte((RELIABLE_ORDERED << 5) | (split ? 0x10 : 0))
                .writeShortBigEndian(payload.length * 8).writeTriadLittleEndian(messageIndex)
                .writeTriadLittleEndian(orderIndex).writeByte(0);
        if (split) frame.writeIntBigEndian(splitCount).writeShortBigEndian(currentSplitId)
                .writeIntBigEndian(currentSplitIndex);
        frame.writeBytes(payload);
        final byte[] datagram = new BedrockBuffer().writeByte(DATA_PACKET | 0x04)
                .writeTriadLittleEndian(sequence).writeBytes(frame.toByteArray()).toByteArray();
        pending.put(sequence, new PendingFrame(sequence, datagram, System.nanoTime(), 0));
        send(datagram);
    }

    private void processDatagram(byte[] packet) throws IOException {
        if (packet.length == 0) return;
        final int id = packet[0] & 0xff;
        if (id == ACK_PACKET) acknowledge(packet);
        else if (id == NAK_PACKET) resend(packet);
        else if ((id & 0x80) != 0 && id < 0xa0) processDataDatagram(packet);
    }

    private void processDataDatagram(byte[] datagram) throws IOException {
        if (datagram.length < 4) throw new IOException("Short RakNet data datagram");
        final int sequence = triad(datagram, 1);
        sendAck(sequence);
        detectMissingDatagrams(sequence);
        int offset = 4;
        while (offset < datagram.length) {
            final int start = offset;
            final int header = datagram[offset++] & 0xff;
            final int reliability = (header & 0xe0) >>> 5;
            final boolean split = (header & 0x10) != 0;
            if (reliability > 7 || offset + 2 > datagram.length) throw new IOException("Invalid RakNet frame");
            final int bitLength = unsignedShort(datagram, offset); offset += 2;
            final boolean reliable = reliability == 2 || reliability == 3 || reliability == 4 || reliability == 6 || reliability == 7;
            final boolean ordered = reliability == 3 || reliability == 4 || reliability == 7;
            if (reliable) { if (offset + 3 > datagram.length) throw new IOException("Truncated reliable header"); offset += 3; }
            if (reliability == 1 || reliability == 4) { if (offset + 3 > datagram.length) throw new IOException("Truncated sequenced header"); offset += 3; }
            int orderIndex = 0;
            if (ordered) { if (offset + 4 > datagram.length) throw new IOException("Truncated ordered header"); orderIndex = triad(datagram, offset); offset += 4; }
            int splitCount = 0, splitNumber = 0, splitIndex = 0;
            if (split) {
                if (offset + 10 > datagram.length) throw new IOException("Truncated split header");
                splitCount = readIntBigEndian(datagram, offset); splitNumber = unsignedShort(datagram, offset + 4); splitIndex = readIntBigEndian(datagram, offset + 6); offset += 10;
            }
            final int contentLength = (bitLength + 7) / 8;
            if (contentLength < 0 || offset + contentLength > datagram.length) throw new IOException("Invalid frame length at " + start);
            final byte[] content = Arrays.copyOfRange(datagram, offset, offset + contentLength);
            offset += contentLength;
            if (split) acceptSplit(splitNumber, splitCount, splitIndex, content, orderIndex);
            else enqueueOrdered(content, orderIndex, ordered);
        }
    }

    private void detectMissingDatagrams(int sequence) throws IOException {
        if (!hasIncomingSequence) { hasIncomingSequence = true; highestIncomingSequence = sequence; return; }
        final int distance = (sequence - highestIncomingSequence) & TRIAD_MASK;
        if (distance > 0 && distance < 0x800000) {
            if (distance > 1) sendNakRange((highestIncomingSequence + 1) & TRIAD_MASK, (sequence - 1) & TRIAD_MASK);
            highestIncomingSequence = sequence;
        }
    }

    private void acceptSplit(int id, int count, int index, byte[] part, int orderIndex) {
        if (count <= 0 || count > MAX_SPLIT_COUNT || index < 0 || index >= count) return;
        final SplitFrame split = splitFrames.computeIfAbsent(id, ignored -> new SplitFrame(count, orderIndex));
        if (split.count != count || split.parts[index] != null) return;
        split.parts[index] = part; split.received++;
        if (split.received != split.count) return;
        int length = 0; for (byte[] value : split.parts) length += value.length;
        final byte[] result = new byte[length]; int offset = 0;
        for (byte[] value : split.parts) { System.arraycopy(value, 0, result, offset, value.length); offset += value.length; }
        splitFrames.remove(id); enqueueOrdered(result, split.orderIndex, true);
    }

    private void enqueueOrdered(byte[] payload, int orderIndex, boolean ordered) {
        synchronized (incomingLock) {
            if (!ordered) incoming.addLast(payload);
            else {
                final int expected = nextIncomingOrderIndex & TRIAD_MASK;
                if (orderIndex == expected) { incoming.addLast(payload); nextIncomingOrderIndex = (nextIncomingOrderIndex + 1) & TRIAD_MASK; drainOrderedQueue(); }
                else if (isAhead(orderIndex, expected)) orderedIncoming.putIfAbsent(orderIndex, payload);
            }
            incomingLock.notifyAll();
        }
    }

    private void drainOrderedQueue() {
        while (true) {
            final byte[] next = orderedIncoming.remove(nextIncomingOrderIndex & TRIAD_MASK);
            if (next == null) return;
            incoming.addLast(next); nextIncomingOrderIndex = (nextIncomingOrderIndex + 1) & TRIAD_MASK;
        }
    }

    private static boolean isAhead(int value, int expected) { final int distance = (value - expected) & TRIAD_MASK; return distance > 0 && distance < 0x800000; }

    private void sendAck(int sequence) throws IOException { send(new BedrockBuffer().writeByte(ACK_PACKET).writeShortBigEndian(1).writeByte(1).writeTriadLittleEndian(sequence).toByteArray()); }
    private void sendNakRange(int first, int last) throws IOException { send(new BedrockBuffer().writeByte(NAK_PACKET).writeShortBigEndian(1).writeByte(0).writeTriadLittleEndian(first).writeTriadLittleEndian(last).toByteArray()); }

    private void acknowledge(byte[] packet) throws IOException { for (int[] range : readRanges(packet)) for (int sequence = range[0]; ; sequence = (sequence + 1) & TRIAD_MASK) { pending.remove(sequence); if (sequence == range[1]) break; } }
    private void resend(byte[] packet) throws IOException { for (int[] range : readRanges(packet)) for (int sequence = range[0]; ; sequence = (sequence + 1) & TRIAD_MASK) { final PendingFrame frame = pending.get(sequence); if (frame != null) send(frame.datagram); if (sequence == range[1]) break; } }

    private static ArrayList<int[]> readRanges(byte[] packet) throws IOException {
        if (packet.length < 3) throw new IOException("Short RakNet ACK/NAK packet");
        final int count = unsignedShort(packet, 1); int offset = 3; final ArrayList<int[]> ranges = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (offset >= packet.length) throw new IOException("Truncated ACK/NAK range");
            final boolean single = packet[offset++] != 0;
            if (single) { if (offset + 3 > packet.length) throw new IOException("Truncated ACK/NAK sequence"); final int value = triad(packet, offset); offset += 3; ranges.add(new int[]{value, value}); }
            else { if (offset + 6 > packet.length) throw new IOException("Truncated ACK/NAK range"); final int first = triad(packet, offset), last = triad(packet, offset + 3); offset += 6; ranges.add(new int[]{first, last}); }
        }
        return ranges;
    }

    private void retryExpiredFrames() {
        if (closed) return;
        final long now = System.nanoTime();
        for (PendingFrame frame : pending.values()) {
            if (now - frame.lastSentNanos < Duration.ofMillis(RETRY_MILLIS).toNanos()) continue;
            if (frame.retries >= RETRY_LIMIT) { pending.remove(frame.sequence); continue; }
            try { send(frame.datagram); pending.put(frame.sequence, frame.nextRetry()); } catch (IOException ignored) { }
        }
    }

    private void send(byte[] payload) throws IOException { if (channel.write(ByteBuffer.wrap(payload)) != payload.length) throw new IOException("Неполная отправка RakNet datagram"); }
    private static void pauseReceiver() throws IOException { try { Thread.sleep(1L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Ожидание Bedrock-пакета прервано", interrupted); } }
    private void ensureOpen() throws IOException { if (closed || !channel.isOpen()) throw new IOException("RakNet transport is closed"); }
    private static byte[] rakNetAddress(byte[] address, int port) { final byte[] value = address.length == 4 ? address : new byte[]{0,0,0,0}; return new BedrockBuffer().writeByte(4).writeByte(~value[0]).writeByte(~value[1]).writeByte(~value[2]).writeByte(~value[3]).writeShortBigEndian(port).toByteArray(); }
    private static int unsignedShort(byte[] value, int offset) { return ((value[offset] & 0xff) << 8) | (value[offset + 1] & 0xff); }
    private static int triad(byte[] value, int offset) { return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8) | ((value[offset + 2] & 0xff) << 16); }
    private static int readIntBigEndian(byte[] value, int offset) { return ((value[offset]&0xff)<<24)|((value[offset+1]&0xff)<<16)|((value[offset+2]&0xff)<<8)|(value[offset+3]&0xff); }
    private static long readLongBigEndian(byte[] value, int offset) { long result=0; for(int index=0;index<8;index++) result=(result<<8)|(value[offset+index]&0xffL); return result; }

    @Override public void close() throws IOException { closed=true; retryExecutor.shutdownNow(); pending.clear(); splitFrames.clear(); orderedIncoming.clear(); synchronized(incomingLock){incoming.clear();} channel.close(); }
    private record PendingFrame(int sequence, byte[] datagram, long lastSentNanos, int retries) { private PendingFrame nextRetry(){return new PendingFrame(sequence,datagram,System.nanoTime(),retries+1);} }
    private static final class SplitFrame { private final int count; private final int orderIndex; private final byte[][] parts; private int received; private SplitFrame(int count,int orderIndex){this.count=count;this.orderIndex=orderIndex;this.parts=new byte[count][];} }
}
