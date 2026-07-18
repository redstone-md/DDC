package com.ddc.client.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.dice.Die;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The turn that puts a rolled face upward.
 *
 * <p>Written because the dice kept being reported as broken while the geometry kept testing clean,
 * and geometry is only half of what a die is: the other half is the rotation that lands it. A
 * rotation with a NaN in it draws a shape with no shape at all, which is exactly what "broken faces"
 * looks like, and nothing in the mesh tests could have caught one.
 */
class DiceOrientationTest {

    private static final Vector3f UP = new Vector3f(0, 1, 0);

    @Test
    @DisplayName("every face of every die can be turned upward, and none of it is NaN")
    void everyLandingIsFinite() {
        for (Die die : Die.values()) {
            for (int value = 1; value <= die.sides(); value++) {
                Vector3f side = DiceMesh.sideFor(die, value);
                Quaternionf landed = new Quaternionf().rotationTo(side, UP);

                assertTrue(isFinite(landed), die + " value " + value + " lands on a NaN rotation");

                // The point of the whole thing: the face the roll chose ends up pointing at the sky.
                Vector3f turned = landed.transform(new Vector3f(side));
                assertEquals(1.0f, turned.dot(UP), 0.001f,
                        die + " value " + value + " does not land face-up");
            }
        }
    }

    @Test
    @DisplayName("a face that already points straight down still turns cleanly")
    void theOppositeFaceIsNotUndefined() {
        // rotationTo between opposite vectors has no single answer -- any axis will do -- and a
        // library that picks badly picks NaN. Every die has a face pointing down, so this is not a
        // corner case, it is one twentieth of every roll.
        Quaternionf flip = new Quaternionf().rotationTo(new Vector3f(0, -1, 0), UP);

        assertTrue(isFinite(flip), "turning a downward face upward produced a NaN");
        Vector3f turned = flip.transform(new Vector3f(0, -1, 0));
        assertEquals(1.0f, turned.dot(UP), 0.001f);
    }

    @Test
    @DisplayName("the tumble blends into the landing without leaving the rails")
    void theBlendStaysFinite() {
        Vector3f side = DiceMesh.sideFor(Die.D20, 20);
        Quaternionf landed = new Quaternionf().rotationTo(side, UP);
        Quaternionf tumbling = new Quaternionf().rotateXYZ(2.4f, 5.1f, 1.2f);

        for (float ease = 0; ease <= 1.0f; ease += 0.1f) {
            Quaternionf blended = new Quaternionf(landed).slerp(tumbling, ease);
            assertTrue(isFinite(blended), "the blend went NaN at ease " + ease);
        }
    }

    private static boolean isFinite(Quaternionf rotation) {
        return Float.isFinite(rotation.x) && Float.isFinite(rotation.y)
                && Float.isFinite(rotation.z) && Float.isFinite(rotation.w);
    }
}
