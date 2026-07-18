package com.ddc.network;

import com.ddc.DDC;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Race;
import com.ddc.rules.Spell;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The names of the rules a client is allowed to pick from.
 *
 * <p>ARCHITECTURE.md's "Registry --Sync S2C--> Client". Data packs live on the server, so without
 * this a client cannot name a single class: it has an id at best, and no way to know what else
 * exists. That is why picking a class was a command with an id typed by hand -- there was nothing for
 * a menu to draw.
 *
 * <p>Names and ids only. The rules themselves -- hit dice, slot tables, damage -- stay on the server,
 * because a client that held them would be a client that could be argued with about them.
 *
 * <p>Sent on join and again after {@code /reload}, so an addon added mid-session appears in the menu
 * without anyone reconnecting.
 *
 * @param classes     what {@code ddc_classes} defines
 * @param races       what {@code ddc_races} defines
 * @param spells      what {@code ddc_spells} defines
 * @param encounters  what {@code ddc_encounters} defines; only a Game Master is sent any
 * @param gameMaster  whether this player may use the GM tools, so their wheel can offer them
 */
public record RulesPayload(List<Entry> classes, List<Entry> races, List<Entry> spells,
        List<Entry> encounters, boolean gameMaster) implements CustomPacketPayload {

    public static final Type<RulesPayload> TYPE = new Type<>(DDC.id("rules"));

    /** Generous for any pack, bounded so a hostile server cannot make a client allocate on its word. */
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_NAME = 64;

    /**
     * One rule a player can pick.
     *
     * @param id    what the command takes
     * @param name  what the menu shows, as the pack wrote it
     * @param level a spell's level; zero for anything that has none
     */
    public record Entry(ResourceLocation id, String name, int level) {

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.stringUtf8(MAX_NAME), Entry::name,
                ByteBufCodecs.VAR_INT, Entry::level,
                Entry::new);

        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }

        public static Entry of(ResourceLocation id, String name) {
            return new Entry(id, name, 0);
        }
    }

    private static final StreamCodec<ByteBuf, List<Entry>> ENTRIES =
            ByteBufCodecs.collection(ArrayList::new, Entry.STREAM_CODEC, MAX_ENTRIES);

    public static final StreamCodec<RegistryFriendlyByteBuf, RulesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ENTRIES, RulesPayload::classes,
                    ENTRIES, RulesPayload::races,
                    ENTRIES, RulesPayload::spells,
                    ENTRIES, RulesPayload::encounters,
                    ByteBufCodecs.BOOL, RulesPayload::gameMaster,
                    RulesPayload::new);

    public RulesPayload {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        races = List.copyOf(Objects.requireNonNull(races, "races"));
        spells = List.copyOf(Objects.requireNonNull(spells, "spells"));
        encounters = List.copyOf(Objects.requireNonNull(encounters, "encounters"));
    }

    /**
     * Reads the registries into the few facts a menu needs. Sorted, so menus do not shuffle.
     *
     * <p>The encounters are sent only to a Game Master. It is not a secret worth guarding -- the
     * server refuses the spawn either way -- but a wheel that offers a player something they cannot
     * do is a wheel that lies to them.
     */
    public static RulesPayload of(DataRegistry<CharacterClass> classes, DataRegistry<Race> races,
            DataRegistry<Spell> spells, DataRegistry<com.ddc.rules.Encounter> encounters,
            boolean gameMaster) {
        return new RulesPayload(
                entries(classes, (id, definition) -> Entry.of(id, definition.name())),
                entries(races, (id, definition) -> Entry.of(id, definition.name())),
                entries(spells, (id, spell) -> new Entry(id, spell.name(), spell.level())),
                gameMaster
                        ? entries(encounters, (id, encounter) -> new Entry(id, encounter.name(), encounter.total()))
                        : List.of(),
                gameMaster);
    }

    /**
     * Describes every entry in a registry, dropping any the registry has an id for but no definition.
     *
     * <p>The id is handed to the describer rather than patched in afterwards, because an entry with no
     * id is not a thing that should ever exist for even one line: {@link Entry} refuses to be built
     * without one, and it is right to.
     */
    private static <T> List<Entry> entries(DataRegistry<T> registry,
            java.util.function.BiFunction<ResourceLocation, T, Entry> describe) {
        return registry.ids().stream()
                .sorted(ResourceLocation::compareTo)
                .flatMap(id -> registry.get(id).map(definition -> describe.apply(id, definition)).stream())
                .toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
