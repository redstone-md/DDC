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
import net.minecraft.resources.Identifier;

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
 * @param classes what {@code ddc_classes} defines
 * @param races   what {@code ddc_races} defines
 * @param spells  what {@code ddc_spells} defines
 */
public record RulesPayload(List<Entry> classes, List<Entry> races, List<Entry> spells)
        implements CustomPacketPayload {

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
    public record Entry(Identifier id, String name, int level) {

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Entry::id,
                ByteBufCodecs.stringUtf8(MAX_NAME), Entry::name,
                ByteBufCodecs.VAR_INT, Entry::level,
                Entry::new);

        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }

        public static Entry of(Identifier id, String name) {
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
                    RulesPayload::new);

    public RulesPayload {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        races = List.copyOf(Objects.requireNonNull(races, "races"));
        spells = List.copyOf(Objects.requireNonNull(spells, "spells"));
    }

    /** Reads the registries into the few facts a menu needs. Sorted, so menus do not shuffle. */
    public static RulesPayload of(DataRegistry<CharacterClass> classes, DataRegistry<Race> races,
            DataRegistry<Spell> spells) {
        return new RulesPayload(
                entries(classes, definition -> Entry.of(null, definition.name())),
                entries(races, definition -> Entry.of(null, definition.name())),
                entries(spells, spell -> new Entry(null, spell.name(), spell.level())));
    }

    private static <T> List<Entry> entries(DataRegistry<T> registry,
            java.util.function.Function<T, Entry> describe) {
        return registry.ids().stream()
                .sorted(Identifier::compareTo)
                .map(id -> {
                    Entry described = registry.get(id).map(describe).orElse(null);
                    return described == null ? null : new Entry(id, described.name(), described.level());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
