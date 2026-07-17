package com.ddc.character;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Where experience comes from: killing things.
 *
 * <p>D&D awards experience by challenge rating, which Minecraft does not have. Hit points are the
 * closest thing it does have -- a zombie is 20, a ravager is 100 -- so that is what a kill is worth.
 * It is an approximation and openly one; a pack that disagrees changes the levelling table instead,
 * which is the knob ADR-0002 promised and the one that actually decides the pace.
 *
 * <p>Only the player who struck the blow earns it. Splitting a kill across a party is the sort of
 * rule a table should agree on rather than a mod decide, and the GM can hand out experience directly.
 */
public final class ExperienceListener {

    private final ExperienceService experience;

    public ExperienceListener(ExperienceService experience) {
        this.experience = experience;
    }

    public void register() {
        EntityEvent.LIVING_DEATH.register(this::onDeath);
    }

    private EventResult onDeath(LivingEntity dying, DamageSource source) {
        // A player dying is not experience for whoever killed them: this is a game about a party, and
        // farming each other is not what the rules are for.
        if (dying instanceof Player || !(source.getEntity() instanceof ServerPlayer killer)) {
            return EventResult.pass();
        }
        experience.award(killer, worth(dying));
        return EventResult.pass();
    }

    /** What a creature is worth, in experience. Package-visible so the rule can be tested. */
    static int worth(LivingEntity dying) {
        return Math.max(1, Math.round(dying.getMaxHealth()));
    }
}
