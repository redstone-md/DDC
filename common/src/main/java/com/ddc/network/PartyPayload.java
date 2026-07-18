package com.ddc.network;

import com.ddc.DDC;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Who is at the table, and how they are doing.
 *
 * <p>PRD 4.5 asks the OBS widget for party health cards, and PRD 3.1's HUD is one player's own sheet
 * -- so the party had no way to reach a client at all. This is that way: every character, to every
 * player, because a party knows each other's hit points. It is the one place DDC tells a client about
 * someone else, and it says only what the table would say out loud.
 *
 * <p>No abilities, no spells, no sheet. A card wants a name, a class, a level and a health bar; the
 * rest is that character's own business and would only be a bigger packet to keep in step.
 *
 * @param members every player who has made a character, in join order
 */
public record PartyPayload(List<Member> members) implements CustomPacketPayload {

    /** How many characters a payload may carry, which is far more than a table has. */
    private static final int MAX_MEMBERS = 64;

    public static final Type<PartyPayload> TYPE = new Type<>(DDC.id("party"));

    /**
     * One character's card.
     *
     * @param name         the player's name
     * @param className    their class, as its data pack named it
     * @param level        their level
     * @param hitPoints    their health right now
     * @param maxHitPoints the health their hit die gives them
     */
    public record Member(String name, String className, int level, int hitPoints, int maxHitPoints,
            int experience, int nextLevel) {

        private static final int MAX_TEXT = 64;

        // Written by hand rather than with composite(): a member has seven fields and composite tops
        // out at six. The last two are ARCHITECTURE 5's level-progress meter -- what they have, and
        // what the next level asks for, zero at the last level, which a widget draws as full.
        public static final StreamCodec<ByteBuf, Member> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Member decode(ByteBuf buf) {
                return new Member(
                        ByteBufCodecs.stringUtf8(MAX_TEXT).decode(buf),
                        ByteBufCodecs.stringUtf8(MAX_TEXT).decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, Member member) {
                ByteBufCodecs.stringUtf8(MAX_TEXT).encode(buf, member.name());
                ByteBufCodecs.stringUtf8(MAX_TEXT).encode(buf, member.className());
                ByteBufCodecs.VAR_INT.encode(buf, member.level());
                ByteBufCodecs.VAR_INT.encode(buf, member.hitPoints());
                ByteBufCodecs.VAR_INT.encode(buf, member.maxHitPoints());
                ByteBufCodecs.VAR_INT.encode(buf, member.experience());
                ByteBufCodecs.VAR_INT.encode(buf, member.nextLevel());
            }
        };

        public Member {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(className, "className");
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PartyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, Member.STREAM_CODEC, MAX_MEMBERS)
                            .map(List::copyOf, ArrayList::new),
                    PartyPayload::members,
                    PartyPayload::new);

    public PartyPayload {
        members = List.copyOf(Objects.requireNonNull(members, "members"));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
