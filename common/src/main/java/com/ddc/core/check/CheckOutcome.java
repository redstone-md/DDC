package com.ddc.core.check;

import com.ddc.core.dice.RollResult;
import java.util.Objects;

/**
 * A resolved d20 test: the throw, the number it had to beat, and how it went.
 *
 * @param roll            the underlying throw, kept whole so the client can replay it
 * @param difficultyClass the DC or AC the roll was measured against
 * @param degree          how the test resolved
 */
public record CheckOutcome(RollResult roll, int difficultyClass, Degree degree) {

    public CheckOutcome {
        Objects.requireNonNull(roll, "roll");
        Objects.requireNonNull(degree, "degree");
    }

    public int total() {
        return roll.total();
    }

    public boolean isSuccess() {
        return degree.isSuccess();
    }

    /** By how much the roll beat the DC, negative when it fell short. Drives the GM's roll log. */
    public int margin() {
        return total() - difficultyClass;
    }

    /** How a d20 test resolved. */
    public enum Degree {
        /** A natural 20: succeeds regardless of the DC, and triggers the Nat 20 fanfare. */
        CRITICAL_SUCCESS(true),
        SUCCESS(true),
        FAILURE(false),
        /** A natural 1: fails regardless of the DC, and triggers the Nat 1 pratfall. */
        CRITICAL_FAILURE(false);

        private final boolean success;

        Degree(boolean success) {
            this.success = success;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isCritical() {
            return this == CRITICAL_SUCCESS || this == CRITICAL_FAILURE;
        }
    }
}
