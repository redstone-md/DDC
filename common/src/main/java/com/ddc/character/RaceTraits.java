package com.ddc.character;

import com.ddc.DDC;
import com.ddc.rules.Race;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What a race does, rather than what it says.
 *
 * <p>ARCHITECTURE 2 says a race registers attributes and passive traits, naming darkvision and speed.
 * Both were text: a dwarf's 25 feet and an elf's darkvision were printed in a chat message and then
 * forgotten. A trait nobody can feel is a trait nobody has.
 *
 * <p>Speed is the SRD's own number turned into Minecraft's: 30 feet is what everyone walks at in both
 * games, so 30 is no modifier and everything else is measured against it. A dwarf is slower and knows
 * it; a pack that writes 40 has a race that outruns the party.
 *
 * <p>Darkvision is the only trait with behaviour so far, because it is the only one in the SRD that
 * Minecraft already has a word for. The rest stay text until the game has something to say them with,
 * which is the same bargain the rest of the mod makes.
 */
public final class RaceTraits {

    /** What everyone walks at in the SRD, and therefore the speed that means "no change". */
    private static final double BASE_SPEED_FEET = 30.0;

    /**
     * The id the speed modifier is stored under. Stable, so a change of race replaces it rather than
     * stacking a second one on top -- the same reason the hit die modifier has an id.
     */
    private static final Identifier SPEED_ID = DDC.id("race_speed");

    /** The trait a pack writes for seeing in the dark. */
    private static final String DARKVISION = "darkvision";

    /** Re-applied often enough that it never blinks, and long enough that it costs nothing. */
    private static final int DARKVISION_TICKS = 400;

    private RaceTraits() {
    }

    /**
     * Applies a race's traits to a player, or takes them off when they have no race.
     *
     * <p>Transient, like the hit die's: a modifier written into a player's data would outlive the mod,
     * and a dwarf who uninstalled DDC should not be slow forever.
     */
    public static void apply(ServerPlayer player, java.util.Optional<Race> race) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            race.ifPresent(definition -> {
                double scale = definition.speed() / BASE_SPEED_FEET - 1.0;
                if (scale != 0) {
                    speed.addTransientModifier(new AttributeModifier(SPEED_ID, scale,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }
            });
        }
        if (race.map(RaceTraits::hasDarkvision).orElse(false)) {
            // Ambient and hidden: a race's own eyes are not a potion, and the icon in the corner would
            // say a dwarf had drunk something.
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DARKVISION_TICKS, 0,
                    true, false, false));
        }
    }

    /** Whether a pack gave this race darkvision, however they capitalised it. */
    static boolean hasDarkvision(Race race) {
        return race.traits().stream()
                .map(trait -> trait.toLowerCase(Locale.ROOT))
                .anyMatch(trait -> trait.contains(DARKVISION));
    }
}
