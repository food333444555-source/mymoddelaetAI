package dev.funtime.pe.protocol;

import dev.funtime.pe.PeAccountState;
import dev.funtime.pe.PeServerEntry;
import dev.funtime.pe.world.BedrockPlayDecoder;
import dev.funtime.pe.world.BedrockWorldState;
import dev.funtime.pe.world.JavaInventoryState;
import dev.funtime.pe.world.JavaItemStack;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Bedrock transport and login state machine for the PE session.
 *
 * <p>This driver intentionally keeps the Bedrock packet flow explicit so we can
 * identify exactly which stage fails during a real login attempt: RakNet setup,
 * network settings, login, handshake, resource-pack negotiation, or world start.
 */
public final class BedrockClient implements AutoCloseable {
    private static final int BEDROCK_BATCH_ID = 0xfe;
    private static final boolean ENABLE_PACKET_TRACE = true;

    private final PeServerEntry server;
    private final PeAccountState account;
    private final Consumer<String> status;
    private final BedrockWorldState worldState = new BedrockWorldState();
    private final JavaInventoryState inventory = new JavaInventoryState();
    private final List<Consumer<byte[]>> packetListeners = new CopyOnWriteArrayList<>();

    private RakNetTransport transport;
    private volatile boolean closed;
    private volatile boolean compressed;
    private volatile int compressionAlgorithm = 2;
    private volatile int compressionThreshold;
    private volatile boolean joined;
    private volatile boolean resourcePackInfoReceived;
    private volatile boolean resourcePackStackReceived;
    private volatile boolean resourcePackCompletedResponseSent;
    private BedrockCrypto crypto;
    private final BedrockResourcePackTransfer resourcePacks = new BedrockResourcePackTransfer();

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

    public BedrockWorldState worldState() {
        return worldState;
    }

    public JavaInventoryState inventory() {
        return inventory;
    }

    public boolean isJoined() {
        return joined && !closed;
    }

    public void addPacketListener(Consumer<byte[]> listener) {
        if (listener != null) {
            packetListeners.add(listener);
        }
    }

