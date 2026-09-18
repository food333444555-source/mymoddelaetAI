package dev.funtime.pe;

import java.util.UUID;

public final class PeServerEntry {
    private String id;
    private String name;
    private String host;
    private int port;
    private PeAuthMode authMode;
    private int protocolVersion;

    public PeServerEntry() {
        this(UUID.randomUUID().toString(), "FUNTIME", "mc.funtime.su", 19132,
                PeAuthMode.MICROSOFT, BedrockVersion.DEFAULT.protocolVersion());
    }

    public PeServerEntry(String id, String name, String host, int port,
                         PeAuthMode authMode, int protocolVersion) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.authMode = authMode;
        this.protocolVersion = protocolVersion;
    }

    public static PeServerEntry createDefault() {
        return new PeServerEntry();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public PeAuthMode authMode() {
        return authMode == null ? PeAuthMode.MICROSOFT : authMode;
    }

    public int protocolVersion() {
        return protocolVersion <= 0 ? BedrockVersion.DEFAULT.protocolVersion() : protocolVersion;
    }

    public void update(String name, String host, int port, PeAuthMode authMode, int protocolVersion) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.authMode = authMode;
        this.protocolVersion = protocolVersion;
    }

    public boolean isValid() {
        return name != null && !name.isBlank()
                && host != null && !host.isBlank() && !host.contains(" ")
                && port >= 1 && port <= 65535
                && protocolVersion() > 0;
    }
}