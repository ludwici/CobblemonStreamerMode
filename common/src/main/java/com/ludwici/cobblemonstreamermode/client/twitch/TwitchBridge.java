package com.ludwici.cobblemonstreamermode.client.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.ChatConnectionStateEvent;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.ludwici.cobblemonstreamermode.client.BattleManager;
import net.minecraft.network.chat.Component;

public final class TwitchBridge {
    private static TwitchClient twitchClient;

    private TwitchBridge() {
    }

    public static synchronized void start(TwitchCredentials credentials) {
        if (credentials.channelName == null || credentials.channelName.isBlank()) {
            throw new IllegalStateException(Component.translatable("cobblemonstreamermode.error.channel_missing").getString());
        }
        if (credentials.accessToken == null || credentials.accessToken.isBlank()) {
            throw new IllegalStateException("Missing Twitch access token");
        }

        stop();

        OAuth2Credential chatCredential = new OAuth2Credential("twitch", credentials.accessToken);
        twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withChatAccount(chatCredential)
                .build();

        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> BattleManager.INSTANCE.submitChatMessage(event.getUser().getId(), event.getMessage()));
        twitchClient.getEventManager().onEvent(ChatConnectionStateEvent.class, event -> TwitchClientManager.INSTANCE.onChatConnectionState(event.getState().name()));
        twitchClient.getChat().joinChannel(credentials.channelName);
    }

    public static synchronized boolean hasClient() {
        return twitchClient != null;
    }

    public static synchronized void reconnect() {
        if (twitchClient == null) {
            throw new IllegalStateException(Component.translatable("cobblemonstreamermode.error.client_not_initialized").getString());
        }
        twitchClient.getChat().reconnect();
    }

    public static synchronized void stop() {
        if (twitchClient == null) {
            return;
        }
        try {
            twitchClient.close();
        } finally {
            twitchClient = null;
        }
    }
}
