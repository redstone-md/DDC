package com.ddc.network;

import com.ddc.core.dice.RollMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wire form for {@link RollMode}.
 *
 * <p>The enum lives in the loader-free rules engine and must stay free of Minecraft imports, so the
 * codec lives out here instead of on the enum itself.
 */
public final class RollModeCodecs {

    private static final RollMode[] BY_ORDINAL = RollMode.values();

    /** Sent as an ordinal; an out-of-range value is rejected rather than defaulted. */
    public static final StreamCodec<ByteBuf, RollMode> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            ordinal -> {
                if (ordinal < 0 || ordinal >= BY_ORDINAL.length) {
                    throw new IllegalArgumentException("Unknown roll mode ordinal: " + ordinal);
                }
                return BY_ORDINAL[ordinal];
            },
            Enum::ordinal);

    private RollModeCodecs() {
    }
}
