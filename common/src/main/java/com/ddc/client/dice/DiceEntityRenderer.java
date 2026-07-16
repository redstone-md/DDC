package com.ddc.client.dice;

import com.ddc.core.dice.DiceThrow;
import com.ddc.core.dice.DieRoll;
import com.ddc.dice.DiceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the dice of PRD 4.1 tumbling where they were thrown.
 *
 * <p>Nothing about the tumble is sent or ticked: the entity carries the roll's seed, the flight comes
 * out of {@link DiceThrow}, and every client draws the same throw because they all compute it from
 * the same number. That is ARCHITECTURE.md's deterministic replay, arrived at differently -- the
 * render layer it describes does not exist in Minecraft 26, whose renderer submits geometry to be
 * drawn later rather than drawing it in place.
 *
 * <p>The faces are never recomputed here. They arrive in the {@code ddc:dice_result} payload and are
 * looked up by seed, so the die that lands showing a 17 is the 17 the server rolled. A client that
 * was out of earshot when the roll happened draws nothing rather than inventing numbers.
 */
@Environment(EnvType.CLIENT)
public class DiceEntityRenderer extends EntityRenderer<DiceEntity, DiceRenderState> {

    /** Brass, to match the mod's own art. */
    private static final int[] BRASS = {201, 151, 63};

    /** A natural 20 is gold and a natural 1 is red: the table should read them from across the room. */
    private static final int[] CRIT = {242, 200, 121};
    private static final int[] FUMBLE = {200, 60, 60};

    private static final Vector3f LIGHT_DIRECTION = new Vector3f(-0.4f, 0.8f, 0.45f).normalize();

    public DiceEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public DiceRenderState createRenderState() {
        return new DiceRenderState();
    }

    @Override
    public void extractRenderState(DiceEntity entity, DiceRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.seconds = entity.seconds(partialTick);
        state.result = RollCache.get(entity.seed()).orElse(null);
        state.flights = state.result == null ? List.of() : DiceThrow.forRoll(state.result);
    }

    @Override
    public void submit(DiceRenderState state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.result == null || DiceThrow.isDone(state.seconds)) {
            return;
        }
        for (int index = 0; index < state.flights.size(); index++) {
            submitDie(state, index, pose, collector);
        }
    }

    private static void submitDie(DiceRenderState state, int index, PoseStack pose,
            SubmitNodeCollector collector) {
        DiceThrow flight = state.flights.get(index);
        DieRoll die = state.result.rolls().get(index);
        double seconds = state.seconds;

        pose.pushPose();
        pose.translate(flight.x(seconds), flight.y(seconds), flight.z(seconds));

        double[] rotation = flight.rotation(seconds);
        pose.mulPose(new Quaternionf().rotateXYZ(
                (float) rotation[0], (float) rotation[1], (float) rotation[2]));
        float scale = DiceMesh.scaleOf(die.die());
        pose.scale(scale, scale, scale);

        // A discarded advantage die is drawn faint: it was thrown, it just did not count.
        int alpha = (int) (255 * DiceThrow.alpha(seconds) * (die.discarded() ? 0.35 : 1.0));
        int[] colour = colourFor(die);

        collector.submitCustomGeometry(pose, RenderTypes.debugFilledBox(),
                (entry, consumer) -> emitDie(entry.pose(), consumer, die, colour, alpha));
        pose.popPose();
    }

    private static int[] colourFor(DieRoll die) {
        if (die.isNatural20()) {
            return CRIT;
        }
        return die.isNatural1() ? FUMBLE : BRASS;
    }

    private static void emitDie(Matrix4f matrix, VertexConsumer consumer, DieRoll die,
            int[] colour, int alpha) {
        for (DiceMesh.Face face : DiceMesh.facesOf(die.die())) {
            // Shaded by the facet's own normal, so the solid reads as a solid rather than a blob.
            float shade = 0.55f + 0.45f * Math.max(0, face.normal().dot(LIGHT_DIRECTION));
            int r = (int) (colour[0] * shade);
            int g = (int) (colour[1] * shade);
            int b = (int) (colour[2] * shade);

            vertex(consumer, matrix, face.a(), r, g, b, alpha);
            vertex(consumer, matrix, face.b(), r, g, b, alpha);
            vertex(consumer, matrix, face.c(), r, g, b, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vector3f at,
            int r, int g, int b, int alpha) {
        consumer.addVertex(matrix, at.x(), at.y(), at.z()).setColor(r, g, b, alpha);
    }
}
