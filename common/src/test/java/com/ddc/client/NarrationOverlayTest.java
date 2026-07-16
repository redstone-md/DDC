package com.ddc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the narration timing curve, which is the part of the overlay that has no screen in it.
 */
class NarrationOverlayTest {

    private static final long TOTAL = NarrationOverlay.totalMs();

    @Test
    void startsClosedAndSlidesIn() {
        assertEquals(0.0, NarrationOverlay.barExtent(0, TOTAL));
        assertTrue(NarrationOverlay.barExtent(300, TOTAL) > 0.0);
        assertTrue(NarrationOverlay.barExtent(300, TOTAL) < 1.0);
    }

    @Test
    @DisplayName("holds fully open for the whole middle of its life")
    void holdsOpen() {
        assertEquals(1.0, NarrationOverlay.barExtent(TOTAL / 2, TOTAL));
    }

    @Test
    void slidesBackOutAndEndsClosed() {
        assertTrue(NarrationOverlay.barExtent(TOTAL - 300, TOTAL) < 1.0);
        assertTrue(NarrationOverlay.barExtent(TOTAL - 300, TOTAL) > 0.0);
        assertEquals(0.0, NarrationOverlay.barExtent(TOTAL, TOTAL));
    }

    @Test
    void staysClosedOnceItsTimeHasPassed() {
        assertEquals(0.0, NarrationOverlay.barExtent(TOTAL + 5_000, TOTAL));
    }

    @Test
    void neverLeavesTheZeroToOneRange() {
        for (long elapsed = -500; elapsed <= TOTAL + 500; elapsed += 50) {
            double extent = NarrationOverlay.barExtent(elapsed, TOTAL);
            assertTrue(extent >= 0.0 && extent <= 1.0, "extent " + extent + " at " + elapsed);
        }
    }
}
