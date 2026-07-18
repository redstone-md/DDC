package com.ddc.client;

import com.ddc.core.dice.RollResult;
import com.ddc.network.DiceResultPayload;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The recent rolls, drawn in the corner of the screen.
 *
 * <p>This is the client's view of the table's dice. It holds no authority: every entry arrives from
 * the server as a seed and is replayed locally, so what it shows is what the server rolled.
 */
@Environment(EnvType.CLIENT)
public final class RollLog {

    /** How many rolls stay on screen. Beyond this the oldest drops off. */
    private static final int MAX_ENTRIES = 5;

    /** How long a roll stays up, in milliseconds. */
    private static final long LIFETIME_MS = 10_000L;

    /** How long an entry takes to fade out at the end of its life. */
    private static final long FADE_MS = 1_000L;

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;

    // These are or-ed with a computed alpha, so they carry only colour.
    private static final int COLOUR_NORMAL = 0xFFFFFF;
    private static final int COLOUR_CRITICAL_SUCCESS = 0xFFD700;
    private static final int COLOUR_CRITICAL_FAILURE = 0xFF5555;

    private final Deque<Entry> entries = new ArrayDeque<>();

    /** Adds a roll the server resolved. The numbers are the server's; this only displays them. */
    public void accept(DiceResultPayload payload, long nowMs) {
        RollResult result = payload.result();
        entries.addLast(new Entry(payload.rollerName() + ": " + result.describe(), colourFor(result), nowMs));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    private static int colourFor(RollResult result) {
        if (result.isNatural20()) {
            return COLOUR_CRITICAL_SUCCESS;
        }
        if (result.isNatural1()) {
            return COLOUR_CRITICAL_FAILURE;
        }
        return COLOUR_NORMAL;
    }

    /** Draws the log in the top-right corner, dropping entries that have timed out. */
    public void render(GuiGraphics graphics, Font font, long nowMs) {
        entries.removeIf(entry -> entry.isExpired(nowMs));
        if (entries.isEmpty()) {
            return;
        }
        int y = MARGIN;
        for (Entry entry : entries) {
            int alpha = entry.alpha(nowMs);
            if (alpha > 0) {
                int x = graphics.guiWidth() - font.width(entry.text) - MARGIN;
                graphics.drawString(font, entry.text, x, y, alpha << 24 | entry.colour);
                y += LINE_HEIGHT;
            }
        }
    }

    /** One line of the log. */
    private record Entry(String text, int colour, long addedAtMs) {

        boolean isExpired(long nowMs) {
            return nowMs - addedAtMs > LIFETIME_MS;
        }

        /** Fully opaque until the last second of its life, then fades to nothing. */
        int alpha(long nowMs) {
            long remaining = LIFETIME_MS - (nowMs - addedAtMs);
            if (remaining >= FADE_MS) {
                return 0xFF;
            }
            return Mth.clamp((int) (0xFF * remaining / FADE_MS), 0, 0xFF);
        }
    }
}
