package com.ludwici.cobblemonstreamermode.client.gui;

import com.ludwici.cobblemonstreamermode.client.twitch.TwitchAuthManager;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchClientManager;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchCredentials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TwitchScreen extends Screen {

    private Button requestAuthButton;
    private Button startClientButton;
    private Button copyLinkButton;

    public TwitchScreen() {
        super(Component.literal("Twitch"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 60;

        System.out.println(-1);
        requestAuthButton = Button.builder(Component.literal("Запросить код авторизации"), btn -> {
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            TwitchAuthManager.INSTANCE.beginDeviceFlow();
                        }
                        client.setScreen(TwitchScreen.this);
                    },
                    Component.literal("Подтверждение"),
                    Component.literal("Сейчас будет сгенерирован код авторизации.\n" +
                            "Никому не показывай и не пересылай этот код — он даёт доступ к твоему аккаунту.\n" +
                            "Продолжить?")
            ));
        }).bounds(centerX - 100, y, 200, 20).build();
        addRenderableWidget(requestAuthButton);
        System.out.println(0);
        y += 30;

        copyLinkButton = Button.builder(Component.literal("Скопировать ссылку"), btn -> {
            String uri = TwitchAuthManager.INSTANCE.getPendingVerificationUri();
            if (uri != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(uri);
            }
        }).bounds(this.width / 2 - 100, 150, 200, 20).build();
        addRenderableWidget(copyLinkButton);

        startClientButton = Button.builder(Component.literal("Запустить Twitch клиент"), btn -> {
            System.out.println(1);
            TwitchCredentials creds = TwitchCredentials.load();
            if (creds != null) {
                System.out.println(2);
                TwitchClientManager.INSTANCE.start(creds);
            }
        }).bounds(centerX - 100, y, 200, 20).build();
        addRenderableWidget(startClientButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);


        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);

        var auth = TwitchAuthManager.INSTANCE;
        String authLine = switch (auth.getAuthStatus()) {
            case NOT_AUTHORIZED -> "§cНе авторизован";
            case PENDING -> "§eОжидание подтверждения...";
            case VALID -> "§aАвторизован";
        };
        graphics.drawCenteredString(this.font, Component.literal(authLine), centerX, 40, 0xFFFFFF);

        if (auth.getAuthStatus() == TwitchAuthManager.AuthStatus.PENDING) {
            String code = auth.getPendingUserCode();
            String uri = auth.getPendingVerificationUri();
            if (code != null) {
                graphics.drawCenteredString(this.font,
                        Component.literal("§bОткрой: §f" + uri), centerX, 115, 0xFFFFFF);
                graphics.drawCenteredString(this.font,
                        Component.literal("§bКод: §f" + code), centerX, 130, 0xFFFFFF);
            }
        }

        var client = TwitchClientManager.INSTANCE;
        String clientLine = client.isRunning() ? "§aTwitch клиент запущен" : "§7Twitch клиент не запущен";
        graphics.drawCenteredString(this.font, Component.literal(clientLine), centerX, 175, 0xFFFFFF);

        if (client.getLastError() != null) {
            graphics.drawCenteredString(this.font,
                    Component.literal("§cОшибка: " + client.getLastError()), centerX, 160, 0xFFFFFF);
        }

        requestAuthButton.active = auth.getAuthStatus() != TwitchAuthManager.AuthStatus.PENDING;
        startClientButton.active = auth.getAuthStatus() == TwitchAuthManager.AuthStatus.VALID
                && !client.isRunning();

        boolean hasPendingLink = auth.getAuthStatus() == TwitchAuthManager.AuthStatus.PENDING
                && auth.getPendingVerificationUri() != null;

        copyLinkButton.visible = hasPendingLink;
        copyLinkButton.active = hasPendingLink;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}