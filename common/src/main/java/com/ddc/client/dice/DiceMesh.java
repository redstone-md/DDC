package com.ddc.client.dice;

import com.ddc.core.dice.Die;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3f;

/**
 * The shape of a die, as triangles.
 *
 * <p>Built rather than modelled: an icosahedron is twenty identical triangles about a known set of
 * vertices, and generating it is both shorter than a model file and exact. The other dice borrow it
 * for now, scaled -- a d6 that is really a d20 is a lie the eye can see, and is noted in the
 * changelog rather than hidden.
 */
@Environment(EnvType.CLIENT)
public final class DiceMesh {

    private static final float PHI = (float) ((1 + Math.sqrt(5)) / 2);

    /** One triangle of a die, with the normal it is lit by. */
    public record Face(Vector3f a, Vector3f b, Vector3f c, Vector3f normal) {
    }

    private static final List<Face> ICOSAHEDRON = buildIcosahedron();

    private DiceMesh() {
    }

    /** The faces of a die, in the die's own local space, radius 1. */
    public static List<Face> facesOf(Die die) {
        return ICOSAHEDRON;
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

    /**
     * The twenty faces of an icosahedron.
     *
     * <p>The winding of a face found this way cannot be assumed, so each normal is turned outward
     * against the face's own centroid. Without that, half the die would be lit as though from inside
     * and culled away -- the same fault the banner's generator had.
     */
    private static List<Face> buildIcosahedron() {
        List<Vector3f> vertices = new ArrayList<>();
        for (int s1 = -1; s1 <= 1; s1 += 2) {
            for (int s2 = -1; s2 <= 1; s2 += 2) {
                vertices.add(new Vector3f(0, s1, s2 * PHI));
                vertices.add(new Vector3f(s1, s2 * PHI, 0));
                vertices.add(new Vector3f(s1 * PHI, 0, s2));
            }
        }
        float radius = (float) Math.sqrt(1 + PHI * PHI);
        vertices.forEach(vertex -> vertex.div(radius));

        float edge = 2f / radius;
        List<Face> faces = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                for (int k = j + 1; k < vertices.size(); k++) {
                    Vector3f a = vertices.get(i);
                    Vector3f b = vertices.get(j);
                    Vector3f c = vertices.get(k);
                    if (isFace(a, b, c, edge)) {
                        faces.add(new Face(a, b, c, outwardNormal(a, b, c)));
                    }
                }
            }
        }
        return List.copyOf(faces);
    }

    private static boolean isFace(Vector3f a, Vector3f b, Vector3f c, float edge) {
        return near(a.distance(b), edge) && near(b.distance(c), edge) && near(a.distance(c), edge);
    }

    private static boolean near(float value, float target) {
        return Math.abs(value - target) < 1e-4;
    }

    private static Vector3f outwardNormal(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        Vector3f centroid = new Vector3f(a).add(b).add(c).div(3);
        return normal.dot(centroid) < 0 ? normal.negate() : normal;
    }
}
