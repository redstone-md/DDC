package com.ddc.character;

import com.ddc.network.PartyPayload;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps every client's picture of the party up to date.
 *
 * <p>Health changes constantly and nothing announces it, so this looks rather than listens: once a
 * second it builds the party and sends it, and only if it changed since last time. A packet per hit
 * would be a packet per tick in a fight; a packet a second that usually is not sent at all is the
 * cheaper bargain, and a health bar that is a second stale is a health bar nobody notices.
 */
public final class PartyService {

    /** How often the party is looked at, in ticks. */
    private static final int INTERVAL_TICKS = 20;

    private final CharacterService characters;
    private int countdown = INTERVAL_TICKS;
    private PartyPayload last = new PartyPayload(List.of());

    public PartyService(CharacterService characters) {
        this.characters = characters;
    }

    public void register() {
        TickEvent.SERVER_POST.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (--countdown > 0) {
            return;
        }
        countdown = INTERVAL_TICKS;

        PartyPayload party = build(server);
        if (party.equals(last)) {
            return;
        }
        last = party;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkManager.sendToPlayer(player, party);
        }
    }

    /** Everyone who has made a character. A player with no class is not in a party yet. */
    private PartyPayload build(MinecraftServer server) {
        return new PartyPayload(server.getPlayerList().getPlayers().stream()
                .flatMap(player -> member(player).stream())
                .toList());
    }

    private java.util.Optional<PartyPayload.Member> member(ServerPlayer player) {
        CharacterSheet sheet = characters.get(player);
        return characters.definitionFor(sheet).map(definition -> new PartyPayload.Member(
                player.getGameProfile().name(),
                definition.name(),
                sheet.level(),
                Math.round(player.getHealth()),
                Math.round(player.getMaxHealth()),
                sheet.experience(),
                definition.leveling().remainingTo(sheet.experience()).orElse(0) + sheet.experience()));
    }
}
