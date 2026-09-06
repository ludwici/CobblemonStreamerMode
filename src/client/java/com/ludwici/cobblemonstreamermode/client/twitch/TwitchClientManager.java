package com.ludwici.cobblemonstreamermode.client.twitch;

import java.util.concurrent.CompletableFuture;

public class TwitchClientManager {
    public static final TwitchClientManager INSTANCE = new TwitchClientManager();

    private volatile boolean running = false;
    private volatile String lastError;

    public boolean isRunning() { return running; }
    public String getLastError() { return lastError; }

    public void start(TwitchCredentials credentials) {
        if (running) return;
        lastError = null;

        CompletableFuture.runAsync(() -> {
            try {
                TwitchBridge.start();
                running = true;
            } catch (Exception e) {
                lastError = e.getMessage();
                running = false;
            }
        });
    }
}