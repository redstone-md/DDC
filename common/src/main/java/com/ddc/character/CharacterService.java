package com.ddc.character;

import com.ddc.network.CharacterSheetPayload;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.CharacterClassRegistry;
import dev.architectury.networking.NetworkManager;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reads and writes character sheets, and keeps the owning client in step.
 *
 * <p>The one door to sheet state on the server. Every write goes through {@link #update}, so no path
 * can change a sheet and forget to sync it.
 */
public final class CharacterService {

    private final CharacterClassRegistry classes;

    public CharacterService(CharacterClassRegistry classes) {
        this.classes = classes;
    }

    public CharacterSheet get(ServerPlayer player) {
        return store(player).get(player.getUUID());
    }

    /** Applies a change, saves it, and sends the player their new sheet. */
    public CharacterSheet update(ServerPlayer player, UnaryOperator<CharacterSheet> change) {
        CharacterSheet updated = store(player).update(player.getUUID(), change);
        sync(player, updated);
        return updated;
    }

    /** Sends the player their sheet as it stands. Call on join and on respawn. */
    public void sync(ServerPlayer player) {
        sync(player, get(player));
    }

    private void sync(ServerPlayer player, CharacterSheet sheet) {
        NetworkManager.sendToPlayer(player, new CharacterSheetPayload(sheet, maxHitPoints(sheet)));
    }

    /**
     * Sets a player's class.
     *
     * @return empty if no loaded data pack defines that class, which the caller must report rather
     *         than silently ignore
     */
    public Optional<CharacterSheet> chooseClass(ServerPlayer player, Identifier classId) {
        return classes.get(classId)
                .map(definition -> update(player, sheet -> sheet.withClass(classId, definition)));
    }

    /** The sheet's maximum hit points, or empty when it has no class to derive them from. */
    public OptionalInt maxHitPoints(CharacterSheet sheet) {
        return sheet.characterClass()
                .flatMap(classes::get)
                .map(definition -> OptionalInt.of(sheet.maxHitPoints(definition)))
                .orElseGet(OptionalInt::empty);
    }

    /** The definition behind a sheet's class, empty when unset or when its pack is gone. */
    public Optional<CharacterClass> definitionFor(CharacterSheet sheet) {
        return sheet.characterClass().flatMap(classes::get);
    }

    private static CharacterSheetStore store(ServerPlayer player) {
        return CharacterSheetStore.of(player.level().getServer());
    }
}
