package dev.funtime.pe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

final class MicrosoftDeviceLogin {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL =
            "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize";

    private MicrosoftDeviceLogin() {
    }

    static CompletableFuture<DeviceCode> requestDeviceCode() {
        final String form = form(
                "client_id", CLIENT_ID,
                "scope", "XboxLive.signin offline_access");
        return postForm(DEVICE_CODE_URL, form).thenApply(json -> new DeviceCode(
                json.get("device_code").getAsString(),
                json.get("user_code").getAsString(),
                json.get("verification_uri").getAsString(),
                json.get("expires_in").getAsInt(),
                Math.max(5, json.get("interval").getAsInt())));
    }

    static CompletableFuture<MicrosoftSession> login(DeviceCode deviceCode) {
        return pollToken(deviceCode, System.nanoTime() + Duration.ofSeconds(deviceCode.expiresIn()).toNanos())
                .thenCompose(MicrosoftDeviceLogin::exchangeForXbox)
                .thenCompose(MicrosoftDeviceLogin::exchangeForXsts)
                .thenCompose(result -> {
                    final String identityToken = "XBL3.0 x=" + result.uhs() + ";" + result.xstsToken();
                    try {
                        final KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
                        generator.initialize(new ECGenParameterSpec("secp384r1"));
                        final KeyPair keyPair = generator.generateKeyPair();
                        return requestMinecraftChain(identityToken, keyPair)
                                .thenApply(chain -> new MicrosoftSession(
                                        result.gamertag(), result.xuid(), identityToken,
                                        result.accessToken(), chain, keyPair.getPrivate(),
                                        keyPair.getPublic().getEncoded()));
                    } catch (NoSuchAlgorithmException | java.security.InvalidAlgorithmParameterException exception) {
                        return CompletableFuture.failedFuture(exception);
                    }
                });
    }

    static void start(Consumer<DeviceCode> onCode, Consumer<MicrosoftSession> onSuccess,
                      Consumer<Throwable> onFailure) {
        CompletableFuture.runAsync(() -> {
            try {
                final DeviceCode code = requestDeviceCode().join();
                onCode.accept(code);
                final MicrosoftSession session = login(code).join();
                onSuccess.accept(session);
            } catch (CompletionException exception) {
                onFailure.accept(exception.getCause() == null ? exception : exception.getCause());
            } catch (RuntimeException exception) {
                onFailure.accept(exception);
            }
        });
    }

    private static CompletableFuture<String> pollToken(DeviceCode deviceCode, long deadline) {
        final String form = form(
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "client_id", CLIENT_ID,
                "device_code", deviceCode.deviceCode());

        return postForm(TOKEN_URL, form).handle((json, error) -> {
            CompletableFuture<String> result;
            if (error == null) {
                result = CompletableFuture.completedFuture(json.get("access_token").getAsString());
            } else {
                final Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                final String message = cause.getMessage() == null ? "" : cause.getMessage();
                if (System.nanoTime() >= deadline || !message.contains("authorization_pending")) {
                    result = CompletableFuture.failedFuture(cause);
                } else {
                    try {
                        Thread.sleep(deviceCode.intervalSeconds() * 1000L);
                        result = pollToken(deviceCode, deadline);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        result = CompletableFuture.failedFuture(interrupted);
                    }
                }
            }
            return result;
        }).thenCompose(future -> future);
    }

    private static CompletableFuture<XboxResult> exchangeForXbox(String accessToken) {
        final JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + accessToken);

        final JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");
        return postJson(XBL_AUTH_URL, body).thenApply(json -> {
            final String token = json.get("Token").getAsString();
            final String userHash = json.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui").get(0).getAsJsonObject()
                    .get("uhs").getAsString();
            return new XboxResult(accessToken, token, userHash, null, null, null);
        });
    }

    private static CompletableFuture<XboxResult> exchangeForXsts(XboxResult xbox) {
        final JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        final JsonArray userTokens = new JsonArray();
        userTokens.add(xbox.xboxToken());
        properties.add("UserTokens", userTokens);

        final JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://banned.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        return postJson(XSTS_URL, body).thenApply(json -> {
            final String token = json.get("Token").getAsString();
            final JsonObject user = json.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui").get(0).getAsJsonObject();
            return new XboxResult(xbox.accessToken(), xbox.xboxToken(), xbox.uhs(),
                    token, user.get("xid").getAsString(),
                    user.has("gtg") ? user.get("gtg").getAsString() : "Player");
        });
    }

    private static CompletableFuture<JsonObject> postForm(String url, String form) {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return send(request);
    }

    private static CompletableFuture<JsonObject> postJson(String url, JsonObject body) {
        return postJson(url, body, java.util.Map.of());
    }

    private static CompletableFuture<JsonObject> postJson(String url, JsonObject body,
                                                          java.util.Map<String, String> headers) {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .headers(headers.entrySet().stream()
                        .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                        .toArray(String[]::new))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return send(request);
    }

    private static CompletableFuture<String> requestMinecraftChain(String identityToken, KeyPair keyPair) {
        final String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        final JsonObject body = new JsonObject();
        body.addProperty("identityPublicKey", publicKey);
        final HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://multiplayer.minecraft.net/authentication"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "MCPE/Android")
                        .header("Client-Version", "26.50")
                .header("Content-Type", "application/json")
                .header("Authorization", identityToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new CompletionException(new IOException(
                                "Minecraft chain request failed: HTTP " + response.statusCode()));
                    }
                    return response.body();
                });
    }

    private static CompletableFuture<JsonObject> send(HttpRequest request) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    final JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (response.statusCode() / 100 != 2) {
                        final String error = body.has("error_description")
                                ? body.get("error_description").getAsString()
                                : body.toString();
                        throw new CompletionException(new IOException(error));
                    }
                    if (body.has("error")) {
                        throw new CompletionException(new IOException(body.get("error").getAsString()));
                    }
                    return body;
                });
    }

    private static String form(String... values) {
        final StringBuilder form = new StringBuilder();
        for (int i = 0; i < values.length; i += 2) {
            if (form.length() > 0) {
                form.append('&');
            }
            form.append(URLEncoder.encode(values[i], StandardCharsets.UTF_8));
            form.append('=');
            form.append(URLEncoder.encode(values[i + 1], StandardCharsets.UTF_8));
        }
        return form.toString();
    }

    record DeviceCode(String deviceCode, String userCode, String verificationUri,
                      int expiresIn, int intervalSeconds) {
    }

    private record XboxResult(String accessToken, String xboxToken, String uhs,
                              String xstsToken, String xuid, String gamertag) {
    }
}