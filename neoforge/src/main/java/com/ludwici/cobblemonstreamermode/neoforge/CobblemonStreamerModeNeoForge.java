package com.ludwici.cobblemonstreamermode.neoforge;

import com.ludwici.cobblemonstreamermode.CobblemonStreamerMode;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(CobblemonStreamerMode.MODID)
public final class CobblemonStreamerModeNeoForge {
    public CobblemonStreamerModeNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        CobblemonStreamerMode.init(PacketDistributor::sendToPlayer);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(BattleStatusS2C.ID, BattleStatusS2C.STREAM_CODEC, (payload, context) -> BattleStatusS2C.handleClient(payload));
    }
}
