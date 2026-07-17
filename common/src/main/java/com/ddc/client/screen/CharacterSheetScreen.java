package com.ddc.client.screen;

import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * PRD 3.1's character sheet, on the {@code C} key.
 *
 * <p>Reads the sheet the server sent and the health the player already has, and shows both. It is a
 * page, not a form: DDC has no way for a client to change a sheet, and a screen with a button that
 * asked the server nicely would be a lie about where the rules live. Choices are made with commands,
 * which the server can check.
 *
 * <p>PRD 3.1's glassmorphic panel: a Screen blurs the world behind itself, and the card is drawn on
 * top of that. The blur is the renderer's, not this class's -- asking for one here as well crashed
 * the game with "can only blur once per frame".
 */
@Environment(EnvType.CLIENT)
public class CharacterSheetScreen extends Screen {

    private static final int CARD_WIDTH = 240;
    private static final int CARD_HEIGHT = 150;

    private static final int BACKDROP = 0x90101010;
    private static final int BORDER = 0xFFC9973F;
    // Alpha is not optional: a colour written 0xFFFFFF is fully transparent.
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int BRASS = 0xFFC9973F;

    /** A line with a 16-pixel picture on it needs more room than a line of text. */
    private static final int ABILITY_LINE = 18;

    private static final int LINE = 12;

    private final CharacterSheet sheet;
    private final String className;

    public CharacterSheetScreen(CharacterSheet sheet, String className) {
        super(Component.translatable("ddc.screen.sheet"));
        this.sheet = sheet;
        this.className = className;
    }

    /** A sheet is a page to read, so the game keeps running behind it. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Minecraft 26's screens extract what to draw rather than drawing in place, so this is
     * {@code extractRenderState} rather than the {@code render} older versions had.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int left = (width - CARD_WIDTH) / 2;
        int top = (height - CARD_HEIGHT) / 2;

        // A Screen already blurs what is behind it, so the card only has to be drawn on top. Asking
        // for a second blur in one frame is an error the renderer throws on.
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, BACKDROP);
        graphics.outline(left, top, CARD_WIDTH, CARD_HEIGHT, BORDER);

        LocalPlayer player = minecraft == null ? null : minecraft.player;
        int y = top + 8;

        graphics.text(font, Component.translatable("ddc.screen.level",
                className.toUpperCase(java.util.Locale.ROOT), sheet.level()), left + 10, y, BRASS);
        y += LINE + 2;

        if (player != null) {
            graphics.text(font, Component.translatable("ddc.screen.hit_points",
                            Math.round(player.getHealth()), Math.round(player.getMaxHealth())),
                    left + 10, y, TEXT);
            y += LINE;
        }
        graphics.text(font, Component.translatable("ddc.screen.proficiency", sheet.proficiencyBonus()),
                left + 10, y, TEXT);
        y += LINE + 6;

        y = renderAbilities(graphics, left, y);

        graphics.text(font, sheet.preparedSpells().isEmpty()
                        ? Component.translatable("ddc.screen.no_prepared")
                        : Component.translatable("ddc.screen.prepared", sheet.preparedSpells().size()),
                left + 10, y, MUTED);
        y += LINE;
        graphics.text(font, Component.translatable("ddc.screen.hint"), left + 10, y, MUTED);
    }

    /**
     * The six abilities as picture, score and modifier, two columns, the way a paper sheet reads.
     *
     * <p>The same icons the HUD uses, for the same reason: a sword and a feather say what strength
     * and dexterity are without asking a Minecraft player to learn three-letter codes first. The
     * sheet keeps the score as well as the modifier -- this is the screen you open to read, and the
     * score is what the modifier came from.
     */
    private int renderAbilities(GuiGraphicsExtractor graphics, int left, int top) {
        Ability[] abilities = Ability.values();
        for (int i = 0; i < abilities.length; i++) {
            Ability ability = abilities[i];
            int modifier = sheet.modifier(ability);
            String text = sheet.scores().get(ability) + "  (" + (modifier >= 0 ? "+" : "") + modifier + ")";

            int column = left + 10 + (i % 2) * (CARD_WIDTH / 2 - 10);
            int row = top + (i / 2) * ABILITY_LINE;
            Icon.of(ability).draw(graphics, column, row - 4);
            graphics.text(font, Component.literal(text), column + Icon.SIZE + 4, row, TEXT);
        }
        return top + (abilities.length / 2) * ABILITY_LINE + 6;
    }
}
