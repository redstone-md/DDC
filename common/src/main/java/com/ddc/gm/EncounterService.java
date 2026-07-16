package com.ddc.gm;

import com.ddc.rules.Encounter;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Drops a Game Master's encounter into the world.
 *
 * <p>Every path here checks the caller is a GM first, the same gate every other GM capability uses.
 * Spawning monsters on demand is exactly the power ADR-0003 says a hacked client must never reach.
 */
public final class EncounterService {

    /** How far apart the group is scattered, in blocks, so they do not spawn inside each other. */
    private static final double SPREAD = 1.5;

    /** Why a spawn did not happen. A key: the client picks the language. */
    public enum Failure {
        NOT_A_GAME_MASTER("ddc.error.not_gm"),
        UNKNOWN_ENTITY("ddc.error.unknown_entity");

        private final String key;

        Failure(String key) {
            this.key = key;
        }

        public net.minecraft.network.chat.Component message() {
            return net.minecraft.network.chat.Component.translatable(key);
        }
    }

    /**
     * Spawns an encounter around a point.
     *
     * @return how many mobs were spawned, or why none were
     */
    public Result spawn(ServerPlayer gameMaster, Encounter encounter, ServerLevel level, Vec3 centre) {
        if (!GameMasters.isGameMaster(gameMaster)) {
            return new Result(Optional.of(Failure.NOT_A_GAME_MASTER), 0);
        }

        int spawned = 0;
        int index = 0;
        for (Encounter.Member member : encounter.members()) {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(member.entity());
            if (type.isEmpty()) {
                return new Result(Optional.of(Failure.UNKNOWN_ENTITY), spawned);
            }
            for (int i = 0; i < member.count(); i++) {
                if (spawnOne(type.get(), level, offsetFor(centre, index++))) {
                    spawned++;
                }
            }
        }
        return new Result(Optional.empty(), spawned);
    }

    /** Spirals the group outward so a four-mob patrol does not arrive as one mob-shaped pile. */
    private static Vec3 offsetFor(Vec3 centre, int index) {
        double angle = index * (Math.PI * 2 / 5);
        double radius = SPREAD * (1 + index / 5.0);
        return centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    private static boolean spawnOne(EntityType<?> type, ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        Entity entity = type.spawn(level, pos, EntitySpawnReason.COMMAND);
        return entity != null;
    }

    /**
     * What a spawn attempt did.
     *
     * @param failure why it stopped, empty when it did not
     * @param spawned how many mobs made it into the world, even if it then stopped
     */
    public record Result(Optional<Failure> failure, int spawned) {

        public boolean isSuccess() {
            return failure.isEmpty();
        }
    }
}
