package com.ludwici.cobblemonstreamermode.client.twitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.ludwici.cobblemonstreamermode.platform.PlatformPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.ludwici.cobblemonstreamermode.CobblemonStreamerMode.LOGGER;

public final class TwitchCredentialStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformPaths.configDir()
            .resolve("cobblemon-streamer-mode")
            .resolve("credentials.json");
    private static final Path LEGACY_FILE = PlatformPaths.configDir()
            .resolve("twitchmod")
            .resolve("credentials.json");

    private TwitchCredentialStore() {
    }

    public static TwitchCredentials load() {
        Path source = Files.exists(FILE) ? FILE : LEGACY_FILE;
        if (!Files.exists(source)) {
            return null;
        }

        try {
            TwitchCredentials credentials = GSON.fromJson(Files.readString(source), TwitchCredentials.class);
            if (credentials != null && source.equals(LEGACY_FILE)) {
                save(credentials);
                deleteLegacyFile();
            }
            return credentials;
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read Twitch credentials from {}", source, e);
            return null;
        }
    }

    public static void save(TwitchCredentials credentials) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(credentials));
        } catch (IOException e) {
            LOGGER.error("Failed to save Twitch credentials to {}", FILE, e);
        }
    }

    private static void deleteLegacyFile() {
        try {
            Files.deleteIfExists(LEGACY_FILE);
            Path parent = LEGACY_FILE.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }
}
