package dev.funtime.pe.world;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

import dev.funtime.pe.protocol.BedrockBuffer;
import dev.funtime.pe.protocol.BedrockItemStackCodec;
import dev.funtime.pe.protocol.BedrockPacketIds;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Decodes the state-changing packets needed by the first world bridge.
 * Unknown packets are deliberately left available for later protocol modules;
 * they are not converted into fake Java packets.
 */
public final class BedrockPlayDecoder {
    private static final int START_GAME = BedrockPacketIds.START_GAME;
    private static final int SET_TIME = BedrockPacketIds.SET_TIME;
    private static final int LEVEL_CHUNK = BedrockPacketIds.LEVEL_CHUNK;
    private static final int UPDATE_BLOCK = BedrockPacketIds.UPDATE_BLOCK;
    private static final int TEXT = BedrockPacketIds.TEXT;
    private static final int REMOVE_ACTOR = BedrockPacketIds.REMOVE_ACTOR;
    private static final int ADD_PLAYER = BedrockPacketIds.ADD_PLAYER;
    private static final int ADD_ACTOR = BedrockPacketIds.ADD_ACTOR;
    private static final int MOVE_ACTOR_ABSOLUTE = BedrockPacketIds.MOVE_ACTOR_ABSOLUTE;
    private static final int MOVE_PLAYER = BedrockPacketIds.MOVE_PLAYER;
    private static final int SET_HEALTH = BedrockPacketIds.SET_HEALTH;
    private static final int SET_HUNGER = BedrockPacketIds.SET_HUNGER;
    private static final int INVENTORY_CONTENT = BedrockPacketIds.INVENTORY_CONTENT;
    private static final int INVENTORY_SLOT = BedrockPacketIds.INVENTORY_SLOT;
    private static final int MOB_EQUIPMENT = BedrockPacketIds.MOB_EQUIPMENT;
    private static final int CONTAINER_OPEN = BedrockPacketIds.CONTAINER_OPEN;
    private static final int CONTAINER_CLOSE = BedrockPacketIds.CONTAINER_CLOSE;
    private static final int RESPAWN = BedrockPacketIds.RESPAWN;
    private static final int CHANGE_DIMENSION = BedrockPacketIds.CHANGE_DIMENSION;

    private BedrockPlayDecoder() {
    }

    public static void accept(byte[] packet, BedrockWorldState world,
                              Consumer<String> messages) throws IOException {
        accept(packet, world, null, messages);
    }

    public static void accept(byte[] packet, BedrockWorldState world,
                              JavaInventoryState inventory,
                              Consumer<String> messages) throws IOException {
        final BedrockBuffer.Reader reader = BedrockBuffer.reader(packet);
        final int id = reader.readVarInt();
        switch (id) {
            case START_GAME -> decodeStartGame(reader, world, messages);
            case SET_TIME -> world.setGameTime(reader.readVarInt());
            case LEVEL_CHUNK -> decodeLevelChunk(reader, world, messages);
            case UPDATE_BLOCK -> decodeUpdateBlock(reader, world);
            case TEXT -> decodeText(reader, messages);
            case REMOVE_ACTOR -> world.removeEntity(reader.readVarLong());
            case ADD_PLAYER -> decodeAddPlayer(reader, world);
            case ADD_ACTOR -> decodeAddActor(reader, world);
            case MOVE_ACTOR_ABSOLUTE -> decodeMoveActor(reader, world);
            case MOVE_PLAYER -> decodeMovePlayer(reader, world);
            case SET_HEALTH -> decodeHealth(reader, inventory);
            case SET_HUNGER -> decodeHunger(reader, inventory);
            case INVENTORY_CONTENT -> decodeInventoryContent(reader, inventory);
            case INVENTORY_SLOT -> decodeInventorySlot(reader, inventory);
            case MOB_EQUIPMENT -> decodeMobEquipment(reader, inventory);
            case CONTAINER_OPEN -> decodeContainerOpen(reader, inventory, messages);
            case CONTAINER_CLOSE -> decodeContainerClose(reader, inventory);
            case RESPAWN -> decodeRespawn(reader, world, messages);
            case CHANGE_DIMENSION -> decodeChangeDimension(reader, world, messages);
            default -> {
                // The packet registry is versioned; unsupported packets must
                // not corrupt the following packet in the batch.
            }
        }
    }

    private static void decodeInventoryContent(BedrockBuffer.Reader reader,
                                               JavaInventoryState inventory)
            throws IOException {
        final int containerId = reader.readUnsignedByte();
        final int count = reader.readVarInt();
        if (count < 0 || count > 512) {
            throw new IOException("Invalid Bedrock inventory content size: " + count);
        }
        if (containerId != 0) {
            final JavaItemStack[] contents = new JavaItemStack[count];
            for (int index = 0; index < count; index++) {
                contents[index] = BedrockItemStackCodec.read(reader);
            }
            if (inventory != null) {
                inventory.replaceContainer(containerId, contents);
            }
            return;
        }
        final JavaItemStack[] contents = new JavaItemStack[Math.min(count,
                JavaInventoryState.PLAYER_SLOT_COUNT)];
        for (int index = 0; index < count; index++) {
            final JavaItemStack stack = BedrockItemStackCodec.read(reader);
            if (index < contents.length) {
                contents[index] = stack;
            }
        }
        if (inventory != null) {
            inventory.clearPlayerInventory();
            inventory.replacePlayerInventory(contents);
        }
    }

