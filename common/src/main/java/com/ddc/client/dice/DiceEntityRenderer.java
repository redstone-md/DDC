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

    /**
     * The die's skin: one white pixel.
     *
     * <p>The colour is in the vertices, so the texture only has to not tint it. It exists at all
     * because the render type that culls back faces is an entity type, and an entity type wants a
     * texture -- and culling is the whole point: the die used to be drawn with the game's debug box,
     * which does not cull, so every die showed its own far side through its near side. Twenty
     * triangles laid over each other is what a player photographed twice and called broken faces.
     */
    private static final net.minecraft.resources.Identifier TEXTURE =
            com.ddc.DDC.id("textures/entity/dice.png");

    /** Which way is up, for a die that has to show its number to the room. */
    private static final Vector3f UP = new Vector3f(0, 1, 0);

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
        // Lifted by its own radius. The flight rests a die's *centre* on the ground plane, which puts
        // half of every die inside the floor: a solid cut through the middle by a block, which is what
        // a player photographed and called broken faces. A die sits on the ground; it is not buried in
        // it. The d10 is taller than it is wide, so it is lifted by what it actually is.
        float radius = DiceMesh.scaleOf(die.die()) * DiceMesh.restingHeight(die.die());
        pose.translate(flight.x(seconds), flight.y(seconds) + radius, flight.z(seconds));

        pose.mulPose(orientation(flight, die, seconds));
        float scale = DiceMesh.scaleOf(die.die());
        pose.scale(scale, scale, scale);

        // Opaque, and here is why. The render type these are drawn with is the game's own debug box:
        // it does not cull back faces, which is right for a hitbox outline and wrong for a solid. A
        // translucent die shows its own far side through its near side, and twenty triangles laid over
        // each other read as a broken shape -- which is exactly what a player photographed and called
        // broken faces. Opaque, the depth test hides the far side and the solid reads as a solid.
        //
        // The fade is spent on the last moments only, when the die is small on screen and going away.
        int alpha = (int) (255 * fadeOf(seconds));
        // A discarded advantage die is drawn dark rather than faint: it was thrown, it just did not
        // count, and that has to be said in colour rather than in transparency now.
        int[] colour = die.discarded() ? dim(colourFor(die)) : colourFor(die);

        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(TEXTURE),
                (entry, consumer) -> emitDie(entry.pose(), consumer, die, colour, alpha, state.lightCoords));
        pose.popPose();
    }

    /**
     * How the die is turned right now: tumbling in the air, and showing its number once it lands.
     *
     * <p>PRD 3.1 says a die settles on a number, and it never did -- it eased to a stop at whatever
     * angle it happened to be at, and the number the table read in chat had nothing to do with the
     * face pointing at the sky. A die that lands on 3 and shows 17 is a prop, not a die.
     *
     * <p>So the resting orientation is the one that turns the rolled face upward, and the tumble is
     * turned into it: the same easing as before, now with somewhere to arrive. The face is chosen by
     * the number, so the same roll always looks the same, and nothing about the roll itself moves --
     * the server decided it long before this frame.
     */
    private static Quaternionf orientation(DiceThrow flight, DieRoll die, double seconds) {
        double[] rotation = flight.rotation(seconds);
        Quaternionf tumbling = new Quaternionf().rotateXYZ(
                (float) rotation[0], (float) rotation[1], (float) rotation[2]);

        Quaternionf landed = new Quaternionf()
                .rotationTo(DiceMesh.sideFor(die.die(), die.value()), UP)
                .rotateY((float) rotation[1]);

        // Slerp from where it ends to where it is: at rest the ease is zero and this is the landed
        // orientation exactly, which is the half that has to be right.
        return landed.slerp(tumbling, (float) flight.tumbleEase(seconds));
    }

    /**
     * How solid the die is right now: entirely, until the last of its life.
     *
     * <p>{@link DiceThrow#alpha} fades in as well as out, which is a fade a die spends most of its
     * life inside. Without culling that is most of its life spent see-through, so the fade is kept for
     * the end, where it is a die leaving rather than a die arriving.
     */
    private static float fadeOf(double seconds) {
        float alpha = (float) DiceThrow.alpha(seconds);
        return alpha > 0.75f ? 1.0f : alpha / 0.75f;
    }

    /** A colour, darkened: what a die that did not count looks like. */
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
            // Shaded by the facet's own normal, so the solid reads as a solid rather than a blob.
            float shade = 0.55f + 0.45f * Math.max(0, face.normal().dot(LIGHT_DIRECTION));
            int r = (int) (colour[0] * shade);
            int g = (int) (colour[1] * shade);
            int b = (int) (colour[2] * shade);

            vertex(consumer, matrix, face, face.a(), r, g, b, alpha, light);
            vertex(consumer, matrix, face, face.b(), r, g, b, alpha, light);
            vertex(consumer, matrix, face, face.c(), r, g, b, alpha, light);
        }
    }

    /**
     * One corner, with everything a solid entity's vertex format asks for.
     *
     * <p>The texture is one white pixel, so the uv is anywhere in it and the colour is what shows.
     * The normal is the facet's own, and the light is the die's -- a die in a dark cave should be a
     * shape in the dark, not a glowing one.
     */
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
