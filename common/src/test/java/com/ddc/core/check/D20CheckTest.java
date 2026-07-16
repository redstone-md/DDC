package com.ddc.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.ddc.core.combat.ArmorCategory;
import com.ddc.core.combat.ArmorClass;
import com.ddc.core.dice.DiceRoller;
import com.ddc.core.dice.RollMode;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class D20CheckTest {

    private static final AbilityScores FIGHTER = AbilityScores.builder()
            .set(Ability.STRENGTH, 16)
            .set(Ability.DEXTERITY, 12)
            .build();

    /**
     * Finds a seed whose bare d20 satisfies {@code face}, so the crit tests exercise a real natural
     * 20 or 1 without hardcoding a seed that a future change to the roller would silently invalidate.
     */
    private static long seedRolling(IntPredicate face) {
        for (long seed = 0; seed < 10_000; seed++) {
            if (face.test(DiceRoller.replaying(seed).roll("1d20").diceTotal())) {
                return seed;
            }
        }
        throw new AssertionError("No seed under 10000 produced the wanted face");
    }

    @Test
    void anAbilityCheckTakesItsModifierFromTheScore() {
        D20Check check = D20Check.ability(FIGHTER, Ability.STRENGTH, 15);

        assertEquals(3, check.modifier());
        assertEquals(15, check.difficultyClass());
        assertEquals(RollMode.NORMAL, check.mode());
    }

    @Test
    void proficiencyAddsOnTopOfTheAbilityModifier() {
        D20Check check = D20Check.ability(FIGHTER, Ability.STRENGTH, 15).plusProficiency(5);

        assertEquals(6, check.modifier(), "+3 Strength and the +3 proficiency bonus at level 5");
    }

    @Test
    void anAttackRollTargetsTheDefendersArmorClass() {
        AbilityScores target = AbilityScores.defaults().with(Ability.DEXTERITY, 14);
        ArmorClass armor = ArmorClass.of(2, ArmorCategory.LIGHT).withShield(2);

        D20Check attack = D20Check.attack(5, armor, target);

        assertEquals(5, attack.modifier());
        assertEquals(16, attack.difficultyClass());
    }

    @Test
    void theExpressionMatchesTheModifier() {
        assertEquals("1d20+3", D20Check.ability(FIGHTER, Ability.STRENGTH, 10).expression().toString());
        assertEquals("1d20-1", D20Check.of(-1, 10).expression().toString());
    }

    @Test
    void meetingTheDcSucceeds() {
        long seed = seedRolling(face -> face == 12);

        CheckOutcome outcome = D20Check.of(3, 15).resolve(DiceRoller.replaying(seed));

        assertEquals(CheckOutcome.Degree.SUCCESS, outcome.degree());
        assertTrue(outcome.isSuccess());
        assertEquals(15, outcome.total(), "meeting the DC exactly is a success");
        assertEquals(0, outcome.margin());
    }

    @Test
    void fallingShortFails() {
        long seed = seedRolling(face -> face == 5);

        CheckOutcome outcome = D20Check.of(3, 15).resolve(DiceRoller.replaying(seed));

        assertEquals(CheckOutcome.Degree.FAILURE, outcome.degree());
        assertFalse(outcome.isSuccess());
        assertEquals(-7, outcome.margin());
    }

    @Test
    @DisplayName("a natural 20 succeeds against a DC it could never reach")
    void naturalTwentyAlwaysSucceeds() {
        long seed = seedRolling(face -> face == 20);

        CheckOutcome outcome = D20Check.of(0, 30).resolve(DiceRoller.replaying(seed));

        assertEquals(CheckOutcome.Degree.CRITICAL_SUCCESS, outcome.degree());
        assertTrue(outcome.isSuccess());
        assertTrue(outcome.degree().isCritical());
    }

    @Test
    @DisplayName("a natural 1 fails a DC it would otherwise clear on the modifier alone")
    void naturalOneAlwaysFails() {
        long seed = seedRolling(face -> face == 1);

        CheckOutcome outcome = D20Check.of(20, 5).resolve(DiceRoller.replaying(seed));

        assertEquals(CheckOutcome.Degree.CRITICAL_FAILURE, outcome.degree());
        assertFalse(outcome.isSuccess());
        assertTrue(outcome.degree().isCritical());
    }

    @Test
    void theOutcomeCarriesTheRollSoTheClientCanReplayIt() {
        long seed = seedRolling(face -> face == 20);

        CheckOutcome outcome = D20Check.of(2, 10).resolve(DiceRoller.replaying(seed));

        assertEquals(seed, outcome.roll().seed());
        assertTrue(outcome.roll().isNatural20());
    }

    @Test
    void advantageAndDisadvantageCancelOut() {
        D20Check check = D20Check.of(0, 10).withAdvantage().withDisadvantage();

        assertEquals(RollMode.NORMAL, check.mode());
    }

    @Test
    void stackedAdvantageStaysAdvantage() {
        assertEquals(RollMode.ADVANTAGE, D20Check.of(0, 10).withAdvantage().withAdvantage().mode());
    }

    @Test
    void modeSurvivesIntoTheResolvedRoll() {
        CheckOutcome outcome = D20Check.of(0, 10).withAdvantage().resolve(DiceRoller.replaying(5L));

        assertEquals(RollMode.ADVANTAGE, outcome.roll().mode());
        assertEquals(2, outcome.roll().rolls().size());
    }

    @Test
    void buildersReturnCopiesRatherThanMutating() {
        D20Check base = D20Check.of(1, 10);

        base.plusModifier(5).withAdvantage();

        assertEquals(1, base.modifier());
        assertEquals(RollMode.NORMAL, base.mode());
    }
}
