package com.ludwici.cobblemonstreamermode.client.gui;

import com.ludwici.cobblemonstreamermode.client.BattleManager;
import com.ludwici.cobblemonstreamermode.client.PollData;
import com.ludwici.cobblemonstreamermode.client.StreamerModeConfig;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchAuthManager;
import com.ludwici.cobblemonstreamermode.client.twitch.TwitchClientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CobblemonTwitchScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_MAX_HEIGHT = 330;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 40;

    private static final int FRAME_DARK = 0xFF252525;
    private static final int FRAME_LIGHT = 0xFF929292;
    private static final int PANEL_FILL = 0xFF303030;
    private static final int PANEL_INNER = 0xFF3A3A3A;
    private static final int SECTION_FILL = 0xFF444444;
    private static final int SECTION_TOP = 0xFF555555;
    private static final int ACCENT_PURPLE = 0xFF9146FF;
    private static final int ACCENT_PURPLE_DARK = 0xFF5B28A9;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFBDBDBD;
    private static final int STATUS_OK = 0xFF65D98A;
    private static final int STATUS_WARN = 0xFFFFC857;
    private static final int STATUS_ERROR = 0xFFFF6B66;

    private SettingsList settingsList;

    public CobblemonTwitchScreen() {
        super(Component.translatable("cobblemonstreamermode.screen.title"));
    }

    @Override
    protected void init() {
        int listX = panelLeft() + 7;
        int listY = panelTop() + 36;
        int listWidth = panelWidth() - 14;
        int listHeight = panelHeight() - 44;

        settingsList = new SettingsList(this.minecraft, listX, listY, listWidth, listHeight);
        settingsList.add(new SectionEntry(Component.translatable("cobblemonstreamermode.section.account")));
        settingsList.add(new AccountStatusEntry());
        settingsList.add(new AccountActionEntry());
        settingsList.add(new SectionEntry(Component.translatable("cobblemonstreamermode.section.mode")));
        settingsList.add(new StreamerModeEntry());
        settingsList.add(new SectionEntry(Component.translatable("cobblemonstreamermode.section.voting")));
        settingsList.add(new VotingEntry(PollData.Actions.MOVE, Component.translatable("cobblemonstreamermode.settings.battle.move")));
        settingsList.add(new VotingEntry(PollData.Actions.TARGET, Component.translatable("cobblemonstreamermode.settings.battle.target")));
        settingsList.add(new VotingEntry(PollData.Actions.SWITCH_POKEMON, Component.translatable("cobblemonstreamermode.settings.battle.switch")));
        settingsList.add(new SectionEntry(Component.translatable("cobblemonstreamermode.section.other")));
        settingsList.add(new RepeatVotesEntry());
        addRenderableWidget(settingsList);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.flush();

        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();

        drawBattlePanel(graphics, left, top, right, bottom);
        settingsList.render(graphics, mouseX, mouseY, partialTick);
        drawHeader(graphics, left, top, right);
        graphics.drawString(this.font, Component.translatable("cobblemonstreamermode.screen.title"), left + 25, top + 11, TEXT_PRIMARY, false);
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
    }

    private static boolean isValidDurationInput(String value) {
        if (value.isBlank()) {
            return true;
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            return false;
        }
        try {
            int seconds = Integer.parseInt(value);
            return seconds >= StreamerModeConfig.MIN_VOTING_DURATION_SECONDS && seconds <= StreamerModeConfig.MAX_VOTING_DURATION_SECONDS;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void updateVotingDuration(PollData.Actions action, String value) {
        if (value.isBlank()) {
            return;
        }
        try {
            StreamerModeConfig.INSTANCE.setVotingDurationSeconds(action, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private void drawBattlePanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left + 4, top + 5, right + 4, bottom + 5, 0x99000000);
        graphics.fill(left, top, right, bottom, FRAME_DARK);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, FRAME_LIGHT);
        graphics.fill(left + 4, top + 4, right - 4, bottom - 4, PANEL_FILL);
        graphics.fill(left + 6, top + 31, right - 6, bottom - 7, PANEL_INNER);
    }

    private void drawHeader(GuiGraphics graphics, int left, int top, int right) {
        graphics.fill(left + 4, top + 4, right - 4, top + 31, 0xFF3A3A3A);
        graphics.fill(left + 4, top + 4, left + 12, top + 31, ACCENT_PURPLE);
        graphics.fill(left + 12, top + 27, right - 4, top + 31, ACCENT_PURPLE_DARK);

        int slashX = right - 47;
        for (int i = 0; i < 3; i++) {
            graphics.fill(slashX + i * 10, top + 8, slashX + 6 + i * 10, top + 12, ACCENT_PURPLE);
            graphics.fill(slashX + 3 + i * 10, top + 12, slashX + 9 + i * 10, top + 16, ACCENT_PURPLE);
        }
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, this.width - 20);
    }

    private int panelHeight() {
        return Math.min(PANEL_MAX_HEIGHT, this.height - 24);
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(12, (this.height - panelHeight()) / 2);
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

    private final class SettingsList extends ContainerObjectSelectionList<SettingsEntry> {
        private SettingsList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, width, height, y, ROW_HEIGHT);
            setX(x);
            centerListVertically = false;
        }

        private void add(SettingsEntry entry) {
            addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 20;
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + getWidth() - 6;
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics graphics) {
        }
    }

    private abstract class SettingsEntry extends ContainerObjectSelectionList.Entry<SettingsEntry> {
        private final List<AbstractWidget> widgets;

        private SettingsEntry(AbstractWidget... widgets) {
            this.widgets = List.of(widgets);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }

        protected void drawRowBackground(GuiGraphics graphics, int left, int top, int width, int height) {
            graphics.fill(left, top + 2, left + width, top + height - 2, FRAME_DARK);
            graphics.fill(left + 1, top + 3, left + width - 1, top + height - 3, SECTION_FILL);
        }
    }

    private final class SectionEntry extends SettingsEntry {
        private final Component title;

        private SectionEntry(Component title) {
            this.title = title;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.fill(left, top + 7, left + width, top + height - 5, FRAME_DARK);
            graphics.fill(left + 1, top + 8, left + width - 1, top + height - 6, SECTION_TOP);
            graphics.fill(left + 1, top + 8, left + 5, top + height - 6, ACCENT_PURPLE_DARK);
            graphics.drawString(font, title, left + 11, top + 14, TEXT_PRIMARY, false);
        }
    }

    private final class AccountStatusEntry extends SettingsEntry {
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawRowBackground(graphics, left, top, width, height);

            TwitchAuthManager auth = TwitchAuthManager.INSTANCE;
            TwitchClientManager client = TwitchClientManager.INSTANCE;
            TwitchAuthManager.AuthStatus status = auth.getAuthStatus();
            Component firstLine;
            Component secondLine = Component.empty();
            int color;

            switch (status) {
                case NOT_AUTHORIZED -> {
                    firstLine = Component.translatable("cobblemonstreamermode.auth.not_authorized");
                    secondLine = Component.translatable("cobblemonstreamermode.auth.not_authorized_hint");
                    color = STATUS_ERROR;
                }
                case PENDING -> {
                    firstLine = Component.translatable("cobblemonstreamermode.auth.pending");
                    secondLine = auth.getPendingVerificationUri() == null || auth.getPendingVerificationUri().isBlank()
                            ? Component.translatable("cobblemonstreamermode.auth.requesting_hint")
                            : Component.translatable("cobblemonstreamermode.auth.copy_hint");
                    color = STATUS_WARN;
                }
                case VALID -> {
                    firstLine = Component.translatable("cobblemonstreamermode.auth.authorized");
                    String channelName = auth.getAuthorizedChannelName();
                    secondLine = channelName != null && !channelName.isBlank()
                            ? Component.translatable("cobblemonstreamermode.auth.channel", compact(channelName, 28))
                            : Component.translatable("cobblemonstreamermode.auth.channel_detecting");
                    color = STATUS_OK;
                }
                default -> throw new IllegalStateException();
            }

            graphics.drawString(font, firstLine, left + 10, top + 8, color, false);
            String error = auth.getLastError() != null ? auth.getLastError() : client.getLastError();
            if (error != null && !error.isBlank()) {
                secondLine = Component.translatable("cobblemonstreamermode.error", compact(error, 42));
                graphics.drawString(font, secondLine, left + 10, top + 23, STATUS_ERROR, false);
            } else {
                graphics.drawString(font, secondLine, left + 10, top + 23, TEXT_SECONDARY, false);
            }
        }
    }

    private final class AccountActionEntry extends SettingsEntry {
        private final Button requestButton;
        private final EditBox authorizationUrlBox;

        private AccountActionEntry() {
            this(Button.builder(Component.translatable("cobblemonstreamermode.auth.request"),
                            btn -> TwitchAuthManager.INSTANCE.beginDeviceFlow())
                            .bounds(0, 0, 230, BUTTON_HEIGHT)
                            .build(),
                    new EditBox(font, 0, 0, 300, BUTTON_HEIGHT,
                            Component.translatable("cobblemonstreamermode.auth.url")));
        }

        private AccountActionEntry(Button requestButton, EditBox authorizationUrlBox) {
            super(requestButton, authorizationUrlBox);
            this.requestButton = requestButton;
            this.authorizationUrlBox = authorizationUrlBox;
            authorizationUrlBox.setMaxLength(512);
            authorizationUrlBox.setEditable(false);
            authorizationUrlBox.setTextColorUneditable(TEXT_PRIMARY);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawRowBackground(graphics, left, top, width, height);

            TwitchAuthManager auth = TwitchAuthManager.INSTANCE;
            TwitchAuthManager.AuthStatus status = auth.getAuthStatus();
            String pendingUrl = auth.getPendingVerificationUri();
            boolean hasPendingUrl = status == TwitchAuthManager.AuthStatus.PENDING && pendingUrl != null && !pendingUrl.isBlank();

            requestButton.visible = status == TwitchAuthManager.AuthStatus.NOT_AUTHORIZED;
            authorizationUrlBox.visible = hasPendingUrl;

            if (requestButton.visible) {
                requestButton.setX(left + (width - requestButton.getWidth()) / 2);
                requestButton.setY(top + 10);
                requestButton.render(graphics, mouseX, mouseY, partialTick);
                return;
            }

            if (hasPendingUrl) {
                if (!pendingUrl.equals(authorizationUrlBox.getValue())) {
                    authorizationUrlBox.setValue(pendingUrl);
                    authorizationUrlBox.setCursorPosition(0);
                    authorizationUrlBox.setHighlightPos(0);
                }
                authorizationUrlBox.setWidth(Math.min(300, width - 20));
                authorizationUrlBox.setX(left + (width - authorizationUrlBox.getWidth()) / 2);
                authorizationUrlBox.setY(top + 10);
                authorizationUrlBox.render(graphics, mouseX, mouseY, partialTick);
                return;
            }

            if (status == TwitchAuthManager.AuthStatus.PENDING) {
                graphics.drawCenteredString(font, Component.translatable("cobblemonstreamermode.auth.requesting"), left + width / 2, top + 9, STATUS_WARN);
                graphics.drawCenteredString(font, Component.translatable("cobblemonstreamermode.auth.requesting_hint"), left + width / 2, top + 23, TEXT_SECONDARY);
            } else if (status == TwitchAuthManager.AuthStatus.VALID) {
                graphics.drawCenteredString(font, Component.translatable("cobblemonstreamermode.auth.authorized_hint"), left + width / 2, top + 16, TEXT_SECONDARY);
            }
        }
    }

    private final class StreamerModeEntry extends SettingsEntry {
        private final Button button;

        private StreamerModeEntry() {
            this(Button.builder(Component.empty(), btn -> {
                        CobblemonTwitchScreen.this.toggleStreamerMode();
                        updateStreamerModeMessage((Button) btn);
                    })
                    .tooltip(Tooltip.create(Component.translatable("cobblemonstreamermode.streamer_mode.tooltip")))
                    .bounds(0, 0, 230, BUTTON_HEIGHT)
                    .build());
        }

        private StreamerModeEntry(Button button) {
            super(button);
            this.button = button;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawRowBackground(graphics, left, top, width, height);
            boolean authorized = TwitchAuthManager.INSTANCE.getAuthStatus() == TwitchAuthManager.AuthStatus.VALID;
            button.visible = authorized;
            button.active = authorized;
            if (authorized) {
                updateStreamerModeMessage(button);
                button.setX(left + (width - button.getWidth()) / 2);
                button.setY(top + 8);
                button.render(graphics, mouseX, mouseY, partialTick);
            } else {
                graphics.drawCenteredString(font, Component.translatable("cobblemonstreamermode.streamer_mode.requires_auth"), left + width / 2, top + 16, TEXT_SECONDARY);
            }
        }

    }

    private final class VotingEntry extends SettingsEntry {
        private final PollData.Actions action;
        private final Component label;
        private final Button toggleButton;
        private final EditBox durationBox;

        private VotingEntry(PollData.Actions action, Component label) {
            this(action, label,
                    Button.builder(Component.empty(), btn -> {
                                StreamerModeConfig config = StreamerModeConfig.INSTANCE;
                                config.setVotingEnabled(action, !config.isVotingEnabled(action));
                                updateToggleMessage((Button) btn, action);
                            })
                            .bounds(0, 0, 68, BUTTON_HEIGHT)
                            .build(),
                    createDurationBox(action));
        }

        private VotingEntry(PollData.Actions action, Component label, Button toggleButton, EditBox durationBox) {
            super(toggleButton, durationBox);
            this.action = action;
            this.label = label;
            this.toggleButton = toggleButton;
            this.durationBox = durationBox;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawRowBackground(graphics, left, top, width, height);
            graphics.drawString(font, label, left + 10, top + 16, TEXT_PRIMARY, false);

            if (!durationBox.isFocused() && durationBox.getValue().isBlank()) {
                durationBox.setValue(String.valueOf(StreamerModeConfig.INSTANCE.getVotingDurationSeconds(action)));
            }
            updateToggleMessage(toggleButton, action);

            durationBox.setX(left + width - 62);
            durationBox.setY(top + 10);
            toggleButton.setX(durationBox.getX() - 78);
            toggleButton.setY(top + 10);

            toggleButton.render(graphics, mouseX, mouseY, partialTick);
            durationBox.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(font, Component.translatable("cobblemonstreamermode.settings.seconds_short"), durationBox.getRight() + 4, top + 16, TEXT_SECONDARY, false);
        }
    }

    private final class RepeatVotesEntry extends SettingsEntry {
        private final Button button;

        private RepeatVotesEntry() {
            this(Button.builder(Component.empty(), btn -> {
                        StreamerModeConfig config = StreamerModeConfig.INSTANCE;
                        config.setAllowRepeatVotes(!config.isAllowRepeatVotes());
                        updateRepeatVotesMessage((Button) btn);
                    })
                    .tooltip(Tooltip.create(Component.translatable("cobblemonstreamermode.settings.repeat_votes.tooltip")))
                    .bounds(0, 0, 230, BUTTON_HEIGHT)
                    .build());
        }

        private RepeatVotesEntry(Button button) {
            super(button);
            this.button = button;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            drawRowBackground(graphics, left, top, width, height);
            updateRepeatVotesMessage(button);
            button.setX(left + (width - button.getWidth()) / 2);
            button.setY(top + 8);
            button.render(graphics, mouseX, mouseY, partialTick);
        }

    }

    private EditBox createDurationBox(PollData.Actions action) {
        EditBox box = new EditBox(font, 0, 0, 52, BUTTON_HEIGHT, Component.translatable("cobblemonstreamermode.settings.voting_duration"));
        box.setMaxLength(4);
        box.setFilter(CobblemonTwitchScreen::isValidDurationInput);
        box.setValue(String.valueOf(StreamerModeConfig.INSTANCE.getVotingDurationSeconds(action)));
        box.setResponder(value -> updateVotingDuration(action, value));
        box.setTooltip(Tooltip.create(Component.translatable(
                "cobblemonstreamermode.settings.voting_duration.tooltip",
                StreamerModeConfig.MIN_VOTING_DURATION_SECONDS,
                StreamerModeConfig.MAX_VOTING_DURATION_SECONDS)));
        return box;
    }


    private static void updateStreamerModeMessage(Button button) {
        button.setMessage(Component.translatable(
                StreamerModeConfig.INSTANCE.isStreamerModeEnabled()
                        ? "cobblemonstreamermode.streamer_mode.on"
                        : "cobblemonstreamermode.streamer_mode.off"));
    }

    private static void updateRepeatVotesMessage(Button button) {
        button.setMessage(Component.translatable(
                StreamerModeConfig.INSTANCE.isAllowRepeatVotes()
                        ? "cobblemonstreamermode.settings.repeat_votes_on"
                        : "cobblemonstreamermode.settings.repeat_votes_off"));
    }

    private static void updateToggleMessage(Button button, PollData.Actions action) {
        button.setMessage(Component.translatable(
                StreamerModeConfig.INSTANCE.isVotingEnabled(action)
                        ? "cobblemonstreamermode.settings.enabled"
                        : "cobblemonstreamermode.settings.disabled"));
    }
}
