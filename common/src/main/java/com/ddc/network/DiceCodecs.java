package com.ddc.network;

import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.DicePool;
import com.ddc.core.dice.Die;
import com.ddc.core.dice.DieRoll;
import com.ddc.core.dice.RollMode;
import com.ddc.core.dice.RollResult;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wire forms for the rules engine's dice types.
 *
 * <p>The engine stays free of Minecraft, so its codecs live out here rather than on the types.
 *
 * <p>Everything decoded here is untrusted. The bounds below are what stops a hostile or broken peer
 * from making a client allocate on its say-so; past them, the engine's own constructors reject
 * nonsense (a face outside a die's range, a pool of zero dice), so a bad packet throws during decode
 * instead of becoming a strange-looking roll.
 */
public final class DiceCodecs {

    /** An expression's notation is capped at 64 characters, which cannot hold more pools than this. */
    private static final int MAX_POOLS = 16;

    /** {@link DicePool#MAX_COUNT} dice, plus the extra d20 that advantage throws. */
    private static final int MAX_ROLLS = DicePool.MAX_COUNT + 1;

    private static final RollMode[] MODES = RollMode.values();

    /**
     * A die, sent as its number of sides rather than its ordinal, so that adding or reordering a
     * constant in {@link Die} cannot silently change what an existing build reads off the wire.
     */
    public static final StreamCodec<ByteBuf, Die> DIE =
            ByteBufCodecs.VAR_INT.map(Die::ofSides, Die::sides);

    public static final StreamCodec<ByteBuf, RollMode> ROLL_MODE = ByteBufCodecs.VAR_INT.map(
            ordinal -> {
                if (ordinal < 0 || ordinal >= MODES.length) {
                    throw new IllegalArgumentException("Unknown roll mode ordinal: " + ordinal);
                }
                return MODES[ordinal];
            },
            Enum::ordinal);

    public static final StreamCodec<ByteBuf, DicePool> DICE_POOL = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DicePool::count,
            DIE, DicePool::die,
            DicePool::new);

    private static final StreamCodec<ByteBuf, List<DicePool>> POOLS =
            ByteBufCodecs.collection(ArrayList::new, DICE_POOL, MAX_POOLS);

    public static final StreamCodec<ByteBuf, DiceExpression> DICE_EXPRESSION = StreamCodec.composite(
            POOLS, DiceExpression::pools,
            ByteBufCodecs.VAR_INT, DiceExpression::modifier,
            DiceExpression::new);

    public static final StreamCodec<ByteBuf, DieRoll> DIE_ROLL = StreamCodec.composite(
            DIE, DieRoll::die,
            ByteBufCodecs.VAR_INT, DieRoll::value,
            ByteBufCodecs.BOOL, DieRoll::discarded,
            DieRoll::new);

    /**
     * A resolved roll, faces and all.
     *
     * <p>The expression is sent as structure rather than as its notation string: a client rebuilds
     * the pools and the modifier from numbers, so nothing about the roll depends on both sides
     * parsing "1d20+3" the same way.
     */
    private static final StreamCodec<ByteBuf, List<DieRoll>> ROLLS =
            ByteBufCodecs.collection(ArrayList::new, DIE_ROLL, MAX_ROLLS);

    public static final StreamCodec<ByteBuf, RollResult> ROLL_RESULT = StreamCodec.composite(
            DICE_EXPRESSION, RollResult::expression,
            ROLLS, RollResult::rolls,
            ROLL_MODE, RollResult::mode,
            ByteBufCodecs.VAR_LONG, RollResult::seed,
            RollResult::new);

    private DiceCodecs() {
    }
}
