package com.ludwici.cobblemonstreamermode.neoforge.client;

import com.ludwici.cobblemonstreamermode.CobblemonStreamerMode;
import com.ludwici.cobblemonstreamermode.client.CobblemonStreamerModeClient;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CobblemonStreamerMode.MODID, dist = Dist.CLIENT)
public final class CobblemonStreamerModeNeoForgeClient {
    public CobblemonStreamerModeNeoForgeClient() {
        CobblemonStreamerModeClient.init();
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::registerClientCommands);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        CobblemonStreamerModeClient.tick();
    }

    private void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("streamermode").executes(context -> {
            CobblemonStreamerModeClient.openSettings();
            return 1;
        }));
    }
}
