package com.ddc.character;

import com.ddc.network.CharacterSheetPayload;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.DataRegistry;
import dev.architectury.networking.NetworkManager;
import java.util.Optional;
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

    private final DataRegistry<CharacterClass> classes;
    private final HealthService health = new HealthService(this);

    public CharacterService(DataRegistry<CharacterClass> classes) {
        this.classes = classes;
    }

    /** Sizes a player's health from their sheet. */
    public HealthService health() {
        return health;
    }

    public CharacterSheet get(ServerPlayer player) {
        return store(player).get(player.getUUID());
    }

    /**
     * Applies a change, saves it, sizes the player's health to it, and sends them the new sheet.
     *
     * <p>Health is resized here rather than at each call site: a change to level, class or
     * Constitution moves the maximum, and a path that forgot to say so would leave a character with
     * the wrong number of hit points.
     */
    public CharacterSheet update(ServerPlayer player, UnaryOperator<CharacterSheet> change) {
        CharacterSheet updated = store(player).update(player.getUUID(), change);
        health.apply(player);
        sync(player, updated);
        return updated;
    }

    /** Sends the player their sheet and sizes their health. Call on join and on respawn. */
    public void sync(ServerPlayer player) {
        health.apply(player);
        sync(player, get(player));
    }

    private void sync(ServerPlayer player, CharacterSheet sheet) {
        NetworkManager.sendToPlayer(player, new CharacterSheetPayload(sheet));
    }

    /**
     * Sets a player's class, and fills the health that class gives them.
     *
     * @return empty if no loaded data pack defines that class, which the caller must report rather
     *         than silently ignore
     */
    public Optional<CharacterSheet> chooseClass(ServerPlayer player, Identifier classId) {
        return classes.get(classId).map(definition -> {
            CharacterSheet updated = update(player, sheet -> sheet.withClass(classId, definition));
            health.applyAndHeal(player);
            return updated;
        });
    }

    /** The definition behind a sheet's class, empty when unset or when its pack is gone. */
    public Optional<CharacterClass> definitionFor(CharacterSheet sheet) {
        return sheet.characterClass().flatMap(classes::get);
    }

    private static CharacterSheetStore store(ServerPlayer player) {
        return CharacterSheetStore.of(player.level().getServer());
    }
}
