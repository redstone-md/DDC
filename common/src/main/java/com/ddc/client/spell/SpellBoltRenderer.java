package com.ddc.client.spell;

import com.ddc.client.dice.DiceMesh;
import com.ddc.core.dice.Die;
import com.ddc.spell.SpellBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;

/**
 * Draws a spell in flight: a small bright solid, spinning.
 *
 * <p>The octahedron the d8 is built from, shrunk. Not because a spell is a die, but because the mod
 * already builds solids and a second way to draw a lump would be a second thing to keep looking right.
 * It is drawn unlit and bright, which is what makes it read as light rather than as a rock.
 */
@Environment(EnvType.CLIENT)
public class SpellBoltRenderer extends EntityRenderer<SpellBoltEntity, SpellBoltRenderer.State> {

    /** How big a bolt is, in blocks. */
    private static final float SCALE = 0.16f;

    /** How fast it spins, in degrees a tick: fast enough to shimmer, not fast enough to strobe. */
    private static final float SPIN = 14f;

    /** What the client needs to draw one, extracted from the entity for the frame. */
    public static class State extends EntityRenderState {
        int colour;
        float spin;
    }

    public SpellBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpellBoltEntity bolt, State state, float partialTick) {
        super.extractRenderState(bolt, state, partialTick);
        state.colour = bolt.colour();
        state.spin = (bolt.tickCount + partialTick) * SPIN;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0, 0.2, 0);
        pose.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(state.spin))
                .rotateX((float) Math.toRadians(state.spin * 0.6)));
        pose.scale(SCALE, SCALE, SCALE);

        int colour = state.colour;
        collector.submitCustomGeometry(pose, RenderTypes.debugFilledBox(),
                (entry, consumer) -> emit(entry.pose(), consumer, colour));
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }

    private static void emit(Matrix4f matrix, VertexConsumer consumer, int colour) {
        int red = (colour >> 16) & 0xFF;
        int green = (colour >> 8) & 0xFF;
        int blue = colour & 0xFF;
        for (DiceMesh.Face face : DiceMesh.facesOf(Die.D8)) {
            // Every facet at full brightness: a bolt is a light, and a light with a shaded side is a
            // pebble.
            vertex(matrix, consumer, face.a(), red, green, blue);
            vertex(matrix, consumer, face.b(), red, green, blue);
            vertex(matrix, consumer, face.c(), red, green, blue);
        }
    }

    private static void vertex(Matrix4f matrix, VertexConsumer consumer, org.joml.Vector3f at,
            int red, int green, int blue) {
        consumer.addVertex(matrix, at.x(), at.y(), at.z()).setColor(red, green, blue, 255);
    }
}
