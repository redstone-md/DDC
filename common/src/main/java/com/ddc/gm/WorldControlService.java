package com.ddc.gm;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameRules;
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
        DAY("ddc.world.day"),
        NIGHT("ddc.world.night"),
        STORM("ddc.world.storm"),
        CLEAR("ddc.world.clear"),
        PAUSE_TIME("ddc.world.pause_time"),
        RESUME_TIME("ddc.world.resume_time"),
        FREEZE("ddc.world.freeze"),
        RELEASE("ddc.world.release");

        private final String key;

        Change(String key) {
            this.key = key;
        }

        /** What the GM is told, in their own language, and what reads well if they pass it on. */
        public net.minecraft.network.chat.Component narration() {
            return net.minecraft.network.chat.Component.translatable(key);
        }
    }

    /**
     * Applies a change to the world.
     *
     * @return the reason nothing happened, or empty once it has
     */
    public Optional<net.minecraft.network.chat.Component> apply(ServerPlayer gameMaster, Change change) {
        if (!GameMasters.isGameMaster(gameMaster)) {
            return Optional.of(net.minecraft.network.chat.Component.translatable("ddc.error.not_gm"));
        }
        ServerLevel level = gameMaster.level();

        switch (change) {
            case DAY -> level.setDayTime(1000L);
            case NIGHT -> level.setDayTime(13000L);
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
     * PRD 3.2's "slow down time", in the only honest version of it: stop the day/night cycle.
     *
     * <p>The daylight gamerule rather than the tick-rate manager, because a table wants the sun to
     * hold still for a monologue, not the whole world to stop moving.
     */
    private static void pauseClock(ServerLevel level, boolean paused) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(!paused, level.getServer());
    }

    private static void storm(ServerLevel level, boolean stormy) {
        level.setWeatherParameters(stormy ? 0 : STORM_TICKS, stormy ? STORM_TICKS : 0, stormy, stormy);
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
