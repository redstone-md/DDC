package com.ddc.gm;

import com.ddc.network.NarrationPayload;
import dev.architectury.networking.NetworkManager;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the Game Master's narration to the table.
 *
 * <p>Every path to a narration packet goes through here, and every path through here checks that the
 * sender is a GM. That is the ADR-0003 gate: a client can ask, but only the server sends.
 */
public final class NarrationService {

    /**
     * Narrates to every player on the server, including the GM.
     *
     * @return how many players were told
     * @throws IllegalArgumentException if the sender is not a GM, which means a caller skipped its
     *                                  own check and is a bug worth failing loudly
     */
    public int narrate(ServerPlayer gameMaster, String text) {
        if (!GameMasters.isGameMaster(gameMaster)) {
            throw new IllegalArgumentException(
                    "Refusing to narrate for a non-GM: " + gameMaster.getGameProfile().name());
        }
        List<ServerPlayer> audience = gameMaster.level().getServer().getPlayerList().getPlayers();
        NetworkManager.sendToPlayers(audience, new NarrationPayload(text));
        return audience.size();
    }

    /** Whether a line fits the payload's cap, so a command can say so rather than throwing. */
    public boolean isWithinLimit(String text) {
        return text.length() <= NarrationPayload.MAX_LENGTH;
    }
}
