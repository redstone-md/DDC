package com.ddc.rules;

import com.ddc.DDC;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * One data pack directory's worth of rules.
 *
 * <p>The scan is by directory, not by namespace: {@link FileToIdConverter} walks {@code <directory>/}
 * across every active pack, so {@code data/ddc/ddc_spells/fireball.json} and
 * {@code data/some_addon/ddc_spells/eldritch_blast.json} both land here with no registration step and
 * no Java from the addon, which is what ADR-0002 promises. Pack order resolves conflicts, which is
 * what lets a world pack override an addon's numbers.
 *
 * <p>Lives on the server. Clients learn about rules through sync, never by reading packs.
 *
 * @param <T> what a file in this directory describes
 */
public class DataRegistry<T> extends SimpleJsonResourceReloadListener {

    private final String directory;
    private final String describes;
    private volatile Map<ResourceLocation, T> entries = Map.of();

    /**
     * @param directory the data pack directory to scan, such as {@code ddc_spells}
     * @param describes what the entries are, plural, for the log line, such as {@code spells}
     */
    private static final Gson GSON = new Gson();

    private final Codec<T> codec;

    public DataRegistry(String directory, String describes, Codec<T> codec) {
        super(GSON, directory);
        this.directory = directory;
        this.describes = describes;
        this.codec = codec;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        // Each file is parsed through the codec here. One that fails is logged against its own name
        // and dropped, so a broken addon reports itself rather than taking the reload down.
        Map<ResourceLocation, T> parsed = new HashMap<>();
        object.forEach((id, json) -> codec.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> DDC.LOGGER.error("Skipping {} '{}': {}", describes, id, error))
                .ifPresent(value -> parsed.put(id, value)));
        entries = Map.copyOf(parsed);
        DDC.LOGGER.info("Loaded {} {}: {}", entries.size(), describes, entries.keySet());
        // ARCHITECTURE 3's registry callback: an addon that wants to react to what a pack defined has
        // something to hook, rather than the map changing in silence.
        DDCRegistryEvents.announce(this, entries);
    }

    public String directory() {
        return directory;
    }

    public Optional<T> get(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    /**
     * Sets the entries directly, standing in for a reload. Package-visible so only a test in this
     * package can reach it -- a registry anything could write to would be a registry no pack owned.
     */
    void putForTest(Map<ResourceLocation, T> loaded) {
        entries = Map.copyOf(loaded);
        DDCRegistryEvents.announce(this, entries);
    }

    public Set<ResourceLocation> ids() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
