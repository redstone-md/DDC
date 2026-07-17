package com.ddc.rules;

import com.ddc.core.character.Ability;
import com.ddc.core.dice.DiceExpression;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.List;
import java.util.Optional;

/**
 * A spell, as a data pack describes it.
 *
 * <p>A subset of ARCHITECTURE.md's shape: enough for a spell to be cast and to do something, not yet
 * enough for components, areas of effect, or ritual casting. Fields DDC cannot honour are left out
 * rather than parsed and ignored, so a pack author is never told a spell does something it does not.
 *
 * <p>Example, {@code data/ddc/ddc_spells/fireball.json}:
 * <pre>{@code
 * {
 *   "name": "Fireball",
 *   "level": 3,
 *   "school": "evocation",
 *   "range": 150,
 *   "damage_dice": "8d6",
 *   "saving_throw": { "ability": "dexterity", "effect_on_success": "half_damage" }
 * }
 * }</pre>
 *
 * @param name        the display name
 * @param level       the spell's level; 0 is a cantrip and costs no slot
 * @param school      the school of magic, carried as text
 * @param range       range in feet, as the SRD counts it
 * @param damageDice   what it rolls for damage, absent for a spell that deals none
 * @param savingThrow  the save it allows, absent for a spell that allows none
 * @param castTime     seconds of casting before it lands; zero for a spell that is instant
 * @param components   the SRD's components, carried as text for anyone printing the spell
 * @param areaOfEffect the radius in blocks it catches everything within, zero for a single target
 */
public record Spell(String name, int level, String school, int range,
        Optional<DiceExpression> damageDice, Optional<SavingThrow> savingThrow,
        int castTime, List<String> components, int areaOfEffect) {

    /** Feet. Generous enough for SRD's longest ranged spells. */
    private static final int MAX_RANGE = 500;

    /** Seconds. Long enough for a ritual, short enough that a table does not wait out an evening. */
    private static final int MAX_CAST_TIME = 30;

    /** Blocks. A fireball's twenty-foot radius is about six of them. */
    private static final int MAX_AREA = 32;

    public static final Codec<Spell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Spell::name),
            Codec.intRange(0, Spellcasting.MAX_SPELL_LEVEL).fieldOf("level").forGetter(Spell::level),
            Codec.STRING.optionalFieldOf("school", "unknown").forGetter(Spell::school),
            Codec.intRange(0, MAX_RANGE).optionalFieldOf("range", 30).forGetter(Spell::range),
            DDCCodecs.DICE_EXPRESSION.optionalFieldOf("damage_dice").forGetter(Spell::damageDice),
            SavingThrow.CODEC.optionalFieldOf("saving_throw").forGetter(Spell::savingThrow),
            // ARCHITECTURE 5 prints these three in its own example schema, and the codec ignored all
            // of them: a pack copying the documentation had two thirds of its file silently dropped.
            Codec.intRange(0, MAX_CAST_TIME).optionalFieldOf("cast_time", 0).forGetter(Spell::castTime),
            Codec.STRING.listOf().optionalFieldOf("components", List.of()).forGetter(Spell::components),
            Codec.intRange(0, MAX_AREA).optionalFieldOf("area_of_effect", 0).forGetter(Spell::areaOfEffect)
    ).apply(instance, Spell::new));

    public Spell {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(school, "school");
        Objects.requireNonNull(damageDice, "damageDice");
        Objects.requireNonNull(savingThrow, "savingThrow");
        components = List.copyOf(Objects.requireNonNull(components, "components"));
    }

    /** Whether this spell takes time to cast, which is what the runes on the ground warn about. */
    public boolean hasCastTime() {
        return castTime > 0;
    }

    /** Whether this spell catches more than what it was aimed at. */
    public boolean isAreaOfEffect() {
        return areaOfEffect > 0;
    }

    /** A cantrip costs no slot. */
    public boolean isCantrip() {
        return level == 0;
    }

    /** Range in blocks. The SRD counts feet, and Minecraft counts blocks of five of them. */
    public double rangeInBlocks() {
        return range / 5.0;
    }

    /**
     * The save a spell allows.
     *
     * @param ability         which ability the target rolls
     * @param effectOnSuccess what a successful save earns the target
     */
    public record SavingThrow(Ability ability, Effect effectOnSuccess) {

        public static final Codec<SavingThrow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DDCCodecs.ABILITY.fieldOf("ability").forGetter(SavingThrow::ability),
                Effect.CODEC.optionalFieldOf("effect_on_success", Effect.HALF_DAMAGE)
                        .forGetter(SavingThrow::effectOnSuccess)
        ).apply(instance, SavingThrow::new));

        public SavingThrow {
            Objects.requireNonNull(ability, "ability");
            Objects.requireNonNull(effectOnSuccess, "effectOnSuccess");
        }

        /** What saving does for the target. */
        public enum Effect {
            /** The classic fireball: the target still burns, just less. */
            HALF_DAMAGE("half_damage"),
            /** The target is unaffected. */
            NONE("none");

            public static final Codec<Effect> CODEC = Codec.STRING.comapFlatMap(
                    key -> {
                        for (Effect effect : values()) {
                            if (effect.id.equals(key)) {
                                return DataResult.success(effect);
                            }
                        }
                        return DataResult.error(() -> "Unknown effect_on_success: '" + key + "'");
                    },
                    effect -> effect.id);

            private final String id;

            Effect(String id) {
                this.id = id;
            }

            public String id() {
                return id;
            }
        }
    }
}
