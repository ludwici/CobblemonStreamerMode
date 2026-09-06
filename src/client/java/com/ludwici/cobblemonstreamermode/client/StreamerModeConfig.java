package com.ludwici.cobblemonstreamermode.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StreamerModeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("cobblemon-streamer-mode.json");

    public static final StreamerModeConfig INSTANCE = new StreamerModeConfig();

    private boolean allowRepeatVotes = false;
    private boolean streamerModeEnabled = true;

    private StreamerModeConfig() {
        load();
    }

    public synchronized boolean isAllowRepeatVotes() {
        return allowRepeatVotes;
    }

    public synchronized void setAllowRepeatVotes(boolean allowRepeatVotes) {
        this.allowRepeatVotes = allowRepeatVotes;
        save();
    }

    public synchronized boolean isStreamerModeEnabled() {
        return streamerModeEnabled;
    }

    public synchronized void setStreamerModeEnabled(boolean streamerModeEnabled) {
        this.streamerModeEnabled = streamerModeEnabled;
        save();
    }

    public synchronized void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }

        try {
            ConfigFile loaded = GSON.fromJson(Files.readString(FILE), ConfigFile.class);
            if (loaded != null) {
                allowRepeatVotes = loaded.allowRepeatVotes;
                // Older configs did not contain this property. Preserve the old behavior
                // (streamer mode enabled) when upgrading them.
                streamerModeEnabled = loaded.streamerModeEnabled == null || loaded.streamerModeEnabled;
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(new ConfigFile(allowRepeatVotes, streamerModeEnabled)));
        } catch (IOException ignored) {
        }
    }

    private static final class ConfigFile {
        private boolean allowRepeatVotes;
        private Boolean streamerModeEnabled;

        private ConfigFile() {
        }

        private ConfigFile(boolean allowRepeatVotes, boolean streamerModeEnabled) {
            this.allowRepeatVotes = allowRepeatVotes;
            this.streamerModeEnabled = streamerModeEnabled;
        }
    }
}
