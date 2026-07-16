package com.ddc.core.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiceExpressionTest {

    @Test
    void parsesASingleDieWithAModifier() {
        DiceExpression expression = DiceExpression.parse("1d20+3");

        assertEquals(List.of(DicePool.of(1, Die.D20)), expression.pools());
        assertEquals(3, expression.modifier());
    }

    @Test
    void parsesAnOmittedCountAsOneDie() {
        assertEquals(DiceExpression.parse("1d6"), DiceExpression.parse("d6"));
    }

    @Test
    void parsesANegativeModifier() {
        DiceExpression expression = DiceExpression.parse("1d20-1");

        assertEquals(-1, expression.modifier());
        assertEquals(List.of(DicePool.of(1, Die.D20)), expression.pools());
    }

    @Test
    void parsesMixedPoolsAndSumsFlatTerms() {
        DiceExpression expression = DiceExpression.parse("1d6+1d4+3-1");

        assertEquals(List.of(DicePool.of(1, Die.D6), DicePool.of(1, Die.D4)), expression.pools());
        assertEquals(2, expression.modifier());
    }

    @Test
    void ignoresWhitespaceAndCase() {
        assertEquals(DiceExpression.parse("2d6+1"), DiceExpression.parse(" 2D6 + 1 "));
    }

    @Test
    void reportsBoundsAcrossPoolsAndModifier() {
        DiceExpression expression = DiceExpression.parse("8d6");

        assertEquals(8, expression.minTotal());
        assertEquals(48, expression.maxTotal());
        assertEquals(8, expression.diceCount());
    }

    @Test
    void recognisesASingleD20Test() {
        assertTrue(DiceExpression.parse("1d20+5").isSingleD20());
        assertFalse(DiceExpression.parse("2d20").isSingleD20(), "two dice is not a single d20 test");
        assertFalse(DiceExpression.parse("1d12").isSingleD20());
    }

    @Test
    @DisplayName("round trips through parse unchanged")
    void roundTripsThroughToString() {
        for (String notation : List.of("1d20+3", "8d6", "1d6+1d4-2")) {
            assertEquals(notation, DiceExpression.parse(notation).toString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "d", "1d", "20", "+3", "1d20+", "1d20++1", "1d20 3", "abc", "1d20+x"})
    void rejectsMalformedNotation(String notation) {
        assertThrows(IllegalArgumentException.class, () -> DiceExpression.parse(notation));
    }

    @Test
    void rejectsAnUnsupportedDie() {
        assertThrows(IllegalArgumentException.class, () -> DiceExpression.parse("1d7"));
    }

    @Test
    void rejectsASubtractedDicePool() {
        assertThrows(IllegalArgumentException.class, () -> DiceExpression.parse("1d20-1d4"));
    }

    @Test
    void rejectsACountBeyondTheCap() {
        assertThrows(IllegalArgumentException.class, () -> DiceExpression.parse("101d6"));
        assertThrows(IllegalArgumentException.class, () -> DiceExpression.parse("0d6"));
    }

    @Test
    void rejectsAnOverlongExpression() {
        assertThrows(IllegalArgumentException.class, () -> DiceExpression.parse("1d6+".repeat(30) + "1d6"));
    }

    @Test
    void rejectsANumberTooLargeForAnInt() {
        assertThrows(IllegalArgumentException.class, () -> DiceExpression.parse("1d20+99999999999"));
    }
}
