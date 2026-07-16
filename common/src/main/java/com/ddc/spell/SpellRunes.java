package com.ddc.spell;

import com.ddc.rules.Spell;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * PRD 4.4's spell runes: a ring on the ground under the caster, and one under the target.
 *
 * <p>The document asks for "glowing magic rings and runes expanding on the ground before the spell
 * triggers, warning players and creating anticipation". This is that, drawn with particles rather
 * than with the decal shader it imagines: particles are a thing both loaders and every shader pack
 * already agree about, and a ring of them on the floor reads as a rune from across a room.
 *
 * <p>The ring is sized and coloured by the spell, so a fireball announces itself differently from a
 * cure. It is drawn on the server and sent to everyone nearby, because the warning is for the table,
 * not for the caster's client.
 */
public final class SpellRunes {

    /** How many particles make a ring. Enough to read as a circle, few enough to cost nothing. */
    private static final int RING_PARTICLES = 48;

    /** The smallest a rune gets, in blocks, whatever the spell. */
    private static final double MIN_RADIUS = 0.8;

    /** The largest, so a long-ranged spell does not carpet the room. */
    private static final double MAX_RADIUS = 4.0;

    private SpellRunes() {
    }

    /**
     * Draws the rune a spell casts under a caster and a target.
     *
     * <p>Both ends: the table should see where the magic came from and where it is going.
     */
    public static void draw(ServerLevel level, LivingEntity caster, LivingEntity target, Spell spell) {
        double radius = radiusOf(spell);
        ParticleOptions particle = particleFor(spell);

        ring(level, caster.position(), radius, particle);
        ring(level, target.position(), radius * 0.75, particle);

        level.playSound(null, caster.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.6f, spell.isCantrip() ? 1.6f : 1.0f);
    }

    /**
     * A rune's size comes from the spell's level: a cantrip is a scratch on the floor, a fireball is
     * a circle you would step out of.
     */
    static double radiusOf(Spell spell) {
        return Math.clamp(MIN_RADIUS + spell.level() * 0.4, MIN_RADIUS, MAX_RADIUS);
    }

    /**
     * A spell that deals damage burns; a spell that does not glows. DDC has no damage types on spells
     * yet, so this is as far as the data can be read honestly.
     */
    private static ParticleOptions particleFor(Spell spell) {
        return spell.damageDice().isPresent() ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
    }

    private static void ring(ServerLevel level, Vec3 centre, double radius, ParticleOptions particle) {
        for (int i = 0; i < RING_PARTICLES; i++) {
            double angle = i * Math.TAU / RING_PARTICLES;
            level.sendParticles(particle,
                    centre.x + Math.cos(angle) * radius,
                    centre.y + 0.1,
                    centre.z + Math.sin(angle) * radius,
                    1, 0.0, 0.02, 0.0, 0.0);
        }
    }
}
