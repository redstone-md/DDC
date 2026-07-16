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
                PlayerWheel.name(net.minecraft.resources.Identifier.fromNamespaceAndPath("ddc", "magic_missile")));
        assertEquals("Fireball",
                PlayerWheel.name(net.minecraft.resources.Identifier.fromNamespaceAndPath("ddc", "fireball")));
    }
}
