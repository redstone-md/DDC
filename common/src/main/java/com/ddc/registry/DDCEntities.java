package com.ddc.registry;

import com.ddc.DDC;
import com.ddc.dice.DiceEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The mod's entities.
 *
 * <p>Only one, and it exists for a reason worth stating: Minecraft 26 has no hook for drawing
 * arbitrary geometry into the world, so an entity is the only thing both loaders can draw. See
 * {@link DiceEntity}.
 */
public final class DDCEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(DDC.MOD_ID, Registries.ENTITY_TYPE);

    /** A roll's dice. One per roll, gone in four seconds, never saved. */
    public static final RegistrySupplier<EntityType<DiceEntity>> DICE = ENTITIES.register("dice",
            () -> EntityType.Builder.<DiceEntity>of(DiceEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(4)
                    .build("dice"));

    /** A spell in flight. Cosmetic, brief, and never saved: see {@link com.ddc.spell.SpellBoltEntity}. */
    public static final RegistrySupplier<EntityType<com.ddc.spell.SpellBoltEntity>> SPELL_BOLT =
            ENTITIES.register("spell_bolt",
                    () -> EntityType.Builder.<com.ddc.spell.SpellBoltEntity>of(
                                    com.ddc.spell.SpellBoltEntity::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .noSave()
                            .fireImmune()
                            // Further than a die: a bolt crosses a room, and the room is watching.
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("spell_bolt"));

    private DDCEntities() {
    }

    /** Called once from the shared bootstrap, before registries freeze. */
    public static void register() {
        ENTITIES.register();
    }
}
