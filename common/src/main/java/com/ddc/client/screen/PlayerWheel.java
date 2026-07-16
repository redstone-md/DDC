package com.ddc.client.screen;

import com.ddc.character.CharacterSheet;
import com.ddc.network.ClassSummary;
import com.ddc.rules.ClassFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * What goes on a player's wheel.
 *
 * <p>Built from the sheet and the class summary the server sent, so a wizard is not offered Second
 * Wind and a fighter is not offered Cast. A wheel of things that will not work is worse than no wheel.
 *
 * <p>Spells need a target, and the target is whatever the player is looking at. Sending its UUID is
 * what makes that work: the entity selector takes one, so the command the wheel sends is the command
 * the player could have typed.
 */
@Environment(EnvType.CLIENT)
public final class PlayerWheel {

    private PlayerWheel() {
    }

    /** The wheel for a character, or an empty one when they have not made a character yet. */
    public static List<WheelOption> optionsFor(CharacterSheet sheet, Optional<ClassSummary> summary) {
        List<WheelOption> options = new ArrayList<>();
        if (sheet == null || !sheet.hasClass() || summary.isEmpty()) {
            return options;
        }
        ClassSummary klass = summary.get();

        options.add(new WheelOption("Roll", "1d20", "roll 1d20"));
        options.add(WheelOption.of("Sheet", "ddc sheet"));

        if (klass.canCast()) {
            spellOptions(sheet, options);
        }
        if (klass.has(ClassFeature.Type.SECOND_WIND)) {
            options.add(new WheelOption("Second Wind", "heal", "ddc second-wind"));
        }
        if (klass.has(ClassFeature.Type.CHANNEL_DIVINITY)) {
            options.add(new WheelOption("Channel", "turn undead", "ddc channel-divinity"));
        }
        options.add(new WheelOption("Rest", "slots back", "ddc rest"));
        return options;
    }

    /**
     * A slice per prepared spell, aimed at whatever the player is looking at.
     *
     * <p>Only prepared spells: cantrips are known rather than written down, and the client is not
     * told which spells exist -- that lives in the server's data packs. A caster with an empty book
     * is told to fill it rather than shown an empty wheel.
     */
    private static void spellOptions(CharacterSheet sheet, List<WheelOption> options) {
        Optional<Entity> target = lookingAt();
        if (sheet.preparedSpells().isEmpty()) {
            options.add(new WheelOption("No spells", "use /ddc prepare", "ddc sheet"));
            return;
        }
        if (target.isEmpty()) {
            options.add(new WheelOption("Cast", "look at a target", "ddc sheet"));
            return;
        }
        String selector = target.get().getUUID().toString();
        for (Identifier spell : sheet.preparedSpells()) {
            options.add(new WheelOption(name(spell), "cast", "ddc cast " + spell + " " + selector));
        }
    }

    /** The entity under the crosshair, if the player is looking at one. */
    private static Optional<Entity> lookingAt() {
        Minecraft client = Minecraft.getInstance();
        if (client.crosshairPickEntity != null && client.crosshairPickEntity.isAlive()) {
            return Optional.of(client.crosshairPickEntity);
        }
        return Optional.empty();
    }

    /** A spell's id, tidied for a label: the client is never told the name its pack gave it. */
    static String name(Identifier spell) {
        String path = spell.getPath().replace('_', ' ');
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
