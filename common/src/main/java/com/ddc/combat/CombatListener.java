package com.ddc.combat;

import com.ddc.core.check.CheckOutcome;
import com.ddc.core.dice.DiceRoller;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Puts the d20 between a swing and its damage.
 *
 * <p>Vanilla decides a hit landed the moment the attack connects. DDC asks the SRD's question first:
 * does the attack roll meet the target's armour class? A miss cancels the damage outright and says
 * so with a sweep and a sound, which is PRD 4.2's dodge.
 *
 * <p>The roll is hidden, as the PRD asks: only the attacking player is told the numbers, so the table
 * sees a miss without seeing the maths.
 */
public final class CombatListener {

    /** How many sweep particles a miss throws. */
    private static final int MISS_PARTICLES = 8;

    private final CombatRules rules;
    private final DiceRoller roller;
    private final SneakAttackService sneakAttack;

    public CombatListener(CombatRules rules, DiceRoller roller, SneakAttackService sneakAttack) {
        this.rules = rules;
        this.roller = roller;
        this.sneakAttack = sneakAttack;
    }

    public void register() {
        EntityEvent.LIVING_HURT.register(this::onHurt);
    }

    private EventResult onHurt(LivingEntity target, DamageSource source, float amount) {
        // Only a blow struck by something living gets a roll. An arrow's direct entity is the arrow,
        // and fire, fall and drowning have no attacker at all: ranged and environmental damage are
        // out of scope until DDC has rules for them, and are left to vanilla rather than silently
        // resolved with melee's.
        if (!(source.getDirectEntity() instanceof LivingEntity attacker)) {
            return EventResult.pass();
        }
        if (attacker == target || !(target.level() instanceof ServerLevel level)) {
            return EventResult.pass();
        }
        // Mobs brawling in a cave are not at the table; leave them to vanilla.
        if (!rules.appliesTo(attacker, target)) {
            return EventResult.pass();
        }

        CheckOutcome outcome = rules.attackCheck(attacker, target).resolve(roller);
        if (outcome.isSuccess()) {
            sneakAttack.applyIfEarned(attacker, target, level);
            return EventResult.pass();
        }

        showMiss(level, target);
        tellAttacker(attacker, target, outcome);
        return EventResult.interruptFalse();
    }

    /** The dodge: a sweep across the target and the sound of a swing that found nothing. */
    private static void showMiss(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                MISS_PARTICLES, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * Tells the attacker why they missed, and nobody else. A player who cannot see the roll cannot
     * tell a miss from a bug.
     */
    private static void tellAttacker(LivingEntity attacker, LivingEntity target, CheckOutcome outcome) {
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }
        Component message = Component.literal(
                        "Miss: " + outcome.roll().describe() + " vs AC " + outcome.difficultyClass())
                .withStyle(outcome.degree() == CheckOutcome.Degree.CRITICAL_FAILURE
                        ? ChatFormatting.RED
                        : ChatFormatting.GRAY);
        player.sendSystemMessage(message);
    }
}
