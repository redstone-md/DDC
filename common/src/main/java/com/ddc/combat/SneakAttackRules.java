package com.ddc.combat;

import com.ddc.character.CharacterSheet;
import com.ddc.rules.ClassFeature;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * When a rogue's sneak attack applies.
 *
 * <p>The SRD's condition is "advantage on the attack, or an ally of yours is within 5 feet of the
 * target". DDC has no turn order and no formal advantage on a swing, so the trigger is read off the
 * world instead, and the reading is stated here rather than buried in the listener:
 *
 * <ul>
 *   <li>the rogue is invisible or sneaking, which is this game's version of unseen; or
 *   <li>the rogue is behind the target, which the SRD's own flavour of a sneak attack describes; or
 *   <li>another player is within five feet of the target, which is the SRD's condition unchanged.
 * </ul>
 */
public final class SneakAttackRules {

    /** Five feet, in blocks. */
    private static final double ALLY_REACH = 1.0;

    /** How far behind the target the rogue must be, as a dot product against the target's facing. */
    private static final double BEHIND_THRESHOLD = 0.0;

    private SneakAttackRules() {
    }

    /** Whether this attacker gets their sneak attack on this target, right now. */
    public static boolean applies(LivingEntity attacker, LivingEntity target, ServerLevel level) {
        return isUnseen(attacker) || isBehind(attacker, target) || hasAllyNear(attacker, target, level);
    }

    private static boolean isUnseen(LivingEntity attacker) {
        return attacker.isInvisible() || attacker.isCrouching();
    }

    /** True when the attacker stands in the half of the world the target is facing away from. */
    static boolean isBehind(LivingEntity attacker, LivingEntity target) {
        var toAttacker = attacker.position().subtract(target.position()).normalize();
        var facing = target.getLookAngle().normalize();
        return toAttacker.dot(facing) < BEHIND_THRESHOLD;
    }

    /**
     * True when a player other than the attacker is within five feet of the target.
     *
     * <p>Players only: a wolf or an iron golem beside the target is not the ally the SRD means, and
     * counting every mob would make a sneak attack fire in the middle of any crowd.
     */
    private static boolean hasAllyNear(LivingEntity attacker, LivingEntity target, ServerLevel level) {
        List<? extends Player> nearby = level.getEntitiesOfClass(Player.class,
                target.getBoundingBox().inflate(ALLY_REACH),
                player -> player != attacker && player.isAlive());
        return !nearby.isEmpty();
    }

    /** The dice a rogue's sneak attack adds at their level, if their class has one. */
    public static java.util.Optional<com.ddc.core.dice.DiceExpression> diceFor(
            CharacterSheet sheet, com.ddc.rules.CharacterClass definition) {
        return definition.feature(ClassFeature.SneakAttack.class)
                .map(feature -> feature.diceAtLevel(sheet.level()));
    }
}
