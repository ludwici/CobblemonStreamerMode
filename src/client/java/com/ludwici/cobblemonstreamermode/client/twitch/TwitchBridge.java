package com.ludwici.cobblemonstreamermode.client.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.ludwici.cobblemonstreamermode.client.BattleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class TwitchBridge {
    public static TwitchClient twitchClient;
    private static OAuth2Credential auth2Credential;

    public static void start() {
        TwitchAuthManager.INSTANCE.getValidCredentials(credentials -> {
            System.out.println(1);
            auth2Credential = new OAuth2Credential("twitch", credentials.accessToken);
            System.out.println(2);
            twitchClient = TwitchClientBuilder.builder()
                    .withEnableChat(true)
                    .withDefaultAuthToken(auth2Credential)
                    .build();
            System.out.println(3);
            twitchClient.getChat().joinChannel(credentials.channelName);
            Minecraft minecraft = Minecraft.getInstance();
            twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
//                System.out.println(event.getMessage().trim());
                if (BattleManager.INSTANCE.isCurrentStatus()) {
                    String message = event.getMessage().toLowerCase().trim();

                    if (message.equals("test 1")) {
                        BattleManager.INSTANCE.movePoll(event.getUser().getId(), "test 1");
                    }
                    if (message.equals("test 2")) {
                        BattleManager.INSTANCE.movePoll(event.getUser().getId(), "test 2");
                    }
                }
//                event.getReplyInfo().getUserId()
//                minecraft.gui.getChat().addMessage(Component.literal(event.getMessage().trim()));
            });
            System.out.println(4);
            //            twitchClient.getPubSub().listenForPollEvents(auth2Credential, credentials.channelId);
            //            twitchClient.getEventManager().onEvent(PollsEvent.class, System.out::println);
            //            twitchClient.getHelix().getUser
//            }
//            );
        }, () -> {
            System.out.println("Ошбика");
        });
    }
}
