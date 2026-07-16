package com.ddc.core.dice;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of throwing a {@link DiceExpression}.
 *
 * <p>Immutable and self-describing: the server produces one of these, and the same record drives the
 * chat message, the 3D dice replay, and the OBS widget payload. Nothing recomputes the total.
 *
 * @param expression the expression that was thrown
 * @param rolls      every die thrown, including those discarded by advantage or disadvantage
 * @param mode       the mode the expression was thrown under
 * @param seed       the seed the roll came from, so any client can replay the identical physics
 */
public record RollResult(DiceExpression expression, List<DieRoll> rolls, RollMode mode, long seed) {

    public RollResult {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(mode, "mode");
        rolls = List.copyOf(Objects.requireNonNull(rolls, "rolls"));

        // A result must account for exactly the dice its expression asked for, plus the second d20
        // that advantage and disadvantage throw. This is what makes a result self-consistent, and it
        // is also the check that rejects a malformed result arriving over the network.
        int expected = expression.diceCount() + (mode.isNormal() ? 0 : 1);
        if (rolls.size() != expected) {
            throw new IllegalArgumentException(
                    "A " + mode + " roll of " + expression + " needs " + expected
                            + " dice but got " + rolls.size());
        }
    }

    /** The dice that count, in throw order. */
    public List<DieRoll> keptRolls() {
        return rolls.stream().filter(DieRoll::isKept).toList();
    }

    /** Sum of the kept dice, before the flat modifier. */
    public int diceTotal() {
        return keptRolls().stream().mapToInt(DieRoll::value).sum();
    }

    public int modifier() {
        return expression.modifier();
    }

    /** The number the table cares about: kept dice plus the flat modifier. */
    public int total() {
        return diceTotal() + modifier();
    }

    /**
     * The kept d20 of a single-d20 test. Empty for damage rolls and other multi-die expressions,
     * which is what callers use to decide whether crit rules apply at all.
     */
    public Optional<DieRoll> naturalD20() {
        if (!expression.isSingleD20()) {
            return Optional.empty();
        }
        return keptRolls().stream().filter(roll -> roll.die() == Die.D20).findFirst();
    }

    public boolean isNatural20() {
        return naturalD20().filter(DieRoll::isNatural20).isPresent();
    }

    public boolean isNatural1() {
        return naturalD20().filter(DieRoll::isNatural1).isPresent();
    }

    /** Renders as {@code 1d20+3: [17] + 3 = 20} for chat and the roll log. */
    public String describe() {
        StringBuilder sb = new StringBuilder(expression.toString()).append(": [");
        boolean first = true;
        for (DieRoll roll : rolls) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            if (roll.discarded()) {
                sb.append('~').append(roll.value()).append('~');
            } else {
                sb.append(roll.value());
            }
        }
        sb.append(']');
        int modifier = modifier();
        if (modifier != 0) {
            sb.append(modifier > 0 ? " + " : " - ").append(Math.abs(modifier));
        }
        return sb.append(" = ").append(total()).toString();
    }
}