    private CompletableFuture<Void> login() {
        return CompletableFuture.runAsync(() -> {
            try {
                status.accept("RakNet установлен. Запрашиваю настройки Bedrock...");
                sendBatch(List.of(new BedrockBuffer()
                        .writeByte(BedrockPacketIds.REQUEST_NETWORK_SETTINGS)
                        .writeIntLittleEndian(server.protocolVersion())
                        .toByteArray()), false);

                boolean loginSent = false;
                BedrockLoginFactory.LoginMaterial loginMaterial = null;
                final long deadline = System.nanoTime() + 45_000_000_000L;
                while (!closed && System.nanoTime() < deadline) {
                    for (byte[] packet : receiveBatch(Math.max(1, (int) Math.min(3000,
                            Math.max(1, (deadline - System.nanoTime()) / 1_000_000L))))) {
                        if (packet == null || packet.length == 0) {
                            continue;
                        }
                        tracePacket("incoming", packet);
                        final BedrockBuffer.Reader reader = BedrockBuffer.reader(packet);
                        final int packetId;
                        try {
                            packetId = reader.readVarInt();
                        } catch (IOException exception) {
                            status.accept("Невалидный packet id: " + safeString(exception.getMessage()));
                            continue;
                        }

                        if (packetId == BedrockPacketIds.NETWORK_SETTINGS && !loginSent) {
                            readNetworkSettings(reader);
                            loginMaterial = BedrockLoginFactory.createLoginMaterial(server, account);
                            sendBatch(List.of(loginMaterial.loginPacket()), true);
                            loginSent = true;
                            status.accept("NetworkSettings получен. Bedrock login отправлен.");
                            continue;
                        }
                        if (!loginSent) {
                            continue;
                        }

                        if (packetId == BedrockPacketIds.RESOURCE_PACKS_INFO) {
                            resourcePackInfoReceived = true;
                            final List<String> packIds = readResourcePackIds(reader);
                            resourcePacks.expect(packIds.size());
                            if (packIds.isEmpty()) {
                                sendResourcePackResponse(3, List.of());
                                status.accept("ResourcePacksInfo получен. Пакеты не требуются.");
                            } else {
                                sendResourcePackResponse(2, packIds);
                                status.accept("Запрошена загрузка " + packIds.size() + " resource-pack.");
                            }
                            continue;
                        }
                        if (packetId == BedrockPacketIds.RESOURCE_PACK_STACK) {
                            resourcePackStackReceived = true;
                            if (resourcePacks.allComplete()) {
                                sendResourcePackCompleted();
                            }
                            continue;
                        }
                        if (packetId == BedrockPacketIds.RESOURCE_PACK_DATA_INFO) {
                            final String packId = resourcePacks.acceptDataInfo(reader);
                            final byte[] request = resourcePacks.nextChunkRequest(packId);
                            if (request != null) {
                                sendResourcePackChunkRequest(request);
                            }
                            continue;
                        }
                        if (packetId == BedrockPacketIds.RESOURCE_PACK_CHUNK_DATA) {
                            final String packId = resourcePacks.acceptChunkData(reader);
                            final byte[] request = resourcePacks.nextChunkRequest(packId);
                            if (request != null && !resourcePacks.allComplete()) {
                                sendResourcePackChunkRequest(request);
                            }
                            if (resourcePacks.allComplete() && resourcePackStackReceived) {
                                sendResourcePackCompleted();
                            }
                            continue;
                        }
                        if (packetId == BedrockPacketIds.PLAY_STATUS) {
                            final int playStatus = reader.remaining() >= 4 ? reader.readIntLittleEndian() : -1;
                            if (playStatus == 0) {
                                status.accept("Авторизация принята. Ожидаю готовность мира...");
                            } else if (playStatus == 3) {
                                status.accept("Игрок создан.");
                            } else {
                                throw new IOException("Bedrock PlayStatus отказал во входе: " + playStatus);
                            }
                            continue;
                        }
                        if (packetId == BedrockPacketIds.SERVER_TO_CLIENT_HANDSHAKE) {
                            if (loginMaterial == null || reader.remaining() == 0) {
                                throw new IOException("Пустой Bedrock encryption handshake");
                            }
                            final String serverToken = reader.readString();
                            crypto = BedrockCrypto.fromServerToken(serverToken,
                                    loginMaterial.privateKey(), compressionAlgorithm);
                            final String clientToken = BedrockCrypto.createClientHandshake(loginMaterial);
                            sendBatch(List.of(new BedrockBuffer()
                                    .writeByte(BedrockPacketIds.CLIENT_TO_SERVER_HANDSHAKE)
                                    .writeString(clientToken)
                                    .toByteArray()), true);
                            status.accept("Защищённое соединение включено.");
                            continue;
                        }
                        if (packetId == BedrockPacketIds.START_GAME) {
                            status.accept("Получен StartGame. Начинаем world bootstrap...");
                            BedrockPlayDecoder.accept(packet, worldState, inventory, status);
                            sendClientCacheStatus();
                            sendRequestChunkRadius(10);
                            sendLocalPlayerInitialized();
                            joined = true;
                            status.accept("Мир готов. Игровая сессия создана.");
                            notifyPacket(packet);
                            CompletableFuture.runAsync(this::playLoop);
                            if (!resourcePackInfoReceived) {
                                status.accept("Сервер не прислал resource-pack negotiation.");
                            } else if (resourcePackInfoReceived && !resourcePackStackReceived) {
                                status.accept("Сервер не прислал resource-pack stack.");
                            }
                            return;
                        }
                        if (packetId == BedrockPacketIds.DISCONNECT) {
                            throw new IOException("Bedrock disconnect: "
                                    + safeString(reader.remaining() > 0 ? reader.readString() : "без причины"));
                        }
                        if (packetId == BedrockPacketIds.NETWORK_STACK_LATENCY) {
                            sendLatencyResponse(reader);
                        }
                    }
                }
                throw new IOException("Сервер не завершил Bedrock login за 45 секунд");
            } catch (IOException | GeneralSecurityException exception) {
                closeQuietly();
                throw new CompletionException(exception);
            }
        });
    }

    private void playLoop() {
        while (!closed) {
            try {
                for (byte[] packet : receiveBatch(5000)) {
                    final BedrockBuffer.Reader idReader = BedrockBuffer.reader(packet);
                    final int packetId;
                    try {
                        packetId = idReader.readVarInt();
                    } catch (IOException exception) {
                        continue;
                    }
                    if (packetId == BedrockPacketIds.DISCONNECT) {
                        status.accept("Соединение закрыто: " + safeString(idReader.remaining() > 0 ? idReader.readString() : "без причины"));
                        closeQuietly();
                        return;
                    }
                    if (packetId == BedrockPacketIds.NETWORK_STACK_LATENCY) {
                        sendLatencyResponse(idReader);
                    }
                    BedrockPlayDecoder.accept(packet, worldState, inventory, status);
                    notifyPacket(packet);
                }
            } catch (IOException exception) {
                if (!closed) {
                    status.accept("Игровая сессия остановлена: " + safeString(exception.getMessage()));
                }
                closeQuietly();
            }
        }
    }

