package com.ludwici.cobblemonstreamermode.client.mixin.gui;

import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.cobblemon.mod.common.client.render.RenderHelperKt.drawScaledText;

@Mixin(BattleMoveSelection.class)
public class BattleMoveSelectionMixin {
    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void renderIcons(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BattleMoveSelection self = (BattleMoveSelection) (Object) (this);
        self.getMoveTiles().forEach(moveTile -> {
//            System.out.println(moveTile.getMove().id);
            drawScaledText(
                    context,
                    CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                    Component.literal("1"),
                    moveTile.getX(),
                    moveTile.getY()-10,
                    1F, 1F, Integer.MAX_VALUE,
                    0xFFFFFFFF,
                    true,
                    true,
                    mouseX, mouseY
            );
        });
    }
}
