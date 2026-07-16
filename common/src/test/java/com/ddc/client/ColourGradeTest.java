package com.ddc.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers when the grade ends, which is the part with no renderer in it.
 *
 * <p>Which matrix goes on the screen is a pack file and needs a game to see. That it comes back off
 * does not, and it is the half that matters: a grade that outlived its roll would permanently change
 * the colour of somebody's game.
 */
class ColourGradeTest {

    @Test
    @DisplayName("nothing is graded before a roll")
    void nothingToBeginWith() {
        ColourGrade grade = new ColourGrade();

        assertFalse(grade.isGrading());
        assertTrue(grade.hasExpired(0));
    }

    @Test
    void anUngradedScreenIsAlreadyExpired() {
        ColourGrade grade = new ColourGrade();

        assertTrue(grade.hasExpired(Long.MIN_VALUE + 1));
        assertTrue(grade.hasExpired(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("ticking an ungraded screen leaves the renderer alone")
    void tickingDoesNothingWhenNothingIsGraded() {
        ColourGrade grade = new ColourGrade();

        // No Minecraft here to reach for. The guard is what stops it being reached for at all: a tick
        // on an ungraded screen must return before it touches the renderer, and would throw if it did
        // not.
        grade.tick(Long.MAX_VALUE);

        assertFalse(grade.isGrading());
    }
}
