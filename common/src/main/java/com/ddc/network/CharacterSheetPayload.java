package com.ddc.network;

import com.ddc.DDC;
import com.ddc.character.CharacterSheet;
import java.util.Objects;
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
 * <p>Hit points do not ride along. They are vanilla health now, sized from the hit die, so the client
 * already has them and a copy here could only disagree.
 *
 * @param sheet the player's sheet
 */
public record CharacterSheetPayload(CharacterSheet sheet) implements CustomPacketPayload {

    public static final Type<CharacterSheetPayload> TYPE = new Type<>(DDC.id("character_sheet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSheetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(CharacterSheet.CODEC), CharacterSheetPayload::sheet,
                    CharacterSheetPayload::new);

    public CharacterSheetPayload {
        Objects.requireNonNull(sheet, "sheet");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
