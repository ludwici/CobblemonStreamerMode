package com.ludwici.cobblemonstreamermode.client;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.ForfeitConfirmationSelection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class PollData {
    public static final long VOTING_DURATION_MS = 30_000L;
    public static final long RESULT_DELAY_MS = 5_000L;

    private final Actions currentAction;
    private final String title;
    private final List<Option> options;
    private final int[] votes;
    private final Set<String> voters = new HashSet<>();
    private final long startedAtMs;
    private final long votingEndsAtMs;

    private boolean finished;
    private int winnerIndex = -1;
    private long executeAtMs = Long.MAX_VALUE;

    public PollData(Actions currentAction, String title, List<Option> options, long startedAtMs) {
        this.currentAction = currentAction;
        this.title = title;
        this.options = List.copyOf(options);
        this.votes = new int[options.size()];
        this.startedAtMs = startedAtMs;
        this.votingEndsAtMs = startedAtMs + VOTING_DURATION_MS;
    }

    public Actions getCurrentAction() {
        return currentAction;
    }

    public synchronized boolean vote(String userId, int optionNumber, boolean allowRepeatVotes, long nowMs) {
        if (finished || nowMs >= votingEndsAtMs || optionNumber < 1 || optionNumber > options.size()) {
            return false;
        }
        if (!allowRepeatVotes && !voters.add(userId)) {
            return false;
        }
        votes[optionNumber - 1]++;
        return true;
    }

    public synchronized void finish(long nowMs) {
        if (finished) {
            return;
        }

        finished = true;

        int maxVotes = 0;
        for (int vote : votes) {
            maxVotes = Math.max(maxVotes, vote);
        }

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < votes.length; i++) {
            if (votes[i] == maxVotes) {
                candidates.add(i);
            }
        }

        winnerIndex = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        executeAtMs = nowMs + RESULT_DELAY_MS;
    }

    public synchronized boolean shouldFinish(long nowMs) {
        return !finished && nowMs >= votingEndsAtMs;
    }

    public synchronized boolean isReadyToExecute(long nowMs) {
        return finished && winnerIndex >= 0 && nowMs >= executeAtMs;
    }

    public synchronized Runnable getWinnerAction() {
        return winnerIndex < 0 ? null : options.get(winnerIndex).action();
    }

    public synchronized Snapshot snapshot(long nowMs) {
        int totalVotes = 0;
        for (int vote : votes) {
            totalVotes += vote;
        }

        List<OptionView> optionViews = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            int percentage = totalVotes == 0 ? 0 : Math.round(votes[i] * 100F / totalVotes);
            optionViews.add(new OptionView(
                    i + 1,
                    options.get(i).label(),
                    options.get(i).color(),
                    votes[i],
                    percentage,
                    finished && i == winnerIndex
            ));
        }

        float progress = finished
                ? 0F
                : Math.max(0F, Math.min(1F, (votingEndsAtMs - nowMs) / (float) VOTING_DURATION_MS));
        long deadlineMs = finished ? executeAtMs : votingEndsAtMs;
        int secondsRemaining = (int) Math.ceil(Math.max(0L, deadlineMs - nowMs) / 1000D);

        return new Snapshot(title, optionViews, progress, finished, secondsRemaining);
    }

    public record Option(String label, int color, Runnable action) {
        public static final int DEFAULT_COLOR = -1;

        public Option(String label, Runnable action) {
            this(label, DEFAULT_COLOR, action);
        }
    }

    public record OptionView(int number, String label, int color, int votes, int percentage, boolean winner) {
    }

    public record Snapshot(
            String title,
            List<OptionView> options,
            float progress,
            boolean finished,
            int secondsRemaining
    ) {
    }

    public enum Actions {
        MOVE,
        GENERAL,
        TARGET,
        SWITCH_POKEMON,
        FORFEIT;

        public static Actions toAction(BattleActionSelection selection) {
            if (selection instanceof BattleMoveSelection) {
                return MOVE;
            }
            if (selection instanceof BattleGeneralActionSelection) {
                return GENERAL;
            }
            if (selection instanceof BattleTargetSelection) {
                return TARGET;
            }
            if (selection instanceof BattleSwitchPokemonSelection) {
                return SWITCH_POKEMON;
            }
            if (selection instanceof ForfeitConfirmationSelection) {
                return FORFEIT;
            }
            throw new IllegalArgumentException("Unknown BattleActionSelection: " + selection.getClass());
        }
    }
}
