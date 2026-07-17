package com.ddc.rules;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.Identifier;
import java.util.Map;
import java.util.Objects;

/**
 * A race, as a data pack describes it.
 *
 * <p>Example, {@code data/ddc/ddc_races/dwarf.json}:
 * <pre>{@code
 * {
 *   "name": "Dwarf",
 *   "ability_bonuses": { "constitution": 2 },
 *   "speed": 25,
 *   "traits": ["darkvision", "dwarven resilience"]
 * }
 * }</pre>
 *
 * @param name           the display name shown on the sheet
 * @param abilityBonuses what the race adds to each ability, applied when the race is chosen
 * @param speed          walking speed in feet, as the SRD counts it
 * @param traits         passive traits, carried as text
 * @param items          what the race hands a character when they pick it, once
 */
public record Race(String name, Map<Ability, Integer> abilityBonuses, int speed, List<String> traits,
        List<Identifier> items) {

    /** Wide enough for any SRD race and for homebrew, narrow enough to reject a typo'd number. */
    private static final int MIN_SPEED = 0;
    private static final int MAX_SPEED = 120;

    public static final Codec<Race> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Race::name),
            Codec.unboundedMap(DDCCodecs.ABILITY, Codec.intRange(-5, 5))
                    .optionalFieldOf("ability_bonuses", Map.of()).forGetter(Race::abilityBonuses),
            Codec.intRange(MIN_SPEED, MAX_SPEED).optionalFieldOf("speed", 30).forGetter(Race::speed),
            Codec.STRING.listOf().optionalFieldOf("traits", List.of()).forGetter(Race::traits),
            // What a race puts in your hands. An elf starts with a bow because an elf starts with a
            // bow; which items those are is the pack's story to tell, not the mod's.
            Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Race::items)
    ).apply(instance, Race::new));

    public Race {
        Objects.requireNonNull(name, "name");
        abilityBonuses = Map.copyOf(Objects.requireNonNull(abilityBonuses, "abilityBonuses"));
        traits = List.copyOf(Objects.requireNonNull(traits, "traits"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /**
     * Applies this race's bonuses to a set of scores.
     *
     * <p>Clamped rather than rejected: a homebrew race stacking onto an already high score should
     * stop at the cap, not refuse to be played.
     */
    public AbilityScores applyTo(AbilityScores scores) {
        AbilityScores raised = scores;
        for (Map.Entry<Ability, Integer> bonus : abilityBonuses.entrySet()) {
            raised = raised.plus(bonus.getKey(), bonus.getValue());
        }
        return raised;
    }

    /**
     * Takes this race's bonuses back off, for a character who is changing race.
     *
     * <p>Scores are stored with the bonuses already in them, so the only way to swap a race without
     * the old one's gift lingering is to hand it back first.
     */
    public AbilityScores removeFrom(AbilityScores scores) {
        AbilityScores lowered = scores;
        for (Map.Entry<Ability, Integer> bonus : abilityBonuses.entrySet()) {
            lowered = lowered.plus(bonus.getKey(), -bonus.getValue());
        }
        return lowered;
    }
}
