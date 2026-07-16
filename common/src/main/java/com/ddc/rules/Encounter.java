package com.ddc.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/**
 * A group of monsters the Game Master can drop into the world, as a data pack describes it.
 *
 * <p>PRD 3.2's encounter generator: "Goblin Ambush" as a file rather than a hardcoded list, so a GM
 * writes their own campaign's encounters and an addon ships a bestiary, neither of them writing Java.
 *
 * <p>Example, {@code data/ddc/ddc_encounters/zombie_patrol.json}:
 * <pre>{@code
 * {
 *   "name": "Zombie Patrol",
 *   "members": [
 *     { "entity": "minecraft:zombie", "count": 3 },
 *     { "entity": "minecraft:skeleton", "count": 1 }
 *   ]
 * }
 * }</pre>
 *
 * @param name    the display name the GM sees
 * @param members what to spawn
 */
public record Encounter(String name, List<Member> members) {

    /** Enough for an ambush, few enough that one click cannot wedge a server. */
    public static final int MAX_TOTAL = 32;

    /**
     * The members are validated before the record is built, not inside it.
     *
     * <p>A codec that lets its constructor throw turns a typo in an addon into an exception during
     * the reload, which is the opposite of what ADR-0002 promises: a bad file must report itself and
     * leave the rest of the pack loading. Validating here makes it a {@link com.mojang.serialization.DataResult}
     * error that Minecraft logs against the offending file. The constructor keeps its own checks for
     * code that builds an encounter directly.
     */
    private static final Codec<List<Member>> MEMBERS = Member.CODEC.listOf().validate(members -> {
        if (members.isEmpty()) {
            return com.mojang.serialization.DataResult.error(() -> "An encounter needs at least one member");
        }
        int total = members.stream().mapToInt(Member::count).sum();
        if (total > MAX_TOTAL) {
            return com.mojang.serialization.DataResult.error(
                    () -> "An encounter of " + total + " exceeds the " + MAX_TOTAL
                            + " the server will spawn");
        }
        return com.mojang.serialization.DataResult.success(members);
    });

    public static final Codec<Encounter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Encounter::name),
            MEMBERS.fieldOf("members").forGetter(Encounter::members)
    ).apply(instance, Encounter::new));

    public Encounter {
        Objects.requireNonNull(name, "name");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("An encounter needs at least one member");
        }
        int total = members.stream().mapToInt(Member::count).sum();
        if (total > MAX_TOTAL) {
            throw new IllegalArgumentException(
                    "An encounter of " + total + " exceeds the " + MAX_TOTAL + " the server will spawn");
        }
    }

    /** How many mobs this encounter spawns in total. */
    public int total() {
        return members.stream().mapToInt(Member::count).sum();
    }

    /**
     * One kind of monster in the group.
     *
     * @param entity the entity type's id; unknown ids are reported when the encounter is spawned
     *               rather than at load, since another mod may register the type later
     * @param count  how many
     */
    public record Member(Identifier entity, int count) {

        public static final Codec<Member> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("entity").forGetter(Member::entity),
                Codec.intRange(1, MAX_TOTAL).optionalFieldOf("count", 1).forGetter(Member::count)
        ).apply(instance, Member::new));

        public Member {
            Objects.requireNonNull(entity, "entity");
        }
    }
}
