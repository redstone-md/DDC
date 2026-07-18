package com.ddc.client;

import com.ddc.DDC;
import com.ddc.mixin.client.GameRendererInvoker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * PRD 4.4's colour grading: the screen turns gold on a natural 20, and grey on a natural 1.
 *
 * <p>Both effects are the game's own {@code post/color_convolve} shader with a different matrix in
 * the pack file, because a colour grade is a matrix and Minecraft already ships one that multiplies
 * by a matrix. Writing GLSL to do what a vanilla shader does would be a second thing to keep working
 * across updates for no gain.
 *
 * <p>It ends on a timer rather than at the fanfare's convenience, and it refuses to start when
 * something else is already grading the screen -- spectating a creeper, mostly. A player who chose to
 * see the world through a creeper's eyes should not have that taken away because someone rolled well,
 * and clearing it afterwards would leave them looking at a world that had quietly gone normal.
 */
@Environment(EnvType.CLIENT)
public final class ColourGrade {

    /** How long the grade holds. Roughly the shake, so the screen recovers all at once. */
    static final long GRADE_MS = 700;

    private static final ResourceLocation CRITICAL = DDC.id("critical");
    private static final ResourceLocation FUMBLE = DDC.id("fumble");

    private long endsAtMs = Long.MIN_VALUE;
    private boolean grading;

    /**
     * Grades the screen for a roll, if it deserves one.
     *
     * <p>Only the roller's own dice: the grade is on the camera, and a table of five would spend the
     * evening watching each other's screens change colour.
     */
    public void accept(boolean natural20, boolean natural1, boolean mine, long nowMs) {
        if (!mine || !(natural20 || natural1)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.currentPostEffect() != null) {
            return;
        }
        ((GameRendererInvoker) client.gameRenderer).ddc$setPostEffect(natural20 ? CRITICAL : FUMBLE);
        grading = true;
        endsAtMs = nowMs + GRADE_MS;
    }

    /**
     * Takes the grade off once its moment has passed. Called every frame by each loader's hook.
     *
     * <p>The one thing this class must never do is leave. A grade that outlived its roll would be a
     * mod that permanently changed the colour of somebody's game.
     */
    public void tick(long nowMs) {
        if (!grading || !hasExpired(nowMs)) {
            return;
        }
        grading = false;
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer != null) {
            client.gameRenderer.clearPostEffect();
        }
    }

    /** Whether the grade's moment has passed. Package-visible so the timing is testable. */
    boolean hasExpired(long nowMs) {
        return nowMs >= endsAtMs;
    }

    /** Whether the screen is graded right now. */
    boolean isGrading() {
        return grading;
    }
}
