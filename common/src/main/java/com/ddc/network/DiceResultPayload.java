package com.ddc.network;

import com.ddc.DDC;
import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.DiceRoller;
import com.ddc.core.dice.RollMode;
import com.ddc.core.dice.RollResult;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells nearby clients that a roll happened, so they can render the dice landing on it.
 *
 * <p>The result itself is not sent. The seed is, and the client rebuilds the identical
 * {@link RollResult} through {@link #replay()}, because the roller is deterministic. That keeps the
 * payload at a handful of bytes no matter how many dice were thrown, which is what ADR-0003 asks
 * for, and it means the physics simulation and the number always agree.
 *
 * @param roller     who rolled, for the roll log and the stream overlay
 * @param rollerName their display name at the time of the roll
 * @param notation   the expression thrown, in canonical dice notation
 * @param mode       whether the throw had advantage or disadvantage
 * @param seed       the seed the server rolled from
 */
public record DiceResultPayload(UUID roller, String rollerName, String notation, RollMode mode, long seed)
        implements CustomPacketPayload {

    public static final Type<DiceResultPayload> TYPE = new Type<>(DDC.id("dice_result"));

    /** Bounds what a client will parse, so a hostile server cannot hand out a pathological string. */
    private static final int MAX_NOTATION_LENGTH = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, DiceResultPayload::roller,
                    ByteBufCodecs.STRING_UTF8, DiceResultPayload::rollerName,
                    ByteBufCodecs.stringUtf8(MAX_NOTATION_LENGTH), DiceResultPayload::notation,
                    RollModeCodecs.STREAM_CODEC, DiceResultPayload::mode,
                    ByteBufCodecs.VAR_LONG, DiceResultPayload::seed,
                    DiceResultPayload::new);

    public DiceResultPayload {
        Objects.requireNonNull(roller, "roller");
        Objects.requireNonNull(rollerName, "rollerName");
        Objects.requireNonNull(notation, "notation");
        Objects.requireNonNull(mode, "mode");
    }

    public static DiceResultPayload of(UUID roller, String rollerName, RollResult result) {
        return new DiceResultPayload(roller, rollerName, result.expression().toString(), result.mode(),
                result.seed());
    }

    /**
     * Rebuilds the roll the server made.
     *
     * @throws IllegalArgumentException if the notation or mode is not something this build can throw,
     *                                  which the caller must treat as a bad packet rather than a crash
     */
    public RollResult replay() {
        return DiceRoller.replaying(seed).roll(DiceExpression.parse(notation), mode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
