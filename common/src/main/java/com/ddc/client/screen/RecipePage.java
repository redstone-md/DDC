package com.ddc.client.screen;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What DDC's own items are made of, drawn in the guide.
 *
 * <p>A player who has just been told to craft a spellbook has nowhere to find out how. REI and JEI
 * both show it, and neither is installed on most servers -- so the mod that asked for the spellbook
 * says what it costs.
 *
 * <p>The ingredients are named here rather than read from the recipe files, and that is a real
 * trade: a recipe changed in a data pack would leave this page saying the old one. It is the honest
 * cost of a page that does not need a recipe manager, an ingredient resolver and a layout engine to
 * draw four crafts. Every one of these ingredients is checked by a test against the recipes that
 * ship, so the page and the pack cannot drift apart without someone hearing about it.
 */
@Environment(EnvType.CLIENT)
public final class RecipePage {

    /**
     * One craft: what comes out, and what goes in.
     *
     * @param result what it makes
     * @param ingredients what it takes, in the order they read
     * @param note    the line under it, as a translation key
     */
    public record Craft(String result, List<String> ingredients, String note) {
    }

    /** Every craft DDC adds. The order a player meets them in. */
    public static final List<Craft> CRAFTS = List.of(
            new Craft("ddc:spellbook",
                    List.of("minecraft:book", "minecraft:amethyst_shard", "minecraft:ink_sac"),
                    "ddc.recipe.spellbook"),
            new Craft("ddc:wand",
                    List.of("minecraft:amethyst_shard", "minecraft:stick", "minecraft:stick"),
                    "ddc.recipe.wand"),
            new Craft("ddc:staff",
                    List.of("minecraft:amethyst_block", "minecraft:amethyst_shard",
                            "minecraft:amethyst_shard", "minecraft:stick", "minecraft:stick"),
                    "ddc.recipe.staff"),
            new Craft("ddc:spell_scroll",
                    List.of("minecraft:paper", "minecraft:amethyst_shard", "minecraft:ink_sac"),
                    "ddc.recipe.scroll"));

    private static final int SLOT = 18;
    private static final int ARROW = 14;

    private RecipePage() {
    }

    /**
     * Draws every craft as a row: what it takes, an arrow, what it makes.
     *
     * @return how far down the page it got
     */
    public static int render(GuiGraphicsExtractor graphics, Font font, int left, int top, int width) {
        int y = top;
        for (Craft craft : CRAFTS) {
            int x = left;
            for (String ingredient : craft.ingredients()) {
                graphics.item(stack(ingredient), x, y);
                x += SLOT;
            }
            graphics.text(font, Component.literal("->"), x + 2, y + 5, 0xFF8A7F6B);
            graphics.item(stack(craft.result()), x + ARROW + 4, y);
            graphics.text(font, name(craft.result()), x + ARROW + 4 + SLOT + 4, y + 5, 0xFFE8DCC0);

            y += SLOT;
            // The note sits under its own row, wrapped: a translation is a different length from the
            // English beside it.
            for (net.minecraft.util.FormattedCharSequence line
                    : font.split(Component.translatable(craft.note()), width)) {
                graphics.text(font, line, left, y, 0xFF8A7F6B);
                y += 9;
            }
            y += 4;
        }
        return y;
    }

    /** The item's own name, as the game knows it, so a resource pack's rename shows here too. */
    private static Component name(String id) {
        return stack(id).getHoverName();
    }

    private static ItemStack stack(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }
}
