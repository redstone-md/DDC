package com.ddc.network;

import com.ddc.DDC;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A line of narration from the Game Master, to be shown over the world.
 *
 * <p>Only ever sent by the server, and only after it has checked the sender is a GM. A client that
 * fabricates one of these sends it nowhere: there is no C2S narration payload, the GM asks through a
 * command and the server decides.
 *
 * @param text the line to show; length-capped so narration cannot be used to flood a client
 */
public record NarrationPayload(String text) implements CustomPacketPayload {

    public static final Type<NarrationPayload> TYPE = new Type<>(DDC.id("narrate"));

    /** Long enough for a dramatic paragraph, short enough that it cannot be abused as a buffer. */
    public static final int MAX_LENGTH = 512;

    public static final StreamCodec<RegistryFriendlyByteBuf, NarrationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_LENGTH), NarrationPayload::text,
                    NarrationPayload::new);

    public NarrationPayload {
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Narration exceeds " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
