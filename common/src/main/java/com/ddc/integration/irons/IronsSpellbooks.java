package com.ddc.integration.irons;

import com.ddc.DDC;
import com.ddc.spell.SpellPresentation;
import com.ddc.spell.SpellPresentations;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The bridge to Iron's Spells 'n Spellbooks: when it is installed, a DDC spell that has an Iron's
 * equivalent is cast <em>as</em> that Iron's spell -- its projectile, its animation, its light.
 *
 * <p>Iron's Spells is All Rights Reserved and its author allows addons but not the reuse of its code
 * or assets, so this borrows neither: it holds no Iron's class at compile time and ships no Iron's
 * file. It reaches Iron's entirely through reflection against the mod's own public API, guarded by a
 * check that the API is even there. With Iron's absent -- on Fabric, where it does not exist, or a
 * NeoForge install without it -- {@link #install()} finds nothing and does nothing, and DDC's own
 * particle spells run exactly as before. Nothing here is loader-specific; it activates on whichever
 * loader actually has Iron's, which today is only NeoForge.
 *
 * <p>DDC still owns the rules. A cast reaches this bridge only after the class was allowed to cast,
 * the slot was paid and the target was in range; what the bridge adds is the <em>look</em>. It casts
 * through Iron's {@code COMMAND} source, which by Iron's own rule spends no mana and respects no
 * cooldown -- the D&D slot is the whole cost, and the spell simply happens.
 */
public final class IronsSpellbooks {

    private static final String MOD_ID = "irons_spellbooks";

    /**
     * DDC's spells to their nearest Iron's spell. Thematic, not literal: a sacred flame becomes a
     * guiding bolt because both are holy fire thrown at a foe, and burning hands becomes a flaming
     * barrage because both are a fan of fire up close. A DDC spell with no entry here keeps DDC's own
     * effect. The ids were read from Iron's own spell classes, not guessed.
     */
    private static final Map<ResourceLocation, String> SPELLS = Map.of(
            DDC.id("fire_bolt"), MOD_ID + ":firebolt",
            DDC.id("fireball"), MOD_ID + ":fireball",
            DDC.id("magic_missile"), MOD_ID + ":magic_missile",
            DDC.id("sacred_flame"), MOD_ID + ":guiding_bolt",
            DDC.id("burning_hands"), MOD_ID + ":flaming_barrage");

    private IronsSpellbooks() {
    }

    /**
     * Registers the bridge if Iron's Spells is present. Called once, at start-up; safe to call when
     * Iron's is absent, when it simply returns.
     */
    public static void install() {
        Api api = Api.tryLoad();
        if (api == null) {
            // Iron's is not installed, or its API moved. Either way DDC keeps its own spells.
            return;
        }
        SpellPresentations.register(new IronsPresentation(api));
        DDC.LOGGER.info("Iron's Spells is installed: {} DDC spells will cast as Iron's spells", SPELLS.size());
    }

    /** The presentation DDC offers each cast to. */
    private record IronsPresentation(Api api) implements SpellPresentation {

        @Override
        public boolean present(ServerPlayer caster, com.ddc.rules.Spell spell, ResourceLocation spellId,
                LivingEntity target) {
            String ironsId = SPELLS.get(spellId);
            if (ironsId == null) {
                return false;
            }
            Object ironsSpell = api.spell(ironsId);
            if (ironsSpell == null) {
                // Mapped, but this Iron's build does not have that spell. Fall back to DDC's own.
                return false;
            }
            // Point the caster at the target so Iron's projectile flies where DDC aimed it: Iron's
            // spells cast down the caster's line of sight, and DDC has an explicit target it did not.
            caster.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            // A cantrip is DDC level 0; Iron's spells begin at 1. Never ask for a level Iron's rejects.
            int level = Math.max(1, spell.level());
            return api.cast(ironsSpell, caster, level);
        }
    }

    /**
     * The slice of Iron's public API the bridge uses, resolved once by reflection.
     *
     * <p>Reflection rather than a compile dependency because Iron's publishes only part of its API and
     * its {@code AbstractSpell} refers to internals that are not in that jar -- compiling against it is
     * fragile, and reflection asks for exactly the three methods used and nothing else. If any of them
     * is missing, {@link #tryLoad()} returns null and the bridge stays dark.
     */
    private static final class Api {

        private final Method getSpell;
        private final Method getSpellId;
        private final Method attemptInitiateCast;
        private final Object commandSource;
        private final String noneId;

        private Api(Method getSpell, Method getSpellId, Method attemptInitiateCast, Object commandSource,
                String noneId) {
            this.getSpell = getSpell;
            this.getSpellId = getSpellId;
            this.attemptInitiateCast = attemptInitiateCast;
            this.commandSource = commandSource;
            this.noneId = noneId;
        }

        static Api tryLoad() {
            try {
                Class<?> registry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
                Class<?> abstractSpell = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
                Class<?> castSource = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");

                Method getSpell = registry.getMethod("getSpell", String.class);
                Method getSpellId = abstractSpell.getMethod("getSpellId");
                Method attempt = abstractSpell.getMethod("attemptInitiateCast", ItemStack.class, int.class,
                        Level.class, net.minecraft.world.entity.player.Player.class, castSource, boolean.class,
                        String.class);
                Object command = castSource.getField("COMMAND").get(null);
                // Iron's returns a "none" spell for an unknown id rather than null; learn its id now so
                // a mapping to a spell this build lacks can be told apart from a real one.
                Object none = registry.getMethod("none").invoke(null);
                String noneId = getSpellId.invoke(none).toString();
                return new Api(getSpell, getSpellId, attempt, command, noneId);
            } catch (ReflectiveOperationException notPresentOrChanged) {
                return null;
            }
        }

        /** The Iron's spell for an id, or null if this build does not have it. */
        Object spell(String ironsId) {
            try {
                Object spell = getSpell.invoke(null, ironsId);
                if (spell == null || getSpellId.invoke(spell).toString().equals(noneId)) {
                    return null;
                }
                return spell;
            } catch (ReflectiveOperationException failure) {
                return null;
            }
        }

        /** Casts an Iron's spell as the player, through the mana-free command source. */
        boolean cast(Object ironsSpell, ServerPlayer caster, int level) {
            try {
                Object result = attemptInitiateCast.invoke(ironsSpell, ItemStack.EMPTY, level, caster.level(),
                        caster, commandSource, false, "mainhand");
                return result instanceof Boolean cast && cast;
            } catch (ReflectiveOperationException failure) {
                return false;
            }
        }
    }
}
