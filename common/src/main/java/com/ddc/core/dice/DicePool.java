package com.ddc.core.dice;

/**
 * A count of identical dice, such as the {@code 8d6} of a fireball.
 *
 * @param count how many dice to throw, at least 1
 * @param die   which die to throw
 */
public record DicePool(int count, Die die) {

    /** Guards against a data pack asking for {@code 0d6} or a spell scaling into a denial of service. */
    public static final int MAX_COUNT = 100;

    public DicePool {
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("Dice count must be within 1.." + MAX_COUNT + " but was " + count);
        }
        java.util.Objects.requireNonNull(die, "die");
    }

    public static DicePool of(int count, Die die) {
        return new DicePool(count, die);
    }

    public int minTotal() {
        return count * die.minValue();
    }

    public int maxTotal() {
        return count * die.maxValue();
    }

    @Override
    public String toString() {
        return count + die.toString();
    }
}
