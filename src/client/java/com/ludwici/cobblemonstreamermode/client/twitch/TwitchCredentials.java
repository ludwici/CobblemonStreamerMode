package com.ludwici.cobblemonstreamermode.client.twitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TwitchCredentials {
    public String accessToken;
    public String refreshToken;
    public long expiresAtEpochSeconds;
    public String channelId;
    public String channelName;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("twitchmod/credentials.json");

    public static TwitchCredentials load() {
        try {
            if (!Files.exists(FILE)) return null;
            String json = Files.readString(FILE);
            return GSON.fromJson(json, TwitchCredentials.class);
        } catch (IOException e) {
            return null;
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isExpired() {
        return System.currentTimeMillis() / 1000 >= expiresAtEpochSeconds - 60;
    }
}
