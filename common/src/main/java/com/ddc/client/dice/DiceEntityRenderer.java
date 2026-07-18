package com.ddc.client.dice;

import com.ddc.core.dice.DiceThrow;
import com.ddc.core.dice.DieRoll;
import com.ddc.core.dice.RollResult;
import com.ddc.dice.DiceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the dice of PRD 4.1 tumbling where they were thrown.
 *
 * <p>Nothing about the tumble is sent or ticked: the entity carries the roll's seed, the flight comes
 * out of {@link DiceThrow}, and every client draws the same throw because they all compute it from
 * the same number. That is ARCHITECTURE.md's deterministic replay.
 *
 * <p>The faces are never recomputed here. They arrive in the {@code ddc:dice_result} payload and are
 * looked up by seed, so the die that lands showing a 17 is the 17 the server rolled. A client that was
 * out of earshot when the roll happened draws nothing rather than inventing numbers.
 */
@Environment(EnvType.CLIENT)
public class DiceEntityRenderer extends EntityRenderer<DiceEntity> {

    /** Brass, to match the mod's own art. */
    private static final int[] BRASS = {201, 151, 63};

    /** A natural 20 is gold and a natural 1 is red: the table should read them from across the room. */
    private static final int[] CRIT = {242, 200, 121};
    private static final int[] FUMBLE = {200, 60, 60};

    /**
     * The die's skin: one white pixel. The colour is in the vertices, so the texture only has to not
     * tint it; it exists because the culling entity render type wants one, and culling is what stops
     * a die showing its own far side through its near side.
     */
    private static final ResourceLocation TEXTURE = com.ddc.DDC.id("textures/entity/dice.png");

    /** Which way is up, for a die that has to show its number to the room. */
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private static final Vector3f LIGHT_DIRECTION = new Vector3f(-0.4f, 0.8f, 0.45f).normalize();

    public DiceEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DiceEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(DiceEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffer, int light) {
        RollResult result = RollCache.get(entity.seed()).orElse(null);
        double seconds = entity.seconds(partialTick);
        if (result == null || DiceThrow.isDone(seconds)) {
            return;
        }
        List<DiceThrow> flights = DiceThrow.forRoll(result);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(TEXTURE));
        for (int index = 0; index < flights.size(); index++) {
            renderDie(flights.get(index), result.rolls().get(index), seconds, pose, consumer, light);
        }
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }

    private static void renderDie(DiceThrow flight, DieRoll die, double seconds, PoseStack pose,
            VertexConsumer consumer, int light) {
        pose.pushPose();
        // Lifted by its own radius: the flight rests a die's centre on the ground, which would bury
        // its lower half in the block. A die sits on the floor; it is not sunk into it. The d10 is
        // taller than it is wide, so it is lifted by what it actually is.
        float radius = DiceMesh.scaleOf(die.die()) * DiceMesh.restingHeight(die.die());
        pose.translate(flight.x(seconds), flight.y(seconds) + radius, flight.z(seconds));
        pose.mulPose(orientation(flight, die, seconds));
        float scale = DiceMesh.scaleOf(die.die());
        pose.scale(scale, scale, scale);

        int alpha = (int) (255 * fadeOf(seconds));
        // A discarded advantage die is drawn dark rather than faint: it was thrown, it just did not
        // count, and an opaque solid cannot say that in transparency.
        int[] colour = die.discarded() ? dim(colourFor(die)) : colourFor(die);
        emitDie(pose.last().pose(), consumer, die, colour, alpha, light);
        pose.popPose();
    }

    /**
     * How the die is turned right now: tumbling in the air, and showing its number once it lands.
     *
     * <p>The resting orientation turns the rolled face upward, and the tumble slerps into it, so the
     * same roll always looks the same and the face pointing at the sky is the one the server rolled.
     */
    private static Quaternionf orientation(DiceThrow flight, DieRoll die, double seconds) {
        double[] rotation = flight.rotation(seconds);
        Quaternionf tumbling = new Quaternionf().rotateXYZ(
                (float) rotation[0], (float) rotation[1], (float) rotation[2]);
        Quaternionf landed = new Quaternionf()
                .rotationTo(DiceMesh.sideFor(die.die(), die.value()), UP)
                .rotateY((float) rotation[1]);
        return landed.slerp(tumbling, (float) flight.tumbleEase(seconds));
    }

    /** Solid until the last of its life, where the fade is a die leaving rather than arriving. */
    private static float fadeOf(double seconds) {
        float alpha = (float) DiceThrow.alpha(seconds);
        return alpha > 0.75f ? 1.0f : alpha / 0.75f;
    }

    private static int[] dim(int[] colour) {
        return new int[] {colour[0] / 3, colour[1] / 3, colour[2] / 3};
    }

    private static int[] colourFor(DieRoll die) {
        if (die.isNatural20()) {
            return CRIT;
        }
        return die.isNatural1() ? FUMBLE : BRASS;
    }

    private static void emitDie(Matrix4f matrix, VertexConsumer consumer, DieRoll die,
            int[] colour, int alpha, int light) {
        for (DiceMesh.Face face : DiceMesh.facesOf(die.die())) {
            float shade = 0.55f + 0.45f * Math.max(0, face.normal().dot(LIGHT_DIRECTION));
            int r = (int) (colour[0] * shade);
            int g = (int) (colour[1] * shade);
            int b = (int) (colour[2] * shade);
            vertex(consumer, matrix, face, face.a(), r, g, b, alpha, light);
            vertex(consumer, matrix, face, face.b(), r, g, b, alpha, light);
            vertex(consumer, matrix, face, face.c(), r, g, b, alpha, light);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, DiceMesh.Face face,
            Vector3f at, int r, int g, int b, int alpha, int light) {
        consumer.addVertex(matrix, at.x(), at.y(), at.z())
                .setColor(r, g, b, alpha)
                .setUv(0.5f, 0.5f)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(face.normal().x(), face.normal().y(), face.normal().z());
    }
}
