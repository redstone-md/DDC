package com.ddc.client;

import com.ddc.core.dice.RollResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * PRD 4.4's fanfare: what the screen does when the die comes up 20, or 1.
 *
 * <p>A natural 20 shakes the camera, throws gold, and sounds a note. A natural 1 puffs ash and lands
 * a comic thud. Both are short: the moment is the roll, and a five-second cutscene would be in the
 * way of the game the table is playing.
 *
 * <p>The rest of the moment is elsewhere, because it belongs elsewhere: {@link ColourGrade} owns the
 * screen's colour and {@code SlowMotion} owns the world's speed, which is the server's to change and
 * so cannot live in a client class at all.
 */
@Environment(EnvType.CLIENT)
public final class Fanfare {

    /** How long the screen shakes, in milliseconds. */
    private static final long SHAKE_MS = 700;

    /** How far it shakes, in degrees. Enough to feel; not enough to lose the crosshair. */
    private static final float SHAKE_DEGREES = 1.6f;

    /** How long the word hangs there. */
    private static final long TEXT_MS = 1500;

    private static final int GOLD = 0xFFD700;
    private static final int RED = 0xFF5555;

    private long startedAtMs = Long.MIN_VALUE;
    private boolean critical;
    private String word = "";

    /**
     * Reacts to a roll, if it deserves it.
     *
     * <p>Only the roller's own dice set it off. Everyone at the table sees a natural 20 land gold and
     * hears about it in the log; a screen that shook every time anyone rolled would be unplayable in
     * a party of five.
     */
    public void accept(RollResult result, boolean mine, long nowMs) {
        if (!mine || !(result.isNatural20() || result.isNatural1())) {
            return;
        }
        startedAtMs = nowMs;
        critical = result.isNatural20();
        word = critical ? "NATURAL 20" : "NATURAL 1";

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            play(client.player, critical);
        }
    }

    private static void play(Player player, boolean critical) {
        // Some sound constants are plain events and some are registry holders in 26.x; unwrap the
        // holder rather than mixing the two in a conditional.
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                critical ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_BREAK.value(),
                SoundSource.PLAYERS, 1.0f, critical ? 1.2f : 0.7f, false);

        for (int i = 0; i < 40; i++) {
            double angle = i * Math.TAU / 40;
            player.level().addParticle(
                    critical ? ParticleTypes.END_ROD : ParticleTypes.ASH,
                    player.getX() + Math.cos(angle) * 0.8,
                    player.getY() + 1.0,
                    player.getZ() + Math.sin(angle) * 0.8,
                    Math.cos(angle) * 0.1, critical ? 0.25 : -0.05, Math.sin(angle) * 0.1);
        }
    }

    /**
     * Sets off the fanfare without the sound and particles a client would play.
     *
     * <p>Exists for the tests: {@link #accept} reaches for {@link Minecraft}, which a unit test has
     * none of, and the curve is worth covering without one.
     */
    void fireForTest(boolean natural20, long nowMs) {
        startedAtMs = nowMs;
        critical = natural20;
        word = natural20 ? "NATURAL 20" : "NATURAL 1";
    }

    /**
     * How far the camera is thrown right now, in degrees.
     *
     * <p>High-frequency noise that dies away, so the shake lands hard and lets go, rather than
     * wobbling to a halt. Package-visible so the curve can be tested without a screen.
     */
    float shake(long nowMs) {
        long elapsed = nowMs - startedAtMs;
        if (!critical || elapsed < 0 || elapsed >= SHAKE_MS) {
            return 0;
        }
        float remaining = 1 - (float) elapsed / SHAKE_MS;
        return SHAKE_DEGREES * remaining * remaining * Mth.sin(elapsed * 0.09f);
    }

    /** Applies the shake to the camera. Called by each loader's own render hook. */
    public void applyShake(long nowMs) {
        float shake = shake(nowMs);
        if (shake == 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            // The camera follows the player's head, so the head is what moves. It is put back by the
            // next frame's shake being smaller, and by the shake ending at zero.
            client.player.setYRot(client.player.getYRot() + shake * 0.15f);
        }
    }

    /** Draws the word across the middle of the screen while it lasts. */
    public void render(GuiGraphics graphics, Font font, long nowMs) {
        long elapsed = nowMs - startedAtMs;
        if (elapsed < 0 || elapsed >= TEXT_MS) {
            return;
        }
        int alpha = (int) (255 * (1 - (double) elapsed / TEXT_MS));
        if (alpha <= 0) {
            return;
        }
        graphics.drawCenteredString(font, Component.literal(word),
                graphics.guiWidth() / 2, graphics.guiHeight() / 3,
                alpha << 24 | (critical ? GOLD : RED));
    }
}
