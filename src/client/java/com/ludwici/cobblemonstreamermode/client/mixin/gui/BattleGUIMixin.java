package com.ludwici.cobblemonstreamermode.client.mixin.gui;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import com.ludwici.cobblemonstreamermode.client.BattleManager;
import com.ludwici.cobblemonstreamermode.client.PollData;
import com.ludwici.cobblemonstreamermode.client.StreamerModeConfig;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(BattleGUI.class)
public class BattleGUIMixin {
    private static final int PANEL_WIDTH = 196;
    private static final int PANEL_PADDING = 8;
    private static final int OPTION_HEIGHT = 14;
    private static final int PROGRESS_HEIGHT = 6;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderPoll(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        PollData.Snapshot snapshot = BattleManager.INSTANCE.getPollSnapshot();
        if (snapshot == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int contentHeight = 50 + snapshot.options().size() * OPTION_HEIGHT + 18 + PROGRESS_HEIGHT;
        int panelHeight = contentHeight + PANEL_PADDING * 2;
        int x = minecraft.getWindow().getGuiScaledWidth() - PANEL_WIDTH - 12;
        int y = Math.max(12, (minecraft.getWindow().getGuiScaledHeight() - panelHeight) / 2);

        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, 0xDD0F1018);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + panelHeight - 1, 0xCC1B1D2B);

        int titleBoxTop = y + PANEL_PADDING;
        int titleBoxBottom = titleBoxTop + 18;
        graphics.fill(x + PANEL_PADDING, titleBoxTop, x + PANEL_WIDTH - PANEL_PADDING, titleBoxBottom, 0xCC5B28A9);
        graphics.drawCenteredString(minecraft.font, snapshot.title(), x + PANEL_WIDTH / 2, titleBoxTop + 5, 0xFFFFFFFF);

        int lineY = titleBoxBottom + 8;
        for (PollData.OptionView option : snapshot.options()) {
            String prefix = option.winner() ? "> " : "";
            String line = prefix + option.number() + ". " + option.label() + " (" + option.percentage() + "%)";
            graphics.drawString(minecraft.font, line, x + PANEL_PADDING, lineY, 0xFFFFFFFF, true);
            lineY += OPTION_HEIGHT;
        }

        Component timerLine = snapshot.finished()
                ? Component.translatable("cobblemonstreamermode.poll.action_in", snapshot.secondsRemaining())
                : Component.translatable("cobblemonstreamermode.poll.voting_ends_in", snapshot.secondsRemaining());
        int timerBoxTop = lineY + 2;
        int timerBoxBottom = timerBoxTop + 16;
        graphics.fill(x + PANEL_PADDING, timerBoxTop, x + PANEL_WIDTH - PANEL_PADDING, timerBoxBottom, 0x80443373);
        graphics.drawCenteredString(minecraft.font, timerLine, x + PANEL_WIDTH / 2, timerBoxTop + 4, 0xFFEDE6FF);

        int progressX = x + PANEL_PADDING;
        int progressY = y + panelHeight - PANEL_PADDING - PROGRESS_HEIGHT;
        int progressWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        graphics.fill(progressX, progressY, progressX + progressWidth, progressY + PROGRESS_HEIGHT, 0xFF252734);
        graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 1, 0x30FFFFFF);
        int filledWidth = Math.round(progressWidth * snapshot.progress());
        if (filledWidth > 0) {
            graphics.fill(progressX, progressY, progressX + filledWidth, progressY + PROGRESS_HEIGHT, progressColor(snapshot.progress()));
        }
    }

    private static int progressColor(float progress) {
        float clamped = Math.clamp(progress, 0F, 1F);
        int red = Math.round(255F * (1F - clamped));
        int green = Math.round(210F * clamped + 45F * (1F - clamped));
        return 0xFF000000 | (red << 16) | (green << 8);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void blockManualBattleChoice(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (BattleManager.INSTANCE.isPollBlockingInput()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "changeActionSelection", at = @At("TAIL"))
    private void changeAction(BattleActionSelection newSelection, CallbackInfo ci) {
        if (newSelection == null) {
            BattleManager.INSTANCE.cancelPoll();
            return;
        }
        if (!StreamerModeConfig.INSTANCE.isStreamerModeEnabled()) {
            BattleManager.INSTANCE.cancelPoll();
            return;
        }
        if (!TwitchClientManager.INSTANCE.isRunning()) {
            return;
        }

        PollData.Actions action = PollData.Actions.toAction(newSelection);
        switch (action) {
            case MOVE -> startMovePoll((BattleMoveSelection) newSelection);
            case TARGET -> startTargetPoll((BattleTargetSelection) newSelection);
            case SWITCH_POKEMON -> startSwitchPoll((BattleSwitchPokemonSelection) newSelection);
            case GENERAL, FORFEIT -> BattleManager.INSTANCE.cancelPoll();
        }
    }

    private static void startMovePoll(BattleMoveSelection selection) {
        List<PollData.Option> options = new ArrayList<>();
        for (BattleMoveSelection.MoveTile tile : selection.getMoveTiles()) {
            if (!tile.getSelectable()) {
                continue;
            }

            String label = tile.getMoveTemplate().getDisplayName().getString();
            int typeColor = tile.getElementalType().getHue();
            double clickX = tile.getX() + 1D;
            double clickY = tile.getY() + 1D;
            options.add(new PollData.Option(label, typeColor, () -> selection.mousePrimaryClicked(clickX, clickY)));
        }

        BattleManager.INSTANCE.startPoll(PollData.Actions.MOVE, Component.translatable("cobblemonstreamermode.poll.move").getString(), options);
    }

    private static void startTargetPoll(BattleTargetSelection selection) {
        List<PollData.Option> options = new ArrayList<>();
        for (BattleTargetSelection.TargetTile tile : selection.getTargetTiles()) {
            if (!tile.getSelectable()) {
                continue;
            }

            String label = tile.getTarget().getBattlePokemon() == null ? tile.getTarget().getPNX() : tile.getTarget().getBattlePokemon().getDisplayName().getString();
            double clickX = tile.getX() + 1D;
            double clickY = tile.getY() + 1D;
            options.add(new PollData.Option(label, () -> selection.mousePrimaryClicked(clickX, clickY)));
        }

        BattleManager.INSTANCE.startPoll(PollData.Actions.TARGET, Component.translatable("cobblemonstreamermode.poll.target").getString(), options);
    }

    private static void startSwitchPoll(BattleSwitchPokemonSelection selection) {
        List<PollData.Option> options = new ArrayList<>();
        for (BattleSwitchPokemonSelection.SwitchTile tile : selection.getTiles()) {
            boolean selectable = selection.isReviving() ? tile.isFainted() : !tile.isFainted() && !tile.isCurrentlyInBattle();
            if (!selectable) {
                continue;
            }

            String label = tile.getPokemon().getDisplayName(false).getString();
            double clickX = tile.getX() + 1D;
            double clickY = tile.getY() + 1D;
            options.add(new PollData.Option(label, () -> selection.mousePrimaryClicked(clickX, clickY)));
        }

        BattleManager.INSTANCE.startPoll(PollData.Actions.SWITCH_POKEMON, Component.translatable("cobblemonstreamermode.poll.switch").getString(), options);
    }
}
