package com.ddc.rules;

import com.ddc.core.character.Ability;
import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.Die;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Codecs for the rules engine's types.
 *
 * <p>The engine under {@code com.ddc.core} stays free of Minecraft and Mojang serialisation, so the
 * codecs that let a data pack describe it live out here.
 *
 * <p>Every codec reports an unknown value as a {@link DataResult} error naming what it saw. That
 * error surfaces with the offending file's name when a pack loads, which is the validator ADR-0002
 * asks for.
 */
public final class DDCCodecs {

    /** An ability, written as {@code "strength"} or {@code "STR"}. */
    public static final Codec<Ability> ABILITY = Codec.STRING.comapFlatMap(
            key -> Ability.byId(key)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown ability: '" + key + "'")),
            Ability::id);

    /** A die, written as {@code "d8"}. */
    public static final Codec<Die> DIE = Codec.STRING.comapFlatMap(
            key -> {
                if (key.length() < 2 || (key.charAt(0) != 'd' && key.charAt(0) != 'D')) {
                    return DataResult.error(() -> "A die must look like 'd8' but was: '" + key + "'");
                }
                try {
                    return DataResult.success(Die.ofSides(Integer.parseInt(key.substring(1))));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unsupported die: '" + key + "'");
                }
            },
            Die::toString);

    /** Dice notation, as a data pack writes it: {@code "1d6"}, {@code "8d6"}, {@code "1d10+2"}. */
    public static final Codec<DiceExpression> DICE_EXPRESSION = Codec.STRING.comapFlatMap(
            notation -> {
                try {
                    return DataResult.success(DiceExpression.parse(notation));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Bad dice: " + e.getMessage());
                }
            },
            DiceExpression::toString);

    private DDCCodecs() {
    }
}
