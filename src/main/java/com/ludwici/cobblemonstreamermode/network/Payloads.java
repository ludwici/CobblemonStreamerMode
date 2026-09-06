package com.ludwici.cobblemonstreamermode.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class Payloads {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(BattleStatusS2C.ID, BattleStatusS2C.STREAM_CODEC);
    }
}
