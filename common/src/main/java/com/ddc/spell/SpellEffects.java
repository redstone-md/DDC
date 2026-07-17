package com.ddc.spell;

import com.ddc.rules.Spell;
import java.util.Locale;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What a spell looks and sounds like: the gathering, the throw, and the landing.
 *
 * <p>Written from scratch, and deliberately. Iron's Spells 'n Spellbooks is the mod a player pointed
 * at, and it is All Rights Reserved: its code and its assets are its author's and stay there. What is
 * borrowed is the observation, which is not anyone's property -- that a spell needs a moment of
 * gathering, a thing that crosses the room, and a burst where it lands. Everything here is DDC's own
 * code over Minecraft's own particles.
 *
 * <p>Colour comes from the school, so a fireball and a sacred flame are not the same magic. Vanilla's
 * dust particle takes any colour, which is why the effects are dust rather than a texture: a texture
 * would be an asset to draw, and the game already has one that can be any colour we like.
 */
public final class SpellEffects {

    /** How big a mote is. Small: a spell is made of sparks, not confetti. */
    private static final float MOTE = 0.8f;

    private SpellEffects() {
    }

    /**
     * The gathering: light collecting at the caster's hand while a spell with a casting time runs.
     *
     * <p>PRD 4.4 asks the runes to warn the table before a spell lands. The runes are on the ground
     * at the target; this is the other half, at the caster, so the party can see who is doing it.
     */
    public static void gather(ServerLevel level, ServerPlayer caster, Spell spell, float progress) {
        Vec3 hand = caster.getEyePosition()
                .add(caster.getLookAngle().scale(0.6))
                .add(0, -0.25, 0);
        // Tighter as it goes: light being drawn in reads as something about to happen, where a
        // constant sparkle reads as decoration.
        double spread = 0.6 * (1 - progress) + 0.05;
        level.sendParticles(dust(spell), hand.x, hand.y, hand.z, 6, spread, spread, spread, 0.0);
    }

    /** The word: a sound at the caster, so a spell cast behind you is a spell you heard. */
    public static void speak(ServerLevel level, ServerPlayer caster, Spell spell) {
        level.playSound(null, caster.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 0.7f, spell.isCantrip() ? 1.6f : 1.2f);
    }

    /**
     * The landing: a burst on the target, and a bang.
     *
     * <p>An area spell bursts wider and louder, because a fireball that landed like a fire bolt would
     * be a fireball nobody noticed going off.
     */
    public static void land(ServerLevel level, Spell spell, LivingEntity target) {
        Vec3 at = target.getEyePosition().add(0, -0.3, 0);
        double radius = spell.isAreaOfEffect() ? spell.areaInBlocks() : 0.4;
        int motes = spell.isAreaOfEffect() ? 120 : 24;

        level.sendParticles(dust(spell), at.x, at.y, at.z, motes, radius / 2, radius / 3, radius / 2, 0.02);
        if (spell.isAreaOfEffect()) {
            // The shape of the thing: a ring on the ground says how far it reached, which is the one
            // fact a party needs from a fireball and cannot get from a flash.
            ring(level, spell, target, radius);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 0.9f, 1.4f);
        } else {
            level.playSound(null, target.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL,
                    SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    /** Draws the edge of an area spell on the floor. */
    private static void ring(ServerLevel level, Spell spell, LivingEntity target, double radius) {
        for (int step = 0; step < 40; step++) {
            double angle = step * Math.TAU / 40;
            level.sendParticles(ParticleTypes.FLAME,
                    target.getX() + Math.cos(angle) * radius,
                    target.getY() + 0.1,
                    target.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.02, 0.0, 0.01);
        }
    }

    /** A mote in the school's own colour. */
    private static DustParticleOptions dust(Spell spell) {
        return new DustParticleOptions(colourOf(spell), MOTE);
    }

    /**
     * What colour a school of magic is.
     *
     * <p>The SRD's eight schools. A pack may write any word it likes and an unknown one gets the mod's
     * brass rather than a crash, because a pack inventing a school is a pack doing what packs are for.
     */
    public static int colourOf(Spell spell) {
        return switch (spell.school().toLowerCase(Locale.ROOT)) {
            case "evocation" -> 0xFF7B29;
            case "necromancy" -> 0x6B3FA0;
            case "abjuration" -> 0x4FA3E3;
            case "conjuration" -> 0x54C46A;
            case "enchantment" -> 0xE86AA6;
            case "divination" -> 0xF2E27A;
            case "illusion" -> 0x9B7BE8;
            case "transmutation" -> 0x3FB6A8;
            default -> 0xC9973F;
        };
    }
}
