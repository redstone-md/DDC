package com.ddc.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The pictures on the wheel and in the guide, borrowed from the game itself.
 *
 * <p>Every one is a vanilla item rather than something drawn for DDC. A resource pack that restyles
 * Minecraft restyles these with it, which a folder of hand-drawn PNGs would quietly refuse to do --
 * they would keep their own look while the rest of the game changed around them, and a menu that does
 * not match the game it is in looks like a menu from another game.
 *
 * <p>Each choice is the nearest thing the game already has a picture of: a bed is rest, a sword is a
 * manoeuvre, an eye of ender is the Game Master watching. Nothing here is arbitrary, but nothing here
 * is literal either -- Minecraft has no picture of a d20, so the shiniest polyhedron it does have
 * stands in.
 */
@Environment(EnvType.CLIENT)
public enum Icon {

    /** No d20 exists in vanilla; a cut gem is the closest thing to one it draws. */
    ROLL("minecraft:diamond"),
    CAST("minecraft:blaze_powder"),
    /** Second wind heals, and this is what healing looks like in this game. */
    SECOND_WIND("minecraft:golden_apple"),
    /** A surge is haste, and haste in a bottle is sugar. */
    ACTION_SURGE("minecraft:sugar"),
    MANEUVER("minecraft:iron_sword"),
    REST("minecraft:red_bed"),
    SHEET("minecraft:writable_book"),
    /** Race is who your character is: a face. */
    RACE("minecraft:player_head"),
    CLASS("minecraft:iron_helmet"),
    /** The guide is the book that teaches you something, which is what a knowledge book is for. */
    GUIDE("minecraft:knowledge_book"),
    ENCOUNTER("minecraft:zombie_head"),
    /** The Game Master: watching, and about to reach in. */
    GM("minecraft:ender_eye"),
    CHANNEL_DIVINITY("minecraft:golden_helmet"),
    /** Turning the world: the clock the game already keeps. */
    WORLD("minecraft:clock"),
    NARRATE("minecraft:name_tag"),
    CHECK("minecraft:tripwire_hook"),

    // The six abilities. Each is the thing in this game that does what the ability means: you hit
    // with a sword, you dodge like a feather, you take a blow behind a shield, you learn from a book,
    // you notice through a spyglass, and you are listened to when you are holding an emerald.
    STRENGTH("minecraft:iron_sword"),
    DEXTERITY("minecraft:feather"),
    CONSTITUTION("minecraft:shield"),
    INTELLIGENCE("minecraft:book"),
    WISDOM("minecraft:spyglass"),
    CHARISMA("minecraft:emerald");

    /** The picture for an ability, so the HUD does not have to keep its own table of them. */
    public static Icon of(com.ddc.core.character.Ability ability) {
        return switch (ability) {
            case STRENGTH -> STRENGTH;
            case DEXTERITY -> DEXTERITY;
            case CONSTITUTION -> CONSTITUTION;
            case INTELLIGENCE -> INTELLIGENCE;
            case WISDOM -> WISDOM;
            case CHARISMA -> CHARISMA;
        };
    }

    /** How big an icon is drawn. Vanilla's own item size: anything else would blur it. */
    public static final int SIZE = 16;

    private final String item;
    private ItemStack stack;

    Icon(String item) {
        this.item = item;
    }

    /**
     * Draws the icon with its top-left corner at this point.
     *
     * <p>The stack is looked up once and kept: the item registry is not filled when this enum loads,
     * so asking at that moment would find nothing.
     */
    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.item(stack(), x, y);
    }

    private ItemStack stack() {
        if (stack == null) {
            stack = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(item))
                    .map(ItemStack::new)
                    .orElse(ItemStack.EMPTY);
        }
        return stack;
    }
}
