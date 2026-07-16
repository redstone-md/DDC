package com.ddc.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AbilityTest {

    @ParameterizedTest(name = "score {0} gives modifier {1}")
    @CsvSource({"1,-5", "3,-4", "8,-1", "9,-1", "10,0", "11,0", "12,1", "16,3", "20,5", "30,10"})
    void appliesTheSrdModifierTable(int score, int expected) {
        assertEquals(expected, Ability.modifierFor(score));
    }

    @Test
    @DisplayName("scores below 10 floor rather than truncate toward zero")
    void roundsDownForOddLowScores() {
        assertEquals(-1, Ability.modifierFor(9));
        assertEquals(-2, Ability.modifierFor(7));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 31, Integer.MAX_VALUE})
    void rejectsScoresOutsideTheLegalRange(int score) {
        assertThrows(IllegalArgumentException.class, () -> Ability.modifierFor(score));
    }

    @ParameterizedTest
    @EnumSource(Ability.class)
    void resolvesEveryAbilityFromItsIdAndAbbreviation(Ability ability) {
        assertEquals(Optional.of(ability), Ability.byId(ability.id()));
        assertEquals(Optional.of(ability), Ability.byId(ability.abbreviation()));
        assertEquals(Optional.of(ability), Ability.byId(ability.id().toUpperCase(java.util.Locale.ROOT)));
        assertEquals(Optional.of(ability), Ability.byId(" " + ability.id() + " "));
    }

    @Test
    void returnsEmptyForAnUnknownKeySoTheLoaderCanNameTheBadFile() {
        assertTrue(Ability.byId("luck").isEmpty());
        assertTrue(Ability.byId("").isEmpty());
        assertTrue(Ability.byId(null).isEmpty());
    }
}
