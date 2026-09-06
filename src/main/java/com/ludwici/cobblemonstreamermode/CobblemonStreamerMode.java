package com.ludwici.cobblemonstreamermode;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import com.ludwici.cobblemonstreamermode.network.Payloads;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CobblemonStreamerMode implements ModInitializer {
	public static final String MODID = "cobblemon-streamer-mode";

	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	@Override
	public void onInitialize() {
		Payloads.register();
		CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
			// start polling
			event.getBattle().getPlayers().forEach(player -> {
				BattleStatusS2C payload = new BattleStatusS2C(true);
				ServerPlayNetworking.send(player, payload);
			});
		});

		CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
			event.getBattle().getPlayers().forEach(player -> {
				BattleStatusS2C payload = new BattleStatusS2C(false);
				ServerPlayNetworking.send(player, payload);
			});
		});

		CobblemonEvents.BATTLE_FLED.subscribe(event -> {
			event.getBattle().getPlayers().forEach(player -> {
				BattleStatusS2C payload = new BattleStatusS2C(false);
				ServerPlayNetworking.send(player, payload);
			});
		});

//		CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
//
//		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}
}
