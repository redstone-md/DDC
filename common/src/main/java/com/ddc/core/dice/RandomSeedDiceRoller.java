package com.ddc.core.dice;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Draws a fresh seed per throw and delegates the dice math to {@link SeededDiceRoller}, so the
 * server's rolls and a client's replay of them run the exact same code path.
 *
 * <p>Not thread safe unless the supplied {@link RandomGenerator} is. Roll on the server thread.
 */
record RandomSeedDiceRoller(RandomGenerator source) implements DiceRoller {

    RandomSeedDiceRoller {
        Objects.requireNonNull(source, "source");
    }

    @Override
    public RollResult roll(DiceExpression expression, RollMode mode) {
        return new SeededDiceRoller(source.nextLong()).roll(expression, mode);
    }
}
