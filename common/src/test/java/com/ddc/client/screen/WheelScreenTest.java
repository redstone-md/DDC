package com.ddc.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Covers the pointing, which is the half of a radial menu that can be wrong without looking wrong:
 * a wheel that highlights one slice and fires another is a bug you only find mid-fight.
 */
class WheelScreenTest {

    /** Straight up, and clockwise from there: the way a player reads a wheel. */
    @ParameterizedTest(name = "pointing {0},{1} with 4 slices chooses {2}")
    @CsvSource({
            "0,-100,0",     // up
            "100,0,1",      // right
            "0,100,2",      // down
            "-100,0,3",     // left
    })
    void pointsAtTheSliceUnderTheCursor(double dx, double dy, int expected) {
        assertEquals(Optional.of(expected), WheelScreen.pointingAt(dx, dy, 4));
    }

    @Test
    @DisplayName("the middle chooses nothing, so letting go on the spot cancels")
    void theMiddleIsADeadZone() {
        assertTrue(WheelScreen.pointingAt(0, 0, 6).isEmpty());
        assertTrue(WheelScreen.pointingAt(5, 5, 6).isEmpty());
        assertTrue(WheelScreen.pointingAt(0, -100, 6).isPresent());
    }

    @Test
    void anEmptyWheelChoosesNothing() {
        assertTrue(WheelScreen.pointingAt(0, -100, 0).isEmpty());
    }

    @Test
    @DisplayName("every direction lands on exactly one slice, and every slice is reachable")
    void everySliceCanBeChosen() {
        for (int count = 1; count <= 8; count++) {
            boolean[] reached = new boolean[count];
            for (int degree = 0; degree < 360; degree++) {
                double radians = Math.toRadians(degree);
                Optional<Integer> slice = WheelScreen.pointingAt(
                        Math.sin(radians) * 100, -Math.cos(radians) * 100, count);

                assertTrue(slice.isPresent(), count + " slices left " + degree + " degrees pointing at nothing");
                assertTrue(slice.get() >= 0 && slice.get() < count,
                        count + " slices chose " + slice.get());
                reached[slice.get()] = true;
            }
            for (int i = 0; i < count; i++) {
                assertTrue(reached[i], "slice " + i + " of " + count + " could not be pointed at");
            }
        }
    }

    @Test
    @DisplayName("the slice under the cursor is the one whose card is nearest it")
    void theChoiceMatchesTheCardTheEyeSees() {
        int count = 6;
        for (int slice = 0; slice < count; slice++) {
            // The card's own direction, which is where the eye says that slice is.
            double angle = slice * Math.TAU / count;
            double dx = Math.sin(angle) * 100;
            double dy = -Math.cos(angle) * 100;

            assertEquals(Optional.of(slice), WheelScreen.pointingAt(dx, dy, count),
                    "pointing straight at card " + slice + " chose something else");
        }
    }

    @Test
    void aSpellsLabelReadsLikeAName() {
        assertEquals("Magic missile",
                PlayerWheel.name(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ddc", "magic_missile")));
        assertEquals("Fireball",
                PlayerWheel.name(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ddc", "fireball")));
    }

    @Test
    @DisplayName("a ring holds its cards without piling them")
    void theRingFitsItsCards() {
        int cards = 7;
        int card = 100;

        int radius = WheelScreen.ringRadius(cards, card, 400);

        // Every card gets its own share of the ring, and the share is wide enough to hold it. Without
        // this, seven translated options drew on top of each other, which is what a screenshot showed.
        double share = Math.TAU * radius / cards;
        assertTrue(share >= card, "each card has " + share + " pixels of ring for a " + card + " card");
    }

    @Test
    @DisplayName("a small wheel does not collapse into the middle")
    void theRingHasAFloor() {
        assertEquals(76, WheelScreen.ringRadius(2, 40, 400));
    }

    @Test
    @DisplayName("Russian is wider than English, so the ring is bigger")
    void widerCardsPushTheRingOut() {
        assertTrue(WheelScreen.ringRadius(6, 160, 400) > WheelScreen.ringRadius(6, 92, 400),
                "the ring grows with what is written on it");
    }

    @Test
    @DisplayName("the window wins: a card off the edge cannot be pointed at")
    void theWindowIsTheLimit() {
        // Twelve cards of 200 would want a ring far bigger than this window has room for.
        assertEquals(120, WheelScreen.ringRadius(12, 200, 120));
    }

    @Test
    @DisplayName("a tiny window still gets a wheel, crowded or not")
    void aTinyWindowKeepsTheFloor() {
        assertEquals(76, WheelScreen.ringRadius(8, 120, 10));
    }
}