    private static void decodeInventorySlot(BedrockBuffer.Reader reader,
                                            JavaInventoryState inventory)
            throws IOException {
        final int containerId = reader.readUnsignedByte();
        final int slot = reader.readVarInt();
        final JavaItemStack stack = BedrockItemStackCodec.read(reader);
        if (inventory != null) {
            if (containerId == 0) {
                inventory.setSlot(slot, stack);
            } else {
                inventory.setContainerSlot(containerId, slot, stack);
            }
        }
    }

    private static void decodeMobEquipment(BedrockBuffer.Reader reader,
                                           JavaInventoryState inventory)
            throws IOException {
        reader.readVarLong(); // entity runtime id
        final JavaItemStack stack = BedrockItemStackCodec.read(reader);
        final int slot = reader.readUnsignedByte();
        final int selectedSlot = reader.readUnsignedByte();
        reader.readUnsignedByte(); // container id
        if (inventory != null) {
            inventory.setSlot(slot, stack);
            inventory.select(selectedSlot);
        }
    }

    private static void decodeContainerOpen(BedrockBuffer.Reader reader,
                                            JavaInventoryState inventory,
                                            Consumer<String> messages)
            throws IOException {
        final int containerId = reader.readUnsignedByte();
        final int type = reader.readUnsignedByte();
        final int x = reader.readZigZagInt();
        final int y = reader.readZigZagInt();
        final int z = reader.readZigZagInt();
        reader.readVarLong(); // unique entity id
        if (inventory != null) {
            inventory.openContainer(containerId);
        }
        messages.accept("Контейнер открыт: " + containerId + " ("
                + type + ") в " + x + ", " + y + ", " + z);
    }

    private static void decodeContainerClose(BedrockBuffer.Reader reader,
                                             JavaInventoryState inventory)
            throws IOException {
        final int containerId = reader.readUnsignedByte();
        reader.readUnsignedByte(); // container type
        reader.readBoolean(); // server initiated
        if (inventory != null) {
            inventory.closeContainer(containerId);
        }
    }

    private static void decodeRespawn(BedrockBuffer.Reader reader,
                                      BedrockWorldState world,
                                      Consumer<String> messages)
            throws IOException {
        final double x = reader.readFloatLittleEndian();
        final double y = reader.readFloatLittleEndian();
        final double z = reader.readFloatLittleEndian();
        final int state = reader.readVarInt();
        if (state == 2) {
            world.respawn(x, y, z, world.playerYaw(), world.playerPitch(),
                    world.localRuntimeId());
            messages.accept("Игрок возрождён.");
        } else {
            messages.accept("Ожидание возрождения игрока.");
        }
    }

    private static void decodeChangeDimension(BedrockBuffer.Reader reader,
                                              BedrockWorldState world,
                                              Consumer<String> messages)
            throws IOException {
        final int dimension = reader.readVarInt();
        final double x = reader.readFloatLittleEndian();
        final double y = reader.readFloatLittleEndian();
        final double z = reader.readFloatLittleEndian();
        if (reader.remaining() > 0) {
            reader.readBoolean(); // respawn
        }
        world.changeDimension(dimension, x, y, z);
        messages.accept("Измерение изменено: " + dimension);
    }

    private static void decodeHealth(BedrockBuffer.Reader reader,
                                     JavaInventoryState inventory) throws IOException {
        final int health = reader.readVarInt();
        if (inventory != null) {
            inventory.setHealth(health);
        }
    }

    private static void decodeHunger(BedrockBuffer.Reader reader,
                                     JavaInventoryState inventory) throws IOException {
        final int hunger = reader.readVarInt();
        if (inventory != null) {
            inventory.setHunger(hunger);
        }
    }

    private static void decodeStartGame(BedrockBuffer.Reader reader,
                                        BedrockWorldState world,
                                        Consumer<String> messages) throws IOException {
        /*
         * The tail of StartGame is versioned, but the identity and spawn
         * prefix are stable across the 26.x line. Reading this prefix gives
         * the bridge a real local runtime id and spawn position while leaving
         * the profile-specific registry tail to the registry decoder.
         */
        reader.readVarLong(); // unique entity id
        final long runtimeId = reader.readVarLong();
        reader.readVarInt(); // player game mode
        final double x = reader.readFloatLittleEndian();
        final double y = reader.readFloatLittleEndian();
        final double z = reader.readFloatLittleEndian();
        final float pitch = reader.readFloatLittleEndian();
        final float yaw = reader.readFloatLittleEndian();
        world.clear();
        world.configure(0, -64, 320, runtimeId);
        world.setLocalPlayer(x, y, z, yaw, pitch, runtimeId);
        messages.accept("StartGame: world state initialized");
    }

