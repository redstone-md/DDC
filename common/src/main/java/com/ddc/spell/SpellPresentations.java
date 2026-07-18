package com.ddc.spell;

import com.ddc.rules.Spell;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every {@link SpellPresentation} an addon has registered, offered a cast in turn.
 *
 * <p>The list is empty in a plain install, which is the point: DDC presents its own spells and this
 * costs a single {@code isEmpty} check on the way. An addon adds itself at start-up -- see the Iron's
 * Spells bridge -- and from then on each cast is offered to it first.
 *
 * <p>A presentation that throws is caught and treated as not having taken the spell. An addon's bug is
 * the addon's; it must never turn a cast into a crash, so the worst a broken presentation can do is
 * fall through to DDC's own effect.
 */
public final class SpellPresentations {

    private static final List<SpellPresentation> HOOKS = new CopyOnWriteArrayList<>();

    private SpellPresentations() {
    }

    /** Adds a presentation. Addons call this once, at start-up. */
    public static void register(SpellPresentation presentation) {
        HOOKS.add(presentation);
    }

    /**
     * Offers the cast to each presentation until one takes it.
     *
     * @return {@code true} if a presentation cast the spell, so DDC should do nothing more
     */
    public static boolean present(ServerPlayer caster, Spell spell, ResourceLocation spellId,
            LivingEntity target) {
        for (SpellPresentation presentation : HOOKS) {
            try {
                if (presentation.present(caster, spell, spellId, target)) {
                    return true;
                }
            } catch (Throwable failure) {
                // An addon's presentation must never break a cast. Swallow it and fall through to the
                // next presentation, and then to DDC's own effect.
                com.ddc.DDC.LOGGER.warn("A spell presentation threw and was skipped", failure);
            }
        }
        return false;
    }

    /** Forgets every presentation. For tests, which must not leak one into the next. */
    public static void clear() {
        HOOKS.clear();
    }
}
