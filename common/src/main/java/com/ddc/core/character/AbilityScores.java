package com.ddc.core.character;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable set of all six ability scores.
 *
 * <p>Always complete: there is no such thing as a character without a Strength score, so the
 * constructor rejects a partial map rather than defaulting the gap and hiding a broken data pack.
 */
public final class AbilityScores {

    /** What an ability sits at with no racial bonus and no investment. */
    public static final int DEFAULT_SCORE = 10;

    private final Map<Ability, Integer> scores;

    private AbilityScores(Map<Ability, Integer> scores) {
        this.scores = scores;
    }

    /**
     * @throws IllegalArgumentException if an ability is missing or a score is out of range
     */
    public static AbilityScores of(Map<Ability, Integer> scores) {
        Objects.requireNonNull(scores, "scores");
        EnumMap<Ability, Integer> copy = new EnumMap<>(Ability.class);
        for (Ability ability : Ability.values()) {
            Integer score = scores.get(ability);
            if (score == null) {
                throw new IllegalArgumentException("Missing ability score: " + ability.id());
            }
            copy.put(ability, Ability.validateScore(score));
        }
        return new AbilityScores(copy);
    }

    /** Every ability at {@link #DEFAULT_SCORE}. The starting point for the builder. */
    public static AbilityScores defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int score(Ability ability) {
        return scores.get(Objects.requireNonNull(ability, "ability"));
    }

    public int modifier(Ability ability) {
        return Ability.modifierFor(score(ability));
    }

    public Map<Ability, Integer> asMap() {
        return new EnumMap<>(scores);
    }

    /** Returns a copy with one ability changed. The receiver is untouched. */
    public AbilityScores with(Ability ability, int score) {
        Objects.requireNonNull(ability, "ability");
        EnumMap<Ability, Integer> copy = new EnumMap<>(scores);
        copy.put(ability, Ability.validateScore(score));
        return new AbilityScores(copy);
    }

    /**
     * Returns a copy with {@code delta} added to one ability, clamped to the legal range. Racial
     * bonuses and drain effects go through here, where a clamp is correct; direct assignment goes
     * through {@link #with}, where an out-of-range value is a bug worth throwing on.
     */
    public AbilityScores plus(Ability ability, int delta) {
        int raw = score(ability) + delta;
        return with(ability, Math.clamp(raw, Ability.MIN_SCORE, Ability.MAX_SCORE));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AbilityScores other && scores.equals(other.scores);
    }

    @Override
    public int hashCode() {
        return scores.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AbilityScores[");
        boolean first = true;
        for (Map.Entry<Ability, Integer> entry : scores.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(entry.getKey().abbreviation()).append('=').append(entry.getValue());
        }
        return sb.append(']').toString();
    }

    /** Builds a set of scores, defaulting anything left unset to {@link #DEFAULT_SCORE}. */
    public static final class Builder {
        private final EnumMap<Ability, Integer> scores = new EnumMap<>(Ability.class);

        private Builder() {
            for (Ability ability : Ability.values()) {
                scores.put(ability, DEFAULT_SCORE);
            }
        }

        public Builder set(Ability ability, int score) {
            scores.put(Objects.requireNonNull(ability, "ability"), Ability.validateScore(score));
            return this;
        }

        public AbilityScores build() {
            return new AbilityScores(new EnumMap<>(scores));
        }
    }
}
