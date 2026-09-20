package com.ludwici.cobblemonstreamermode.client;

import com.ludwici.cobblemonstreamermode.client.gui.CobblemonTwitchScreen;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchAuthManager;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchClientManager;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
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
        if (TwitchAuthManager.INSTANCE.getAuthStatus() == TwitchAuthManager.AuthStatus.VALID && StreamerModeConfig.INSTANCE.isStreamerModeEnabled()) {
            TwitchClientManager.INSTANCE.start();
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> BattleManager.INSTANCE.tick());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("streamermode").executes(context -> {
                    context.getSource().sendFeedback(Component.translatable("cobblemonstreamermode.command.gui.opening"));
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new CobblemonTwitchScreen()));
                    return 1;
                })
        ));

        ClientPlayNetworking.registerGlobalReceiver(BattleStatusS2C.ID, (payload, context) ->
                context.client().execute(() -> BattleManager.INSTANCE.setCurrentStatus(payload.status()))
        );
    }
}
