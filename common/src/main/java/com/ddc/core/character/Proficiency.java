package com.ddc.core.character;

/**
 * The proficiency bonus progression.
 *
 * <p>Levels and the bonus they grant are a table in the SRD; the closed form below reproduces it
 * exactly and saves shipping the table. A data pack that wants a different progression overrides the
 * class definition rather than this class.
 */
public final class Proficiency {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 20;

    private Proficiency() {
    }

    /**
     * The proficiency bonus at a character level: +2 at levels 1-4, rising by one every four levels
     * to +6 at level 17.
     *
     * @throws IllegalArgumentException if the level is outside 1..20
     */
    public static int bonusAtLevel(int level) {
        return 2 + Math.floorDiv(validateLevel(level) - 1, 4);
    }

    public static int validateLevel(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Character level must be within " + MIN_LEVEL + ".." + MAX_LEVEL + " but was " + level);
        }
        return level;
    }
}
