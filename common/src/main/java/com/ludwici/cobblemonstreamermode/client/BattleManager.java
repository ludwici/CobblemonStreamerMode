package com.ludwici.cobblemonstreamermode.client;

import com.ludwici.cobblemonstreamermode.client.twitch.TwitchVoteEligibility;

import java.util.List;

public final class BattleManager {
    public static final BattleManager INSTANCE = new BattleManager();

    private volatile boolean currentStatus;
    private PollData currentPoll;

    private BattleManager() {
    }

    public boolean isCurrentStatus() {
        return currentStatus;
    }

    public synchronized void startPoll(PollData.Actions action, String title, List<PollData.Option> options) {
        StreamerModeConfig config = StreamerModeConfig.INSTANCE;
        if (!config.isStreamerModeEnabled() || !config.isVotingEnabled(action) || !currentStatus || options.isEmpty()) {
            return;
        }

        long votingDurationMs = config.getVotingDurationSeconds(action) * 1000L;
        currentPoll = new PollData(action, title, options, System.currentTimeMillis(), votingDurationMs);
    }

    public void submitChatMessage(String userId, String message) {
        if (userId == null || userId.isBlank() || message == null || !message.trim().matches("\\d+")) {
            return;
        }

        int optionNumber;
        try {
            optionNumber = Integer.parseInt(message.trim());
        } catch (NumberFormatException ignored) {
            return;
        }

        PollData poll;
        StreamerModeConfig.VotingAudience audience;
        synchronized (this) {
            if (!StreamerModeConfig.INSTANCE.isStreamerModeEnabled() || !currentStatus || currentPoll == null) {
                return;
            }
            poll = currentPoll;
            if (!poll.isAcceptingVotes(optionNumber, System.currentTimeMillis())) {
                return;
            }
            audience = StreamerModeConfig.INSTANCE.getVotingAudience();
        }

        if (audience == StreamerModeConfig.VotingAudience.EVERYONE) {
            acceptVote(poll, audience, userId, optionNumber);
        } else {
            TwitchVoteEligibility.INSTANCE.check(userId, audience).thenAccept(allowed -> {
                if (allowed) {
                    acceptVote(poll, audience, userId, optionNumber);
                }
            });
        }
    }

    private synchronized void acceptVote(PollData poll, StreamerModeConfig.VotingAudience audience, String userId, int optionNumber) {
        StreamerModeConfig config = StreamerModeConfig.INSTANCE;
        if (currentPoll != poll || !currentStatus || !config.isStreamerModeEnabled() || !config.isVotingEnabled(poll.getCurrentAction()) || config.getVotingAudience() != audience) {
            return;
        }
        poll.vote(userId, optionNumber, config.isAllowRepeatVotes(), System.currentTimeMillis());
    }

    public void tick() {
        StreamerModeConfig config = StreamerModeConfig.INSTANCE;
        if (!config.isStreamerModeEnabled()) {
            cancelPoll();
            return;
        }

        Runnable winnerAction = null;
        synchronized (this) {
            if (currentPoll == null) {
                return;
            }
            if (!config.isVotingEnabled(currentPoll.getCurrentAction())) {
                currentPoll = null;
                return;
            }

            long now = System.currentTimeMillis();
            if (currentPoll.shouldFinish(now)) {
                currentPoll.finish(now);
            }

            if (currentPoll.isReadyToExecute(now)) {
                winnerAction = currentPoll.getWinnerAction();
                currentPoll = null;
            }
        }

        if (winnerAction != null) {
            winnerAction.run();
        }
    }

    public synchronized PollData.Snapshot getPollSnapshot() {
        return currentPoll == null ? null : currentPoll.snapshot(System.currentTimeMillis());
    }

    public synchronized boolean isPollBlockingInput() {
        return StreamerModeConfig.INSTANCE.isStreamerModeEnabled() && currentPoll != null && StreamerModeConfig.INSTANCE.isVotingEnabled(currentPoll.getCurrentAction());
    }

    public synchronized void cancelPoll() {
        currentPoll = null;
    }

    public void setCurrentStatus(boolean currentStatus) {
        this.currentStatus = currentStatus;
        if (!currentStatus) {
            cancelPoll();
        }
    }
}
