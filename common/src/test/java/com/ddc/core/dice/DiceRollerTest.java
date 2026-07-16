package com.ddc.core.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DiceRollerTest {

    private static final DiceExpression D20_PLUS_3 = DiceExpression.parse("1d20+3");

    @Test
    @DisplayName("a seed reproduces the same throw, which is what lets clients replay a server roll")
    void replayingTheSameSeedGivesTheSameResult() {
        RollResult first = DiceRoller.replaying(4242L).roll(D20_PLUS_3);
        RollResult second = DiceRoller.replaying(4242L).roll(D20_PLUS_3);

        assertEquals(first, second);
    }

    @Test
    void differentSeedsDiverge() {
        List<Integer> totals = new Random(1).longs(200).mapToObj(seed -> DiceRoller.replaying(seed))
                .map(roller -> roller.roll(D20_PLUS_3).total())
                .distinct()
                .toList();

        assertTrue(totals.size() > 1, "200 seeds all produced the same total: " + totals);
    }

    @Test
    void aRandomRollerCanBeReplayedFromTheSeedItReports() {
        RollResult rolled = DiceRoller.random(new Random(7)).roll(D20_PLUS_3);

        RollResult replayed = DiceRoller.replaying(rolled.seed()).roll(D20_PLUS_3);

        assertEquals(rolled, replayed);
    }

    @Test
    void aRandomRollerDrawsAFreshSeedPerThrow() {
        DiceRoller roller = DiceRoller.random(new Random(7));

        assertNotEquals(roller.roll(D20_PLUS_3).seed(), roller.roll(D20_PLUS_3).seed());
    }

    @Test
    void throwsEveryDieInEveryPool() {
        RollResult result = DiceRoller.replaying(99L).roll("2d6+1d4");

        assertEquals(3, result.rolls().size());
        assertEquals(List.of(Die.D6, Die.D6, Die.D4), result.rolls().stream().map(DieRoll::die).toList());
    }

    @ParameterizedTest
    @EnumSource(Die.class)
    void staysWithinTheDiesFacesAcrossManySeeds(Die die) {
        DiceExpression expression = DiceExpression.of(1, die, 0);

        for (long seed = 0; seed < 500; seed++) {
            int value = DiceRoller.replaying(seed).roll(expression).diceTotal();
            assertTrue(value >= die.minValue() && value <= die.maxValue(),
                    die + " rolled " + value + " on seed " + seed);
        }
    }

    @Test
    void totalIsKeptDicePlusModifier() {
        RollResult result = DiceRoller.replaying(11L).roll(D20_PLUS_3);

        assertEquals(result.diceTotal() + 3, result.total());
    }

    @Test
    void advantageKeepsTheHigherOfTwoDice() {
        RollResult result = DiceRoller.replaying(5L).roll(D20_PLUS_3, RollMode.ADVANTAGE);

        assertEquals(2, result.rolls().size());
        assertEquals(1, result.keptRolls().size());
        int kept = result.keptRolls().getFirst().value();
        int discarded = discardedValue(result);
        assertEquals(Math.max(kept, discarded), kept);
    }

    @Test
    void disadvantageKeepsTheLowerOfTwoDice() {
        RollResult result = DiceRoller.replaying(5L).roll(D20_PLUS_3, RollMode.DISADVANTAGE);

        int kept = result.keptRolls().getFirst().value();
        int discarded = discardedValue(result);
        assertEquals(Math.min(kept, discarded), kept);
    }

    @Test
    @DisplayName("advantage and disadvantage keep opposite dice from the same seed")
    void advantageAndDisadvantageAgreeOnThePairTheyChooseFrom() {
        RollResult advantage = DiceRoller.replaying(5L).roll(D20_PLUS_3, RollMode.ADVANTAGE);
        RollResult disadvantage = DiceRoller.replaying(5L).roll(D20_PLUS_3, RollMode.DISADVANTAGE);

        assertEquals(values(advantage), values(disadvantage), "same seed must throw the same pair");
        assertTrue(advantage.total() >= disadvantage.total());
    }

    @Test
    void discardedDiceAreReportedButDoNotCount() {
        RollResult result = DiceRoller.replaying(5L).roll(D20_PLUS_3, RollMode.ADVANTAGE);

        assertEquals(result.keptRolls().getFirst().value() + 3, result.total());
    }

    @Test
    void aModeOtherThanNormalNeedsASingleD20() {
        DiceRoller roller = DiceRoller.replaying(1L);

        assertThrows(IllegalArgumentException.class, () -> roller.roll("8d6", RollMode.ADVANTAGE));
        assertThrows(IllegalArgumentException.class, () -> roller.roll("2d20", RollMode.DISADVANTAGE));
    }

    @Test
    void describeShowsTheDiceTheModifierAndTheTotal() {
        RollResult result = DiceRoller.replaying(4242L).roll(D20_PLUS_3);

        int natural = result.keptRolls().getFirst().value();
        assertEquals("1d20+3: [" + natural + "] + 3 = " + (natural + 3), result.describe());
    }

    private static int discardedValue(RollResult result) {
        return result.rolls().stream().filter(DieRoll::discarded).findFirst().orElseThrow().value();
    }

    private static List<Integer> values(RollResult result) {
        return result.rolls().stream().map(DieRoll::value).sorted().toList();
    }
}
