package com.ddc.character;

import com.ddc.core.dice.RollResult;
import com.ddc.dice.DiceRollService;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.ClassFeature;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The class features a player triggers themselves: the fighter's second wind, the cleric's channel
 * divinity.
 *
 * <p>Each is per rest. Uses live on the sheet beside spell slots, because they are the same idea --
 * something a rest gives back -- and {@code /ddc rest} already clears them. Most features are worth
 * one use; the fighter's superiority dice are worth several, which is why the sheet counts uses
 * rather than flagging them.
 */
public final class FeatureService {

    private final CharacterService characters;
    private final DiceRollService rolls;

    public FeatureService(CharacterService characters, DiceRollService rolls) {
        this.characters = characters;
        this.rolls = rolls;
    }

    /** Why a feature could not be used. A key: the client picks the language. */
    public enum Failure {
        NO_CLASS("ddc.error.no_class"),
        NOT_YOURS("ddc.error.feature_not_yours"),
        ALREADY_USED("ddc.error.feature_used"),
        NO_TARGET("ddc.error.no_target");

        private final String key;

        Failure(String key) {
            this.key = key;
        }

        public net.minecraft.network.chat.Component message() {
            return net.minecraft.network.chat.Component.translatable(key);
        }
    }

    /** What using a feature did, for the player's feedback. */
    public record Used(net.minecraft.network.chat.Component message) {
    }

