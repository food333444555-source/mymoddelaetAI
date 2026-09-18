package dev.funtime.pe;

import java.security.PrivateKey;

public final class MicrosoftSession {
    private final String gamertag;
    private final String xuid;
    private final String identityToken;
    private final String accessToken;
    private final String minecraftChain;
    private final PrivateKey privateKey;
    private final byte[] publicKey;

    public MicrosoftSession(String gamertag, String xuid, String identityToken, String accessToken,
                            String minecraftChain, PrivateKey privateKey, byte[] publicKey) {
        this.gamertag = gamertag;
        this.xuid = xuid;
        this.identityToken = identityToken;
        this.accessToken = accessToken;
        this.minecraftChain = minecraftChain;
        this.privateKey = privateKey;
        this.publicKey = publicKey.clone();
    }

    public String gamertag() {
        return gamertag;
    }

    public String xuid() {
        return xuid;
    }

    public String identityToken() {
        return identityToken;
    }

    public String accessToken() {
        return accessToken;
    }

    public String minecraftChain() {
        return minecraftChain;
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public byte[] publicKey() {
        return publicKey.clone();
    }

    public boolean isUsable() {
        return identityToken != null && !identityToken.isBlank()
                && gamertag != null && !gamertag.isBlank()
                && minecraftChain != null && !minecraftChain.isBlank()
                && privateKey != null;
    }
}