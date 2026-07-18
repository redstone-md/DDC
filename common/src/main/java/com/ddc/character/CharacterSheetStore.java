package com.ddc.character;

import com.ddc.DDC;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every player's character sheet, saved with the world.
 *
 * <p>Kept in the world's saved data rather than attached to the player entity. Attachments would mean
 * a Fabric implementation and a NeoForge implementation of the same idea; one vanilla {@link
 * SavedData} is the same code on both loaders, and it survives death and dimension changes for free
 * because it was never tied to an entity in the first place.
 *
 * <p>Stored against the overworld, so there is one set of sheets per world rather than one per
 * dimension.
 */
public final class CharacterSheetStore extends SavedData {

    private static final Codec<Map<UUID, CharacterSheet>> SHEETS_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, CharacterSheet.CODEC);

    /** The file the sheets live in, under the overworld's data directory. */
    private static final String FILE = "ddc_character_sheets";

    private static SavedData.Factory<CharacterSheetStore> factory() {
        return new SavedData.Factory<>(CharacterSheetStore::new, CharacterSheetStore::load, DataFixTypes.LEVEL);
    }

    private static CharacterSheetStore load(CompoundTag tag, HolderLookup.Provider provider) {
        return new CharacterSheetStore(SHEETS_CODEC
                .parse(NbtOps.INSTANCE, tag.get("sheets"))
                .result()
                .orElseGet(Map::of));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SHEETS_CODEC.encodeStart(NbtOps.INSTANCE, sheets).result().ifPresent(nbt -> tag.put("sheets", nbt));
        return tag;
    }

    private final Map<UUID, CharacterSheet> sheets;

    public CharacterSheetStore() {
        this(Map.of());
    }

    private CharacterSheetStore(Map<UUID, CharacterSheet> sheets) {
        this.sheets = new HashMap<>(sheets);
    }

    /** The store for this server, creating it on first use. */
    public static CharacterSheetStore of(MinecraftServer server) {
        ServerLevel overworld = Objects.requireNonNull(server.overworld(), "overworld");
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    /** This player's sheet, a fresh one if they have never had a sheet before. */
    public CharacterSheet get(UUID player) {
        return sheets.getOrDefault(Objects.requireNonNull(player, "player"), CharacterSheet.initial());
    }

    /** Replaces a player's sheet and marks the world to be saved. */
    public void put(UUID player, CharacterSheet sheet) {
        sheets.put(Objects.requireNonNull(player, "player"), Objects.requireNonNull(sheet, "sheet"));
        setDirty();
    }

    /** Applies a change to a player's sheet, returning the updated sheet. */
    public CharacterSheet update(UUID player, java.util.function.UnaryOperator<CharacterSheet> change) {
        CharacterSheet updated = change.apply(get(player));
        put(player, updated);
        return updated;
    }
}
