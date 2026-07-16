package com.ddc.client.screen;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * PRD 3.2's control panel, on the {@code G} key.
 *
 * <p>Narration and world control, one click each, so a GM running a scene is not typing commands
 * while the table waits.
 *
 * <p>Every button sends the command it names. That is on purpose: the commands already carry the
 * permission checks ADR-0003 insists on, and a panel with its own packet would be a second way in
 * that has to be secured all over again. A player who somehow opened this screen would find that
 * every button fails, exactly as typing the command would.
 *
 * <p>The radial menu and the encounter preview grid the PRD draws are not here: the wand does
 * encounters, and a placement preview needs world rendering that Minecraft 26 does not hand out.
 */
@Environment(EnvType.CLIENT)
public class GameMasterScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private static final int BACKDROP = 0x90101010;
    private static final int BORDER = 0xFFC9973F;
    private static final int BRASS = 0xC9973F;

    /** The world changes, in the order a scene tends to want them. */
    private static final List<String> WORLD_CHANGES =
            List.of("night", "day", "storm", "clear", "freeze", "release", "pause-time", "resume-time");

    private EditBox narration;

    public GameMasterScreen() {
        super(Component.literal("Game Master"));
    }

    /** The table does not stop while the GM works. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = height / 2 - 70;

        narration = new EditBox(font, left, top + 14, PANEL_WIDTH, BUTTON_HEIGHT,
                Component.literal("Narration"));
        narration.setMaxLength(com.ddc.network.NarrationPayload.MAX_LENGTH);
        narration.setHint(Component.literal("The walls begin to tremble..."));
        addRenderableWidget(narration);

        addRenderableWidget(Button.builder(Component.literal("Narrate"), button -> narrate())
                .bounds(left, top + 14 + BUTTON_HEIGHT + GAP, PANEL_WIDTH, BUTTON_HEIGHT)
                .build());

        int y = top + 14 + (BUTTON_HEIGHT + GAP) * 2 + 8;
        int buttonWidth = (PANEL_WIDTH - GAP) / 2;
        for (int i = 0; i < WORLD_CHANGES.size(); i++) {
            String change = WORLD_CHANGES.get(i);
            addRenderableWidget(Button.builder(Component.literal(change),
                            button -> send("ddc world " + change))
                    .bounds(left + (i % 2) * (buttonWidth + GAP), y + (i / 2) * (BUTTON_HEIGHT + GAP),
                            buttonWidth, BUTTON_HEIGHT)
                    .build());
        }
    }

    private void narrate() {
        String text = narration.getValue().trim();
        if (!text.isEmpty()) {
            send("ddc narrate " + text);
            narration.setValue("");
        }
    }

    /** Sends a command as the player: the server checks it exactly as if it had been typed. */
    private void send(String command) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - PANEL_WIDTH) / 2;
        int top = height / 2 - 84;
        int panelHeight = 14 + (BUTTON_HEIGHT + GAP) * 2 + 8
                + (WORLD_CHANGES.size() / 2) * (BUTTON_HEIGHT + GAP) + 12;

        graphics.nextStratum();
        graphics.blurBeforeThisStratum();

        graphics.fill(left - 8, top, left + PANEL_WIDTH + 8, top + panelHeight, BACKDROP);
        graphics.outline(left - 8, top, PANEL_WIDTH + 16, panelHeight, BORDER);
        graphics.text(font, Component.literal("GAME MASTER"), left, top + 6, BRASS);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
