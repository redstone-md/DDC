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
 * <p>The class's summary rides along: the definition lives in a data pack the client does not have,
 * and without it a client cannot print the class's name or know whether to offer Cast. What it gets
 * is the answers, never the rules.
 *
 * <p>Armour class does ride along, because it cannot be worked out from the sheet alone: it is the
 * sheet's Dexterity and the armour the player is wearing, and the rule that combines them is the
 * server's. The client draws the number; it never decides it.
 *
 * @param sheet      the player's sheet
 * @param summary    what their class can do, absent until they have picked one
 * @param armorClass the number an attack must beat, as the server works it out
 */
public record CharacterSheetPayload(CharacterSheet sheet, java.util.Optional<ClassSummary> summary,
        int armorClass) implements CustomPacketPayload {

    public static final Type<CharacterSheetPayload> TYPE = new Type<>(DDC.id("character_sheet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSheetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(CharacterSheet.CODEC), CharacterSheetPayload::sheet,
                    ClassSummary.STREAM_CODEC.apply(ByteBufCodecs::optional), CharacterSheetPayload::summary,
                    ByteBufCodecs.VAR_INT, CharacterSheetPayload::armorClass,
                    CharacterSheetPayload::new);

    public CharacterSheetPayload {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(summary, "summary");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
