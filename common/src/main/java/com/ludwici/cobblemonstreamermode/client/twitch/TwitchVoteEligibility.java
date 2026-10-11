package com.ludwici.cobblemonstreamermode.client.twitch;

import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.InboundFollow;
import com.github.twitch4j.helix.domain.InboundFollowers;
import com.github.twitch4j.helix.domain.Subscription;
import com.github.twitch4j.helix.domain.SubscriptionList;
import com.ludwici.cobblemonstreamermode.client.StreamerModeConfig;
import feign.FeignException;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class TwitchVoteEligibility {
    public static final TwitchVoteEligibility INSTANCE = new TwitchVoteEligibility();

    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_CACHE_ENTRIES = 5_000;
    private static final int MAX_PENDING = 2_000;
    private static final int SUBSCRIBER_BATCH_SIZE = 100;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cobblemon-streamer-mode-vote-eligibility");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Key, CompletableFuture<Boolean>> pending = new HashMap<>();
    private final ArrayDeque<Key> queue = new ArrayDeque<>();
    private final LinkedHashMap<Key, CacheEntry> cache = new LinkedHashMap<>(128, 0.75F, true);

    private TwitchCredentials credentials;
    private TwitchHelix helix;
    private long pauseUntilMs;
    private int generation;
    private boolean inFlight;
    private ScheduledFuture<?> queueTask;
    private volatile Component lastError;

    private TwitchVoteEligibility() {
    }

    public void start(TwitchCredentials credentials, TwitchHelix helix) {
        List<CompletableFuture<Boolean>> discarded;
        synchronized (this) {
            discarded = reset();
            this.credentials = credentials;
            this.helix = helix;
        }
        discarded.forEach(future -> future.complete(false));
    }

    public void updateCredentials(TwitchCredentials credentials) {
        synchronized (this) {
            if (this.credentials != null && this.credentials.channelId != null && this.credentials.channelId.equals(credentials.channelId)) {
                this.credentials = credentials;
                return;
            }
        }
        TwitchHelix current;
        synchronized (this) {
            current = helix;
        }
        start(credentials, current);
    }

    public void stop() {
        List<CompletableFuture<Boolean>> discarded;
        synchronized (this) {
            discarded = reset();
            credentials = null;
            helix = null;
        }
        discarded.forEach(future -> future.complete(false));
    }

    public String getLastError() {
        Component error = lastError;
        return error == null ? null : error.getString();
    }

    public CompletableFuture<Boolean> check(String userId, StreamerModeConfig.VotingAudience audience) {
        if (audience == StreamerModeConfig.VotingAudience.EVERYONE) {
            return CompletableFuture.completedFuture(true);
        }
        if (userId == null || userId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        return switch (audience) {
            case FOLLOWERS -> checkRole(userId, Role.FOLLOWER);
            case SUBSCRIBERS -> checkRole(userId, Role.SUBSCRIBER);
            case FOLLOWERS_OR_SUBSCRIBERS -> checkRole(userId, Role.FOLLOWER).thenCompose(follower -> follower ? CompletableFuture.completedFuture(true) : checkRole(userId, Role.SUBSCRIBER));
            case EVERYONE -> CompletableFuture.completedFuture(true);
        };
    }

    private synchronized CompletableFuture<Boolean> checkRole(String userId, Role role) {
        if (credentials == null || helix == null || credentials.channelId == null || credentials.accessToken == null || credentials.accessToken.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (!credentials.hasScope(role.scope)) {
            lastError = Component.translatable("cobblemonstreamermode.error.vote_lookup");
            return CompletableFuture.completedFuture(false);
        }
        Key key = new Key(role, userId);
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAtMs > now) {
            return CompletableFuture.completedFuture(entry.allowed);
        }
        cache.remove(key);
        CompletableFuture<Boolean> existing = pending.get(key);
        if (existing != null) {
            return existing;
        }
        if (pending.size() >= MAX_PENDING) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(key, future);
        queue.addLast(key);
        scheduleNext(150L);
        return future;
    }

    private synchronized void scheduleNext(long delayMs) {
        if (inFlight || queue.isEmpty() || queueTask != null && !queueTask.isDone()) {
            return;
        }
        queueTask = scheduler.schedule(this::processQueue, delayMs, TimeUnit.MILLISECONDS);
    }

    private void processQueue() {
        List<Key> batch = new ArrayList<>(SUBSCRIBER_BATCH_SIZE);
        TwitchCredentials current;
        TwitchHelix client;
        int requestGeneration;
        synchronized (this) {
            queueTask = null;
            long now = System.currentTimeMillis();
            if (inFlight || credentials == null || helix == null || queue.isEmpty()) {
                return;
            }
            if (now < pauseUntilMs) {
                scheduleNext(pauseUntilMs - now);
                return;
            }
            current = credentials;
            client = helix;
            requestGeneration = generation;
            Key first = queue.removeFirst();
            batch.add(first);
            if (first.role == Role.SUBSCRIBER) {
                var iterator = queue.iterator();
                while (iterator.hasNext() && batch.size() < SUBSCRIBER_BATCH_SIZE) {
                    Key next = iterator.next();
                    if (next.role == Role.SUBSCRIBER) {
                        batch.add(next);
                        iterator.remove();
                    }
                }
            }
            inFlight = true;
        }

        try {
            if (batch.getFirst().role == Role.FOLLOWER) {
                client.getChannelFollowers(current.accessToken, current.channelId, batch.getFirst().userId, 1, null).observe().subscribe(result -> {
                    InboundFollowers followers = (InboundFollowers) result;
                    Set<String> allowed = new HashSet<>();
                    if (followers != null && followers.getFollows() != null) {
                        for (InboundFollow follower : followers.getFollows()) {
                            allowed.add(follower.getUserId());
                        }
                        finishBatch(batch, requestGeneration, allowed, null);
                    } else {
                        finishBatch(batch, requestGeneration, null, null);
                    }
                }, failure -> finishBatch(batch, requestGeneration, null, failure));
            } else {
                List<String> userIds = batch.stream().map(Key::userId).toList();
                client.getSubscriptionsByUser(current.accessToken, current.channelId, userIds).observe().subscribe(result -> {
                    SubscriptionList subscriptions = (SubscriptionList) result;
                    Set<String> allowed = new HashSet<>();
                    if (subscriptions != null && subscriptions.getSubscriptions() != null) {
                        for (Subscription subscription : subscriptions.getSubscriptions()) {
                            allowed.add(subscription.getUserId());
                        }
                        finishBatch(batch, requestGeneration, allowed, null);
                    } else {
                        finishBatch(batch, requestGeneration, null, null);
                    }
                }, failure -> finishBatch(batch, requestGeneration, null, failure));
            }
        } catch (RuntimeException | LinkageError exception) {
            finishBatch(batch, requestGeneration, null, exception);
        }
    }

    private void finishBatch(List<Key> batch, int requestGeneration, Set<String> allowed, Throwable failure) {
        Map<CompletableFuture<Boolean>, Boolean> resolved = new LinkedHashMap<>();
        synchronized (this) {
            if (generation != requestGeneration) {
                return;
            }
            inFlight = false;
            long now = System.currentTimeMillis();
            if (allowed == null) {
                lastError = Component.translatable("cobblemonstreamermode.error.vote_lookup");
                FeignException apiError = findApiError(failure);
                if (apiError != null && apiError.status() == 429) {
                    pauseUntilMs = Math.max(now + 1_000L, rateLimitReset(apiError, now));
                } else if (apiError != null && (apiError.status() == 401 || apiError.status() == 403)) {
                    pauseUntilMs = now + 60_000L;
                }
            } else {
                lastError = null;
            }
            for (Key key : batch) {
                CompletableFuture<Boolean> future = pending.remove(key);
                if (future == null) {
                    continue;
                }
                boolean isAllowed = allowed != null && allowed.contains(key.userId);
                if (allowed != null) {
                    cache.put(key, new CacheEntry(isAllowed, now + CACHE_TTL_MS));
                }
                resolved.put(future, isAllowed);
            }
            while (cache.size() > MAX_CACHE_ENTRIES) {
                cache.remove(cache.keySet().iterator().next());
            }
            scheduleNext(Math.max(200L, pauseUntilMs - now));
        }
        resolved.forEach(CompletableFuture::complete);
    }

    private static FeignException findApiError(Throwable failure) {
        while (failure != null) {
            if (failure instanceof FeignException exception) {
                return exception;
            }
            failure = failure.getCause();
        }
        return null;
    }

    private static long rateLimitReset(FeignException exception, long now) {
        if (exception.responseHeaders() == null) {
            return now + 60_000L;
        }
        for (var header : exception.responseHeaders().entrySet()) {
            if (header.getKey().equalsIgnoreCase("Ratelimit-Reset")) {
                for (String value : header.getValue()) {
                    try {
                        return Long.parseLong(value) * 1000L;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return now + 60_000L;
    }

    private List<CompletableFuture<Boolean>> reset() {
        generation++;
        List<CompletableFuture<Boolean>> discarded = new ArrayList<>(pending.values());
        pending.clear();
        queue.clear();
        cache.clear();
        pauseUntilMs = 0;
        inFlight = false;
        if (queueTask != null) {
            queueTask.cancel(false);
            queueTask = null;
        }
        lastError = null;
        return discarded;
    }

    private enum Role {
        FOLLOWER(TwitchAuthManager.FOLLOWERS_SCOPE),
        SUBSCRIBER(TwitchAuthManager.SUBSCRIBERS_SCOPE);

        private final String scope;

        Role(String scope) {
            this.scope = scope;
        }
    }

    private record Key(Role role, String userId) {
    }

    private record CacheEntry(boolean allowed, long expiresAtMs) {
    }
}
