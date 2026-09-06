package com.ludwici.cobblemonstreamermode.client.twitch;

import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TwitchClientManager {
    public static final TwitchClientManager INSTANCE = new TwitchClientManager();

    private static final long REVALIDATION_INTERVAL_MINUTES = 60L;
    private static final long REVALIDATION_RETRY_MINUTES = 5L;
    private static final long RECONNECT_DELAY_SECONDS = 10L;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cobblemon-streamer-mode-twitch");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean revalidating = new AtomicBoolean(false);

    private volatile boolean running = false;
    private volatile boolean starting = false;
    private volatile boolean reconnecting = false;
    private volatile boolean shouldRun = false;
    private volatile String lastError;

    private ScheduledFuture<?> revalidationTask;
    private ScheduledFuture<?> revalidationRetryTask;
    private ScheduledFuture<?> reconnectTask;

    private TwitchClientManager() {
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isStarting() {
        return starting;
    }

    public boolean isReconnecting() {
        return reconnecting;
    }

    public String getLastError() {
        return lastError;
    }

    public void start() {
        synchronized (this) {
            if (running || starting) {
                return;
            }
            shouldRun = true;
            starting = true;
            reconnecting = false;
            lastError = null;
        }

        TwitchAuthManager.INSTANCE.getValidCredentials(
                this::startValidated,
                () -> handleAuthorizationLost(tr("cobblemonstreamermode.error.auth_required")),
                error -> {
                    synchronized (this) {
                        starting = false;
                        running = false;
                        lastError = tr("cobblemonstreamermode.error.oauth", error);
                    }
                    scheduleStartRetry();
                }
        );
    }

    public void start(TwitchCredentials credentials) {
        synchronized (this) {
            if (running || starting) {
                return;
            }
            shouldRun = true;
            starting = true;
            reconnecting = false;
            lastError = null;
        }
        startValidated(credentials);
    }

    private void startValidated(TwitchCredentials credentials) {
        synchronized (this) {
            if (!shouldRun) {
                starting = false;
                reconnecting = false;
                return;
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                TwitchBridge.start(credentials);
                synchronized (this) {
                    if (!shouldRun) {
                        running = false;
                        starting = false;
                        reconnecting = false;
                        TwitchBridge.stop();
                        return;
                    }
                    running = true;
                    starting = false;
                    reconnecting = false;
                    lastError = null;
                    cancelReconnectTask();
                    scheduleHourlyRevalidation();
                }
            } catch (Exception e) {
                synchronized (this) {
                    lastError = e.getMessage();
                    running = false;
                    starting = false;
                    reconnecting = shouldRun;
                }
                scheduleStartRetry();
            }
        });
    }


    public void stop() {
        synchronized (this) {
            shouldRun = false;
            running = false;
            starting = false;
            reconnecting = false;
            lastError = null;
            revalidating.set(false);
            cancelScheduledTasks();
        }
        TwitchBridge.stop();
    }

    public void onChatConnectionState(String state) {
        if (!shouldRun) {
            return;
        }

        switch (state) {
            case "CONNECTED" -> {
                synchronized (this) {
                    running = true;
                    starting = false;
                    reconnecting = false;
                    lastError = null;
                    cancelReconnectTask();
                }
            }
            case "CONNECTING", "RECONNECTING" -> {
                synchronized (this) {
                    reconnecting = true;
                    running = false;
                    cancelReconnectTask();
                }
            }
            case "DISCONNECTED" -> {
                running = false;
                starting = false;
                reconnecting = true;
                scheduleSocketReconnect();
            }
            default -> {
            }
        }
    }

    private synchronized void scheduleHourlyRevalidation() {
        if (revalidationTask != null && !revalidationTask.isDone()) {
            return;
        }
        revalidationTask = scheduler.scheduleAtFixedRate(
                this::revalidateSession,
                REVALIDATION_INTERVAL_MINUTES,
                REVALIDATION_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private void revalidateSession() {
        if (!shouldRun || !revalidating.compareAndSet(false, true)) {
            return;
        }

        TwitchAuthManager.INSTANCE.getValidCredentials(
                credentials -> {
                    revalidating.set(false);
                    synchronized (this) {
                        cancelRevalidationRetryTask();
                        if (lastError != null && lastError.startsWith("Twitch OAuth:")) {
                            lastError = null;
                        }
                    }
                },
                () -> {
                    revalidating.set(false);
                    handleAuthorizationLost(tr("cobblemonstreamermode.error.auth_invalid"));
                },
                error -> {
                    revalidating.set(false);
                    lastError = tr("cobblemonstreamermode.error.oauth", error);
                    scheduleRevalidationRetry();
                }
        );
    }

    private synchronized void scheduleRevalidationRetry() {
        if (!shouldRun || (revalidationRetryTask != null && !revalidationRetryTask.isDone())) {
            return;
        }
        revalidationRetryTask = scheduler.schedule(
                this::revalidateSession,
                REVALIDATION_RETRY_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private synchronized void scheduleSocketReconnect() {
        if (!shouldRun || (reconnectTask != null && !reconnectTask.isDone())) {
            return;
        }
        reconnectTask = scheduler.schedule(() -> {
            synchronized (this) {
                reconnectTask = null;
            }
            if (!shouldRun || running) {
                return;
            }
            try {
                TwitchBridge.reconnect();
            } catch (Exception e) {
                lastError = tr("cobblemonstreamermode.error.chat_reconnect", e.getMessage());
                scheduleStartRetry();
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void scheduleStartRetry() {
        if (!shouldRun || (reconnectTask != null && !reconnectTask.isDone())) {
            return;
        }
        reconnecting = true;
        reconnectTask = scheduler.schedule(() -> {
            synchronized (this) {
                reconnectTask = null;
            }
            if (!shouldRun || running || starting) {
                return;
            }
            if (TwitchBridge.hasClient()) {
                try {
                    TwitchBridge.reconnect();
                    return;
                } catch (Exception ignored) {
                    TwitchBridge.stop();
                }
            }
            start();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void handleAuthorizationLost(String message) {
        synchronized (this) {
            shouldRun = false;
            running = false;
            starting = false;
            reconnecting = false;
            lastError = message;
            cancelScheduledTasks();
        }
        TwitchBridge.stop();
    }

    private synchronized void cancelScheduledTasks() {
        cancelReconnectTask();
        cancelRevalidationRetryTask();
        if (revalidationTask != null) {
            revalidationTask.cancel(false);
            revalidationTask = null;
        }
    }

    private synchronized void cancelReconnectTask() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    private synchronized void cancelRevalidationRetryTask() {
        if (revalidationRetryTask != null) {
            revalidationRetryTask.cancel(false);
            revalidationRetryTask = null;
        }
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
