package com.ddc.rules;

import com.ddc.core.character.Ability;
import com.ddc.core.character.Proficiency;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;

/**
 * What a class can cast, if anything.
 *
 * <p>The slot table is data, not code. It is a table in the SRD with no formula behind it, and it
 * differs per class, so a homebrew half-caster describes its own progression rather than asking DDC
 * to know about half-casters.
 *
 * <pre>{@code
 * "spellcasting": {
 *   "ability": "intelligence",
 *   "slots": [[2], [3], [4, 2], [4, 3], [4, 3, 2]]
 * }
 * }</pre>
 *
 * @param ability the ability spells are cast with: the save DC and attack bonus come from it
 * @param slots   one entry per character level from 1 upward; each holds the slots for spell levels
 *                1, 2, 3 ... in order
 */
public record Spellcasting(Ability ability, List<List<Integer>> slots) {

    /** The SRD's highest spell level. */
    public static final int MAX_SPELL_LEVEL = 9;

    /**
     * Validated before the record is built, so a broken slot table is reported against its own file
     * rather than thrown during a data pack reload. See the note on {@link Encounter}.
     */
    private static final Codec<List<List<Integer>>> SLOTS =
            Codec.intRange(0, 9).listOf().listOf().validate(slots -> {
                if (slots.isEmpty()) {
                    return com.mojang.serialization.DataResult.error(
                            () -> "A spellcasting class needs a slot table");
                }
                if (slots.size() > Proficiency.MAX_LEVEL) {
                    return com.mojang.serialization.DataResult.error(
                            () -> "A slot table cannot go past level " + Proficiency.MAX_LEVEL);
                }
                return com.mojang.serialization.DataResult.success(slots);
            });

    public static final Codec<Spellcasting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DDCCodecs.ABILITY.fieldOf("ability").forGetter(Spellcasting::ability),
            SLOTS.fieldOf("slots").forGetter(Spellcasting::slots)
    ).apply(instance, Spellcasting::new));

    public Spellcasting {
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(slots, "slots");
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("A spellcasting class needs a slot table");
        }
        if (slots.size() > Proficiency.MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "A slot table cannot go past level " + Proficiency.MAX_LEVEL);
        }
        slots = slots.stream().map(List::copyOf).toList();
    }

    /**
     * How many slots of {@code spellLevel} a character of {@code characterLevel} has.
     *
     * <p>A table shorter than the character's level keeps its last row rather than dropping to zero:
     * an addon that only bothered to write the first few levels should degrade into "no more slots
     * than it said", not into a caster who loses their magic on levelling up.
     */
    public int slotsFor(int characterLevel, int spellLevel) {
        Proficiency.validateLevel(characterLevel);
        if (spellLevel < 1 || spellLevel > MAX_SPELL_LEVEL) {
            return 0;
        }
        List<Integer> row = slots.get(Math.min(characterLevel, slots.size()) - 1);
        return spellLevel <= row.size() ? row.get(spellLevel - 1) : 0;
    }

    /** The highest spell level this character can cast at all. */
    public int highestSlotLevel(int characterLevel) {
        for (int level = MAX_SPELL_LEVEL; level >= 1; level--) {
            if (slotsFor(characterLevel, level) > 0) {
                return level;
            }
        }
        return 0;
    }
}
