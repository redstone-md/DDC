package com.ddc.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProficiencyTest {

    @ParameterizedTest(name = "level {0} grants +{1}")
    @CsvSource({"1,2", "4,2", "5,3", "8,3", "9,4", "12,4", "13,5", "16,5", "17,6", "20,6"})
    void reproducesTheSrdProgressionTable(int level, int expected) {
        assertEquals(expected, Proficiency.bonusAtLevel(level));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 21})
    void rejectsLevelsOutsideOneToTwenty(int level) {
        assertThrows(IllegalArgumentException.class, () -> Proficiency.bonusAtLevel(level));
    }
}
