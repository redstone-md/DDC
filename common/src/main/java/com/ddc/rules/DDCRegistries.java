package com.ddc.rules;

import com.ddc.DDC;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.server.packs.PackType;

/**
 * The rules the loaded data packs define.
 *
 * <p>Four directories, one shape: {@code ddc_classes}, {@code ddc_races}, {@code ddc_spells} and
 * {@code ddc_encounters} are all scanned across every namespace, so an addon adds any of them by
 * dropping in JSON. All of them reload with {@code /reload}.
 */
public final class DDCRegistries {

    public static final DataRegistry<CharacterClass> CLASSES =
            new DataRegistry<>("ddc_classes", "character classes", CharacterClass.CODEC);

    public static final DataRegistry<Race> RACES =
            new DataRegistry<>("ddc_races", "races", Race.CODEC);

    public static final DataRegistry<Spell> SPELLS =
            new DataRegistry<>("ddc_spells", "spells", Spell.CODEC);

    public static final DataRegistry<Encounter> ENCOUNTERS =
            new DataRegistry<>("ddc_encounters", "encounters", Encounter.CODEC);

    /** Which blocks ask a character to roll before they open, as PRD 3.1 wants. */
    public static final DataRegistry<BlockCheck> BLOCK_CHECKS =
            new DataRegistry<>("ddc_checks", "block checks", BlockCheck.CODEC);

    private DDCRegistries() {
    }

    /** Hooks every registry up to data pack reloads. Called once from the shared bootstrap. */
    public static void register() {
        for (DataRegistry<?> registry : new DataRegistry<?>[] {CLASSES, RACES, SPELLS, ENCOUNTERS, BLOCK_CHECKS}) {
            ReloadListenerRegistry.register(PackType.SERVER_DATA, registry, DDC.id(registry.directory()));
        }
        // A reload can add a class or retire one, and a client whose menu still offers the old list
        // would be offering something that no longer exists.
        dev.architectury.event.events.common.LifecycleEvent.SERVER_LEVEL_LOAD.register(
                level -> sendTo(level.getServer()));
        dev.architectury.event.events.common.PlayerEvent.PLAYER_JOIN.register(DDCRegistries::sendTo);
    }

    /** Tells every player what the packs define. */
    public static void sendTo(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(DDCRegistries::sendTo);
        }
    }

    /** Tells one player what the packs define, and what they are allowed to do with it. */
    public static void sendTo(net.minecraft.server.level.ServerPlayer player) {
        dev.architectury.networking.NetworkManager.sendToPlayer(player,
                com.ddc.network.RulesPayload.of(CLASSES, RACES, SPELLS, ENCOUNTERS,
                        com.ddc.gm.GameMasters.isGameMaster(player)));
    }
}
