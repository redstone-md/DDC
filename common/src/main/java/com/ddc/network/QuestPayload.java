package com.ddc.network;

import com.ddc.DDC;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What the party is doing, as the Game Master put it.
 *
 * <p>ARCHITECTURE 5 asks the stream widget for active quests, and there was no such thing anywhere in
 * the mod. There still is no quest <em>system</em>: no objectives, no tracking, no completion, because
 * a table's quest lives in the GM's head and the players' notes, and a mod that tried to model it
 * would be a mod telling a table how to play.
 *
 * <p>This is the one line of it a viewer needs -- the thing a GM would write on a whiteboard.
 *
 * @param text the line, empty to clear it
 */
public record QuestPayload(String text) implements CustomPacketPayload {

    /** A line, not an essay: this is a whiteboard, and it is drawn on a stream. */
    public static final int MAX_LENGTH = 120;

    public static final Type<QuestPayload> TYPE = new Type<>(DDC.id("quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_LENGTH), QuestPayload::text,
                    QuestPayload::new);

    public QuestPayload {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