    private void readNetworkSettings(BedrockBuffer.Reader reader) throws IOException {
        compressionThreshold = reader.readUnsignedShortLittleEndian();
        compressionAlgorithm = reader.readUnsignedShortLittleEndian();
        if (reader.remaining() > 0) {
            reader.readUnsignedByte();
        }
        if (reader.remaining() > 0) {
            reader.readUnsignedByte();
        }
        if (reader.remaining() >= 4) {
            reader.readFloatLittleEndian();
        }
        compressed = compressionAlgorithm == 0;
        if (compressionAlgorithm != 0 && compressionAlgorithm != 2) {
            throw new IOException("Неподдерживаемый Bedrock compression algorithm: " + compressionAlgorithm);
        }
        status.accept("Compression: " + compressionName() + ", threshold " + compressionThreshold);
    }

    private String compressionName() {
        return switch (compressionAlgorithm) {
            case 0 -> "zlib";
            case 1 -> "snappy";
            default -> "none";
        };
    }

    private List<byte[]> receiveBatch(long timeoutMillis) throws IOException {
        final byte[] payload = transport.receive(timeoutMillis);
        if (payload.length == 0 || (payload[0] & 0xff) != BEDROCK_BATCH_ID) {
            throw new IOException("Получен неизвестный Bedrock batch packet");
        }
        final byte[] batch = Arrays.copyOfRange(payload, 1, payload.length);
        if (crypto != null) {
            return BedrockBatchCodec.decode(crypto.decryptBatch(batch), false);
        }
        if (!compressed) {
            return BedrockBatchCodec.decode(batch, false);
        }
        try {
            return BedrockBatchCodec.decode(batch, true);
        } catch (IOException compressedFailure) {
            return BedrockBatchCodec.decode(batch, false);
        }
    }

    private void sendBatch(List<byte[]> packets, boolean forceCompression) throws IOException {
        if (crypto != null) {
            final byte[] encrypted = crypto.encryptBatch(BedrockBatchCodec.encode(packets, false));
            transport.sendReliable(new BedrockBuffer().writeByte(BEDROCK_BATCH_ID).writeBytes(encrypted).toByteArray());
            return;
        }
        final boolean shouldCompress = forceCompression && compressed && compressionAlgorithm == 0;
        final byte[] batch = BedrockBatchCodec.encode(packets, shouldCompress);
        transport.sendReliable(new BedrockBuffer().writeByte(BEDROCK_BATCH_ID).writeBytes(batch).toByteArray());
    }

    private void sendResourcePackResponse(int responseStatus, List<String> packIds) throws IOException {
        final BedrockBuffer packet = new BedrockBuffer().writeByte(BedrockPacketIds.RESOURCE_PACK_CLIENT_RESPONSE)
                .writeByte(responseStatus).writeVarInt(packIds.size());
        for (String packId : packIds) {
            packet.writeString(packId);
        }
        sendBatch(List.of(packet.toByteArray()), true);
    }

    private void sendResourcePackChunkRequest(byte[] request) throws IOException {
        sendBatch(List.of(new BedrockBuffer().writeByte(BedrockPacketIds.RESOURCE_PACK_CHUNK_REQUEST)
                .writeBytes(request).toByteArray()), true);
    }

    private boolean sendResourcePackCompleted() throws IOException {
        if (resourcePackCompletedResponseSent) {
            return false;
        }
        sendResourcePackResponse(4, List.of());
        resourcePackCompletedResponseSent = true;
        return true;
    }

    private void sendLatencyResponse(BedrockBuffer.Reader reader) throws IOException {
        final long timestamp = reader.readLongLittleEndian();
        final boolean needsResponse = reader.remaining() > 0 && reader.readBoolean();
        if (!needsResponse) {
            return;
        }
        sendGamePacket(new BedrockBuffer()
                .writeByte(BedrockPacketIds.NETWORK_STACK_LATENCY)
                .writeLongLittleEndian(timestamp)
                .writeBoolean(false)
                .toByteArray());
    }

