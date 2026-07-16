package com.ddc.client;

import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The character sheet overlay: class, level, hit points, and the six abilities.
 *
 * <p>Draws only what the server sent, plus the health the client already has: hit points are vanilla
 * health now, so the panel reads them off the player rather than being told them. It holds no rules
 * and derives no numbers, so it cannot disagree with the server about a character.
 *
 * <p>The PRD asks for a glassmorphic panel; this is the readable version of that panel, a translucent
 * backdrop behind plain text. The blur shader it describes is not here yet.
 */
@Environment(EnvType.CLIENT)
public final class CharacterHud {

    private static final int MARGIN = 4;
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 10;

    private static final int BACKDROP = 0x80000000;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_DIM = 0xAAAAAA;
    private static final int HP_HEALTHY = 0x55FF55;
    private static final int HP_HURT = 0xFFAA00;
    private static final int HP_CRITICAL = 0xFF5555;

    private CharacterSheet sheet;

    /** Takes the sheet the server sent. */
    public void accept(CharacterSheet sheet) {
        this.sheet = sheet;
    }

    public Optional<CharacterSheet> sheet() {
        return Optional.ofNullable(sheet);
    }

    /** Draws the panel in the top-left corner. Silent until the server has sent a sheet. */
    public void render(GuiGraphicsExtractor graphics, Font font, LocalPlayer player) {
        if (sheet == null || !sheet.hasClass() || player == null) {
            return;
        }
        int hitPoints = Math.round(player.getHealth());
        int maxHitPoints = Math.round(player.getMaxHealth());
        String header = headerText(hitPoints, maxHitPoints);
        String abilities = abilityText();
        int width = Math.max(font.width(header), font.width(abilities)) + PADDING * 2;
        int height = LINE_HEIGHT * 2 + PADDING * 2;

        graphics.fill(MARGIN, MARGIN, MARGIN + width, MARGIN + height, BACKDROP);
        graphics.text(font, header, MARGIN + PADDING, MARGIN + PADDING,
                hitPointColour(hitPoints, maxHitPoints));
        graphics.text(font, abilities, MARGIN + PADDING, MARGIN + PADDING + LINE_HEIGHT, TEXT);
    }

    private String headerText(int hitPoints, int maxHitPoints) {
        return "LVL " + sheet.level() + "  HP " + hitPoints + "/" + maxHitPoints
                + "  PROF +" + sheet.proficiencyBonus();
    }

    private String abilityText() {
        StringBuilder sb = new StringBuilder();
        for (Ability ability : Ability.values()) {
            int modifier = sheet.modifier(ability);
            sb.append(ability.abbreviation()).append(' ')
                    .append(modifier >= 0 ? "+" : "").append(modifier).append("  ");
        }
        return sb.toString().trim();
    }

    /** Hit points turn amber below half and red below a quarter, the usual table warning. */
    private static int hitPointColour(int hitPoints, int maxHitPoints) {
        if (maxHitPoints <= 0) {
            return TEXT_DIM;
        }
        double fraction = (double) hitPoints / maxHitPoints;
        if (fraction <= 0.25) {
            return HP_CRITICAL;
        }
        return fraction <= 0.5 ? HP_HURT : HP_HEALTHY;
    }
}
