package com.ddc.client.screen;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
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

    /**
     * How far the slices sit from the middle, at the least.
     *
     * <p>At the least, because the wheel grows. A fixed radius with seven translated options piled
     * the cards on top of each other -- the ring is only so long, and Russian is wider than English.
     */
    private static final int MIN_RADIUS = 76;

    /** The narrowest a card may be, so a one-word option still reads as a button. */
    private static final int MIN_CARD_WIDTH = 92;

    /** Room around a label inside its card, and between two cards on the ring. */
    private static final int CARD_PADDING = 8;
    private static final int CARD_GAP = 6;

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

    private final Component heading;
    private final List<WheelOption> options;
    private int chosen = -1;

    public WheelScreen(Component heading, List<WheelOption> options) {
        super(heading);
        this.heading = heading;
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
        int x = (int) (width / 2 + Math.sin(angle) * radius()) - cardWidth(index) / 2;
        int y = (int) (height / 2 - Math.cos(angle) * radius()) - CARD_HEIGHT / 2;
        return new int[] {x, y};
    }

    /**
     * How wide a card has to be to hold what is written on it.
     *
     * <p>The card used to be a fixed 92 pixels and the text was drawn centred in it whatever its
     * length, so a longer label simply ran out over the edges. What a button says decides how big
     * the button is, not the other way round.
     */
    private int cardWidth(int index) {
        WheelOption option = options.get(index);
        int text = Math.max(font.width(option.label()), font.width(option.detail()));
        int icon = option.icon().isPresent() ? Icon.SIZE + CARD_PADDING : 0;
        return Math.max(MIN_CARD_WIDTH, text + icon + CARD_PADDING * 2);
    }

    /**
     * How far out the ring sits: far enough that the widest card fits in its own slice.
     *
     * <p>Cards are laid around a circle, so the room each one has is the ring's circumference divided
     * between them. Seven cards needing a hundred pixels each need a ring seven hundred long, and a
     * ring that short is a pile. So the ring grows to fit rather than the cards shrinking to lie.
     */
    private int radius() {
        int widest = MIN_CARD_WIDTH;
        for (int index = 0; index < options.size(); index++) {
            widest = Math.max(widest, cardWidth(index));
        }
        return ringRadius(options.size(), widest, Math.min(width, height) / 2 - CARD_HEIGHT);
    }

    /**
     * How far out a ring of this many cards of this width has to sit.
     *
     * <p>Package-visible and free of the font and the screen, because this is the part that has to be
     * right: seven cards on a ring built for four is the pile a player photographed.
     *
     * @param count  how many cards go on the ring
     * @param widest how wide the widest of them is
     * @param limit  how far out the window allows, which wins over crowding
     */
    static int ringRadius(int count, int widest, int limit) {
        // Each card gets a share of the circumference, so the circumference must be big enough for all
        // of them: TAU * r >= count * (card + gap). The height matters too, or the cards at the sides
        // of a short list would touch top to bottom.
        double needed = Math.max(widest + CARD_GAP, CARD_HEIGHT + CARD_GAP) * count / Math.TAU;
        // Never past the edge of the window: a card that cannot be seen cannot be pointed at, and a
        // crowded wheel beats one that ran off the screen.
        return (int) Math.max(MIN_RADIUS, Math.min(needed, Math.max(MIN_RADIUS, limit)));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        chosen = pointingAt(mouseX - width / 2.0, mouseY - height / 2.0, options.size()).orElse(-1);
    }

    /** Minecraft 26 hands input in as events rather than as loose numbers. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseMoved(mouseX, mouseY);
        run();
        return true;
    }

    /** Releasing the key that opened it runs the choice, which is what makes it one gesture. */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        run();
        return true;
    }

    /**
     * Runs the choice: another wheel, a screen, a command, or nothing.
     *
     * <p>A wheel that opens a wheel replaces this one rather than stacking, so a player who picks a
     * class ends up back in the world rather than three menus deep.
     */
    private void run() {
        if (chosen < 0 || chosen >= options.size() || minecraft == null || minecraft.player == null) {
            onClose();
            return;
        }
        WheelOption option = options.get(chosen);
        if (option.isInert()) {
            onClose();
        } else if (PlayerWheel.Wheels.isMenu(option.command())) {
            minecraft.setScreen(submenu(option.command()));
        } else {
            minecraft.player.connection.sendCommand(option.command());
            onClose();
        }
    }

    /** Opens whatever a menu slice names. */
    private Screen submenu(String command) {
        return switch (command) {
            case PlayerWheel.Wheels.CLASS_MENU -> new WheelScreen(
                    Component.translatable("ddc.wheel.class"), PlayerWheel.classes());
            case PlayerWheel.Wheels.RACE_MENU -> new WheelScreen(
                    Component.translatable("ddc.wheel.race"), PlayerWheel.races());
            case PlayerWheel.Wheels.SPELL_MENU -> new WheelScreen(
                    Component.translatable("ddc.wheel.cast"),
                    com.ddc.client.DDCClient.sheet().map(PlayerWheel::spells).orElse(List.of()));
            case PlayerWheel.Wheels.SHEET_SCREEN -> com.ddc.client.DDCClient.sheetScreen();
            case PlayerWheel.Wheels.GM_PANEL -> new GameMasterScreen();
            case PlayerWheel.Wheels.GUIDE_SCREEN -> new GuideScreen();
            case PlayerWheel.Wheels.CHANNEL_MENU -> new WheelScreen(
                    Component.translatable("ddc.wheel.channel"), PlayerWheel.channel());
            case PlayerWheel.Wheels.MANEUVER_MENU -> new WheelScreen(
                    Component.translatable("ddc.wheel.maneuver"), PlayerWheel.maneuvers());
            default -> this;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Follows the mouse even when it has not moved since the wheel opened.
        chosen = pointingAt(mouseX - width / 2.0, mouseY - height / 2.0, options.size()).orElse(-1);

        // No blur is asked for here: a Screen already blurs what is behind it, and asking twice in
        // one frame is an error the renderer throws on. That crash is how this was found.
        graphics.fill(0, 0, width, height, BACKDROP);

        // The heading sits in the hole in the middle, which is now big enough to hold it: the ring
        // is pushed out by its cards, and the middle is what is left.
        graphics.drawCenteredString(font, heading, width / 2, height / 2 - 5, TITLE);
        if (options.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("ddc.wheel.empty"),
                    width / 2, height / 2 + 8, TEXT_DETAIL);
            return;
        }

        for (int i = 0; i < options.size(); i++) {
            drawSlice(graphics, i);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSlice(GuiGraphics graphics, int index) {
        WheelOption option = options.get(index);
        int[] at = cardAt(index);
        int cardWidth = cardWidth(index);
        boolean picked = index == chosen;

        graphics.fill(at[0], at[1], at[0] + cardWidth, at[1] + CARD_HEIGHT, picked ? CARD_CHOSEN : CARD);
        graphics.renderOutline(at[0], at[1], cardWidth, CARD_HEIGHT, picked ? BORDER_CHOSEN : BORDER);

        // The icon takes the left of the card and the words take what is left, so a row of cards
        // lines its pictures up down the ring rather than each one centring itself differently.
        int textLeft = at[0] + CARD_PADDING;
        if (option.icon().isPresent()) {
            option.icon().get().draw(graphics, at[0] + CARD_PADDING, at[1] + (CARD_HEIGHT - Icon.SIZE) / 2);
            textLeft += Icon.SIZE + CARD_PADDING;
        }

        boolean hasDetail = !option.detail().getString().isEmpty();
        int textY = hasDetail ? at[1] + 4 : at[1] + 8;
        graphics.drawString(font, option.label(), textLeft, textY, TEXT);
        if (hasDetail) {
            graphics.drawString(font, option.detail(), textLeft, textY + 10, TEXT_DETAIL);
        }
    }
}