    private static List<String> readResourcePackIds(BedrockBuffer.Reader reader)
            throws IOException {
        // Bedrock resource-pack negotiation can include a few optional boolean flags
        // before the descriptor list. Consume them if they are present.
        int flagsSeen = 0;
        while (reader.remaining() > 0 && flagsSeen < 3) {
            final int remainingBefore = reader.remaining();
            try {
                reader.readBoolean();
                flagsSeen++;
            } catch (IOException ignored) {
                break;
            }
            if (reader.remaining() == remainingBefore) {
                break;
            }
        }
        final List<String> ids = new ArrayList<>();
        readResourcePackDescriptors(reader, ids);
        readResourcePackDescriptors(reader, ids);
        return ids;
    }

    private static void readResourcePackDescriptors(BedrockBuffer.Reader reader, List<String> ids)
            throws IOException {
        final int count = reader.readVarInt();
        if (count < 0 || count > 4096) {
            throw new IOException("Invalid Bedrock resource-pack count: " + count);
        }
        for (int index = 0; index < count; index++) {
            ids.add(reader.readString());
            reader.readString();
            reader.readLongLittleEndian();
            reader.readString();
            reader.readString();
            reader.readString();
        }
    }

    private void sendClientCacheStatus() throws IOException {
        sendGamePacket(new BedrockBuffer()
                .writeByte(BedrockPacketIds.CLIENT_CACHE_STATUS)
                .writeBoolean(false)
                .toByteArray());
    }

    private void sendRequestChunkRadius(int radius) throws IOException {
        sendGamePacket(new BedrockBuffer()
                .writeByte(BedrockPacketIds.REQUEST_CHUNK_RADIUS)
                .writeVarInt(radius)
                .writeByte(radius)
                .toByteArray());
    }

    public void sendLocalPlayerInitialized() throws IOException {
        sendGamePacket(new BedrockBuffer()
                .writeByte(BedrockPacketIds.SET_LOCAL_PLAYER_AS_INITIALIZED)
                .writeVarLong(worldState.localRuntimeId())
                .toByteArray());
    }

    public void sendMove(double x, double y, double z, float yaw, float pitch,
                         boolean onGround, long tick) throws IOException {
        sendGamePacket(new BedrockBuffer().writeByte(BedrockPacketIds.MOVE_PLAYER).writeVarLong(worldState.localRuntimeId())
                .writeFloatLittleEndian((float) x).writeFloatLittleEndian((float) y).writeFloatLittleEndian((float) z)
                .writeFloatLittleEndian(pitch).writeFloatLittleEndian(yaw).writeFloatLittleEndian(yaw)
                .writeVarInt(0).writeBoolean(onGround).writeVarLong(0).writeVarLong(tick).toByteArray());
    }

    public void sendChat(String message) throws IOException {
        sendGamePacket(new BedrockBuffer().writeByte(BedrockPacketIds.TEXT).writeVarInt(1).writeBoolean(false)
                .writeString(account.displayName()).writeString(message).toByteArray());
    }

    public void sendHotbarSelect(int slot) throws IOException {
        final int selected = Math.max(0, Math.min(JavaInventoryState.HOTBAR_SIZE - 1, slot));
        sendGamePacket(new BedrockBuffer().writeByte(BedrockPacketIds.MOB_EQUIPMENT).writeVarLong(worldState.localRuntimeId())
                .writeBytes(itemStackBytes(inventory.slot(selected))).writeByte(selected).writeByte(selected).writeByte(0).toByteArray());
        inventory.select(selected);
    }

    public void sendUseItemOnBlock(BlockPos position, Direction face,
                                   double clickX, double clickY, double clickZ)
            throws IOException {
        if (position == null || face == null) {
            return;
        }
        final int side = switch (face) {
            case DOWN -> 0;
            case UP -> 1;
            case NORTH -> 2;
            case SOUTH -> 3;
            case WEST -> 4;
            case EAST -> 5;
        };
        final JavaItemStack stack = inventory.slot(inventory.selectedSlot());
        sendGamePacket(new BedrockBuffer().writeByte(BedrockPacketIds.INVENTORY_TRANSACTION).writeVarInt(2).writeVarInt(0)
                .writeZigZagInt(position.getX()).writeZigZagInt(position.getY()).writeZigZagInt(position.getZ())
                .writeVarInt(side).writeVarInt(inventory.selectedSlot()).writeBytes(itemStackBytes(stack))
                .writeFloatLittleEndian((float) worldState.playerX()).writeFloatLittleEndian((float) worldState.playerY())
                .writeFloatLittleEndian((float) worldState.playerZ()).writeFloatLittleEndian((float) clickX)
                .writeFloatLittleEndian((float) clickY).writeFloatLittleEndian((float) clickZ).writeVarInt(0).toByteArray());
    }

