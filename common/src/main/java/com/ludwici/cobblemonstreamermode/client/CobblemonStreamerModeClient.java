package com.ludwici.cobblemonstreamermode.client;

import com.ludwici.cobblemonstreamermode.client.gui.CobblemonTwitchScreen;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchAuthManager;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchClientManager;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class CobblemonStreamerModeClient {
    private static boolean initialized;

    private CobblemonStreamerModeClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        BattleStatusS2C.setClientHandler(CobblemonStreamerModeClient::handleBattleStatus);
        TwitchAuthManager.INSTANCE.refreshStatusFromDisk();
        if (TwitchAuthManager.INSTANCE.getAuthStatus() == TwitchAuthManager.AuthStatus.VALID && StreamerModeConfig.INSTANCE.isStreamerModeEnabled()) {
            TwitchClientManager.INSTANCE.start();
        }
    }

    public static void tick() {
        BattleManager.INSTANCE.tick();
    }

    public static void openSettings() {
        Minecraft client = Minecraft.getInstance();
        client.tell(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable("cobblemonstreamermode.command.gui.opening"), false);
            }
            client.setScreen(new CobblemonTwitchScreen());
        });
    }

    public static void handleBattleStatus(boolean status) {
        BattleManager.INSTANCE.setCurrentStatus(status);
    }
}
