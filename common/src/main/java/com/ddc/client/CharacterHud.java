package com.ddc.client;

import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import com.ddc.network.ClassSummary;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The character sheet overlay: class, level, hit points, armour class, spell slots, and the six
 * abilities -- what PRD 3.1 lists, and now all of it.
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
    private static final int SLOT_FULL = 0xFF6699FF;
    private static final int SLOT_SPENT = 0xFF303030;

    /** A slot pip, and the gap around it. Small: there can be nine levels of them. */
    private static final int PIP = 5;
    private static final int PIP_GAP = 2;

    private CharacterSheet sheet;
    private ClassSummary summary;
    private int armorClass;

    /** Takes the sheet the server sent, and what it says the class can do. */
    public void accept(CharacterSheet sheet, java.util.Optional<ClassSummary> summary, int armorClass) {
        this.sheet = sheet;
        this.summary = summary.orElse(null);
        this.armorClass = armorClass;
    }

    /** What the class can do, as the server described it. */
    public Optional<ClassSummary> summary() {
        return Optional.ofNullable(summary);
    }

    /** The class's display name, as its data pack wrote it. */
    public String className() {
        return summary == null ? "No class" : summary.name();
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
        boolean casts = !spellSlots().isEmpty();
        int width = Math.max(font.width(header), font.width(abilities)) + PADDING * 2;
        int height = LINE_HEIGHT * 2 + PADDING * 2 + (casts ? PIP + PIP_GAP : 0);

        graphics.fill(MARGIN, MARGIN, MARGIN + width, MARGIN + height, BACKDROP);
        graphics.outline(MARGIN, MARGIN, width, height, BORDER);
        graphics.text(font, header, MARGIN + PADDING, MARGIN + PADDING,
                hitPointColour(hitPoints, maxHitPoints));
        graphics.text(font, abilities, MARGIN + PADDING, MARGIN + PADDING + LINE_HEIGHT, TEXT);
        if (casts) {
            renderSlots(graphics, MARGIN + PADDING, MARGIN + PADDING + LINE_HEIGHT * 2);
        }
    }

    /**
     * The spell slots, as PRD 3.1 draws them: a pip per slot, dark once it is spent.
     *
     * <p>Pips rather than a number because a caster's question at the table is "have I got a second
     * level left", and counting three lit dots answers it without reading.
     */
    private void renderSlots(GuiGraphicsExtractor graphics, int x, int y) {
        List<Integer> slots = spellSlots();
        int left = x;
        for (int spellLevel = 1; spellLevel <= slots.size(); spellLevel++) {
            int total = slots.get(spellLevel - 1);
            int spent = sheet.usedSlots(spellLevel);
            for (int pip = 0; pip < total; pip++) {
                graphics.fill(left, y, left + PIP, y + PIP, pip < total - spent ? SLOT_FULL : SLOT_SPENT);
                left += PIP + 1;
            }
            // A gap between levels, so a row of nine pips still reads as "three first, two second".
            left += PIP_GAP;
        }
    }

    /** The slots this character has, or none for a class that does not cast. */
    private List<Integer> spellSlots() {
        return summary == null ? List.of() : summary.spellSlots();
    }

    private String headerText(int hitPoints, int maxHitPoints) {
        return "LVL " + sheet.level() + "  HP " + hitPoints + "/" + maxHitPoints
                + "  AC " + armorClass + "  PROF +" + sheet.proficiencyBonus();
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
