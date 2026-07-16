package com.ddc.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ddc.core.dice.Die;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HitDieTest {

    private static final HitDie D10 = new HitDie(Die.D10);

    @Test
    @DisplayName("first level takes the whole die plus Constitution")
    void firstLevelIsMaximised() {
        assertEquals(12, D10.firstLevelHitPoints(2));
        assertEquals(10, D10.firstLevelHitPoints(0));
    }

    @Test
    @DisplayName("later levels take the die's fixed average plus Constitution")
    void laterLevelsUseTheAverage() {
        assertEquals(8, D10.laterLevelHitPoints(2));
        assertEquals(6, D10.laterLevelHitPoints(0));
    }

    @ParameterizedTest(name = "a level {0} fighter with +{1} Constitution has {2} hit points")
    @CsvSource({"1,2,12", "2,2,20", "5,2,44", "20,2,164", "1,0,10", "5,0,34"})
    void buildsMaxHitPointsFromLevelAndConstitution(int level, int constitution, int expected) {
        assertEquals(expected, D10.maxHitPoints(level, constitution));
    }

    @Test
    @DisplayName("the PRD's level 5 fighter with 14 Constitution has 44 hit points")
    void matchesThePrdFighterSheet() {
        int constitution = Ability.modifierFor(14);

        assertEquals(44, D10.maxHitPoints(5, constitution));
    }

    @Test
    void aCrushingConstitutionPenaltyStillLeavesACharacterAlive() {
        assertEquals(1, new HitDie(Die.D6).firstLevelHitPoints(-10));
        assertEquals(5, new HitDie(Die.D6).maxHitPoints(5, -10), "one hit point per level, never zero");
    }

    @Test
    void rejectsALevelOutsideTheProgression() {
        assertThrows(IllegalArgumentException.class, () -> D10.maxHitPoints(0, 0));
        assertThrows(IllegalArgumentException.class, () -> D10.maxHitPoints(21, 0));
    }
}
