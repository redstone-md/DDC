package com.ddc.registry;

import com.ddc.DDC;
import com.ddc.gm.GmWandItem;
import com.ddc.gm.PossessionService;
import com.ddc.spell.SpellbookItem;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The mod's items.
 *
 * <p>Registered through Architectury's deferred register, so one declaration serves both loaders, as
 * common/AGENTS.md requires.
 */
public final class DDCItems {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(DDC.MOD_ID, Registries.ITEM);

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(DDC.MOD_ID, Registries.CREATIVE_MODE_TAB);

    /** The one possession service, shared by the wand and by anything else that needs it. */
    public static final PossessionService POSSESSIONS = new PossessionService();

    /**
     * The Game Master's wand. It is only a tool for a GM: holding it grants nothing, and every use
     * re-checks the permission.
     */
    public static final RegistrySupplier<Item> GM_WAND = ITEMS.register("gm_wand",
            () -> new GmWandItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .setId(ResourceKey.create(Registries.ITEM, DDC.id("gm_wand"))), POSSESSIONS));

    /** The wizard's spellbook. What is written in it lives on the sheet; see {@link SpellbookItem}. */
    public static final RegistrySupplier<Item> SPELLBOOK = ITEMS.register("spellbook",
            () -> new SpellbookItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)
                    .setId(ResourceKey.create(Registries.ITEM, DDC.id("spellbook")))));

    public static final RegistrySupplier<CreativeModeTab> TAB = TABS.register("ddc",
            () -> CreativeTabRegistry.create(
                    Component.literal(DDC.MOD_NAME),
                    () -> new ItemStack(GM_WAND.get())));

    private DDCItems() {
    }

    /** Called once from the shared bootstrap, before registries freeze. */
    public static void register() {
        ITEMS.register();
        TABS.register();
        CreativeTabRegistry.append(TAB, GM_WAND, SPELLBOOK);
        POSSESSIONS.register();
    }

    /** The wand's id, for anything that needs to name it. */
    public static Identifier gmWandId() {
        return DDC.id("gm_wand");
    }
}
