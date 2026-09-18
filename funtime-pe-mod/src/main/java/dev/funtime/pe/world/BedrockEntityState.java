package dev.funtime.pe.world;

public final class BedrockEntityState {
    private final long runtimeId;
    private final long uniqueId;
    private final String type;
    private volatile String name;
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile float yaw;
    private volatile float pitch;
    private volatile boolean player;

    public BedrockEntityState(long runtimeId, long uniqueId, String type,
                              double x, double y, double z) {
        this.runtimeId = runtimeId;
        this.uniqueId = uniqueId;
        this.type = type == null ? "minecraft:unknown" : type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public long runtimeId() {
        return runtimeId;
    }

    public long uniqueId() {
        return uniqueId;
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean player() {
        return player;
    }

    public void setPlayer(boolean player) {
        this.player = player;
    }

    public void move(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}