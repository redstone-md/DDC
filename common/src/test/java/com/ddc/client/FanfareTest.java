package com.ddc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the shake curve, which is the part of the fanfare with no screen in it.
 *
 * <p>The particles, the sound and the word need a client and are not covered here; what is covered is
 * that the shake ends, stays inside its bounds, and never fires for someone else's dice.
 */
class FanfareTest {

    /** A shake with no roll behind it must be still: this is the state a fresh client is in. */
    @Test
    void aFanfareThatNeverFiredDoesNotShake() {
        assertEquals(0f, new Fanfare().shake(0));
        assertEquals(0f, new Fanfare().shake(1_000_000));
    }

    @Test
    @DisplayName("the shake dies away and stops, rather than wobbling forever")
    void theShakeEnds() {
        Fanfare fanfare = firedNatural20();

        boolean shookAtSomePoint = false;
        for (long t = 0; t < 700; t += 10) {
            if (Math.abs(fanfare.shake(t)) > 0.01f) {
                shookAtSomePoint = true;
            }
        }

        assertTrue(shookAtSomePoint, "a natural 20 did not shake at all");
        assertEquals(0f, fanfare.shake(700), "the shake outlived its window");
        assertEquals(0f, fanfare.shake(5_000));
    }

    @Test
    @DisplayName("the shake never throws the camera far enough to lose the crosshair")
    void theShakeStaysSmall() {
        Fanfare fanfare = firedNatural20();

        for (long t = 0; t < 700; t += 1) {
            assertTrue(Math.abs(fanfare.shake(t)) <= 1.6f, "shook " + fanfare.shake(t) + " at " + t);
        }
    }

    @Test
    void theShakeIsStrongestAtTheStart() {
        Fanfare fanfare = firedNatural20();

        float early = peak(fanfare, 0, 200);
        float late = peak(fanfare, 500, 700);

        assertTrue(early > late, "the shake did not die away: " + early + " then " + late);
    }

    private static float peak(Fanfare fanfare, long from, long to) {
        float peak = 0;
        for (long t = from; t < to; t++) {
            peak = Math.max(peak, Math.abs(fanfare.shake(t)));
        }
        return peak;
    }

    /**
     * A fanfare mid-shake, built without touching Minecraft.
     *
     * <p>{@link Fanfare#accept} reaches for the client to play its sound, which a test has none of,
     * so the state it would set is set directly here. The curve is what is under test.
     */
    private static Fanfare firedNatural20() {
        Fanfare fanfare = new Fanfare();
        fanfare.fireForTest(true, 0);
        return fanfare;
    }
}
