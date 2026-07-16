package com.ddc.core.check;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.ddc.core.character.Proficiency;
import com.ddc.core.combat.ArmorClass;
import com.ddc.core.dice.Die;
import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.DiceRoller;
import com.ddc.core.dice.RollMode;
import com.ddc.core.dice.RollResult;
import java.util.Objects;

/**
 * A d20 test waiting to be thrown: a modifier, a number to beat, and a mode.
 *
 * <p>Ability checks, saving throws, and attack rolls are the same mechanic with different names, so
 * they share one resolver here. The factories only differ in where the modifier and the target
 * number come from.
 *
 * <p>This describes the test but does not perform it. The server builds one, resolves it with its
 * own {@link DiceRoller}, and ships the {@link CheckOutcome} out; a client never resolves its own.
 *
 * @param modifier        the total bonus added to the d20
 * @param difficultyClass the DC or AC the roll must meet or beat
 * @param mode            whether the character is favoured or hindered
 */
public record D20Check(int modifier, int difficultyClass, RollMode mode) {

    public D20Check {
        Objects.requireNonNull(mode, "mode");
    }

    /** A test with an already-computed modifier, for callers that assemble their own bonuses. */
    public static D20Check of(int modifier, int difficultyClass) {
        return new D20Check(modifier, difficultyClass, RollMode.NORMAL);
    }

    /** An ability check or saving throw with no proficiency. Add it with {@link #plusProficiency}. */
    public static D20Check ability(AbilityScores scores, Ability ability, int difficultyClass) {
        Objects.requireNonNull(scores, "scores");
        return of(scores.modifier(ability), difficultyClass);
    }

    /**
     * An attack roll against a target's armour class.
     *
     * @param attackModifier the attacker's total to-hit bonus
     * @param targetArmor    the target's armour
     * @param targetScores   the target's scores, which supply the Dexterity in their AC
     */
    public static D20Check attack(int attackModifier, ArmorClass targetArmor, AbilityScores targetScores) {
        Objects.requireNonNull(targetArmor, "targetArmor");
        return of(attackModifier, targetArmor.value(targetScores));
    }

    /** Adds the proficiency bonus for a character level. */
    public D20Check plusProficiency(int level) {
        return plusModifier(Proficiency.bonusAtLevel(level));
    }

    /** Adds any further bonus or penalty, such as a magic weapon or a bane spell. */
    public D20Check plusModifier(int delta) {
        return new D20Check(modifier + delta, difficultyClass, mode);
    }

    /** Applies a mode, cancelling against any mode already set per {@link RollMode#combine}. */
    public D20Check with(RollMode other) {
        return new D20Check(modifier, difficultyClass, mode.combine(other));
    }

    public D20Check withAdvantage() {
        return with(RollMode.ADVANTAGE);
    }

    public D20Check withDisadvantage() {
        return with(RollMode.DISADVANTAGE);
    }

    /** The notation this test throws, for the roll log and the dice renderer. */
    public DiceExpression expression() {
        return DiceExpression.of(1, Die.D20, modifier);
    }

    /**
     * Throws the test.
     *
     * <p>A natural 20 succeeds and a natural 1 fails regardless of the DC. The SRD applies that only
     * to attack rolls, but DDC extends it to every d20 test: the Nat 20 and Nat 1 fanfares are a
     * headline feature, and a critical that quietly resolved as an ordinary failure would read as a
     * bug on stream.
     */
    public CheckOutcome resolve(DiceRoller roller) {
        Objects.requireNonNull(roller, "roller");
        RollResult roll = roller.roll(expression(), mode);
        return new CheckOutcome(roll, difficultyClass, degreeOf(roll));
    }

    private CheckOutcome.Degree degreeOf(RollResult roll) {
        if (roll.isNatural20()) {
            return CheckOutcome.Degree.CRITICAL_SUCCESS;
        }
        if (roll.isNatural1()) {
            return CheckOutcome.Degree.CRITICAL_FAILURE;
        }
        return roll.total() >= difficultyClass
                ? CheckOutcome.Degree.SUCCESS
                : CheckOutcome.Degree.FAILURE;
    }
}
