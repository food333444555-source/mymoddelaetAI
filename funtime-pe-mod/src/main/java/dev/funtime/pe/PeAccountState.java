package dev.funtime.pe;

public final class PeAccountState {
    private String guestName = "Steve";
    private String microsoftName;
    private String xuid;
    private transient MicrosoftSession microsoftSession;

    public String guestName() {
        return guestName == null || guestName.isBlank() ? "Steve" : guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String microsoftName() {
        return microsoftName;
    }

    public String xuid() {
        return xuid;
    }

    public boolean isMicrosoftConnected() {
        return microsoftSession != null && microsoftSession.isUsable();
    }

    public MicrosoftSession microsoftSession() {
        return microsoftSession;
    }

    public void setMicrosoftSession(MicrosoftSession microsoftSession) {
        this.microsoftSession = microsoftSession;
        if (microsoftSession != null) {
            this.microsoftName = microsoftSession.gamertag();
            this.xuid = microsoftSession.xuid();
        }
    }

    public void clearMicrosoftSession() {
        microsoftSession = null;
        microsoftName = null;
        xuid = null;
    }

    public String displayName() {
        return isMicrosoftConnected() ? microsoftName : guestName();
    }
}