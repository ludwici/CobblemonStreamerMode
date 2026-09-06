package com.ludwici.cobblemonstreamermode.client.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TwitchAuthManager {

    public enum AuthStatus {
        NOT_AUTHORIZED,
        PENDING,
        VALID
    }

    public volatile AuthStatus authStatus = AuthStatus.NOT_AUTHORIZED;

    private volatile String pendingUserCode;
    private volatile String pendingVerificationUri;
    private volatile String lastError;

    private static final String CLIENT_ID = "3lsck8o6uuez43dqpu96zaycj7b5ra";
    public static final String SCOPES = "user:read:chat chat:read channel:read:polls";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private volatile String pendingDeviceCode;
    private volatile boolean pollingActive = false;

    public static final TwitchAuthManager INSTANCE = new TwitchAuthManager();

    public AuthStatus getAuthStatus() { return authStatus; }
    public String getPendingUserCode() { return pendingUserCode; }
    public String getPendingVerificationUri() { return pendingVerificationUri; }
    public String getLastError() { return lastError; }

    public void refreshStatusFromDisk() {
        TwitchCredentials creds = TwitchCredentials.load();
        authStatus = (creds != null && creds.refreshToken != null)
                ? AuthStatus.VALID
                : AuthStatus.NOT_AUTHORIZED;
    }

    public void beginDeviceFlow() {
        String body = "client_id=" + CLIENT_ID
                + "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://id.twitch.tv/oauth2/device"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(responseBody -> {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            pendingUserCode = json.get("user_code").getAsString();
            pendingVerificationUri = json.get("verification_uri").getAsString();
            authStatus = AuthStatus.PENDING;

            startPolling(
                    json.get("device_code").getAsString(),
                    json.get("interval").getAsInt(),
                    json.get("expires_in").getAsInt()
            );
        }).exceptionally(ex -> {
            lastError = ex.getMessage();
            authStatus = AuthStatus.NOT_AUTHORIZED;
            return null;
        });
    }

    private void refreshAccessToken(TwitchCredentials creds, java.util.function.Consumer<String> onDone, Runnable onFail) {
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(creds.refreshToken, StandardCharsets.UTF_8)
                + "&client_id=" + CLIENT_ID;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://id.twitch.tv/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            if (resp.statusCode() != 200) {
                onFail.run();
                return;
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();

            creds.accessToken = json.get("access_token").getAsString();
            creds.refreshToken = json.get("refresh_token").getAsString();
            int expiresIn = json.get("expires_in").getAsInt();
            creds.expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn;

            if (creds.channelId == null) {
                fetchChannelId(creds.accessToken,
                        userData -> {
                            creds.channelId = userData.get("id").getAsString();
                            creds.channelName = userData.get("login").getAsString();
                            creds.save();
                            onDone.accept(creds.accessToken);
                        },
                        () -> {
                            creds.save();
                            onDone.accept(creds.accessToken);
                        }
                );
            } else {
                creds.save();
                onDone.accept(creds.accessToken);
            }
        }).exceptionally(ex -> {
            authStatus = AuthStatus.NOT_AUTHORIZED;
            onFail.run();
            return null;
        });
    }

    public void getValidAccessToken(java.util.function.Consumer<String> onToken, Runnable onNeedAuth) {
        TwitchCredentials creds = TwitchCredentials.load();

        if (creds == null || creds.refreshToken == null) {
            onNeedAuth.run();
            return;
        }

        if (!creds.isExpired()) {
            onToken.accept(creds.accessToken);
            return;
        }

        refreshAccessToken(creds, onToken, onNeedAuth);
    }

    public void getValidCredentials(java.util.function.Consumer<TwitchCredentials> onReady, Runnable onNeedAuth) {
        TwitchCredentials creds = TwitchCredentials.load();

        if (creds == null || creds.refreshToken == null) {
            onNeedAuth.run();
            return;
        }

        if (!creds.isExpired()) {
            onReady.accept(creds);
            return;
        }

        refreshAccessToken(creds, token -> onReady.accept(creds), onNeedAuth);
    }

    private void startPolling(String deviceCode, int interval, int expiresIn) {
        pollingActive = true;
        long deadline = System.currentTimeMillis() + expiresIn * 1000L;

        Runnable[] pollTask = new Runnable[1];
        pollTask[0] = () -> {
            if (!pollingActive) return;
            if (System.currentTimeMillis() > deadline) {
                pollingActive = false;
                return;
            }

            String body = "client_id=" + CLIENT_ID
                    + "&scopes=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8)
                    + "&device_code=" + deviceCode
                    + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://id.twitch.tv/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    onSuccess(json);
                    pollingActive = false;
                    return;
                }

                JsonObject err = JsonParser.parseString(resp.body()).getAsJsonObject();
                String message = err.has("message") ? err.get("message").getAsString() : "";

                if (message.contains("authorization_pending")) {
                } else if (message.contains("slow_down")) {
                } else {
                    pollingActive = false;
                    return;
                }

                SCHEDULER.schedule(pollTask[0], interval, TimeUnit.SECONDS);
            } catch (Exception e) {
                pollingActive = false;
            }
        };

        SCHEDULER.schedule(pollTask[0], interval, TimeUnit.SECONDS);
    }

    public void fetchChannelId(String accessToken, java.util.function.Consumer<JsonObject> onId, Runnable onFail) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twitch.tv/helix/users"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Client-Id", CLIENT_ID)
                .GET()
                .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            if (resp.statusCode() != 200) {
                onFail.run();
                return;
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject userData = json.getAsJsonArray("data")
                    .get(0).getAsJsonObject();
            onId.accept(userData);
        }).exceptionally(ex -> {
            onFail.run();
            return null;
        });
    }

    private void onSuccess(JsonObject json) {
        authStatus = AuthStatus.VALID;
        pendingUserCode = null;
        pendingVerificationUri = null;

        TwitchCredentials creds = new TwitchCredentials();
        creds.accessToken = json.get("access_token").getAsString();
        creds.refreshToken = json.get("refresh_token").getAsString();
        int expiresIn = json.get("expires_in").getAsInt();
        creds.expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn;

        fetchChannelId(creds.accessToken,
                userData -> {
                    creds.channelId = userData.get("id").getAsString();
                    creds.channelName = userData.get("login").getAsString();
                    creds.save();
                },
                creds::save
        );
    }
}