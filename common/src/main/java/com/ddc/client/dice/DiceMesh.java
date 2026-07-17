package com.ddc.client.dice;

import com.ddc.core.dice.Die;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3f;

/**
 * The shape of a die, as triangles.
 *
 * <p>Built rather than modelled: these solids are defined by their corners, and generating one is
 * both shorter than a model file and exact. Every die used to borrow the d20's icosahedron, scaled --
 * a d6 that was really a shrunken d20 is a lie the eye can see, and PRD 3.1 asks for the real set.
 *
 * <p>One builder makes all of them. A solid is its corners and the directions its faces look; which
 * corners belong to a face is not written down, it is worked out -- the corners on a face are the ones
 * furthest along its normal, which is what a face of a convex solid is. That is why the dodecahedron's
 * pentagons need no table of their own, and why the d4 and the d12 cost no more code than the d20.
 */
@Environment(EnvType.CLIENT)
public final class DiceMesh {

    private static final float PHI = (float) ((1 + Math.sqrt(5)) / 2);

    /** How close a corner must be to a face's plane to be one of its corners. */
    private static final float ON_FACE = 0.001f;

    /** One triangle of a die, with the normal it is lit by. */
    public record Face(Vector3f a, Vector3f b, Vector3f c, Vector3f normal) {
    }

    /**
     * A die's shape: the triangles that draw it, and where each numbered side looks.
     *
     * @param triangles what is drawn
     * @param sides     one outward direction per numbered side. A die's numbering is arbitrary; what
     *                  matters is that a given roll can always turn the same face upward.
     */
    public record Solid(List<Face> triangles, List<Vector3f> sides) {
    }

    private static final Map<Die, Solid> SOLIDS = build();

    private DiceMesh() {
    }

    /** The shape of a die, in the die's own local space, radius 1. */
    public static Solid solidOf(Die die) {
        return SOLIDS.get(die);
    }

    /** The faces of a die, in the die's own local space, radius 1. */
    public static List<Face> facesOf(Die die) {
        return solidOf(die).triangles();
    }

    /**
     * Which way the side showing this number looks.
     *
     * <p>The number picks a side by counting round the solid, so the same roll always shows the same
     * face and two different rolls usually do not. A d100 counts round ten sides, because a d100 is
     * two d10s in every set of dice ever sold.
     */
    public static Vector3f sideFor(Die die, int value) {
        List<Vector3f> sides = solidOf(die).sides();
        return sides.get(Math.floorMod(value - 1, sides.size()));
    }

    /** How big this die is drawn, in blocks. A d20 is the biggest; a d4 the smallest. */
    public static float scaleOf(Die die) {
        return switch (die) {
            case D4 -> 0.09f;
            case D6, D8 -> 0.10f;
            case D10, D12 -> 0.11f;
            case D20, D100 -> 0.12f;
        };
    }

    private static Map<Die, Solid> build() {
        Solid tenSided = bipyramid();
        return Map.of(
                Die.D4, solid(tetrahedronVertices(), tetrahedronNormals()),
                Die.D6, solid(cubeVertices(), axes()),
                Die.D8, solid(octahedronVertices(), cubeVertices()),
                Die.D10, tenSided,
                Die.D100, tenSided,
                // Duals: a dodecahedron's corners are an icosahedron's face centres, and its faces
                // look at the icosahedron's corners. Written down rather than worked out, the two
                // solids drift a couple of degrees out of true and no face finds its corners.
                Die.D12, solid(icosahedronFaceCentres(), icosahedronVertices()),
                Die.D20, solid(icosahedronVertices(), icosahedronFaceCentres()));
    }

