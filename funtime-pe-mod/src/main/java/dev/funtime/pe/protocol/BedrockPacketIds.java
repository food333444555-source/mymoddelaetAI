package dev.funtime.pe.protocol;

/*
 * Source version:
 * Minecraft Java 1.21.4
 * Bedrock 26.50
 * Bedrock protocol 2193
 * Mod version 0.4.0
 */

/**
 * Version-scoped Bedrock packet registry.
 *
 * The Java-facing code uses these names instead of scattering protocol
 * literals through the session and world layers. Packet structures remain
 * isolated in their decoders so a later Bedrock version can replace this
 * registry without rewriting the Java presentation layer.
 */
public final class BedrockPacketIds {
    public static final int LOGIN = 0x01;
    public static final int PLAY_STATUS = 0x02;
    public static final int SERVER_TO_CLIENT_HANDSHAKE = 0x03;
    public static final int CLIENT_TO_SERVER_HANDSHAKE = 0x04;
    public static final int DISCONNECT = 0x05;
    public static final int RESOURCE_PACKS_INFO = 0x06;
    public static final int RESOURCE_PACK_STACK = 0x07;
    public static final int RESOURCE_PACK_CLIENT_RESPONSE = 0x08;
    public static final int RESOURCE_PACK_DATA_INFO = 0x52;
    public static final int RESOURCE_PACK_CHUNK_DATA = 0x53;
    public static final int RESOURCE_PACK_CHUNK_REQUEST = 0x54;
    public static final int TEXT = 0x09;
    public static final int SET_TIME = 0x0A;
    public static final int START_GAME = 0x0B;
    public static final int ADD_PLAYER = 0x0C;
    public static final int ADD_ACTOR = 0x0D;
    public static final int REMOVE_ACTOR = 0x0E;
    public static final int MOVE_ACTOR_ABSOLUTE = 0x12;
    public static final int MOVE_PLAYER = 0x13;
    public static final int MOB_EQUIPMENT = 0x1F;
    public static final int INVENTORY_CONTENT = 0x31;
    public static final int INVENTORY_SLOT = 0x32;
    public static final int CONTAINER_OPEN = 0x2E;
    public static final int CONTAINER_CLOSE = 0x2F;
    public static final int SET_HEALTH = 0x2A;
    public static final int SET_HUNGER = 0x2B;
    public static final int RESPAWN = 0x2D;
    public static final int PLAYER_HOTBAR = 0x6D;
    public static final int INVENTORY_TRANSACTION = 0x1E;
    public static final int PLAYER_ACTION = 0x24;
    public static final int LEVEL_CHUNK = 0x3A;
    public static final int UPDATE_BLOCK = 0x15;
    public static final int REQUEST_CHUNK_RADIUS = 0x45;
    public static final int SET_LOCAL_PLAYER_AS_INITIALIZED = 0x71;
    public static final int CLIENT_CACHE_STATUS = 0x81;
    public static final int NETWORK_SETTINGS = 0x8F;
    public static final int NETWORK_STACK_LATENCY = 0x93;
    public static final int CHANGE_DIMENSION = 0x3D;
    public static final int REQUEST_NETWORK_SETTINGS = 0xC1;

    private BedrockPacketIds() {
    }

    public static String name(int packetId) {
        return switch (packetId) {
            case LOGIN -> "Login";
            case PLAY_STATUS -> "PlayStatus";
            case SERVER_TO_CLIENT_HANDSHAKE -> "ServerToClientHandshake";
            case CLIENT_TO_SERVER_HANDSHAKE -> "ClientToServerHandshake";
            case DISCONNECT -> "Disconnect";
            case RESOURCE_PACKS_INFO -> "ResourcePacksInfo";
            case RESOURCE_PACK_STACK -> "ResourcePackStack";
            case RESOURCE_PACK_CLIENT_RESPONSE -> "ResourcePackClientResponse";
            case RESOURCE_PACK_DATA_INFO -> "ResourcePackDataInfo";
            case RESOURCE_PACK_CHUNK_DATA -> "ResourcePackChunkData";
            case RESOURCE_PACK_CHUNK_REQUEST -> "ResourcePackChunkRequest";
            case TEXT -> "Text";
            case SET_TIME -> "SetTime";
            case START_GAME -> "StartGame";
            case ADD_PLAYER -> "AddPlayer";
            case ADD_ACTOR -> "AddActor";
            case REMOVE_ACTOR -> "RemoveActor";
            case MOVE_ACTOR_ABSOLUTE -> "MoveActorAbsolute";
            case MOVE_PLAYER -> "MovePlayer";
            case MOB_EQUIPMENT -> "MobEquipment";
            case INVENTORY_CONTENT -> "InventoryContent";
            case INVENTORY_SLOT -> "InventorySlot";
            case CONTAINER_OPEN -> "ContainerOpen";
            case CONTAINER_CLOSE -> "ContainerClose";
            case SET_HEALTH -> "SetHealth";
            case SET_HUNGER -> "SetHunger";
            case RESPAWN -> "Respawn";
            case PLAYER_HOTBAR -> "PlayerHotbar";
            case LEVEL_CHUNK -> "LevelChunk";
            case UPDATE_BLOCK -> "UpdateBlock";
            case REQUEST_CHUNK_RADIUS -> "RequestChunkRadius";
            case SET_LOCAL_PLAYER_AS_INITIALIZED -> "SetLocalPlayerAsInitialized";
            case CLIENT_CACHE_STATUS -> "ClientCacheStatus";
            case NETWORK_SETTINGS -> "NetworkSettings";
            case NETWORK_STACK_LATENCY -> "NetworkStackLatency";
            case CHANGE_DIMENSION -> "ChangeDimension";
            case REQUEST_NETWORK_SETTINGS -> "RequestNetworkSettings";
            default -> "Unknown(0x" + Integer.toHexString(packetId) + ")";
        };
    }
}