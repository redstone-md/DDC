package com.ddc.spell;

import net.minecraft.world.item.Item;

/**
 * The wizard's spellbook, from PRD 3.1.
 *
 * <p>The book itself holds nothing. What is written in it lives on the character sheet, because a
 * sheet is already the one place a character's state lives and a second one would only be able to
 * disagree with it -- and because a book left in a chest should not take a wizard's magic with it.
 *
 * <p>What the book is for is the writing. {@code /ddc prepare} needs it in hand, so preparing spells
 * is something you do with a book rather than something you simply know, which is the rule the PRD is
 * after.
 */
public class SpellbookItem extends Item {

    public SpellbookItem(Properties properties) {
        super(properties);
    }
}
