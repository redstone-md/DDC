package com.ddc.network;

import com.ddc.DDC;
import com.ddc.character.CharacterSheet;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sends a player their own character sheet.
 *
 * <p>Sent on join, on respawn, and whenever the sheet changes. The client uses it to draw the HUD and
 * nothing else: it is a copy of server state, never a source of it.
 *
 * <p>Maximum hit points ride along rather than being derived on the client, because deriving them
 * needs the class definition from the data packs, which the client does not have.
 *
 * @param sheet         the player's sheet
 * @param maxHitPoints  the derived maximum, empty when the player has not picked a class yet
 */
public record CharacterSheetPayload(CharacterSheet sheet, OptionalInt maxHitPoints)
        implements CustomPacketPayload {

    public static final Type<CharacterSheetPayload> TYPE = new Type<>(DDC.id("character_sheet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSheetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(CharacterSheet.CODEC), CharacterSheetPayload::sheet,
                    ByteBufCodecs.OPTIONAL_VAR_INT, CharacterSheetPayload::maxHitPoints,
                    CharacterSheetPayload::new);

    public CharacterSheetPayload {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(maxHitPoints, "maxHitPoints");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
