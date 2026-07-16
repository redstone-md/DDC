package com.ddc.core.dice;

/**
 * How a d20 test resolves when a character is favoured or hindered.
 *
 * <p>Under the SRD, advantage throws the die twice and keeps the higher result; disadvantage keeps
 * the lower. Both discard rather than delete the other die, because the streamer overlay shows the
 * discarded value.
 */
public enum RollMode {
    NORMAL,
    ADVANTAGE,
    DISADVANTAGE;

    public boolean isNormal() {
        return this == NORMAL;
    }

    /**
     * Combines two sources of advantage. Under the SRD, advantage and disadvantage cancel out
     * entirely no matter how many of each apply, and the result is a single normal roll.
     */
    public RollMode combine(RollMode other) {
        if (this == other) {
            return this;
        }
        return this == NORMAL ? other : NORMAL;
    }
}
