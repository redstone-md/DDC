package com.ddc.rules;

import com.ddc.DDC;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Every character class the loaded data packs define.
 *
 * <p>The scan is by directory, not by namespace: {@link FileToIdConverter} walks {@code
 * ddc_classes/} across every active pack, so {@code data/ddc/ddc_classes/fighter.json} and
 * {@code data/some_addon/ddc_classes/paladin.json} both land here with no registration step and no
 * Java from the addon, exactly as ADR-0002 requires. Pack order resolves conflicts, which is what
 * lets a world pack override an addon's numbers.
 *
 * <p>Lives on the server. The client learns about classes through sync, never by reading packs.
 */
public final class CharacterClassRegistry extends SimpleJsonResourceReloadListener<CharacterClass> {

    /** The data pack directory addons and world packs write into. */
    public static final String DIRECTORY = "ddc_classes";

    private volatile Map<Identifier, CharacterClass> classes = Map.of();

    public CharacterClassRegistry() {
        super(CharacterClass.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, CharacterClass> parsed, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        // A file that fails its codec never reaches this map; Minecraft logs it against its own name,
        // so a broken addon reports itself rather than taking the reload down.
        classes = Map.copyOf(parsed);
        DDC.LOGGER.info("Loaded {} character class(es): {}", classes.size(), classes.keySet());
    }

    public Optional<CharacterClass> get(Identifier id) {
        return Optional.ofNullable(classes.get(id));
    }

    public Set<Identifier> ids() {
        return Collections.unmodifiableSet(classes.keySet());
    }

    public boolean isEmpty() {
        return classes.isEmpty();
    }
}
