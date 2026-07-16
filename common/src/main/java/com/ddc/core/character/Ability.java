package com.ddc.core.character;

import java.util.Locale;
import java.util.Optional;

/**
 * The six ability scores.
 *
 * <p>The {@link #id()} is the stable key used in data pack JSON and network payloads. Renaming an
 * enum constant must not change it, which is why the id is written out rather than derived from
 * {@link #name()}.
 */
public enum Ability {
    STRENGTH("strength", "STR"),
    DEXTERITY("dexterity", "DEX"),
    CONSTITUTION("constitution", "CON"),
    INTELLIGENCE("intelligence", "INT"),
    WISDOM("wisdom", "WIS"),
    CHARISMA("charisma", "CHA");

    /** Below this an ability is not a number a character sheet can hold. */
    public static final int MIN_SCORE = 1;

    /** The SRD cap for a player character; magic items that exceed it set the score directly. */
    public static final int MAX_SCORE = 30;

    private final String id;
    private final String abbreviation;

    Ability(String id, String abbreviation) {
        this.id = id;
        this.abbreviation = abbreviation;
    }

    public String id() {
        return id;
    }

    /** The three-letter form the HUD renders, for example {@code STR}. */
    public String abbreviation() {
        return abbreviation;
    }

    /**
     * Resolves a data pack or command key, accepting either the full id or the abbreviation, in any
     * case. Returns empty rather than throwing so callers can report the offending file themselves.
     */
    public static Optional<Ability> byId(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (Ability ability : values()) {
            if (ability.id.equals(normalized) || ability.abbreviation.toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(ability);
            }
        }
        return Optional.empty();
    }

    /**
     * The SRD ability modifier: {@code floor((score - 10) / 2)}.
     *
     * <p>Floor division matters for scores below 10. Java's {@code /} truncates toward zero, which
     * would give a score of 9 a modifier of 0 instead of the correct -1.
     */
    public static int modifierFor(int score) {
        return Math.floorDiv(validateScore(score) - 10, 2);
    }

    static int validateScore(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "Ability score must be within " + MIN_SCORE + ".." + MAX_SCORE + " but was " + score);
        }
        return score;
    }
}
