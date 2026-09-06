package com.ludwici.cobblemonstreamermode.client.mixin.gui;

import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.ludwici.cobblemonstreamermode.client.BattleManager;
import com.ludwici.cobblemonstreamermode.client.PollData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cobblemon.mod.common.client.render.RenderHelperKt.drawScaledText;

@Mixin(BattleGUI.class)
public class BattleGUIMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void renderPoll(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Map<String, Integer> pollData = Map.copyOf(BattleManager.INSTANCE.getMovesPoll());
        if (pollData.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        AtomicInteger startY = new AtomicInteger(minecraft.getWindow().getGuiScaledHeight() / 2);

        pollData.forEach((key, value) -> {
            drawScaledText(
                    context,
                    CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                    Component.literal(key + " : " + value),
                    minecraft.getWindow().getGuiScaledWidth() - (169 + 12),
                    startY,
                    1F, 1F, Integer.MAX_VALUE,
                    0xFFFFFFFF,
                    false,
                    true,
                    mouseX, mouseY
            );
            startY.addAndGet(15);
        });
    }

    @Inject(method = "changeActionSelection", at = @At("TAIL"))
    private void changeAction(BattleActionSelection newSelection, CallbackInfo ci) {
        if (newSelection == null) {
            return;
        }

        PollData poll = new PollData();
        PollData.Actions action = PollData.Actions.toAction(newSelection);
        poll.setCurrentAction(action);
        System.out.println(action);
    }
}
