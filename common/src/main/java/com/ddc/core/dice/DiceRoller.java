package com.ddc.core.dice;

import java.util.random.RandomGenerator;

/**
 * Throws dice.
 *
 * <p>Every roll carries the seed it came from. The server rolls with {@link #random()}, puts the
 * seed in the {@code ddc:dice_result} payload, and each client rebuilds the same result with
 * {@link #replaying(long)} to drive its physics simulation. That is why the seed lives on
 * {@link RollResult} rather than in the roller alone: the result is the whole story of the throw.
 */
@FunctionalInterface
public interface DiceRoller {

    /**
     * @throws IllegalArgumentException if {@code mode} is not {@link RollMode#NORMAL} for an
     *                                  expression that is not a single d20
     */
    RollResult roll(DiceExpression expression, RollMode mode);

    default RollResult roll(DiceExpression expression) {
        return roll(expression, RollMode.NORMAL);
    }

    default RollResult roll(String notation) {
        return roll(DiceExpression.parse(notation), RollMode.NORMAL);
    }

    default RollResult roll(String notation, RollMode mode) {
        return roll(DiceExpression.parse(notation), mode);
    }

    /**
     * A roller that always reproduces the same throw for the same expression and mode. Clients use
     * this to replay a roll the server already resolved.
     */
    static DiceRoller replaying(long seed) {
        return new SeededDiceRoller(seed);
    }

    /** A roller that draws a fresh seed per throw. This is the server's roller. */
    static DiceRoller random() {
        return random(RandomGenerator.getDefault());
    }

    /** A roller that draws each throw's seed from {@code source}. */
    static DiceRoller random(RandomGenerator source) {
        return new RandomSeedDiceRoller(source);
    }
}
