package com.ddc.rules;

import com.ddc.core.character.Ability;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/**
 * A block a character has to roll for: PRD 3.1's locked chest and shouldered door.
 *
 * <p>Which blocks these are is a data pack's business, not the mod's. A campaign where iron doors are
 * ordinary and chests are trapped says exactly that in JSON, and a mod that hardcoded "iron doors are
 * hard" would be a mod telling every table the same story.
 *
 * <p>The file's own id names the block, so {@code data/minecraft/ddc_checks/iron_door.json} is the
 * rule for {@code minecraft:iron_door}. It reads as what it is, and an addon's own blocks work the
 * same way with no code.
 *
 * @param ability what the check rolls: Strength to shoulder, Dexterity to pick
 * @param dc      the number to beat
 * @param message what to say when it fails, as a translation key
 */
public record BlockCheck(Ability ability, int dc, String message) {

    /** The SRD's own range: 5 is very easy, 30 is nearly impossible. */
    private static final int MIN_DC = 1;
    private static final int MAX_DC = 30;

    public static final Codec<BlockCheck> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DDCCodecs.ABILITY.fieldOf("ability").forGetter(BlockCheck::ability),
            Codec.intRange(MIN_DC, MAX_DC).fieldOf("dc").forGetter(BlockCheck::dc),
            Codec.STRING.optionalFieldOf("message", "ddc.check.block.failed").forGetter(BlockCheck::message)
    ).apply(instance, BlockCheck::new));

    public BlockCheck {
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(message, "message");
    }
}
