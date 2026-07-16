package com.ddc.gm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the timing, which is the part of a slow moment with no server in it.
 *
 * <p>The tick rate itself needs one, and slowing a whole world is exactly the sort of thing that must
 * end: a fanfare that forgot to speed the world back up would be a mod that broke the game it was
 * decorating.
 */
class SlowMotionTest {

    @Test
    @DisplayName("a moment that never started has already expired")
    void nothingIsSlowedToBeginWith() {
        SlowMotion slow = new SlowMotion();

        assertFalse(slow.isSlowed());
        assertTrue(slow.hasExpired(0));
        assertTrue(slow.hasExpired(Long.MAX_VALUE));
    }

    @Test
    void aMomentEndsOnItsOwn() {
        SlowMotion slow = new SlowMotion();
        // play() needs a server to slow, but the clock it starts is this class's own.
        slow.play(null, 1_000);

        // Nothing was slowed, because there was no server: the expiry is what is under test here,
        // and it must be true again the moment the window passes.
        assertTrue(slow.hasExpired(10_000));
    }

    @Test
    void anExpiredMomentStaysExpired() {
        SlowMotion slow = new SlowMotion();

        assertTrue(slow.hasExpired(1));
        assertTrue(slow.hasExpired(2));
    }

    @Test
    @DisplayName("a null server is refused rather than thrown at")
    void aMissingServerIsHarmless() {
        SlowMotion slow = new SlowMotion();

        slow.play(null, 0);

        assertFalse(slow.isSlowed(), "nothing can be slowed without a server to slow");
    }
}
