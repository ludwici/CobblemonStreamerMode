package com.ludwici.cobblemonstreamermode.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class StreamerModeConfig {
    public static final int DEFAULT_VOTING_DURATION_SECONDS = 30;
    public static final int MIN_VOTING_DURATION_SECONDS = 1;
    public static final int MAX_VOTING_DURATION_SECONDS = 3600;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cobblemon-streamer-mode.json");

    public static final StreamerModeConfig INSTANCE = new StreamerModeConfig();

    private final Map<String, PlayerSettings> players = new HashMap<>();
    private Boolean legacyAllowRepeatVotes;
    private Boolean legacyStreamerModeEnabled;

    private StreamerModeConfig() {
        load();
    }

    public synchronized boolean isAllowRepeatVotes() {
        return currentPlayerSettings().allowRepeatVotes;
    }

    public synchronized void setAllowRepeatVotes(boolean allowRepeatVotes) {
        currentPlayerSettings().allowRepeatVotes = allowRepeatVotes;
        save();
    }

    public synchronized boolean isStreamerModeEnabled() {
        return currentPlayerSettings().streamerModeEnabled;
    }

    public synchronized void setStreamerModeEnabled(boolean streamerModeEnabled) {
        currentPlayerSettings().streamerModeEnabled = streamerModeEnabled;
        save();
    }

    public synchronized boolean isVotingEnabled(PollData.Actions action) {
        VotingActionSettings settings = actionSettings(currentPlayerSettings(), action);
        return settings != null && settings.enabled;
    }

    public synchronized void setVotingEnabled(PollData.Actions action, boolean enabled) {
        VotingActionSettings settings = actionSettings(currentPlayerSettings(), action);
        if (settings == null) {
            return;
        }
        settings.enabled = enabled;
        save();
    }

    public synchronized int getVotingDurationSeconds(PollData.Actions action) {
        VotingActionSettings settings = actionSettings(currentPlayerSettings(), action);
        return settings == null ? DEFAULT_VOTING_DURATION_SECONDS : settings.durationSeconds;
    }

    public synchronized void setVotingDurationSeconds(PollData.Actions action, int seconds) {
        VotingActionSettings settings = actionSettings(currentPlayerSettings(), action);
        if (settings == null) {
            return;
        }
        settings.durationSeconds = clampDuration(seconds);
        save();
    }

    private void load() {
        if (!Files.exists(FILE)) {
            return;
        }

        try {
            ConfigFile loaded = GSON.fromJson(Files.readString(FILE), ConfigFile.class);
            if (loaded == null) {
                return;
            }
            if (loaded.players != null) {
                players.putAll(loaded.players);
            }
            legacyAllowRepeatVotes = loaded.allowRepeatVotes;
            legacyStreamerModeEnabled = loaded.streamerModeEnabled;
            normalize();
        } catch (Exception ignored) {
        }
    }

    private PlayerSettings currentPlayerSettings() {
        String playerKey = Minecraft.getInstance().getUser().getProfileId().toString();
        PlayerSettings settings = players.get(playerKey);
        if (settings != null) {
            normalize(settings);
            return settings;
        }

        settings = new PlayerSettings();
        if (legacyAllowRepeatVotes != null) {
            settings.allowRepeatVotes = legacyAllowRepeatVotes;
        }
        if (legacyStreamerModeEnabled != null) {
            settings.streamerModeEnabled = legacyStreamerModeEnabled;
        }
        players.put(playerKey, settings);
        legacyAllowRepeatVotes = null;
        legacyStreamerModeEnabled = null;
        save();
        return settings;
    }

    private void normalize() {
        for (PlayerSettings settings : players.values()) {
            normalize(settings);
        }
    }

    private void normalize(PlayerSettings settings) {
        if (settings.voting == null) {
            settings.voting = new VotingSettings();
        }

        if (settings.voting.battle != null) {
            int legacyDuration = settings.voting.battle.durationSeconds > 0 ? clampDuration(settings.voting.battle.durationSeconds) : DEFAULT_VOTING_DURATION_SECONDS;
            settings.voting.move = newVotingActionSettings(legacyDuration);
            settings.voting.target = newVotingActionSettings(legacyDuration);
            settings.voting.switchPokemon = newVotingActionSettings(legacyDuration);
            settings.voting.battle = null;
        } else {
            settings.voting.move = normalizeAction(settings.voting.move, DEFAULT_VOTING_DURATION_SECONDS);
            settings.voting.target = normalizeAction(settings.voting.target, DEFAULT_VOTING_DURATION_SECONDS);
            settings.voting.switchPokemon = normalizeAction(settings.voting.switchPokemon, DEFAULT_VOTING_DURATION_SECONDS);
        }
    }


    private static VotingActionSettings newVotingActionSettings(int durationSeconds) {
        VotingActionSettings settings = new VotingActionSettings();
        settings.durationSeconds = durationSeconds;
        return settings;
    }

    private static VotingActionSettings normalizeAction(VotingActionSettings settings, int fallbackDuration) {
        if (settings == null) {
            settings = new VotingActionSettings();
            settings.durationSeconds = fallbackDuration;
        }
        if (settings.durationSeconds <= 0) {
            settings.durationSeconds = fallbackDuration;
        } else {
            settings.durationSeconds = clampDuration(settings.durationSeconds);
        }
        return settings;
    }

    private static VotingActionSettings actionSettings(PlayerSettings settings, PollData.Actions action) {
        return switch (action) {
            case MOVE -> settings.voting.move;
            case TARGET -> settings.voting.target;
            case SWITCH_POKEMON -> settings.voting.switchPokemon;
            case GENERAL, FORFEIT -> null;
        };
    }

    private static int clampDuration(int seconds) {
        return Math.clamp(seconds, MIN_VOTING_DURATION_SECONDS, MAX_VOTING_DURATION_SECONDS);
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(new ConfigFile(players)));
        } catch (IOException ignored) {
        }
    }

    private static final class ConfigFile {
        private Map<String, PlayerSettings> players;
        private Boolean allowRepeatVotes;
        private Boolean streamerModeEnabled;

        private ConfigFile() {
        }

        private ConfigFile(Map<String, PlayerSettings> players) {
            this.players = players;
        }
    }

    private static final class PlayerSettings {
        private boolean allowRepeatVotes;
        private boolean streamerModeEnabled = true;
        private VotingSettings voting = new VotingSettings();
    }

    private static final class VotingSettings {
        private VotingActionSettings battle;
        private VotingActionSettings move = new VotingActionSettings();
        private VotingActionSettings target = new VotingActionSettings();
        private VotingActionSettings switchPokemon = new VotingActionSettings();
    }

    private static final class VotingActionSettings {
        private boolean enabled = true;
        private int durationSeconds = DEFAULT_VOTING_DURATION_SECONDS;
    }
}
