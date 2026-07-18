package com.ddc.combat;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.RollResult;
import com.ddc.dice.DiceRollService;
import com.ddc.rules.CharacterClass;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The rogue's sneak attack, once a hit has landed.
 *
 * <p>The extra dice arrive as a second helping of damage rather than as a bigger first one. The event
 * DDC hooks reports a hit's damage but cannot change it, so the choice is between this and a mixin
 * into the damage pipeline, and one extra hurt is far less likely to fight other mods than a rewrite
 * of how damage is calculated.
 *
 * <p>That second hurt has to step over Minecraft's invulnerability window, which exists to stop a
 * mob being hit twice by one swing. Here it is one swing, deliberately dealing its damage in two
 * parts, so the window is cleared for exactly that call and put back.
 */
public final class SneakAttackService {

    private static final int PARTICLES = 12;

    private final CharacterService characters;
    private final DiceRollService rolls;

    public SneakAttackService(CharacterService characters, DiceRollService rolls) {
        this.characters = characters;
        this.rolls = rolls;
    }

    /** Adds the sneak attack's dice when the attacker is a rogue who has earned them. */
    public void applyIfEarned(LivingEntity attacker, LivingEntity target, ServerLevel level) {
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }
        CharacterSheet sheet = characters.get(player);
        Optional<CharacterClass> definition = characters.definitionFor(sheet);
        if (definition.isEmpty()) {
            return;
        }
        Optional<DiceExpression> dice = SneakAttackRules.diceFor(sheet, definition.get());
        if (dice.isEmpty() || !SneakAttackRules.applies(attacker, target, level)) {
            return;
        }

        RollResult damage = rolls.rollPublic(player, dice.get(), com.ddc.core.dice.RollMode.NORMAL);
        deal(player, target, level, damage.total());
        announce(player, damage);
    }

    private static void deal(ServerPlayer attacker, LivingEntity target, ServerLevel level, int damage) {
        if (damage <= 0) {
            return;
        }
        int invulnerable = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            target.hurt(level.damageSources().playerAttack(attacker), damage);
        } finally {
            // Put the window back rather than leaving the target open to everything else this tick.
            target.invulnerableTime = Math.max(target.invulnerableTime, invulnerable);
        }
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                PARTICLES, 0.3, 0.3, 0.3, 0.1);
    }

    private static void announce(ServerPlayer attacker, RollResult damage) {
        attacker.sendSystemMessage(Component.translatable("ddc.combat.sneak_attack", damage.total())
                .withStyle(ChatFormatting.DARK_RED), true);
    }
}