    public void sendContainerClose(int containerId) throws IOException {
        sendGamePacket(new BedrockBuffer().writeByte(BedrockPacketIds.CONTAINER_CLOSE).writeByte(containerId)
                .writeByte(0).writeBoolean(false).toByteArray());
        inventory.closeContainer(containerId);
    }

    public void sendContainerSwap(int containerId, int firstSlot, int secondSlot)
            throws IOException {
        if (containerId < 0 || firstSlot < 0 || secondSlot < 0 || firstSlot == secondSlot) {
            return;
        }
        final JavaItemStack first = inventory.containerSlot(containerId, firstSlot);
        final JavaItemStack second = inventory.containerSlot(containerId, secondSlot);
        final BedrockBuffer packet = new BedrockBuffer().writeByte(BedrockPacketIds.INVENTORY_TRANSACTION)
                .writeVarInt(0).writeVarInt(0).writeVarInt(2);
        writeInventoryAction(packet, containerId, firstSlot, first, second);
        writeInventoryAction(packet, containerId, secondSlot, second, first);
        sendGamePacket(packet.toByteArray());
    }

    private static void writeInventoryAction(BedrockBuffer packet, int containerId,
                                             int slot, JavaItemStack oldStack,
                                             JavaItemStack newStack) {
        packet.writeVarInt(0)
                .writeVarInt(containerId)
                .writeVarInt(slot);
        BedrockItemStackCodec.write(packet, oldStack);
        BedrockItemStackCodec.write(packet, newStack);
    }

    private static byte[] itemStackBytes(JavaItemStack stack) {
        final BedrockBuffer buffer = new BedrockBuffer();
        BedrockItemStackCodec.write(buffer, stack);
        return buffer.toByteArray();
    }

    public void sendPlayerAction(int action, int x, int y, int z, int face)
            throws IOException {
        sendGamePacket(new BedrockBuffer().writeByte(BedrockPacketIds.PLAYER_ACTION)
                .writeVarLong(worldState.localRuntimeId()).writeVarInt(action).writeZigZagInt(x)
                .writeZigZagInt(y).writeZigZagInt(z).writeVarInt(face).toByteArray());
    }

    public void sendBlockBreakStart(int x, int y, int z, int face) throws IOException {
        sendPlayerAction(0, x, y, z, face);
    }

    public void sendBlockBreakAbort(int x, int y, int z, int face) throws IOException {
        sendPlayerAction(1, x, y, z, face);
    }

    public void sendBlockBreakStop(int x, int y, int z, int face) throws IOException {
        sendPlayerAction(2, x, y, z, face);
    }

    public void sendInteract(long targetRuntimeId, int action) throws IOException {
        sendGamePacket(new BedrockBuffer().writeByte(0x21).writeVarInt(action).writeVarLong(targetRuntimeId).toByteArray());
    }

    public void sendGamePacket(byte[] packet) throws IOException {
        if (packet == null || packet.length == 0) {
            return;
        }
        sendBatch(List.of(packet), true);
    }

    private void notifyPacket(byte[] packet) {
        for (Consumer<byte[]> listener : packetListeners) {
            try {
                listener.accept(packet);
            } catch (RuntimeException ignored) {
                // A UI listener must not kill the network loop.
            }
        }
    }

    private void tracePacket(String phase, byte[] packet) {
        if (!ENABLE_PACKET_TRACE || packet == null || packet.length == 0) {
            return;
        }
        final BedrockBuffer.Reader reader = BedrockBuffer.reader(packet);
        try {
            final int packetId = reader.readVarInt();
            final String name = BedrockPacketIds.name(packetId);
            status.accept(phase + ": " + name + " (len=" + packet.length + ")");
        } catch (IOException ignored) {
            status.accept(phase + ": invalid packet (len=" + packet.length + ")");
        }
    }

    private static String safeString(String value) {
        if (value == null || value.isBlank()) {
            return "неизвестная ошибка";
        }
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        closed = true;
        joined = false;
        if (transport != null) {
            try {
                transport.close();
            } catch (IOException ignored) {
                // Closing a failed connection is best effort.
            }
        }
    }
}
