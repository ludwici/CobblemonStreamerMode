package com.ludwici.cobblemonstreamermode.fabric;

import com.ludwici.cobblemonstreamermode.CobblemonStreamerMode;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class CobblemonStreamerModeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(BattleStatusS2C.ID, BattleStatusS2C.STREAM_CODEC);
        CobblemonStreamerMode.init(ServerPlayNetworking::send);
    }
}