    /**
     * Catches a fighter's breath: heals the feature's dice plus their level, once per rest.
     *
     * <p>Adding the level is the SRD's own rule, and it is what keeps second wind worth using at
     * higher levels rather than becoming a rounding error.
     */
    public Either<Failure, Used> secondWind(ServerPlayer player) {
        return use(player, ClassFeature.SecondWind.class, ClassFeature.Type.SECOND_WIND, (sheet, feature) -> {
            RollResult roll = rolls.rollPublic(player, feature.dice(), com.ddc.core.dice.RollMode.NORMAL);
            int healed = roll.total() + sheet.level();
            player.heal(healed);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.5f, 1.6f);
            return new Used(net.minecraft.network.chat.Component.translatable(
                    "ddc.feature.second_wind", healed));
        });
    }

    /**
     * Turns the undead: the SRD's cleric drives them off rather than killing them.
     *
     * <p>Minecraft has no fleeing state DDC can set from here, so a turned undead is slowed and made
     * weak and visible instead: it stops being the threat it was, and the table can see who is
     * affected, which is what the moment is for.
     */
    public Either<Failure, Used> channelDivinity(ServerPlayer player, Divinity what) {
        return use(player, ClassFeature.ChannelDivinity.class, ClassFeature.Type.CHANNEL_DIVINITY,
                (sheet, feature) -> {
                    ServerLevel level = player.serverLevel();
                    Used used = switch (what) {
                        case TURN -> turnUndead(player, level, feature);
                        case HEAL -> mendTheParty(player, level, feature);
                        case BLESS -> blessTheParty(player, level, feature);
                    };
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1,
                            player.getZ(), 60, feature.radius() / 2, 1.0, feature.radius() / 2, 0.05);
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 1.0f, 1.4f);
                    return used;
                });
    }

    /** What a cleric can spend their channel on. */
    public enum Divinity {
        TURN("turn"),
        HEAL("heal"),
        BLESS("bless");

        private final String id;

        Divinity(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** The one a command named, if it named one. */
        public static Optional<Divinity> byId(String key) {
            for (Divinity value : values()) {
                if (value.id.equalsIgnoreCase(key)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Turns the undead: the SRD's cleric drives them off rather than killing them.
     *
     * <p>Minecraft has no fleeing state DDC can set from here, so a turned undead is slowed and made
     * weak and visible instead: it stops being the threat it was, and the table can see who is
     * affected, which is what the moment is for.
     */
    private static Used turnUndead(ServerPlayer player, ServerLevel level,
            ClassFeature.ChannelDivinity feature) {
        int ticks = feature.seconds() * 20;
        int turnedCount = 0;
        for (LivingEntity undead : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(feature.radius()),
                entity -> entity != player && isUndead(entity))) {
            undead.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2));
            undead.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
            undead.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            turnedCount++;
        }
        return new Used(net.minecraft.network.chat.Component.translatable(
                "ddc.feature.channel_divinity", turnedCount));
    }

    /**
     * Mends everyone nearby, the cleric included.
     *
     * <p>One roll for the whole party rather than a roll each: it is one channel, and five separate
     * d8s for one button would bury the moment in dice.
     */
    private Used mendTheParty(ServerPlayer player, ServerLevel level,
            ClassFeature.ChannelDivinity feature) {
        RollResult roll = rolls.rollPublic(player, feature.heal(), com.ddc.core.dice.RollMode.NORMAL);
        int healed = 0;
        for (ServerPlayer ally : level.getServer().getPlayerList().getPlayers()) {
            if (ally.distanceTo(player) <= feature.radius() && ally.isAlive()) {
                ally.heal(roll.total());
                healed++;
            }
        }
        return new Used(net.minecraft.network.chat.Component.translatable(
                "ddc.feature.channel_heal", roll.total(), healed));
    }

    /** Steadies everyone nearby: PRD 3.1's "buff attributes", in the words this game has for it. */
    private static Used blessTheParty(ServerPlayer player, ServerLevel level,
            ClassFeature.ChannelDivinity feature) {
        int ticks = feature.seconds() * 20;
        int blessed = 0;
        for (ServerPlayer ally : level.getServer().getPlayerList().getPlayers()) {
            if (ally.distanceTo(player) <= feature.radius() && ally.isAlive()) {
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
                blessed++;
            }
        }
        return new Used(net.minecraft.network.chat.Component.translatable(
                "ddc.feature.channel_bless", blessed, feature.seconds()));
    }

    /**
     * Action surge: a few seconds of swinging and moving faster than everyone else.
     *
     * <p>The SRD grants an extra action. There are no turns here to take one in, so the surge is
     * spent in the currency this game does have -- time. Haste is vanilla's own name for swinging
     * faster, so the effect is vanilla's rather than a second system that means the same thing.
     */
    public Either<Failure, Used> actionSurge(ServerPlayer player) {
        return use(player, ClassFeature.ActionSurge.class, ClassFeature.Type.ACTION_SURGE,
                (sheet, feature) -> {
                    int ticks = feature.seconds() * 20;
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 2));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1));
                    player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                            SoundSource.PLAYERS, 1.0f, 1.4f);
                    return new Used(net.minecraft.network.chat.Component.translatable(
                            "ddc.feature.action_surge", feature.seconds()));
                });
    }

    /**
     * A manoeuvre: spend a superiority die, hurt them with it, and do something to them besides.
     *
     * <p>The die is rolled in public, because the table is watching the fighter do this. What the
     * manoeuvre does to the target is the SRD's intent translated into what this game can express --
     * see {@link Maneuver}, where each translation is argued one at a time.
     */
    public Either<Failure, Used> maneuver(ServerPlayer player, Maneuver maneuver, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return Either.left(Failure.NO_TARGET);
        }
        return use(player, ClassFeature.CombatSuperiority.class, ClassFeature.Type.COMBAT_SUPERIORITY,
                (sheet, feature) -> {
                    RollResult roll = rolls.rollPublic(player, feature.dice(),
                            com.ddc.core.dice.RollMode.NORMAL);
                    target.hurt(player.damageSources().playerAttack(player), roll.total());
                    apply(maneuver, player, target);
                    return new Used(net.minecraft.network.chat.Component.translatable(
                            "ddc.feature.maneuver." + maneuver.id(), roll.total(),
                            target.getDisplayName()));
                }, feature -> feature.uses());
    }

    /** What each manoeuvre does once its die has been spent. */
    private static void apply(Maneuver maneuver, ServerPlayer player, LivingEntity target) {
        switch (maneuver) {
            case TRIP -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
            case PARRY -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
            case PUSH -> target.push(
                    (target.getX() - player.getX()) * 0.6, 0.35, (target.getZ() - player.getZ()) * 0.6);
        }
    }

    /** Whether an entity is undead, which is a vanilla notion DDC borrows rather than redefines. */
    private static boolean isUndead(LivingEntity entity) {
        return entity.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD);
    }

    /** The shared shape of every once-per-rest feature: find it, check it is unspent, spend it. */
    private <T extends ClassFeature> Either<Failure, Used> use(ServerPlayer player, Class<T> kind,
            ClassFeature.Type type, java.util.function.BiFunction<CharacterSheet, T, Used> action) {
        return use(player, kind, type, action, feature -> 1);
    }

    /**
     * The same, for a feature with more than one use in it.
     *
     * <p>How many uses a feature has is the feature's own business -- a pack decides how many
     * superiority dice a fighter carries -- so the allowance is asked of it rather than assumed.
     */
    private <T extends ClassFeature> Either<Failure, Used> use(ServerPlayer player, Class<T> kind,
            ClassFeature.Type type, java.util.function.BiFunction<CharacterSheet, T, Used> action,
            java.util.function.ToIntFunction<T> allowance) {
        CharacterSheet sheet = characters.get(player);
        Optional<CharacterClass> definition = characters.definitionFor(sheet);
        if (definition.isEmpty()) {
            return Either.left(Failure.NO_CLASS);
        }
        Optional<T> feature = definition.get().feature(kind);
        if (feature.isEmpty()) {
            return Either.left(Failure.NOT_YOURS);
        }
        if (sheet.featureUses(type) >= allowance.applyAsInt(feature.get())) {
            return Either.left(Failure.ALREADY_USED);
        }

        Used used = action.apply(sheet, feature.get());
        characters.update(player, current -> current.withFeatureUsed(type));
        return Either.right(used);
    }

    /** A result or a reason it is not one. Mirrors the spell service's, for the same reason. */
    public sealed interface Either<L, R> {

        static <L, R> Either<L, R> left(L value) {
            return new Left<>(value);
        }

        static <L, R> Either<L, R> right(R value) {
            return new Right<>(value);
        }

        record Left<L, R>(L value) implements Either<L, R> {
        }

        record Right<L, R>(R value) implements Either<L, R> {
        }
    }
}
