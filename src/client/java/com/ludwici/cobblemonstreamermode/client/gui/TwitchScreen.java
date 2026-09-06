package com.ludwici.cobblemonstreamermode.client.gui;

import com.ludwici.cobblemonstreamermode.client.BattleManager;
import com.ludwici.cobblemonstreamermode.client.StreamerModeConfig;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchAuthManager;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchClientManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TwitchScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 218;
    private static final int BUTTON_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TWITCH_PURPLE = 0xFF9146FF;

    private Button requestAuthButton;
    private Button streamerModeButton;
    private Button repeatVotesButton;
    private EditBox authorizationUrlBox;

    public TwitchScreen() {
        super(Component.translatable("cobblemonstreamermode.screen.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = panelTop();
        int fieldWidth = Math.max(180, panelWidth() - 32);
        int buttonWidth = Math.min(BUTTON_WIDTH, fieldWidth);

        requestAuthButton = Button.builder(Component.translatable("cobblemonstreamermode.auth.request"), btn ->
                TwitchAuthManager.INSTANCE.beginDeviceFlow()
        ).bounds(centerX - buttonWidth / 2, top + 104, buttonWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(requestAuthButton);

        authorizationUrlBox = new EditBox(
                this.font,
                centerX - fieldWidth / 2,
                top + 84,
                fieldWidth,
                BUTTON_HEIGHT,
                Component.translatable("cobblemonstreamermode.auth.url")
        );
        authorizationUrlBox.setMaxLength(512);
        authorizationUrlBox.setEditable(false);
        authorizationUrlBox.setTextColorUneditable(0xFFFFFFFF);
        addRenderableWidget(authorizationUrlBox);

        streamerModeButton = Button.builder(Component.empty(), btn -> toggleStreamerMode())
                .bounds(centerX - buttonWidth / 2, top + 110, buttonWidth, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(streamerModeButton);

        repeatVotesButton = Button.builder(Component.empty(), btn -> {
            StreamerModeConfig config = StreamerModeConfig.INSTANCE;
            config.setAllowRepeatVotes(!config.isAllowRepeatVotes());
            updateDevButton();
        }).bounds(centerX - buttonWidth / 2, top + 175, buttonWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(repeatVotesButton);
        updateDevButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int panelWidth = panelWidth();
        int left = centerX - panelWidth / 2;
        int top = panelTop();
        int right = left + panelWidth;
        int bottom = top + PANEL_HEIGHT;

        graphics.fill(left, top, right, bottom, 0xE0121017);
        graphics.fill(left, top, right, top + 2, TWITCH_PURPLE);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF3A3340);

        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.screen.title"),
                centerX,
                top + 14,
                0xFFFFFFFF
        );

        TwitchAuthManager auth = TwitchAuthManager.INSTANCE;
        TwitchClientManager client = TwitchClientManager.INSTANCE;
        TwitchAuthManager.AuthStatus status = auth.getAuthStatus();
        updateWidgets(status, auth, client);

        switch (status) {
            case NOT_AUTHORIZED -> renderNotAuthorized(graphics, centerX, top);
            case PENDING -> renderPending(graphics, centerX, top, auth);
            case VALID -> renderAuthorized(graphics, centerX, top, auth, client);
        }

        graphics.fill(left + 16, top + 150, right - 16, top + 151, 0xFF3A3340);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.dev.repeat_votes_label"),
                centerX,
                top + 160,
                0xFFAAA4AF
        );

        String error = auth.getLastError() != null ? auth.getLastError() : client.getLastError();
        if (error != null && !error.isBlank()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("cobblemonstreamermode.error", compact(error, 48)),
                    centerX,
                    top + 202,
                    0xFFFF7777
            );
        }

        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderNotAuthorized(GuiGraphics graphics, int centerX, int top) {
        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.auth.not_authorized"),
                centerX,
                top + 46,
                0xFFFF7777
        );
        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.auth.channel_auto_hint"),
                centerX,
                top + 70,
                0xFFAAA4AF
        );
    }

    private void renderPending(GuiGraphics graphics, int centerX, int top, TwitchAuthManager auth) {
        String url = auth.getPendingVerificationUri();
        if (url == null || url.isBlank()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("cobblemonstreamermode.auth.requesting"),
                    centerX,
                    top + 46,
                    0xFFFFD866
            );
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("cobblemonstreamermode.auth.requesting_hint"),
                    centerX,
                    top + 70,
                    0xFFAAA4AF
            );
            return;
        }

        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.auth.pending"),
                centerX,
                top + 46,
                0xFFFFD866
        );
        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.auth.pending_hint"),
                centerX,
                top + 66,
                0xFFDDDAE0
        );
        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.auth.copy_hint"),
                centerX,
                top + 111,
                0xFFAAA4AF
        );
    }

    private void renderAuthorized(
            GuiGraphics graphics,
            int centerX,
            int top,
            TwitchAuthManager auth,
            TwitchClientManager client
    ) {
        graphics.drawCenteredString(
                this.font,
                Component.translatable("cobblemonstreamermode.auth.authorized"),
                centerX,
                top + 42,
                0xFF77FF99
        );

        String channelName = auth.getAuthorizedChannelName();
        Component channelLine = channelName != null && !channelName.isBlank()
                ? Component.translatable("cobblemonstreamermode.auth.channel", compact(channelName, 28))
                : Component.translatable("cobblemonstreamermode.auth.channel_detecting");
        graphics.drawCenteredString(this.font, channelLine, centerX, top + 64, 0xFFCDA7FF);

        boolean streamerModeEnabled = StreamerModeConfig.INSTANCE.isStreamerModeEnabled();
        Component clientLine;
        int clientColor;
        if (!streamerModeEnabled) {
            clientLine = Component.translatable("cobblemonstreamermode.streamer_mode.disabled_status");
            clientColor = 0xFFAAA4AF;
        } else if (client.isRunning()) {
            clientLine = Component.translatable("cobblemonstreamermode.chat.connected");
            clientColor = 0xFF77FF99;
        } else if (client.isReconnecting()) {
            clientLine = Component.translatable("cobblemonstreamermode.chat.reconnecting");
            clientColor = 0xFFFFD866;
        } else if (client.isStarting()) {
            clientLine = Component.translatable("cobblemonstreamermode.chat.connecting");
            clientColor = 0xFFFFD866;
        } else {
            clientLine = Component.translatable("cobblemonstreamermode.chat.stopped");
            clientColor = 0xFFAAA4AF;
        }
        graphics.drawCenteredString(this.font, clientLine, centerX, top + 84, clientColor);
    }

    private void updateWidgets(
            TwitchAuthManager.AuthStatus status,
            TwitchAuthManager auth,
            TwitchClientManager client
    ) {
        requestAuthButton.visible = status == TwitchAuthManager.AuthStatus.NOT_AUTHORIZED;
        requestAuthButton.active = requestAuthButton.visible;

        String pendingUrl = auth.getPendingVerificationUri();
        boolean hasPendingUrl = status == TwitchAuthManager.AuthStatus.PENDING
                && pendingUrl != null
                && !pendingUrl.isBlank();
        authorizationUrlBox.setVisible(hasPendingUrl);
        if (hasPendingUrl && !pendingUrl.equals(authorizationUrlBox.getValue())) {
            authorizationUrlBox.setValue(pendingUrl);
            authorizationUrlBox.setCursorPosition(0);
            authorizationUrlBox.setHighlightPos(0);
        }

        streamerModeButton.visible = status == TwitchAuthManager.AuthStatus.VALID;
        streamerModeButton.active = streamerModeButton.visible;
        updateStreamerModeButton();
        updateDevButton();
    }

    private void toggleStreamerMode() {
        StreamerModeConfig config = StreamerModeConfig.INSTANCE;
        boolean enabled = !config.isStreamerModeEnabled();
        config.setStreamerModeEnabled(enabled);

        if (enabled) {
            TwitchClientManager.INSTANCE.start();
        } else {
            BattleManager.INSTANCE.cancelPoll();
            TwitchClientManager.INSTANCE.stop();
        }
        updateStreamerModeButton();
    }

    private void updateStreamerModeButton() {
        if (streamerModeButton == null) {
            return;
        }
        boolean enabled = StreamerModeConfig.INSTANCE.isStreamerModeEnabled();
        streamerModeButton.setMessage(Component.translatable(
                enabled
                        ? "cobblemonstreamermode.streamer_mode.on"
                        : "cobblemonstreamermode.streamer_mode.off"
        ));
    }

    private void updateDevButton() {
        if (repeatVotesButton == null) {
            return;
        }
        boolean enabled = StreamerModeConfig.INSTANCE.isAllowRepeatVotes();
        repeatVotesButton.setMessage(Component.translatable(
                enabled
                        ? "cobblemonstreamermode.dev.repeat_votes_on"
                        : "cobblemonstreamermode.dev.repeat_votes_off"
        ));
    }

    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        requestAuthButton.render(graphics, mouseX, mouseY, partialTick);
        authorizationUrlBox.render(graphics, mouseX, mouseY, partialTick);
        streamerModeButton.render(graphics, mouseX, mouseY, partialTick);
        repeatVotesButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - 24);
    }

    private int panelTop() {
        return Math.max(10, (this.height - PANEL_HEIGHT) / 2);
    }

    private static String compact(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
