package com.ddc.core.character;

import java.util.List;
import java.util.Objects;

/**
 * How much experience a character needs for each level.
 *
 * <p>The SRD's table is the default, but it is only a default: a data pack can hand a class its own,
 * which is what ADR-0002 means by tuning levelling speed without code. A campaign that wants to reach
 * level 5 in a weekend says so in JSON.
 *
 * <p>Thresholds are the total experience for a level, not the step between levels, because that is
 * what the SRD prints and what a pack author will be copying from.
 *
 * @param thresholds total experience needed for levels 2 upward, in order
 */
public record LevelTable(List<Integer> thresholds) {

    /** The SRD's own table: levels 2 to 20. */
    public static final LevelTable SRD = new LevelTable(List.of(
            300, 900, 2_700, 6_500, 14_000, 23_000, 34_000, 48_000, 64_000, 85_000,
            100_000, 120_000, 140_000, 165_000, 195_000, 225_000, 265_000, 305_000, 355_000));

    public LevelTable {
        thresholds = List.copyOf(Objects.requireNonNull(thresholds, "thresholds"));
        validate(thresholds);
    }

    /**
     * Checks a table before one is built from it.
     *
     * <p>Public so a codec can refuse a bad pack with an error rather than an exception: ADR-0002
     * promises a malformed file reports itself and leaves the rest of the reload standing.
     */
    public static void validate(List<Integer> thresholds) {
        if (thresholds.size() != Proficiency.MAX_LEVEL - 1) {
            throw new IllegalArgumentException("A level table needs " + (Proficiency.MAX_LEVEL - 1)
                    + " thresholds, one for each level after the first, but had " + thresholds.size());
        }
        int previous = 0;
        for (int i = 0; i < thresholds.size(); i++) {
            int threshold = thresholds.get(i);
            if (threshold <= previous) {
                throw new IllegalArgumentException("Level " + (i + 2) + " needs more experience than level "
                        + (i + 1) + ", but wanted " + threshold + " after " + previous);
            }
            previous = threshold;
        }
    }

    /**
     * The level a character with this much experience has reached.
     *
     * <p>Stops at the table's end rather than running off it: a character at maximum level keeps
     * earning experience and simply has nowhere left to spend it.
     */
    public int levelFor(int experience) {
        int level = Proficiency.MIN_LEVEL;
        for (int threshold : thresholds) {
            if (experience < threshold) {
                break;
            }
            level++;
        }
        return level;
    }

    /** The total experience a level asks for. Zero for the first, which is free. */
    public int experienceFor(int level) {
        Proficiency.validateLevel(level);
        return level == Proficiency.MIN_LEVEL ? 0 : thresholds.get(level - 2);
    }

    /**
     * How much more experience the next level needs, or empty at the last one.
     *
     * <p>The sheet shows this, so a player can see how far away the next level is rather than
     * guessing from a total.
     */
    public java.util.OptionalInt remainingTo(int experience) {
        int level = levelFor(experience);
        if (level >= Proficiency.MAX_LEVEL) {
            return java.util.OptionalInt.empty();
        }
        return java.util.OptionalInt.of(experienceFor(level + 1) - experience);
    }
}
