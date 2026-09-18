package dev.funtime.pe;

public enum PeAuthMode {
    GUEST("Гость"),
    MICROSOFT("Microsoft");

    private final String displayName;

    PeAuthMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}