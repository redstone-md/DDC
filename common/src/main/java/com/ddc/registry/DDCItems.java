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

    /**
     * A caster's wand: cantrips, which never run dry.
     *
     * <p>Built where the services are, because an item that casts needs the same rules a command
     * does. The services are handed in rather than reached for, so the item cannot grow rules of its
     * own.
     */
    public static RegistrySupplier<Item> WAND;

    /** A caster's staff: anything they have prepared. */
    public static RegistrySupplier<Item> STAFF;

    /**
     * Registers the items that need the rules to work: a wand casts, and casting is a service.
     *
     * <p>Called from the bootstrap once the services exist. The rest of the items are static because
     * they need nothing; these two need the game's rules, and a static field cannot have them.
     */
    public static void registerCasting(com.ddc.character.CharacterService characters,
            com.ddc.rules.DataRegistry<com.ddc.rules.Spell> spells,
            com.ddc.spell.SpellService casting) {
        WAND = ITEMS.register("wand", () -> new com.ddc.item.SpellFocusItem(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .setId(ResourceKey.create(Registries.ITEM, DDC.id("wand"))),
                com.ddc.item.SpellFocusItem.Power.WAND, characters, spells, casting));
        STAFF = ITEMS.register("staff", () -> new com.ddc.item.SpellFocusItem(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
                .setId(ResourceKey.create(Registries.ITEM, DDC.id("staff"))),
                com.ddc.item.SpellFocusItem.Power.STAFF, characters, spells, casting));
    }

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
        CreativeTabRegistry.append(TAB, GM_WAND, SPELLBOOK, WAND, STAFF);
        POSSESSIONS.register();
    }

    /** The wand's id, for anything that needs to name it. */
    public static Identifier gmWandId() {
        return DDC.id("gm_wand");
    }
}
