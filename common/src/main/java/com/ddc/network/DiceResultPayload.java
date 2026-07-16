package com.ddc.network;

import com.ddc.DDC;
import com.ddc.core.dice.RollResult;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells nearby clients what the server rolled, so they can show it.
 *
 * <p>The faces are sent, not recomputed. An earlier design sent only the seed and had each client
 * re-run the roll, since the roller is deterministic and that keeps the packet tiny. The saving was
 * a few bytes and the risk was a silent desync: a client running a different build of the mod could
 * resolve the same seed into a different number, and then show the table a roll that never happened.
 * The server is the authority on the number, so the number travels.
 *
 * <p>The seed still travels, because ARCHITECTURE.md's dice physics needs every client to bounce the
 * dice identically. It decides how the die tumbles, never which face it settles on.
 *
 * @param roller     who rolled, for the roll log and the stream overlay
 * @param rollerName their display name at the time of the roll
 * @param result     the roll as the server resolved it
 */
public record DiceResultPayload(UUID roller, String rollerName, RollResult result)
        implements CustomPacketPayload {

    public static final Type<DiceResultPayload> TYPE = new Type<>(DDC.id("dice_result"));

    /** Bounds an untrusted name from the wire. */
    private static final int MAX_NAME_LENGTH = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, DiceResultPayload::roller,
                    ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), DiceResultPayload::rollerName,
                    DiceCodecs.ROLL_RESULT, DiceResultPayload::result,
                    DiceResultPayload::new);

    public DiceResultPayload {
        Objects.requireNonNull(roller, "roller");
        Objects.requireNonNull(rollerName, "rollerName");
        Objects.requireNonNull(result, "result");
    }

    public static DiceResultPayload of(UUID roller, String rollerName, RollResult result) {
        return new DiceResultPayload(roller, rollerName, result);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