    private static void decodeMoveActor(BedrockBuffer.Reader reader,
                                        BedrockWorldState world) throws IOException {
        final long runtimeId = reader.readVarLong();
        reader.readUnsignedByte(); // move flags
        final double x = reader.readFloatLittleEndian();
        final double y = reader.readFloatLittleEndian();
        final double z = reader.readFloatLittleEndian();
        final float pitch = reader.readFloatLittleEndian();
        final float yaw = reader.readFloatLittleEndian();
        reader.readFloatLittleEndian(); // head yaw
        if (runtimeId == world.localRuntimeId()) {
            world.moveLocalPlayer(x, y, z, yaw, pitch);
        }
        final BedrockEntityState entity = world.entity(runtimeId);
        if (entity != null) {
            entity.move(x, y, z, yaw, pitch);
        }
    }

    private static void decodeMovePlayer(BedrockBuffer.Reader reader,
                                         BedrockWorldState world) throws IOException {
        final long runtimeId = reader.readVarLong();
        final double x = reader.readFloatLittleEndian();
        final double y = reader.readFloatLittleEndian();
        final double z = reader.readFloatLittleEndian();
        final float pitch = reader.readFloatLittleEndian();
        final float yaw = reader.readFloatLittleEndian();
        reader.readFloatLittleEndian(); // head yaw
        if (runtimeId == world.localRuntimeId()) {
            world.moveLocalPlayer(x, y, z, yaw, pitch);
        }
    }

    private static void decodeAddActor(BedrockBuffer.Reader reader,
                                       BedrockWorldState world) throws IOException {
        final long uniqueId = reader.readVarLong();
        final long runtimeId = reader.readVarLong();
        final String type = reader.readString();
        final double x = reader.readFloatLittleEndian();
        final double y = reader.readFloatLittleEndian();
        final double z = reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        final float pitch = reader.readFloatLittleEndian();
        final float yaw = reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        final BedrockEntityState entity = new BedrockEntityState(
                runtimeId, uniqueId, type, x, y, z);
        entity.move(x, y, z, yaw, pitch);
        world.putEntity(entity);
    }

    private static void decodeAddPlayer(BedrockBuffer.Reader reader,
                                        BedrockWorldState world) throws IOException {
        final long uniqueId = reader.readVarLong();
        final long runtimeId = reader.readVarLong();
        reader.readUnsignedLongBigEndian();
        reader.readUnsignedLongBigEndian();
        final String name = reader.readString();
        final double x = reader.readFloatLittleEndian();
        final double y = reader.readFloatLittleEndian();
        final double z = reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        final float pitch = reader.readFloatLittleEndian();
        final float yaw = reader.readFloatLittleEndian();
        reader.readFloatLittleEndian();
        final BedrockEntityState entity = new BedrockEntityState(
                runtimeId, uniqueId, "minecraft:player", x, y, z);
        entity.setName(name);
        entity.setPlayer(true);
        entity.move(x, y, z, yaw, pitch);
        world.putEntity(entity);
    }

    private static void decodeLevelChunk(BedrockBuffer.Reader reader,
                                         BedrockWorldState world,
                                         Consumer<String> messages)
            throws IOException {
        final int chunkX = reader.readZigZagInt();
        final int chunkZ = reader.readZigZagInt();
        final int dimension = reader.readZigZagInt();
        final int sectionCount = reader.readVarInt();
        reader.readVarInt(); // client request sub-chunk limit
        final boolean cacheEnabled = reader.readUnsignedByte() != 0;
        if (cacheEnabled) {
            final int hashCount = reader.readVarInt();
            for (int index = 0; index < hashCount; index++) {
                reader.readLongLittleEndian();
            }
        }
        final byte[] serialized = reader.readBytes(reader.readVarInt());
        final int minSectionY = Math.floorDiv(world.minY(), 16);
        final BedrockChunk chunk = BedrockChunkDecoder.decode(
                chunkX, chunkZ, minSectionY, sectionCount, serialized);
        world.putChunk(chunk);
        messages.accept("Chunk " + chunkX + ", " + chunkZ + " загружен");
    }

    private static void decodeUpdateBlock(BedrockBuffer.Reader reader,
                                          BedrockWorldState world)
            throws IOException {
        final int x = reader.readZigZagInt();
        final int y = reader.readZigZagInt();
        final int z = reader.readZigZagInt();
        final int runtimeId = reader.readVarInt();
        reader.readVarInt(); // update flags
        reader.readVarInt(); // layer
        world.setBlock(x, y, z, BedrockBlockMapper.mapRuntimeId(runtimeId));
    }

    private static void decodeText(BedrockBuffer.Reader reader,
                                   Consumer<String> messages) throws IOException {
        final int type = reader.readVarInt();
        final boolean needsTranslation = reader.readUnsignedByte() != 0;
        String source = "";
        if (type == 1 || type == 2 || type == 3 || type == 4) {
            source = reader.readString();
        }
        final String message = reader.readString();
        messages.accept(source.isBlank() ? message : source + ": " + message);
        if (needsTranslation && reader.remaining() > 0) {
            final int parameters = reader.readVarInt();
            for (int index = 0; index < parameters; index++) {
                reader.readString();
            }
        }
    }
}