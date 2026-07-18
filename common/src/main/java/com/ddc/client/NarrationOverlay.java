package com.ddc.client;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The Game Master's narration, shown letterboxed over the world.
 *
 * <p>The cinematic moment from the PRD, in its plain form: black bars slide in, the line fades up in
 * the middle of the screen, and everything slides back out. The fog and blur the PRD also describes
 * are not here.
 */
@Environment(EnvType.CLIENT)
public final class NarrationOverlay {

    /** How long a line stays up before it starts to leave, in milliseconds. */
    private static final long HOLD_MS = 7_000L;

    /** How long the bars take to slide in, and later out. */
    private static final long SLIDE_MS = 600L;

    /** How tall the bars are at full extent, as a fraction of screen height. */
    private static final double BAR_FRACTION = 0.12;

    private static final int BAR_COLOUR = 0xFF000000;
    private static final int TEXT_COLOUR = 0xFFFFFF;
    private static final int TEXT_WIDTH = 320;
    private static final int LINE_HEIGHT = 12;

    private String text;
    private long shownAtMs;

    /** Shows a line, replacing whatever was on screen. */
    public void accept(String text, long nowMs) {
        this.text = text;
        this.shownAtMs = nowMs;
    }

    public void render(GuiGraphics graphics, Font font, long nowMs) {
        if (text == null) {
            return;
        }
        long elapsed = nowMs - shownAtMs;
        long total = SLIDE_MS + HOLD_MS + SLIDE_MS;
        if (elapsed >= total) {
            text = null;
            return;
        }

        double extent = barExtent(elapsed, total);
        int height = (int) (graphics.guiHeight() * BAR_FRACTION * extent);
        if (height > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), height, BAR_COLOUR);
            graphics.fill(0, graphics.guiHeight() - height, graphics.guiWidth(), graphics.guiHeight(),
                    BAR_COLOUR);
        }

        int alpha = (int) (0xFF * extent);
        if (alpha <= 0) {
            return;
        }
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(net.minecraft.network.chat.Component.literal(text), TEXT_WIDTH);
        int y = graphics.guiHeight() / 2 - lines.size() * LINE_HEIGHT / 2;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, graphics.guiWidth() / 2, y, alpha << 24 | TEXT_COLOUR);
            y += LINE_HEIGHT;
        }
    }

    /**
     * How far in the bars are, from 0 to 1: sliding in, then held, then sliding back out.
     *
     * <p>Package-private so the timing can be tested without a screen.
     */
    static double barExtent(long elapsed, long total) {
        if (elapsed < 0 || elapsed >= total) {
            return 0.0;
        }
        if (elapsed < SLIDE_MS) {
            return Mth.clamp((double) elapsed / SLIDE_MS, 0.0, 1.0);
        }
        long leavingAt = total - SLIDE_MS;
        if (elapsed >= leavingAt) {
            return Mth.clamp((double) (total - elapsed) / SLIDE_MS, 0.0, 1.0);
        }
        return 1.0;
    }

    /** The total time a line is on screen, in milliseconds. */
    static long totalMs() {
        return SLIDE_MS + HOLD_MS + SLIDE_MS;
    }
}
