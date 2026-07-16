package com.ddc.client.screen;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A radial menu: the actions a character has, laid out in a ring around the crosshair.
 *
 * <p>Typing {@code /ddc cast ddc:fireball <target>} mid-fight is not playing a game, it is filing a
 * form. The wheel puts the same actions a flick of the mouse away, and picks the target the player is
 * already looking at.
 *
 * <p>Pointing chooses; releasing the key runs it. That is the pattern every radial menu in every game
 * uses, and it means the whole interaction is one keypress rather than open-aim-click-close.
 *
 * <p>Every slice sends a command, so the server checks exactly what it would have checked had the
 * player typed it. The wheel is a faster way to type and nothing more.
 */
@Environment(EnvType.CLIENT)
public class WheelScreen extends Screen {

    /** How far the slices sit from the middle. */
    private static final int RADIUS = 76;

    private static final int CARD_WIDTH = 92;
    private static final int CARD_HEIGHT = 24;

    private static final int BACKDROP = 0x90101010;
    private static final int CARD = 0xB0181410;
    private static final int CARD_CHOSEN = 0xE04A1C24;
    private static final int BORDER = 0x60C9973F;
    private static final int BORDER_CHOSEN = 0xFFF2C879;
    private static final int TEXT = 0xFFE8DCC0;
    private static final int TEXT_DETAIL = 0xFF8A7F6B;
    private static final int TITLE = 0xFFC9973F;

    /** Nothing is chosen until the mouse leaves the middle: releasing on the spot cancels. */
    private static final double DEAD_ZONE = 18;

    private final String title;
    private final List<WheelOption> options;
    private int chosen = -1;

    public WheelScreen(String title, List<WheelOption> options) {
        super(Component.literal(title));
        this.title = title;
        this.options = List.copyOf(options);
    }

    /** The world keeps running: a wheel is a moment, not a pause. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Which slice the cursor is pointing at, by angle.
     *
     * <p>Package-visible and free of the screen, so the geometry can be tested: the pointing is the
     * part that has to be right, and the drawing is the part that needs eyes.
     *
     * @return the index, or empty when the cursor is in the middle
     */
    static Optional<Integer> pointingAt(double dx, double dy, int count) {
        if (count == 0 || Math.hypot(dx, dy) < DEAD_ZONE) {
            return Optional.empty();
        }
        // Screen y grows downward, so the angle is measured with it flipped: slice 0 sits at the top
        // and they run clockwise, which is how a player expects to read a wheel.
        double angle = Math.atan2(dx, -dy);
        double slice = Math.TAU / count;
        int index = (int) Math.floor(((angle + Math.TAU) % Math.TAU + slice / 2) / slice) % count;
        return Optional.of(index);
    }

    /** Where a slice's card sits, in screen coordinates. */
    private int[] cardAt(int index) {
        double angle = index * Math.TAU / options.size();
        int x = (int) (width / 2 + Math.sin(angle) * RADIUS) - CARD_WIDTH / 2;
        int y = (int) (height / 2 - Math.cos(angle) * RADIUS) - CARD_HEIGHT / 2;
        return new int[] {x, y};
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        chosen = pointingAt(mouseX - width / 2.0, mouseY - height / 2.0, options.size()).orElse(-1);
    }

    /** Minecraft 26 hands input in as events rather than as loose numbers. */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        mouseMoved(event.x(), event.y());
        run();
        return true;
    }

    /** Releasing the key that opened it runs the choice, which is what makes it one gesture. */
    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        run();
        return true;
    }

    private void run() {
        if (chosen >= 0 && chosen < options.size() && minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(options.get(chosen).command());
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Follows the mouse even when it has not moved since the wheel opened.
        chosen = pointingAt(mouseX - width / 2.0, mouseY - height / 2.0, options.size()).orElse(-1);

        graphics.nextStratum();
        graphics.blurBeforeThisStratum();
        graphics.fill(0, 0, width, height, BACKDROP);

        graphics.centeredText(font, Component.literal(title), width / 2, height / 2 - 5, TITLE);
        if (options.isEmpty()) {
            graphics.centeredText(font, Component.literal("Nothing to do yet — pick a class"),
                    width / 2, height / 2 + 8, TEXT_DETAIL);
            return;
        }

        for (int i = 0; i < options.size(); i++) {
            drawSlice(graphics, i);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSlice(GuiGraphicsExtractor graphics, int index) {
        WheelOption option = options.get(index);
        int[] at = cardAt(index);
        boolean picked = index == chosen;

        graphics.fill(at[0], at[1], at[0] + CARD_WIDTH, at[1] + CARD_HEIGHT, picked ? CARD_CHOSEN : CARD);
        graphics.outline(at[0], at[1], CARD_WIDTH, CARD_HEIGHT, picked ? BORDER_CHOSEN : BORDER);

        int textY = option.detail().isEmpty() ? at[1] + 8 : at[1] + 4;
        graphics.centeredText(font, Component.literal(option.label()),
                at[0] + CARD_WIDTH / 2, textY, TEXT);
        if (!option.detail().isEmpty()) {
            graphics.centeredText(font, Component.literal(option.detail()),
                    at[0] + CARD_WIDTH / 2, textY + 10, TEXT_DETAIL);
        }
    }
}
