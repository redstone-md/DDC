package com.ddc.network;

import com.ddc.DDC;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A Game Master telling the monster they are driving to attack.
 *
 * <p>ARCHITECTURE 6 lists this as {@code ddc:possess_mob}, planned, and it stayed planned: a GM could
 * steer a monster around but never hit anyone with it, which is most of what a monster is for. A
 * possessing GM is a spectator, and a spectator's click reaches nothing -- so the click has to be sent.
 *
 * <p>It carries nothing but the fact that it happened. Who is possessing what, whether they may, and
 * what is in range are all the server's answers; a payload that named its own target would be a
 * client choosing who a monster mauls. ADR-0003 is explicit about this, and this is the packet it was
 * warning about.
 */
public record PossessActionPayload() implements CustomPacketPayload {

    public static final Type<PossessActionPayload> TYPE = new Type<>(DDC.id("possess_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PossessActionPayload> STREAM_CODEC =
            StreamCodec.unit(new PossessActionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
