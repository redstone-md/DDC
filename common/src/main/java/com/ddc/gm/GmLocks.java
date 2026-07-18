package com.ddc.gm;

import com.ddc.DDC;
import com.ddc.core.character.Ability;
import com.ddc.rules.BlockCheck;
import com.ddc.rules.DDCCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The blocks a Game Master has locked: PRD 3.2's "lock/unlock door", and the trapped chest with it.
 *
 * <p>A data pack can already say that every iron door is hard to open. What it cannot say is that
 * <em>this</em> door, in this dungeon, is the one with the wizard's seal on it -- and that is the
 * thing a GM actually wants at the table. So a lock is a place rather than a kind of block.
 *
 * <p>Saved with the world, per dimension, because a dungeon that unsealed itself on restart would be
 * a dungeon the GM has to build twice. Kept beside the character sheets in the overworld's saved data
 * for the same reason they are: one file, both loaders, no attachments.
 */
public final class GmLocks extends SavedData {

    /** What a locked block asks for. The same shape a pack's own block check has, deliberately. */
    public record Lock(Ability ability, int dc, Optional<String> message) {

        public static final Codec<Lock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DDCCodecs.ABILITY.fieldOf("ability").forGetter(Lock::ability),
                Codec.intRange(1, 30).fieldOf("dc").forGetter(Lock::dc),
                Codec.STRING.optionalFieldOf("message").forGetter(Lock::message)
        ).apply(instance, Lock::new));

        public Lock {
            Objects.requireNonNull(ability, "ability");
            Objects.requireNonNull(message, "message");
        }

        /** The same rule a data pack writes, so one listener can roll for either. */
        public BlockCheck asCheck() {
            return new BlockCheck(ability, dc, message.orElse("ddc.check.block.sealed"));
        }
    }

    /** A lock's place: which world, and where in it. */
    private record Where(String dimension, BlockPos pos) {

        static final Codec<Where> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Where::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Where::pos)
        ).apply(instance, Where::new));

        static Where of(ServerLevel level, BlockPos pos) {
            return new Where(level.dimension().identifier().toString(), pos.immutable());
        }
    }

    private record Entry(Where where, Lock lock) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Where.CODEC.fieldOf("where").forGetter(Entry::where),
                Lock.CODEC.fieldOf("lock").forGetter(Entry::lock)
        ).apply(instance, Entry::new));
    }

    private static final Codec<GmLocks> CODEC = Entry.CODEC.listOf()
            .xmap(GmLocks::fromEntries, GmLocks::toEntries)
            .fieldOf("locks")
            .codec();

    private static final String FILE = "ddc_gm_locks";

    private static SavedData.Factory<GmLocks> factory() {
        return new SavedData.Factory<>(GmLocks::new, GmLocks::load, DataFixTypes.LEVEL);
    }

    private static GmLocks load(CompoundTag tag, HolderLookup.Provider provider) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElseGet(GmLocks::new);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result().ifPresent(nbt -> {
            if (nbt instanceof CompoundTag encoded) {
                encoded.getAllKeys().forEach(key -> tag.put(key, encoded.get(key)));
            }
        });
        return tag;
    }

    private final Map<Where, Lock> locks = new HashMap<>();

    public GmLocks() {
    }

    private static GmLocks fromEntries(java.util.List<Entry> entries) {
        GmLocks store = new GmLocks();
        entries.forEach(entry -> store.locks.put(entry.where(), entry.lock()));
        return store;
    }

    private java.util.List<Entry> toEntries() {
        return locks.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** The locks for this world, creating the store on first use. */
    public static GmLocks of(ServerLevel level) {
        return Objects.requireNonNull(level.getServer().overworld(), "overworld")
                .getDataStorage().computeIfAbsent(factory(), FILE);
    }

    /** What this block asks for before it opens, if a Game Master sealed it. */
    public Optional<Lock> at(ServerLevel level, BlockPos pos) {
        return Optional.ofNullable(locks.get(Where.of(level, pos)));
    }

    /** Seals a block. Sealing one that is already sealed replaces the seal rather than stacking one. */
    public void lock(ServerLevel level, BlockPos pos, Lock lock) {
        locks.put(Where.of(level, pos), lock);
        setDirty();
    }

    /** Unseals a block, and says whether there was anything there to unseal. */
    public boolean unlock(ServerLevel level, BlockPos pos) {
        boolean removed = locks.remove(Where.of(level, pos)) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** How many blocks are sealed. For the command to report, and for a test to check. */
    public int count() {
        return locks.size();
    }
}
