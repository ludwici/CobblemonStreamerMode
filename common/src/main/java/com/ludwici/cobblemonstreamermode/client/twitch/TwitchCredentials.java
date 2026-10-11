package com.ludwici.cobblemonstreamermode.client.twitch;

import java.util.List;

public final class TwitchCredentials {
    public String accessToken;
    public String refreshToken;
    public long expiresAtEpochSeconds;
    public String channelId;
    public String channelName;
    public List<String> scopes;

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() / 1000 >= expiresAtEpochSeconds - 60;
    }
}
