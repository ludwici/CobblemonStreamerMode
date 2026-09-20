package com.ludwici.cobblemonstreamermode.fabric.client;

import com.ludwici.cobblemonstreamermode.client.CobblemonStreamerModeClient;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CobblemonStreamerModeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CobblemonStreamerModeClient.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> CobblemonStreamerModeClient.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("streamermode").executes(context -> {
            CobblemonStreamerModeClient.openSettings();
            return 1;
        })));
        ClientPlayNetworking.registerGlobalReceiver(BattleStatusS2C.ID, (payload, context) -> context.client().execute(() -> BattleStatusS2C.handleClient(payload)));
    }
}
