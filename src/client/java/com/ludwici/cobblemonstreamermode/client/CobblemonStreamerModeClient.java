package com.ludwici.cobblemonstreamermode.client;

import com.ludwici.cobblemonstreamermode.client.gui.TwitchScreen;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchAuthManager;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CobblemonStreamerModeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TwitchAuthManager.INSTANCE.refreshStatusFromDisk();

		ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) -> {
			dispatcher.register(
					ClientCommandManager.literal("twitchbridge")
							.then(ClientCommandManager.literal("gui")
									.executes(context -> {
										context.getSource().sendFeedback(Component.literal("§7Открываю GUI..."));
										Minecraft.getInstance().tell(() ->
												Minecraft.getInstance().setScreen(new TwitchScreen())
										);
										return 1;
									}))
			);
		}));

		ClientPlayNetworking.registerGlobalReceiver(BattleStatusS2C.ID, (payload, context) -> {
			context.client().execute(() -> {
				BattleManager.INSTANCE.setCurrentStatus(payload.status());
			});
		});
	}
}