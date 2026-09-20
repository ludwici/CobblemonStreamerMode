package com.ludwici.cobblemonstreamermode.client.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ludwici.cobblemonstreamermode.client.StreamerModeConfig;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class TwitchAuthManager {

    public enum AuthStatus {
        NOT_AUTHORIZED,
        PENDING,
        VALID
    }

    private volatile AuthStatus authStatus = AuthStatus.NOT_AUTHORIZED;

    private volatile String pendingUserCode;
    private volatile String pendingVerificationUri;
    private volatile String authorizedChannelName;
    private volatile Component lastError;

    private static final String CLIENT_ID = "3lsck8o6uuez43dqpu96zaycj7b5ra";
    public static final String SCOPES = "chat:read";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cobblemon-streamer-mode-twitch-auth");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean pollingActive = false;

    public static final TwitchAuthManager INSTANCE = new TwitchAuthManager();

    public AuthStatus getAuthStatus() {
        return authStatus;
    }

    public String getPendingVerificationUri() {
        return pendingVerificationUri;
    }

    public String getAuthorizedChannelName() {
        return authorizedChannelName;
    }

    public String getLastError() {
        return lastError == null ? null : lastError.getString();
    }

    public void refreshStatusFromDisk() {
        TwitchCredentials creds = TwitchCredentialStore.load();
        boolean hasRefreshToken = creds != null && creds.refreshToken != null && !creds.refreshToken.isBlank();
        authStatus = hasRefreshToken ? AuthStatus.VALID : AuthStatus.NOT_AUTHORIZED;
        authorizedChannelName = hasRefreshToken ? creds.channelName : null;
        if (!hasRefreshToken) {
            lastError = null;
        }
    }

    public void beginDeviceFlow() {
        lastError = null;
        pendingUserCode = null;
        pendingVerificationUri = null;
        authStatus = AuthStatus.PENDING;

        String body = "client_id=" + CLIENT_ID + "&scopes=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://id.twitch.tv/oauth2/device"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(resp -> {
            if (resp.statusCode() != 200) {
                lastError = Component.translatable("cobblemonstreamermode.error.device_authorization_http", resp.statusCode());
                authStatus = AuthStatus.NOT_AUTHORIZED;
                return;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            pendingUserCode = json.get("user_code").getAsString();
            pendingVerificationUri = completeVerificationUri(json.get("verification_uri").getAsString(), pendingUserCode);
            authStatus = AuthStatus.PENDING;

            startPolling(
                    json.get("device_code").getAsString(),
                    json.get("interval").getAsInt(),
                    json.get("expires_in").getAsInt()
            );
        }).exceptionally(ex -> {
            lastError = Component.literal(exceptionMessage(ex));
            authStatus = AuthStatus.NOT_AUTHORIZED;
            return null;
        });
    }

    private void refreshAccessToken(TwitchCredentials creds, Consumer<TwitchCredentials> onDone, Runnable onInvalidRefresh, Consumer<Component> onTransientError) {
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
                Component error = Component.translatable("cobblemonstreamermode.error.token_refresh_http", resp.statusCode());
                lastError = error;
                if (resp.statusCode() >= 400 && resp.statusCode() < 500) {
                    authStatus = AuthStatus.NOT_AUTHORIZED;
                    onInvalidRefresh.run();
                } else {
                    onTransientError.accept(error);
                }
                return;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            creds.accessToken = json.get("access_token").getAsString();
            if (json.has("refresh_token")) {
                creds.refreshToken = json.get("refresh_token").getAsString();
            }
            int expiresIn = json.get("expires_in").getAsInt();
            creds.expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn;
            TwitchCredentialStore.save(creds);
            onDone.accept(creds);
        }).exceptionally(ex -> {
            Component error = Component.literal(exceptionMessage(ex));
            lastError = error;
            onTransientError.accept(error);
            return null;
        });
    }

    public void getValidCredentials(Consumer<TwitchCredentials> onReady, Runnable onNeedAuth, Consumer<Component> onTransientError) {
        TwitchCredentials creds = TwitchCredentialStore.load();

        if (creds == null || creds.refreshToken == null || creds.refreshToken.isBlank()) {
            authStatus = AuthStatus.NOT_AUTHORIZED;
            authorizedChannelName = null;
            lastError = null;
            onNeedAuth.run();
            return;
        }

        if (creds.accessToken == null || creds.accessToken.isBlank() || creds.isExpired()) {
            refreshAndValidate(creds, onReady, onNeedAuth, onTransientError);
            return;
        }

        validateCredentials(creds, onReady, () -> refreshAndValidate(creds, onReady, onNeedAuth, onTransientError), onTransientError);
    }

    private void refreshAndValidate(TwitchCredentials creds, Consumer<TwitchCredentials> onReady, Runnable onNeedAuth, Consumer<Component> onTransientError) {
        refreshAccessToken(
                creds,
                refreshed -> validateCredentials(
                        refreshed,
                        onReady,
                        () -> markNeedsAuthorization(onNeedAuth),
                        onTransientError
                ),
                () -> markNeedsAuthorization(onNeedAuth),
                onTransientError
        );
    }

    private void validateCredentials(TwitchCredentials creds, Consumer<TwitchCredentials> onReady, Runnable onUnauthorized, Consumer<Component> onTransientError) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://id.twitch.tv/oauth2/validate"))
                .header("Authorization", "Bearer " + creds.accessToken)
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
                Component error = Component.translatable("cobblemonstreamermode.error.token_validation_http", resp.statusCode());
                lastError = error;
                if (resp.statusCode() >= 400 && resp.statusCode() < 500) {
                    onUnauthorized.run();
                } else {
                    onTransientError.accept(error);
                }
                return;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!CLIENT_ID.equals(json.get("client_id").getAsString())) {
                lastError = Component.translatable("cobblemonstreamermode.error.token_wrong_client");
                onUnauthorized.run();
                return;
            }

            creds.channelId = json.get("user_id").getAsString();
            creds.channelName = json.get("login").getAsString();
            authorizedChannelName = creds.channelName;
            creds.expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + json.get("expires_in").getAsInt();
            TwitchCredentialStore.save(creds);
            authStatus = AuthStatus.VALID;
            lastError = null;
            onReady.accept(creds);
        }).exceptionally(ex -> {
            Component error = Component.literal(exceptionMessage(ex));
            lastError = error;
            onTransientError.accept(error);
            return null;
        });
    }

    private void markNeedsAuthorization(Runnable onNeedAuth) {
        lastError = null;
        authStatus = AuthStatus.NOT_AUTHORIZED;
        authorizedChannelName = null;
        onNeedAuth.run();
    }

    private void startPolling(String deviceCode, int interval, int expiresIn) {
        pollingActive = true;
        long deadline = System.currentTimeMillis() + expiresIn * 1000L;
        AtomicInteger currentInterval = new AtomicInteger(interval);

        Runnable[] pollTask = new Runnable[1];
        pollTask[0] = () -> {
            if (!pollingActive) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                pollingActive = false;
                authStatus = AuthStatus.NOT_AUTHORIZED;
                lastError = Component.translatable("cobblemonstreamermode.error.authorization_expired");
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

                if (message.contains("slow_down")) {
                    currentInterval.addAndGet(5);
                } else if (!message.contains("authorization_pending")) {
                    pollingActive = false;
                    authStatus = AuthStatus.NOT_AUTHORIZED;
                    lastError = message.isBlank() ? Component.translatable("cobblemonstreamermode.error.authorization_failed") : Component.literal(message);
                    return;
                }

                SCHEDULER.schedule(pollTask[0], currentInterval.get(), TimeUnit.SECONDS);
            } catch (Exception e) {
                pollingActive = false;
                authStatus = AuthStatus.NOT_AUTHORIZED;
                lastError = Component.literal(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        };

        SCHEDULER.schedule(pollTask[0], currentInterval.get(), TimeUnit.SECONDS);
    }

    private void onSuccess(JsonObject json) {
        pendingUserCode = null;
        pendingVerificationUri = null;

        TwitchCredentials creds = new TwitchCredentials();
        creds.accessToken = json.get("access_token").getAsString();
        creds.refreshToken = json.get("refresh_token").getAsString();
        int expiresIn = json.get("expires_in").getAsInt();
        creds.expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn;
        TwitchCredentialStore.save(creds);

        validateCredentials(
                creds,
                validated -> {
                    if (StreamerModeConfig.INSTANCE.isStreamerModeEnabled()) {
                        TwitchClientManager.INSTANCE.start(validated);
                    }
                },
                () -> authStatus = AuthStatus.NOT_AUTHORIZED,
                error -> {
                    lastError = error;
                    authStatus = AuthStatus.NOT_AUTHORIZED;
                }
        );
    }

    private static String completeVerificationUri(String verificationUri, String userCode) {
        if (verificationUri == null || verificationUri.isBlank() || userCode == null || userCode.isBlank()) {
            return verificationUri;
        }
        if (verificationUri.contains("device-code=")) {
            return verificationUri;
        }

        String separator = verificationUri.contains("?") ? "&" : "?";
        StringBuilder url = new StringBuilder(verificationUri);
        if (!verificationUri.contains("public=")) {
            url.append(separator).append("public=true");
            separator = "&";
        }
        url.append(separator).append("device-code=").append(URLEncoder.encode(userCode, StandardCharsets.UTF_8));
        return url.toString();
    }

    private static String exceptionMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

}
