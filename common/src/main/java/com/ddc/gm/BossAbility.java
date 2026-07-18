package com.ddc.gm;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * What a possessed monster can do besides hit things: PRD 3.2's "Fire Breath", "Web Spray".
 *
 * <p>A Game Master driving a boss had one verb -- attack -- which makes a dragon a zombie with more
 * hit points. These are the other verbs, and they are deliberately few: three things a monster can do
 * that a player cannot, rather than a spell list, because the GM is meant to be running a scene and
 * not reading a menu.
 *
 * <p>Cooldowns are seconds rather than turns, for the same reason a cantrip's is: this game does not
 * have turns. They are long enough that a boss fight has rhythm -- a GM who could breathe fire every
 * tick would be a GM nobody survives, and a monster that only ever does its big thing is a monster
 * with one note.
 */
public enum BossAbility {

    /**
     * Fire breath: a cone of flame in front of the monster.
     *
     * <p>The cone is drawn as fire and lands as fire, which is the same damage the world already
     * knows: a burning player takes it the way they take a lava bath, and knows what happened to them
     * without being told.
     */
    BREATH("breath", 120) {
        @Override
        void perform(ServerLevel level, Mob mob, ServerPlayer gameMaster) {
            Vec3 look = mob.getLookAngle();
            Vec3 mouth = mob.getEyePosition();
            for (int step = 1; step <= 12; step++) {
                Vec3 at = mouth.add(look.scale(step * 0.8));
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6,
                        step * 0.05, step * 0.05, step * 0.05, 0.02);
            }
            for (LivingEntity caught : inCone(level, mob, 9, 0.55)) {
                caught.igniteForSeconds(5);
                caught.hurt(mob.damageSources().mobAttack(mob), 6);
            }
            level.playSound(null, mob.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT,
                    SoundSource.HOSTILE, 1.2f, 0.8f);
        }
    },

    /** Web spray: the cone is held rather than hurt. A boss that can stop you leaving is a boss. */
    WEB("web", 200) {
        @Override
        void perform(ServerLevel level, Mob mob, ServerPlayer gameMaster) {
            for (LivingEntity caught : inCone(level, mob, 8, 0.5)) {
                caught.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
                caught.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                level.sendParticles(ParticleTypes.ITEM_COBWEB, caught.getX(), caught.getY() + 1,
                        caught.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
            }
            level.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT,
                    SoundSource.HOSTILE, 1.2f, 0.6f);
        }
    },

    /** Roar: everything nearby is thrown back. The thing a boss does when it is surrounded. */
    ROAR("roar", 160) {
        @Override
        void perform(ServerLevel level, Mob mob, ServerPlayer gameMaster) {
            for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class,
                    mob.getBoundingBox().inflate(6),
                    entity -> entity != mob && entity != gameMaster && entity.isAlive())) {
                Vec3 away = caught.position().subtract(mob.position()).normalize();
                caught.push(away.x * 1.4, 0.6, away.z * 1.4);
                caught.hurtMarked = true;
                caught.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            }
            level.sendParticles(ParticleTypes.SONIC_BOOM, mob.getX(), mob.getEyeY(), mob.getZ(),
                    1, 0, 0, 0, 0);
            level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.HOSTILE, 1.4f, 1.0f);
        }
    };

    private final String id;
    private final int cooldownTicks;

    BossAbility(String id, int cooldownTicks) {
        this.id = id;
        this.cooldownTicks = cooldownTicks;
    }

    public String id() {
        return id;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    abstract void perform(ServerLevel level, Mob mob, ServerPlayer gameMaster);

    /** The one a client asked for, if it named one that exists. */
    public static Optional<BossAbility> byId(String key) {
        for (BossAbility ability : values()) {
            if (ability.id.equalsIgnoreCase(key)) {
                return Optional.of(ability);
            }
        }
        return Optional.empty();
    }

    /**
     * Everything in front of the monster, within reach.
     *
     * <p>A cone rather than a sphere: a breath weapon that caught the thing behind you would be a
     * breath weapon nobody could dodge, and dodging is the only thing the party can do about it.
     */
    private static List<LivingEntity> inCone(ServerLevel level, Mob mob, double reach, double spread) {
        Vec3 look = mob.getLookAngle();
        return level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(reach),
                entity -> {
                    if (entity == mob || !entity.isAlive()) {
                        return false;
                    }
                    Vec3 toward = entity.getEyePosition().subtract(mob.getEyePosition()).normalize();
                    return look.dot(toward) > 1 - spread;
                });
    }

    /** The name a client draws on the bar. */
    public String translationKey() {
        return "ddc.ability." + id.toLowerCase(Locale.ROOT);
    }
}
