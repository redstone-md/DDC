package com.ddc.client.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.dice.Die;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The solids, which every die used to borrow from the d20.
 *
 * <p>A shape with the wrong number of faces is a die with the wrong number of sides, and that is the
 * kind of thing nobody notices until a d6 has twenty faces on it -- which it did, for eight releases.
 */
class DiceMeshTest {

    @ParameterizedTest(name = "a {0} has {1} sides")
    @CsvSource({"D4,4", "D6,6", "D8,8", "D10,10", "D12,12", "D20,20", "D100,10"})
    @DisplayName("every die has as many sides as its name says")
    void everyDieHasItsSides(Die die, int sides) {
        assertEquals(sides, DiceMesh.solidOf(die).sides().size());
    }

    @ParameterizedTest(name = "a {0} draws {1} triangles")
    @CsvSource({"D4,4", "D6,12", "D8,8", "D12,36", "D20,20"})
    @DisplayName("faces are cut into the right number of triangles")
    void facesAreCutIntoTriangles(Die die, int triangles) {
        // A square is two triangles and a pentagon is three, so a d6 is twelve and a d12 is thirty-six.
        assertEquals(triangles, DiceMesh.facesOf(die).size());
    }

    @Test
    @DisplayName("every side looks away from the middle of the die")
    void everyNormalPointsOutward() {
        for (Die die : Die.values()) {
            for (DiceMesh.Face face : DiceMesh.facesOf(die)) {
                Vector3f centre = new Vector3f(face.a()).add(face.b()).add(face.c()).div(3);
                assertTrue(face.normal().dot(centre) > 0,
                        die + " has a face lit from inside, which is how half a die goes missing");
            }
        }
    }

    @Test
    @DisplayName("a roll always turns up the same side, and different rolls usually do not")
    void aNumberPicksASide() {
        assertEquals(DiceMesh.sideFor(Die.D20, 20), DiceMesh.sideFor(Die.D20, 20));
        assertNotEquals(DiceMesh.sideFor(Die.D20, 20), DiceMesh.sideFor(Die.D20, 1));
        assertNotEquals(DiceMesh.sideFor(Die.D6, 3), DiceMesh.sideFor(Die.D6, 4));
    }

    @Test
    @DisplayName("a d100 counts round its ten sides, because a d100 is two d10s")
    void aHundredWrapsRoundTen() {
        assertEquals(DiceMesh.sideFor(Die.D100, 1), DiceMesh.sideFor(Die.D100, 11));
        assertEquals(DiceMesh.sideFor(Die.D100, 10), DiceMesh.sideFor(Die.D100, 100));
    }

    @Test
    @DisplayName("a d6 is not a shrunken d20 any more")
    void theDiceAreNotAllTheSameShape() {
        assertNotEquals(DiceMesh.facesOf(Die.D6).size(), DiceMesh.facesOf(Die.D20).size());
        assertNotEquals(DiceMesh.facesOf(Die.D4).size(), DiceMesh.facesOf(Die.D8).size());
    }

    @Test
    @DisplayName("every corner sits on the die's own surface")
    void everyCornerIsOnTheSphere() {
        for (Die die : Die.values()) {
            if (die == Die.D10 || die == Die.D100) {
                // The bipyramid is deliberately taller than it is wide: it has points, not corners on
                // a ball.
                continue;
            }
            for (DiceMesh.Face face : DiceMesh.facesOf(die)) {
                assertEquals(1.0f, face.a().length(), 0.001f, die + " has a corner off its own radius");
            }
        }
    }
}