    /**
     * Builds a solid from its corners and the directions its faces look.
     *
     * <p>The corners of a face are the ones furthest along its normal. They are sorted around that
     * normal before being cut into triangles, because corners in whatever order they were listed would
     * fan into a star rather than a pentagon.
     */
    private static Solid solid(List<Vector3f> vertices, List<Vector3f> normals) {
        List<Face> triangles = new ArrayList<>();
        List<Vector3f> sides = new ArrayList<>();

        for (Vector3f normal : normals) {
            Vector3f out = new Vector3f(normal).normalize();
            float far = Float.NEGATIVE_INFINITY;
            for (Vector3f vertex : vertices) {
                far = Math.max(far, vertex.dot(out));
            }
            final float furthest = far;
            List<Vector3f> corners = vertices.stream()
                    .filter(vertex -> Math.abs(vertex.dot(out) - furthest) < ON_FACE)
                    .map(Vector3f::new)
                    .toList();
            if (corners.size() < 3) {
                continue;
            }
            triangles.addAll(fan(sortAround(corners, out), out));
            sides.add(out);
        }
        return new Solid(List.copyOf(triangles), List.copyOf(sides));
    }

    /**
     * Sorts a face's corners into the order they go round it, seen from outside.
     *
     * <p>Anticlockwise from outside, which is what the renderer means by a front face. Sorting them
     * by angle alone gets the order right and the direction by luck: the basis this uses to measure
     * the angle can be either way round, so half the faces came out wound backwards and were culled.
     * The die was drawn with holes in it, which is exactly what it looked like.
     */
    private static List<Vector3f> sortAround(List<Vector3f> corners, Vector3f normal) {
        Vector3f centre = new Vector3f();
        corners.forEach(centre::add);
        centre.div(corners.size());

        Vector3f right = new Vector3f(corners.getFirst()).sub(centre).normalize();
        Vector3f up = new Vector3f(normal).cross(right).normalize();

        List<Vector3f> sorted = new ArrayList<>(corners.stream()
                .sorted(Comparator.comparingDouble(corner -> {
                    Vector3f spoke = new Vector3f(corner).sub(centre);
                    return Math.atan2(spoke.dot(up), spoke.dot(right));
                }))
                .toList());

        if (!isAnticlockwise(sorted, normal)) {
            java.util.Collections.reverse(sorted);
        }
        return sorted;
    }

    /** Whether these corners wind anticlockwise about the face's own normal. */
    private static boolean isAnticlockwise(List<Vector3f> corners, Vector3f normal) {
        Vector3f a = corners.get(0);
        Vector3f b = corners.get(1);
        Vector3f c = corners.get(2);
        Vector3f facing = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        return facing.dot(normal) > 0;
    }

    /** Cuts a face into triangles from its first corner. Every face here is convex, so a fan works. */
    private static List<Face> fan(List<Vector3f> corners, Vector3f normal) {
        List<Face> triangles = new ArrayList<>();
        for (int i = 1; i + 1 < corners.size(); i++) {
            triangles.add(new Face(corners.getFirst(), corners.get(i), corners.get(i + 1),
                    new Vector3f(normal)));
        }
        return triangles;
    }

    private static List<Vector3f> tetrahedronVertices() {
        return normalized(List.of(
                new Vector3f(1, 1, 1), new Vector3f(1, -1, -1),
                new Vector3f(-1, 1, -1), new Vector3f(-1, -1, 1)));
    }

    /** A tetrahedron's faces look away from its opposite corners. */
    private static List<Vector3f> tetrahedronNormals() {
        return tetrahedronVertices().stream().map(vertex -> new Vector3f(vertex).negate()).toList();
    }

