package com.ddc.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The table that decides when a character levels.
 *
 * <p>The SRD's numbers are checked against the SRD, because a levelling curve that is subtly wrong is
 * the kind of bug nobody reports and everybody feels.
 */
class LevelTableTest {

    @ParameterizedTest
    @CsvSource({
            "0, 1", "299, 1", "300, 2", "899, 2", "900, 3", "2699, 3", "2700, 4",
            "6500, 5", "14000, 6", "48000, 9", "64000, 10", "355000, 20"})
    @DisplayName("experience buys the SRD's levels")
    void srdThresholds(int experience, int expectedLevel) {
        assertEquals(expectedLevel, LevelTable.SRD.levelFor(experience));
    }

    @Test
    @DisplayName("a character past the last threshold stops at twenty")
    void theTableEnds() {
        assertEquals(Proficiency.MAX_LEVEL, LevelTable.SRD.levelFor(Integer.MAX_VALUE));
        assertTrue(LevelTable.SRD.remainingTo(400_000).isEmpty(), "there is nothing left to reach");
    }

    @Test
    void remainingCountsDownToTheNextLevel() {
        assertEquals(OptionalInt.of(300), LevelTable.SRD.remainingTo(0));
        assertEquals(OptionalInt.of(1), LevelTable.SRD.remainingTo(299));
        assertEquals(OptionalInt.of(600), LevelTable.SRD.remainingTo(300));
    }

    @Test
    void theFirstLevelIsFree() {
        assertEquals(0, LevelTable.SRD.experienceFor(1));
        assertEquals(300, LevelTable.SRD.experienceFor(2));
        assertEquals(355_000, LevelTable.SRD.experienceFor(Proficiency.MAX_LEVEL));
    }

    @Test
    @DisplayName("a pack can level a party faster than the SRD does")
    void aPackMayWriteItsOwnPace() {
        LevelTable brisk = new LevelTable(IntStream.rangeClosed(1, Proficiency.MAX_LEVEL - 1)
                .map(step -> step * 10).boxed().toList());

        assertEquals(1, brisk.levelFor(9));
        assertEquals(2, brisk.levelFor(10));
        assertEquals(Proficiency.MAX_LEVEL, brisk.levelFor(190));
    }

    @Test
    @DisplayName("a table that goes backwards is refused")
    void thresholdsMustClimb() {
        List<Integer> backwards = new java.util.ArrayList<>(LevelTable.SRD.thresholds());
        backwards.set(5, 1);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> new LevelTable(backwards));
        assertTrue(thrown.getMessage().contains("more experience"), thrown.getMessage());
    }

    @Test
    @DisplayName("a table that is the wrong length is refused, not padded")
    void thresholdsMustCoverEveryLevel() {
        assertThrows(IllegalArgumentException.class, () -> new LevelTable(List.of(300, 900)));
        assertThrows(IllegalArgumentException.class, () -> new LevelTable(List.of()));
    }

    @Test
    void aTableCannotBeChangedAfterwards() {
        List<Integer> mutable = new java.util.ArrayList<>(LevelTable.SRD.thresholds());
        LevelTable table = new LevelTable(mutable);
        mutable.set(0, 1);

        assertEquals(300, table.experienceFor(2));
        assertFalse(table.thresholds() == mutable);
    }
}
