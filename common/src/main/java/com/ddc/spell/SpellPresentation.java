package com.ddc.spell;

import com.ddc.rules.Spell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A thing that can show a spell for DDC, instead of DDC's own particles.
 *
 * <p>DDC's magic draws itself out of vanilla particles ({@link SpellEffects}) because it must run with
 * no other mod installed. But a table that also runs a magic mod would rather see that mod's spell --
 * its projectile, its light, its sound -- than DDC's stand-in. This is the seam: an addon registers a
 * presentation, DDC offers it each cast, and if the addon takes one it owns the whole effect.
 *
 * <p>The bargain is deliberate. DDC still decides <em>whether</em> the spell happens -- the class can
 * cast, the slot is paid, the target is in range -- because those are the game's rules and the table's.
 * What a taken spell hands over is only the <em>doing</em>: the addon's spell is cast, with the addon's
 * damage and the addon's look, and DDC does not also roll its own dice or draw its own bolt over the
 * top. A spell nobody takes falls through to DDC's own effect exactly as before.
 */
@FunctionalInterface
public interface SpellPresentation {

    /**
     * Offers a cast to this presentation.
     *
     * @param caster  who cast it
     * @param spell   the DDC spell being cast
     * @param spellId the spell's registry id, e.g. {@code ddc:fireball} -- the key an addon maps from
     * @param target  what it was aimed at
     * @return {@code true} if this presentation cast the spell itself, so DDC should do nothing more;
     *         {@code false} to let DDC present it the ordinary way
     */
    boolean present(ServerPlayer caster, Spell spell, ResourceLocation spellId, LivingEntity target);
}
