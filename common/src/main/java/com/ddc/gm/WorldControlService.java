package com.ddc.gm;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The Game Master's hand on the world: PRD 3.2's day and night, thunder, and holding the table still
 * for a monologue.
 *
 * <p>All of it is what a GM would otherwise do with operator commands. The point of having it here is
 * that it is one permission, checked in one place, and it reads as part of running a game rather than
 * as administering a server.
 */
public final class WorldControlService {

    /** Long enough for a scene, short enough that a forgotten freeze wears off. */
    private static final int FREEZE_SECONDS = 30;

    /** Slowness this deep stops a player where they stand. */
    private static final int ROOTED = 250;

    /** Thunder for five minutes, which outlasts any entrance. */
    private static final int STORM_TICKS = 6000;

    /** What a GM can do to the world. */
    public enum Change {
        DAY("The sun rises."),
        NIGHT("Night falls."),
        STORM("Thunder rolls in."),
        CLEAR("The sky clears."),
        PAUSE_TIME("Time stands still."),
        RESUME_TIME("Time moves again."),
        FREEZE("The party is rooted where they stand."),
        RELEASE("The party can move again.");

        private final String narration;

        Change(String narration) {
            this.narration = narration;
        }

        /** What the GM is told, and what reads well if they pass it on. */
        public String narration() {
            return narration;
        }
    }

    /**
     * Applies a change to the world.
     *
     * @return the reason nothing happened, or empty once it has
     */
    public Optional<String> apply(ServerPlayer gameMaster, Change change) {
        if (!GameMasters.isGameMaster(gameMaster)) {
            return Optional.of("Only a Game Master can do that.");
        }
        ServerLevel level = gameMaster.level();

        switch (change) {
            case DAY -> moveClock(level, ClockTimeMarkers.DAY);
            case NIGHT -> moveClock(level, ClockTimeMarkers.NIGHT);
            case STORM -> storm(level, true);
            case CLEAR -> storm(level, false);
            case PAUSE_TIME -> pauseClock(level, true);
            case RESUME_TIME -> pauseClock(level, false);
            case FREEZE -> freeze(level, true);
            case RELEASE -> freeze(level, false);
        }
        return Optional.empty();
    }

    /**
     * Minecraft 26 keeps time on clocks with named markers rather than a tick number, so day and
     * night are the markers' own names instead of numbers this code would have to know.
     */
    private static void moveClock(ServerLevel level, ResourceKey<ClockTimeMarker> marker) {
        overworldClock(level).ifPresent(clock -> level.clockManager().moveToTimeMarker(clock, marker));
    }

    /** PRD 3.2's "slow down time", in the only honest version of it: stop the clock. */
    private static void pauseClock(ServerLevel level, boolean paused) {
        overworldClock(level).ifPresent(clock -> level.clockManager().setPaused(clock, paused));
    }

    private static Optional<Holder.Reference<WorldClock>> overworldClock(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).get(WorldClocks.OVERWORLD);
    }

    private static void storm(ServerLevel level, boolean stormy) {
        var weather = level.getWeatherData();
        weather.setRaining(stormy);
        weather.setThundering(stormy);
        weather.setRainTime(stormy ? STORM_TICKS : 0);
        weather.setThunderTime(stormy ? STORM_TICKS : 0);
        weather.setClearWeatherTime(stormy ? 0 : STORM_TICKS);
    }

    /**
     * Roots everyone but the GM for a monologue.
     *
     * <p>An effect rather than a hard lock: it wears off on its own, so a GM who is disconnected mid
     * scene does not leave the table frozen forever with no way out. It also leaves players able to
     * look around, which a monologue rather needs.
     */
    private static void freeze(ServerLevel level, boolean frozen) {
        for (ServerPlayer player : level.players()) {
            if (GameMasters.isGameMaster(player)) {
                continue;
            }
            if (frozen) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_SECONDS * 20, ROOTED,
                        false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, FREEZE_SECONDS * 20, 128,
                        false, false, false));
            } else {
                player.removeEffect(MobEffects.SLOWNESS);
                player.removeEffect(MobEffects.JUMP_BOOST);
            }
        }
    }
}
