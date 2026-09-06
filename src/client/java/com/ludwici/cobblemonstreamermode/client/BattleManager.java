package com.ludwici.cobblemonstreamermode.client;

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
        if (!StreamerModeConfig.INSTANCE.isStreamerModeEnabled() || !currentStatus || options.isEmpty()) {
            return;
        }
        currentPoll = new PollData(action, title, options, System.currentTimeMillis());
    }

    public synchronized void submitChatMessage(String userId, String message) {
        if (!StreamerModeConfig.INSTANCE.isStreamerModeEnabled() || !currentStatus || currentPoll == null) {
            return;
        }

        String trimmed = message.trim();
        if (!trimmed.matches("\\d+")) {
            return;
        }

        try {
            int optionNumber = Integer.parseInt(trimmed);
            currentPoll.vote(
                    userId,
                    optionNumber,
                    StreamerModeConfig.INSTANCE.isAllowRepeatVotes(),
                    System.currentTimeMillis()
            );
        } catch (NumberFormatException ignored) {
        }
    }

    public void tick() {
        if (!StreamerModeConfig.INSTANCE.isStreamerModeEnabled()) {
            cancelPoll();
            return;
        }

        Runnable winnerAction = null;
        synchronized (this) {
            if (currentPoll == null) {
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
        return StreamerModeConfig.INSTANCE.isStreamerModeEnabled() && currentPoll != null;
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
