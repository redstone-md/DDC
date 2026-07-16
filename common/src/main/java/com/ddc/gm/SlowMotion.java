package com.ddc.gm;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;

/**
 * PRD 4.4's slow motion, for the moment a natural 20 lands.
 *
 * <p>The document imagines it as a client trick: "drops the rendering frame interpolation speed". It
 * is not one here. Minecraft can be told to run its world slower, which is what {@code /tick rate}
 * does, and doing it that way means the arrow really does crawl -- every player at the table sees the
 * same moment, rather than one client pretending while the world carries on without it.
 *
 * <p>Brief on purpose. A table waiting out a cinematic every time someone rolls well would stop
 * rolling well on purpose.
 */
public final class SlowMotion {

    /** How slow, in ticks per second. Vanilla is 20; this is the world at a quarter speed. */
    private static final float SLOW_TICK_RATE = 5.0f;

    private static final float NORMAL_TICK_RATE = 20.0f;

    /** How long it lasts, in real milliseconds rather than ticks -- the world's ticks are slowed. */
    private static final long DURATION_MS = 900;

    private long endsAtMs = Long.MIN_VALUE;
    private boolean slowed;

    /** Watches for the end of a slow moment. Called once from the shared bootstrap. */
    public void register() {
        TickEvent.SERVER_POST.register(this::tick);
    }

    /**
     * Slows the world for a moment.
     *
     * <p>Refuses when a Game Master has stopped the clock themselves: a fanfare must never quietly
     * undo something the GM did on purpose.
     */
    public void play(MinecraftServer server, long nowMs) {
        if (server == null || server.tickRateManager().isFrozen()) {
            return;
        }
        endsAtMs = nowMs + DURATION_MS;
        if (!slowed) {
            slowed = true;
            server.tickRateManager().setTickRate(SLOW_TICK_RATE);
        }
    }

    private void tick(MinecraftServer server) {
        if (slowed && System.currentTimeMillis() >= endsAtMs) {
            slowed = false;
            server.tickRateManager().setTickRate(NORMAL_TICK_RATE);
        }
    }

    /** Whether the world is crawling right now. */
    public boolean isSlowed() {
        return slowed;
    }

    /**
     * Whether the moment has run out at this instant.
     *
     * <p>Package-visible so the timing can be tested without a server.
     */
    boolean hasExpired(long nowMs) {
        return nowMs >= endsAtMs;
    }
}
