package com.ddc.rules;

import com.ddc.DDC;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.mojang.serialization.Codec;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
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
public class DataRegistry<T> extends SimpleJsonResourceReloadListener<T> {

    private final String directory;
    private final String describes;
    private volatile Map<Identifier, T> entries = Map.of();

    /**
     * @param directory the data pack directory to scan, such as {@code ddc_spells}
     * @param describes what the entries are, plural, for the log line, such as {@code spells}
     */
    public DataRegistry(String directory, String describes, Codec<T> codec) {
        super(codec, FileToIdConverter.json(directory));
        this.directory = directory;
        this.describes = describes;
    }

    @Override
    protected void apply(Map<Identifier, T> parsed, ResourceManager resourceManager, ProfilerFiller profiler) {
        // A file that fails its codec never reaches this map; Minecraft logs it against its own name,
        // so a broken addon reports itself rather than taking the reload down.
        entries = Map.copyOf(parsed);
        DDC.LOGGER.info("Loaded {} {}: {}", entries.size(), describes, entries.keySet());
    }

    public String directory() {
        return directory;
    }

    public Optional<T> get(Identifier id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Set<Identifier> ids() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
