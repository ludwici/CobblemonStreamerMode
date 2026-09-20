package com.ludwici.cobblemonstreamermode;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.ludwici.cobblemonstreamermode.network.BattleStatusS2C;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonStreamerMode {
    public static final String MODID = "cobblemon_streamer_mode";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private CobblemonStreamerMode() {
    }

    public static void init(BattleStatusSender sender) {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> event.getBattle().getPlayers().forEach(player -> sender.send(player, new BattleStatusS2C(true))));
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> event.getBattle().getPlayers().forEach(player -> sender.send(player, new BattleStatusS2C(false))));
        CobblemonEvents.BATTLE_FLED.subscribe(event -> event.getBattle().getPlayers().forEach(player -> sender.send(player, new BattleStatusS2C(false))));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @FunctionalInterface
    public interface BattleStatusSender {
        void send(ServerPlayer player, BattleStatusS2C payload);
    }
}
