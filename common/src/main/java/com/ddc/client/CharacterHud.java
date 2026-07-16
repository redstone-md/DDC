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
 * <p>PRD 3.1 asks for a glassmorphic panel, and this is not one. Minecraft 26's blur is a full-frame
 * pass -- it blurs everything drawn beneath the stratum, which is the entire world -- and the HUD is
 * on screen the whole time a player is playing. Using it here blurred the game permanently, which a
 * screenshot showed in the plainest possible terms. Glass behind a small always-on card needs a blur
 * bounded to the card's own rectangle, which the renderer does not offer. Until it does, this is a
 * translucent card, and the sheet screen keeps the blur because a screen is a moment, not a state.
 */
@Environment(EnvType.CLIENT)
public final class CharacterHud {

    private static final int MARGIN = 4;
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 10;

    // Every colour carries its alpha. A colour written 0xFFFFFF is fully transparent, which is how
    // the HUD's text came to be invisible from 1.0.0 until a screenshot caught it.
    private static final int BACKDROP = 0xB0101010;
    private static final int BORDER = 0xFFC9973F;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int HP_HEALTHY = 0xFF55FF55;
    private static final int HP_HURT = 0xFFFFAA00;
    private static final int HP_CRITICAL = 0xFFFF5555;

    private CharacterSheet sheet;

    /** Takes the sheet the server sent. */
    public void accept(CharacterSheet sheet) {
        this.sheet = sheet;
    }

    /**
     * The class's display name.
     *
     * <p>The id, tidied: the client has no data packs, so the name a pack gave the class never
     * reaches it. "ddc:fighter" becomes "Fighter", which is right for every class DDC ships and a
     * fair guess for the rest.
     */
    public String className() {
        return sheet == null ? "" : sheet.characterClass()
                .map(id -> {
                    String path = id.getPath().replace('_', ' ');
                    return Character.toUpperCase(path.charAt(0)) + path.substring(1);
                })
                .orElse("No class");
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
        graphics.outline(MARGIN, MARGIN, width, height, BORDER);
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
