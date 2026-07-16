package com.ddc.core.dice;

import java.util.Objects;

/**
 * One physical die and the face it landed on.
 *
 * @param die       which die was thrown
 * @param value     the face rolled, within the die's range
 * @param discarded true when advantage or disadvantage threw this die away; discarded dice still
 *                  render and still appear in the roll log, they just do not count toward the total
 */
public record DieRoll(Die die, int value, boolean discarded) {

    public DieRoll {
        Objects.requireNonNull(die, "die");
        if (value < die.minValue() || value > die.maxValue()) {
            throw new IllegalArgumentException("Value " + value + " is not a face of " + die);
        }
    }

    public static DieRoll kept(Die die, int value) {
        return new DieRoll(die, value, false);
    }

    public DieRoll discard() {
        return discarded ? this : new DieRoll(die, value, true);
    }

    public boolean isKept() {
        return !discarded;
    }

    /** A natural 20 on a d20. Only meaningful for d20 tests; damage dice never crit by face. */
    public boolean isNatural20() {
        return die == Die.D20 && value == 20;
    }

    /** A natural 1 on a d20. */
    public boolean isNatural1() {
        return die == Die.D20 && value == 1;
    }
}
