package com.ludwici.cobblemonstreamermode.client.twitch;

public final class TwitchCredentials {
    public String accessToken;
    public String refreshToken;
    public long expiresAtEpochSeconds;
    public String channelId;
    public String channelName;

    public boolean isExpired() {
        return System.currentTimeMillis() / 1000 >= expiresAtEpochSeconds - 60;
    }
}
