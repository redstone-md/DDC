package com.ddc.gm;

import com.ddc.DDC;
import dev.architectury.event.events.common.TickEvent;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/**
 * The Game Master driving a monster, from PRD 3.2 and ADR-0003.
 *
 * <p>The GM does not become the mob. They go to spectator, their camera moves to it, and the mob is
 * told to go where they go: the GM flies, and the monster follows the point they are flying at. That
 * is ADR-0003's "translate the GM's movement into pathfinding modifications" rather than mirroring
 * raw input, and it means no new input packet exists to forge -- the only thing that crosses the wire
 * is the GM's own ordinary movement, which the server already trusts as far as it trusts any player.
 *
 * <p>Everything here is server-side and gated by {@link GameMasters}. Possessing a mob is exactly the
 * power ADR-0003 says a hacked client must never reach.
 */
public final class PossessionService {

    /** How much tougher a possessed mob becomes: PRD 3.2's mini-boss. */
    private static final double BOSS_HEALTH_MULTIPLIER = 4.0;
    private static final double BOSS_DAMAGE_MULTIPLIER = 2.0;

    private static final Identifier HEALTH_MODIFIER = DDC.id("possessed_health");
    private static final Identifier DAMAGE_MODIFIER = DDC.id("possessed_damage");

    /** How fast a possessed mob chases the point its GM is at. */
    private static final double FOLLOW_SPEED = 1.3;

    /** Close enough that chasing further would only jitter. */
    private static final double ARRIVED = 1.5;

    private final Map<UUID, Possession> possessions = new ConcurrentHashMap<>();

    /** Why a possession did not happen. */
    public enum Failure {
        NOT_A_GAME_MASTER("Only a Game Master can possess a creature."),
        NOT_A_MOB("You can only possess a creature with a mind of its own."),
        ALREADY_POSSESSED("Someone is already driving that one."),
        ALREADY_DRIVING("You are already possessing something. Sneak to let go.");

        private final String message;

        Failure(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** Starts the per-tick work. Called once from the shared bootstrap. */
    public void register() {
        TickEvent.SERVER_POST.register(this::tick);
    }

    /**
     * Puts a Game Master into a mob.
     *
     * @return the reason it did not happen, or empty once it has
     */
    public Optional<Failure> possess(ServerPlayer gameMaster, LivingEntity target) {
        if (!GameMasters.isGameMaster(gameMaster)) {
            return Optional.of(Failure.NOT_A_GAME_MASTER);
        }
        if (!(target instanceof Mob mob)) {
            return Optional.of(Failure.NOT_A_MOB);
        }
        if (possessions.containsKey(gameMaster.getUUID())) {
            return Optional.of(Failure.ALREADY_DRIVING);
        }
        if (isPossessed(mob)) {
            return Optional.of(Failure.ALREADY_POSSESSED);
        }

        possessions.put(gameMaster.getUUID(),
                new Possession(gameMaster.getUUID(), mob, gameMaster.gameMode(), !mob.isNoAi()));

        scaleUp(mob);
        // The mob stops thinking for itself: two things steering one monster would fight each other.
        mob.setNoAi(false);
        mob.setTarget(null);

        gameMaster.setGameMode(GameType.SPECTATOR);
        gameMaster.setCamera(mob);
        gameMaster.sendSystemMessage(Component.literal(
                        "You are " + mob.getName().getString() + ". Sneak to let go.")
                .withStyle(ChatFormatting.GOLD), true);
        return Optional.empty();
    }

    /** Whether this mob already has a GM in it. */
    public boolean isPossessed(Mob mob) {
        return possessions.values().stream().anyMatch(possession -> possession.mob() == mob);
    }

    /** The mob this player is driving, if any. */
    public Optional<Mob> possessedBy(ServerPlayer player) {
        return Optional.ofNullable(possessions.get(player.getUUID())).map(Possession::mob);
    }

    /** Lets go, giving the GM back their body and the mob back its mind. */
    public void release(ServerPlayer gameMaster) {
        Possession possession = possessions.remove(gameMaster.getUUID());
        if (possession == null) {
            return;
        }
        restore(possession);

        gameMaster.setCamera(gameMaster);
        gameMaster.setGameMode(possession.previousMode());
        gameMaster.teleportTo(possession.mob().getX(), possession.mob().getY() + 1,
                possession.mob().getZ());
        gameMaster.sendSystemMessage(Component.literal("You let go.")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Runs every possession: the mob goes where its GM is, and looks where they look.
     *
     * <p>Sneaking lets go, which is the one control a spectator has left that means nothing else.
     * Dying lets go too: PRD 3.2 says the GM is ejected safely rather than dying with the monster.
     */
    private void tick(MinecraftServer server) {
        for (Possession possession : Map.copyOf(possessions).values()) {
            ServerPlayer gameMaster = server.getPlayerList().getPlayer(possession.gameMaster());
            if (gameMaster == null) {
                // The GM left. Give the mob its mind back rather than leaving a puppet with no hand.
                possessions.remove(possession.gameMaster());
                restore(possession);
                continue;
            }
            if (!possession.isAlive()) {
                gameMaster.sendSystemMessage(Component.literal("Your monster died. You are yourself again.")
                        .withStyle(ChatFormatting.RED), true);
                release(gameMaster);
                continue;
            }
            if (gameMaster.isCrouching()) {
                release(gameMaster);
                continue;
            }
            drive(gameMaster, possession.mob());
        }
    }

    /** Points the mob at where the GM is, and turns its head to match theirs. */
    private static void drive(ServerPlayer gameMaster, Mob mob) {
        mob.getLookControl().setLookAt(gameMaster.getLookAngle().scale(10).add(mob.position()));
        if (mob.distanceTo(gameMaster) > ARRIVED) {
            mob.getNavigation().moveTo(gameMaster.getX(), gameMaster.getY(), gameMaster.getZ(),
                    FOLLOW_SPEED);
        } else {
            mob.getNavigation().stop();
        }
    }

    /** Makes a possessed mob worth fearing. */
    private static void scaleUp(Mob mob) {
        multiply(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER, BOSS_HEALTH_MULTIPLIER);
        multiply(mob.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER, BOSS_DAMAGE_MULTIPLIER);
        mob.setHealth(mob.getMaxHealth());
    }

    /** Gives back everything the possession took. */
    private static void restore(Possession possession) {
        Mob mob = possession.mob();
        remove(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER);
        remove(mob.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER);
        if (mob.getHealth() > mob.getMaxHealth()) {
            mob.setHealth(mob.getMaxHealth());
        }
        mob.setNoAi(!possession.mobHadAi());
        mob.getNavigation().stop();
    }

    private static void multiply(AttributeInstance attribute, Identifier id, double factor) {
        if (attribute != null) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    id, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void remove(AttributeInstance attribute, Identifier id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }
}
