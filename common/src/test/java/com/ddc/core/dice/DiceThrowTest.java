package com.ddc.core.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tumble is what every client draws, so it has to be the same everywhere and it has to end.
 * Those are the parts with no screen in them, and they are what these cover.
 */
class DiceThrowTest {

    private static RollResult roll(String notation, long seed) {
        return DiceRoller.replaying(seed).roll(notation);
    }

    private static DiceThrow single(long seed) {
        return DiceThrow.forRoll(roll("1d20", seed)).getFirst();
    }

    @Test
    @DisplayName("the same roll tumbles identically on every client")
    void theFlightIsAFunctionOfTheSeed() {
        List<DiceThrow> first = DiceThrow.forRoll(roll("2d6", 4242L));
        List<DiceThrow> second = DiceThrow.forRoll(roll("2d6", 4242L));

        for (double t = 0; t <= 4.0; t += 0.1) {
            for (int die = 0; die < first.size(); die++) {
                assertEquals(first.get(die).y(t), second.get(die).y(t), 1e-12, "height at " + t);
                assertEquals(first.get(die).x(t), second.get(die).x(t), 1e-12);
                assertEquals(first.get(die).rotation(t)[0], second.get(die).rotation(t)[0], 1e-12);
            }
        }
    }

    /**
     * Sampled across the whole flight rather than at one instant: two dice can be on the floor at the
     * same moment and still have had entirely different throws, and an assertion that missed that
     * would be testing nothing.
     */
    @Test
    void differentRollsTumbleDifferently() {
        boolean differs = false;
        for (double t = 0; t <= DiceThrow.FLIGHT_SECONDS; t += 0.05) {
            if (Math.abs(single(1L).y(t) - single(2L).y(t)) > 1e-6) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "two different rolls threw identical arcs");
    }

    @Test
    void oneThrowPerDie() {
        assertEquals(8, DiceThrow.forRoll(roll("8d6", 1L)).size());
        assertEquals(1, DiceThrow.forRoll(roll("1d20", 1L)).size());
        assertEquals(2, DiceThrow.forRoll(roll("1d20", 1L, RollMode.ADVANTAGE)).size(),
                "advantage throws a second die, and it has to be drawn too");
    }

    private static RollResult roll(String notation, long seed, RollMode mode) {
        return DiceRoller.replaying(seed).roll(notation, mode);
    }

    @Test
    @DisplayName("a die never falls through the floor")
    void theArcStaysAboveTheGround() {
        for (long seed = 0; seed < 50; seed++) {
            DiceThrow die = single(seed);
            for (double t = 0; t <= 4.0; t += 0.02) {
                assertTrue(die.y(t) >= 0, "seed " + seed + " went under the floor at " + t);
            }
        }
    }

    @Test
    @DisplayName("a die comes to rest rather than bouncing forever")
    void theArcSettles() {
        for (long seed = 0; seed < 50; seed++) {
            DiceThrow die = single(seed);

            assertEquals(0.0, die.y(die.landingTime() + 0.01), 0.05,
                    "seed " + seed + " was still in the air after it should have landed");
            assertEquals(0.0, die.y(DiceThrow.FLIGHT_SECONDS), 0.05,
                    "seed " + seed + " was still in the air when its flight ended");
        }
    }

    @Test
    @DisplayName("a die stops spinning exactly when it stops moving, not when the window ends")
    void aDieStopsSpinningOnceItHasLanded() {
        for (long seed = 0; seed < 30; seed++) {
            DiceThrow die = single(seed);
            double landing = die.landingTime();

            assertEquals(0.0, die.tumbleEase(landing), "seed " + seed + " span on after landing");
            assertEquals(0.0, die.tumbleEase(landing + 0.5));
            assertTrue(die.tumbleEase(0) > die.tumbleEase(landing / 2));
            assertTrue(landing <= DiceThrow.FLIGHT_SECONDS,
                    "seed " + seed + " was still bouncing when its flight ended");
            assertTrue(landing > 0.3, "seed " + seed + " landed too fast to see");
        }
    }

    @Test
    void aLandedDieHoldsStillSoItCanBeRead() {
        DiceThrow die = single(7L);
        double[] atRest = die.rotation(die.landingTime());
        double[] later = die.rotation(DiceThrow.FLIGHT_SECONDS + 1.0);

        assertEquals(atRest[0], later[0], 1e-12);
        assertEquals(atRest[1], later[1], 1e-12);
    }

    @Test
    void theDieFadesOutAndThenIsDone() {
        assertEquals(1.0, DiceThrow.alpha(0));
        assertEquals(1.0, DiceThrow.alpha(DiceThrow.FLIGHT_SECONDS));
        assertTrue(DiceThrow.alpha(DiceThrow.FLIGHT_SECONDS + DiceThrow.LINGER_SECONDS - 0.25) < 1.0);
        assertEquals(0.0, DiceThrow.alpha(DiceThrow.FLIGHT_SECONDS + DiceThrow.LINGER_SECONDS));

        assertTrue(DiceThrow.isDone(DiceThrow.FLIGHT_SECONDS + DiceThrow.LINGER_SECONDS));
        assertTrue(!DiceThrow.isDone(0.1));
    }

    @Test
    void progressRunsFromNothingToOneAndStops() {
        assertEquals(0.0, DiceThrow.progress(0));
        assertEquals(1.0, DiceThrow.progress(DiceThrow.FLIGHT_SECONDS + DiceThrow.LINGER_SECONDS));
        assertEquals(1.0, DiceThrow.progress(99));
    }

    @Test
    @DisplayName("several dice of one roll do not land in the same spot")
    void diceOfOneRollAreSpreadOut() {
        List<DiceThrow> dice = DiceThrow.forRoll(roll("4d6", 11L));

        for (int i = 0; i < dice.size(); i++) {
            for (int j = i + 1; j < dice.size(); j++) {
                double dx = dice.get(i).x(2.0) - dice.get(j).x(2.0);
                double dz = dice.get(i).z(2.0) - dice.get(j).z(2.0);
                assertTrue(Math.hypot(dx, dz) > 0.05, "dice " + i + " and " + j + " landed on top of each other");
            }
        }
    }

    @Test
    @DisplayName("the physics cannot change the number that was rolled")
    void theFacesAreTheRollsToDecide() {
        RollResult result = roll("2d6", 4242L);
        int before = result.total();

        DiceThrow.forRoll(result);

        assertEquals(before, result.total(), "building the flight must not touch the roll");
        assertEquals(before, roll("2d6", 4242L).total(), "and must not consume the roll's randomness");
    }
}