    private static List<Vector3f> cubeVertices() {
        List<Vector3f> vertices = new ArrayList<>();
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    vertices.add(new Vector3f(x, y, z).normalize());
                }
            }
        }
        return vertices;
    }

    /** The six directions a cube's faces look. */
    private static List<Vector3f> axes() {
        return List.of(new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0),
                new Vector3f(0, -1, 0), new Vector3f(0, 0, 1), new Vector3f(0, 0, -1));
    }

    /** An octahedron's corners are the six axes; its eight faces look towards a cube's corners. */
    private static List<Vector3f> octahedronVertices() {
        return axes().stream().map(Vector3f::new).toList();
    }

    private static List<Vector3f> icosahedronVertices() {
        List<Vector3f> vertices = new ArrayList<>();
        for (int s1 = -1; s1 <= 1; s1 += 2) {
            for (int s2 = -1; s2 <= 1; s2 += 2) {
                vertices.add(new Vector3f(0, s1, s2 * PHI).normalize());
                vertices.add(new Vector3f(s1, s2 * PHI, 0).normalize());
                vertices.add(new Vector3f(s1 * PHI, 0, s2).normalize());
            }
        }
        return vertices;
    }

    /**
     * The twenty directions an icosahedron's faces look, which are also a dodecahedron's corners.
     *
     * <p>Worked out from the icosahedron rather than written down beside it. The textbook coordinates
     * for the two solids describe duals of each other only in one particular orientation, and a pair
     * that is a few degrees out of true is a pair where no face of one finds its corners among the
     * other -- which is exactly what happened: the d12 came out with no faces at all, and the d20 with
     * eight.
     *
     * <p>A face of the icosahedron is three corners a single edge apart. Its centre, pushed out to the
     * sphere, is where that face looks -- and a corner of the solid whose faces look at the
     * icosahedron's corners.
     */
    private static List<Vector3f> icosahedronFaceCentres() {
        List<Vector3f> vertices = icosahedronVertices();
        float edge = shortestDistance(vertices);

        List<Vector3f> centres = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                for (int k = j + 1; k < vertices.size(); k++) {
                    Vector3f a = vertices.get(i);
                    Vector3f b = vertices.get(j);
                    Vector3f c = vertices.get(k);
                    if (isEdge(a, b, edge) && isEdge(b, c, edge) && isEdge(a, c, edge)) {
                        centres.add(new Vector3f(a).add(b).add(c).normalize());
                    }
                }
            }
        }
        return centres;
    }

    /** The length of an edge: the closest two corners of a solid ever get. */
    private static float shortestDistance(List<Vector3f> vertices) {
        float shortest = Float.MAX_VALUE;
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                shortest = Math.min(shortest, vertices.get(i).distance(vertices.get(j)));
            }
        }
        return shortest;
    }

    private static boolean isEdge(Vector3f a, Vector3f b, float edge) {
        return Math.abs(a.distance(b) - edge) < ON_FACE;
    }

    /**
     * The d10, as a pentagonal bipyramid.
     *
     * <p>A real d10 is a trapezohedron, whose faces are kites rather than triangles, and which no
     * arrangement of corners-and-normals builds. This is the honest near miss: ten faces, two points,
     * and it reads as a d10 at the size a die is drawn in a world.
     */
    private static Solid bipyramid() {
        List<Vector3f> equator = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.TAU / 5;
            equator.add(new Vector3f((float) Math.cos(angle), 0, (float) Math.sin(angle)));
        }
        Vector3f top = new Vector3f(0, 1.2f, 0);
        Vector3f bottom = new Vector3f(0, -1.2f, 0);

        List<Face> triangles = new ArrayList<>();
        List<Vector3f> sides = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Vector3f left = equator.get(i);
            Vector3f right = equator.get((i + 1) % 5);
            triangles.add(faceOf(top, left, right));
            sides.add(triangles.getLast().normal());
            triangles.add(faceOf(bottom, right, left));
            sides.add(triangles.getLast().normal());
        }
        return new Solid(List.copyOf(triangles), List.copyOf(sides));
    }

    /**
     * A triangle, turned to face outward.
     *
     * <p>The corners are swapped rather than the normal negated. Negating gives a face that is lit
     * correctly and still wound inward, so the renderer culls it and the die is drawn with a hole
     * where it was -- the normal and the winding have to agree, and the winding is the one the
     * renderer believes.
     */
    private static Face faceOf(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        Vector3f centre = new Vector3f(a).add(b).add(c).div(3);
        if (normal.dot(centre) < 0) {
            return faceOf(a, c, b);
        }
        return new Face(new Vector3f(a), new Vector3f(b), new Vector3f(c), normal);
    }

    private static List<Vector3f> normalized(List<Vector3f> vertices) {
        return vertices.stream().map(vertex -> new Vector3f(vertex).normalize()).toList();
    }
}
