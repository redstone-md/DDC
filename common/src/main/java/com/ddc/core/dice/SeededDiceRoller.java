package com.ddc.core.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The one place dice actually get thrown.
 *
 * <p>Deterministic for a given seed: the same seed, expression, and mode always produce the same
 * {@link RollResult}, on any client and any JVM. {@link Random} is used deliberately over the newer
 * generators because its algorithm is fixed by its specification, which is what makes the replay on
 * a player's client match the server's authoritative roll.
 *
 * <p>Instances are stateless and safe to reuse; every {@link #roll} builds its own generator.
 */
record SeededDiceRoller(long seed) implements DiceRoller {

    @Override
    public RollResult roll(DiceExpression expression, RollMode mode) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(mode, "mode");
        if (!mode.isNormal() && !expression.isSingleD20()) {
            throw new IllegalArgumentException(
                    mode + " applies only to a single d20 test, not to " + expression);
        }

        Random random = new Random(seed);
        List<DieRoll> rolls = new ArrayList<>(expression.diceCount() + 1);
        for (DicePool pool : expression.pools()) {
            for (int i = 0; i < pool.count(); i++) {
                rolls.add(throwDie(pool.die(), random));
            }
        }
        if (!mode.isNormal()) {
            rolls = resolveAdvantage(rolls, throwDie(Die.D20, random), mode);
        }
        return new RollResult(expression, rolls, mode, seed);
    }

    private static DieRoll throwDie(Die die, Random random) {
        return DieRoll.kept(die, random.nextInt(die.sides()) + 1);
    }

    /**
     * Keeps the higher die under advantage and the lower under disadvantage, discarding the other.
     * Both dice stay in the result so the client can render the pair and the overlay can show which
     * one was thrown away.
     */
    private static List<DieRoll> resolveAdvantage(List<DieRoll> rolls, DieRoll second, RollMode mode) {
        DieRoll first = rolls.getFirst();
        boolean keepFirst = mode == RollMode.ADVANTAGE
                ? first.value() >= second.value()
                : first.value() <= second.value();
        return keepFirst
                ? List.of(first, second.discard())
                : List.of(first.discard(), second);
    }
}
