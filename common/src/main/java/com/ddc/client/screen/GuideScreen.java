package com.ddc.client.screen;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * How to play, for a table that has never played this.
 *
 * <p>DDC asks a Minecraft player to learn a second game, and until now it told them nothing: the
 * rules were in a README on the internet, which is the last place someone standing in a world with a
 * wand in their hand is going to look. So the mod explains itself, in the game, in their language.
 *
 * <p>Chapters rather than one long wall. A player reads the first three and stops; a Game Master
 * reads the ones with their own name on. Nobody reads a manual, but everybody reads a page.
 *
 * <p>Every line is a translation key, because a guide that only speaks English teaches half a table.
 */
@Environment(EnvType.CLIENT)
public final class GuideScreen extends Screen {

    /**
     * A chapter: an icon, a title, and the paragraphs under it.
     *
     * @param recipes whether the crafts are drawn under the words, which one chapter needs and the
     *                rest do not
     */
    private record Chapter(Icon icon, String key, int paragraphs, boolean recipes) {

        Chapter(Icon icon, String key, int paragraphs) {
            this(icon, key, paragraphs, false);
        }

        Component title() {
            return Component.translatable("ddc.guide." + key + ".title");
        }

        List<Component> body() {
            return java.util.stream.IntStream.rangeClosed(1, paragraphs)
                    .mapToObj(line -> Component.translatable("ddc.guide." + key + "." + line))
                    .map(Component.class::cast)
                    .toList();
        }
    }

    /**
     * The chapters, in the order someone learns them.
     *
     * <p>What a character is, then what they do, then how the dice decide it -- and the Game Master's
     * three at the end, because most of a table never needs them and the one person who does knows
     * who they are.
     */
    private static final List<Chapter> CHAPTERS = List.of(
            new Chapter(Icon.GUIDE, "welcome", 3),
            new Chapter(Icon.CLASS, "character", 3),
            new Chapter(Icon.ROLL, "dice", 3),
            new Chapter(Icon.MANEUVER, "combat", 3),
            new Chapter(Icon.CAST, "magic", 3),
            // The crafts, right after the chapter that tells you to go and craft a spellbook. A player
            // told to make one had nowhere to find out how: REI and JEI show it and neither is
            // installed on most servers, so the mod that asked says what it costs.
            new Chapter(Icon.SHEET, "crafting", 1, true),
            new Chapter(Icon.REST, "rest", 2),
            new Chapter(Icon.CHECK, "checks", 2),
            new Chapter(Icon.SHEET, "levelling", 2),
            new Chapter(Icon.GM, "gm", 4),
            new Chapter(Icon.ENCOUNTER, "gm_wand", 3),
            new Chapter(Icon.WORLD, "gm_world", 3),
            new Chapter(Icon.NARRATE, "streaming", 3));

    private static final int PANEL_WIDTH = 300;

    /**
     * How tall a page is: prose, and the taller one the crafts need.
     *
     * <p>One height for every chapter put the last craft's line under the Done button, which a
     * screenshot caught. A page is as tall as what is on it.
     */
    private static final int PROSE_HEIGHT = 190;
    private static final int RECIPE_HEIGHT = 250;
    private static final int PADDING = 12;
    private static final int LINE_HEIGHT = 10;

    // Every colour carries its alpha: a colour written 0xFFFFFF is invisible, which is a lesson this
    // mod learned the hard way in its own HUD.
    private static final int BACKDROP = 0xC0101010;
    private static final int PANEL = 0xE0181410;
    private static final int BORDER = 0xFFC9973F;
    private static final int TITLE = 0xFFF2C879;
    private static final int TEXT = 0xFFE8DCC0;
    private static final int DIM = 0xFF8A7F6B;

    private int chapter;

    public GuideScreen() {
        super(Component.translatable("ddc.guide.title"));
    }

    /** How tall this chapter's page is. */
    private int panelHeight() {
        return CHAPTERS.get(chapter).recipes() ? RECIPE_HEIGHT : PROSE_HEIGHT;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int bottom = (height + panelHeight()) / 2 - PADDING - 20;

        addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                .bounds(left + PADDING, bottom, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                .bounds(left + PANEL_WIDTH - PADDING - 20, bottom, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 40, bottom, 80, 20).build());
    }

    /**
     * Turns a page, stopping at each end rather than wrapping.
     *
     * <p>The buttons are laid out again, because the page they sit on changes size: the crafts need a
     * taller one than a page of prose, and buttons left where the last page put them would be inside
     * this one's text.
     */
    private void turn(int by) {
        int wanted = Math.clamp(chapter + by, 0, CHAPTERS.size() - 1);
        if (wanted == chapter) {
            return;
        }
        chapter = wanted;
        rebuildWidgets();
    }

    /** Arrow keys turn pages, because a book with only mouse buttons is a website. */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        switch (event.key()) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> turn(-1);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> turn(1);
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No blur asked for: a Screen blurs what is behind it already, and asking twice in one frame
        // is an error the renderer throws on.
        graphics.fill(0, 0, width, height, BACKDROP);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight()) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight(), PANEL);
        graphics.renderOutline(left, top, PANEL_WIDTH, panelHeight(), BORDER);

        Chapter current = CHAPTERS.get(chapter);
        current.icon().draw(graphics, left + PADDING, top + PADDING);
        graphics.drawString(font, current.title(), left + PADDING + Icon.SIZE + 8, top + PADDING + 4, TITLE);
        graphics.drawString(font, Component.translatable("ddc.guide.page", chapter + 1, CHAPTERS.size()),
                left + PANEL_WIDTH - PADDING - font.width(
                        Component.translatable("ddc.guide.page", chapter + 1, CHAPTERS.size())),
                top + PADDING + 4, DIM);

        int y = top + PADDING + Icon.SIZE + 10;
        for (Component paragraph : current.body()) {
            // Wrapped to the panel rather than trusted to fit: a translation is a different length
            // from the English it was written beside, and Russian is longer than most.
            for (net.minecraft.util.FormattedCharSequence line
                    : font.split(paragraph, PANEL_WIDTH - PADDING * 2)) {
                graphics.drawString(font, line, left + PADDING, y, TEXT);
                y += LINE_HEIGHT;
            }
            y += 4;
        }
        if (current.recipes()) {
            RecipePage.render(graphics, font, left + PADDING, y, PANEL_WIDTH - PADDING * 2);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
