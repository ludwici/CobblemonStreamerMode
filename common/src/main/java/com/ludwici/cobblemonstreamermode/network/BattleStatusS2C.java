package com.ludwici.cobblemonstreamermode.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

import static com.ludwici.cobblemonstreamermode.CobblemonStreamerMode.MODID;

public record BattleStatusS2C(Boolean status) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(MODID, "battle_status");
    public static final CustomPacketPayload.Type<BattleStatusS2C> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleStatusS2C> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, BattleStatusS2C::status, BattleStatusS2C::new);

    private static Consumer<Boolean> clientHandler = status -> {
    };

    public static void setClientHandler(Consumer<Boolean> handler) {
        clientHandler = handler;
    }

    public static void handleClient(BattleStatusS2C payload) {
        clientHandler.accept(payload.status());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
