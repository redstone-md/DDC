package com.ddc.gm;

import com.ddc.DDC;
import dev.architectury.event.events.common.TickEvent;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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

    private static final ResourceLocation HEALTH_MODIFIER = DDC.id("possessed_health");
    private static final ResourceLocation DAMAGE_MODIFIER = DDC.id("possessed_damage");

    /** How fast a possessed mob chases the point its GM is at. */
    private static final double FOLLOW_SPEED = 1.3;

    /** Close enough that chasing further would only jitter. */
    private static final double ARRIVED = 1.5;

    private final Map<UUID, Possession> possessions = new ConcurrentHashMap<>();

    /** Why a possession did not happen. A key: the client picks the language. */
    public enum Failure {
        NOT_A_GAME_MASTER("ddc.error.not_gm"),
        NOT_A_MOB("ddc.error.not_a_mob"),
        ALREADY_POSSESSED("ddc.error.already_possessed"),
        ALREADY_DRIVING("ddc.error.already_driving");

        private final String key;

        Failure(String key) {
            this.key = key;
        }

        public net.minecraft.network.chat.Component message() {
            return net.minecraft.network.chat.Component.translatable(key);
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
        gameMaster.sendSystemMessage(Component.translatable("ddc.gm.possessing", mob.getName())
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
        // A monster you are no longer driving has no cooldowns of yours on it: the next one is a
        // fresh monster, and a GM who let go to reposition should not find its breath still spent.
        for (BossAbility ability : BossAbility.values()) {
            readyAt.remove(key(gameMaster, ability));
        }
        Possession possession = possessions.remove(gameMaster.getUUID());
        if (possession == null) {
            return;
        }
        restore(possession);

        gameMaster.setCamera(gameMaster);
        gameMaster.setGameMode(possession.previousMode());
        gameMaster.teleportTo(possession.mob().getX(), possession.mob().getY() + 1,
                possession.mob().getZ());
        gameMaster.sendSystemMessage(Component.translatable("ddc.gm.released")
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
                gameMaster.sendSystemMessage(Component.translatable("ddc.gm.mob_died")
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

    /**
     * The possessed monster attacks whatever its Game Master is looking at.
     *
     * <p>The target is worked out here, from the mob's own reach, rather than named by the client:
     * ADR-0003's rule is that a client's word is never taken for who may be hit, and a packet that
     * carried a target would be exactly that.
     *
     * <p>The mob's own attack is what lands, so its damage, its knockback and its enchantments are
     * whatever the mob's are -- including the boss scaling a possession already applies.
     */
    public boolean attack(ServerPlayer gameMaster) {
        Mob mob = possessedBy(gameMaster).orElse(null);
        if (mob == null || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = lookedAt(gameMaster, mob);
        if (target == null) {
            return false;
        }
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return mob.doHurtTarget(level, target);
    }

    /**
     * Uses one of the monster's own abilities: PRD 3.2's "Fire Breath", "Web Spray".
     *
     * <p>The cooldown is kept here rather than on the client, for the reason every rule in this mod is
     * on the server: a client that kept its own cooldown would be a client that could choose not to.
     *
     * @return how many ticks are left, or empty when it happened
     */
    public java.util.OptionalInt useAbility(ServerPlayer gameMaster, BossAbility ability) {
        Mob mob = possessedBy(gameMaster).orElse(null);
        if (mob == null || !(mob.level() instanceof ServerLevel level)) {
            return java.util.OptionalInt.of(0);
        }
        long now = level.getGameTime();
        long ready = readyAt.getOrDefault(key(gameMaster, ability), 0L);
        if (now < ready) {
            return java.util.OptionalInt.of((int) (ready - now));
        }
        ability.perform(level, mob, gameMaster);
        readyAt.put(key(gameMaster, ability), now + ability.cooldownTicks());
        return java.util.OptionalInt.empty();
    }

    /** When each GM's each ability is ready again. Forgotten when they let go: see {@link #release}. */
    private final Map<String, Long> readyAt = new java.util.concurrent.ConcurrentHashMap<>();

    private static String key(ServerPlayer gameMaster, BossAbility ability) {
        return gameMaster.getUUID() + ":" + ability.id();
    }

    /**
     * What the GM is pointing the monster at, within the monster's reach.
     *
     * <p>Nearest to the line of sight rather than nearest to the mob: a GM aiming at the cleric behind
     * the fighter means the cleric, and a monster that always mauled whoever was closest would be
     * playing itself.
     */
    private static LivingEntity lookedAt(ServerPlayer gameMaster, Mob mob) {
        net.minecraft.world.phys.Vec3 look = gameMaster.getLookAngle();
        LivingEntity best = null;
        double bestAlignment = ATTACK_CONE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(ATTACK_REACH))) {
            if (candidate == mob || candidate == gameMaster || !candidate.isAlive()) {
                continue;
            }
            net.minecraft.world.phys.Vec3 toward = candidate.getEyePosition()
                    .subtract(mob.getEyePosition()).normalize();
            double alignment = look.dot(toward);
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                best = candidate;
            }
        }
        return best;
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

    /** How far a possessed monster can reach, in blocks. Generous: a boss is bigger than a player. */
    private static final double ATTACK_REACH = 4.0;

    /**
     * How closely the GM must be looking at something to hit it: the dot product of the two, so 0.5
     * is roughly a sixty-degree cone. Aiming near enough is aiming.
     */
    private static final double ATTACK_CONE = 0.5;

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

    private static void multiply(AttributeInstance attribute, ResourceLocation id, double factor) {
        if (attribute != null) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    id, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void remove(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }
}
