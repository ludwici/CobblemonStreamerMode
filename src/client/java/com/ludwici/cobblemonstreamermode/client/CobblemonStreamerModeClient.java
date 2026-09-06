package com.ludwici.cobblemonstreamermode.client;

import com.ludwici.cobblemonstreamermode.client.gui.TwitchScreen;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchAuthManager;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchClientManager;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CobblemonStreamerModeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TwitchAuthManager.INSTANCE.refreshStatusFromDisk();
        if (TwitchAuthManager.INSTANCE.getAuthStatus() == TwitchAuthManager.AuthStatus.VALID
                && StreamerModeConfig.INSTANCE.isStreamerModeEnabled()) {
            TwitchClientManager.INSTANCE.start();
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> BattleManager.INSTANCE.tick());

        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("twitchbridge")
                            .then(ClientCommandManager.literal("gui")
                                    .executes(context -> {
                                        context.getSource().sendFeedback(Component.translatable("cobblemonstreamermode.command.gui.opening"));
                                        Minecraft.getInstance().tell(() ->
                                                Minecraft.getInstance().setScreen(new TwitchScreen())
                                        );
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("dev-repeat-votes")
                                    .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                            .executes(context -> {
                                                boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                StreamerModeConfig.INSTANCE.setAllowRepeatVotes(enabled);
                                                context.getSource().sendFeedback(Component.translatable(
                                                        enabled
                                                                ? "cobblemonstreamermode.command.repeat_votes.enabled"
                                                                : "cobblemonstreamermode.command.repeat_votes.disabled"
                                                ));
                                                return 1;
                                            })))
            );
        }));

        ClientPlayNetworking.registerGlobalReceiver(BattleStatusS2C.ID, (payload, context) ->
                context.client().execute(() -> BattleManager.INSTANCE.setCurrentStatus(payload.status()))
        );
    }
}
