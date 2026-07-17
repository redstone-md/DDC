package com.ddc.network;

import com.ddc.rules.CharacterClass;
import com.ddc.rules.ClassFeature;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a character's class lets them do, in the few facts a client needs to draw a menu.
 *
 * <p>The class itself lives in a data pack, which is the server's. Rather than send the whole
 * definition -- most of which is rules the client must never apply -- this sends the answers: the
 * name to print, whether the wheel should offer Cast, and which features it should offer.
 *
 * <p>It is a summary, not authority. Every one of these actions is still a command the server checks.
 * A client that lied to itself here would draw a button that fails.
 *
 * @param name       the class's display name, as its data pack wrote it
 * @param canCast    whether the class casts spells at all
 * @param features   the once-per-rest features the class has
 * @param spellSlots how many slots of each spell level this character has at their level, in order
 *                   from first level upward; empty for a class that does not cast
 */
public record ClassSummary(String name, boolean canCast, Set<ClassFeature.Type> features,
        List<Integer> spellSlots) {

    /** A name long enough for any class, short enough that the wire cannot be abused. */
    private static final int MAX_NAME = 64;

    private static final ClassFeature.Type[] TYPES = ClassFeature.Type.values();

    private static final StreamCodec<ByteBuf, ClassFeature.Type> FEATURE = ByteBufCodecs.VAR_INT.map(
            ordinal -> {
                if (ordinal < 0 || ordinal >= TYPES.length) {
                    throw new IllegalArgumentException("Unknown class feature ordinal: " + ordinal);
                }
                return TYPES[ordinal];
            },
            Enum::ordinal);

    public static final StreamCodec<ByteBuf, ClassSummary> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME), ClassSummary::name,
            ByteBufCodecs.BOOL, ClassSummary::canCast,
            FEATURE.apply(ByteBufCodecs.collection(ArrayList::new)).map(Set::copyOf, ArrayList::new),
            ClassSummary::features,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.collection(ArrayList::new)).map(List::copyOf, ArrayList::new),
            ClassSummary::spellSlots,
            ClassSummary::new);

    public ClassSummary {
        Objects.requireNonNull(name, "name");
        features = features.isEmpty() ? EnumSet.noneOf(ClassFeature.Type.class) : EnumSet.copyOf(features);
        spellSlots = List.copyOf(Objects.requireNonNull(spellSlots, "spellSlots"));
    }

    /**
     * Summarises a class definition for the client that plays it, at the level they play it.
     *
     * <p>The slots are worked out here rather than sent as a table, because the client needs the
     * answer for one level and the table is the pack's business.
     */
    public static ClassSummary of(CharacterClass definition, int level) {
        return new ClassSummary(definition.name(), definition.canCast(),
                definition.features().stream().map(ClassFeature::type).collect(
                        java.util.stream.Collectors.toCollection(
                                () -> EnumSet.noneOf(ClassFeature.Type.class))),
                definition.spellcasting().map(casting -> casting.slotsAtLevel(level)).orElse(List.of()));
    }

    public boolean has(ClassFeature.Type feature) {
        return features.contains(feature);
    }
}
