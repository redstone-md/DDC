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
 * <p>Each is once per rest. Uses live on the sheet beside spell slots, because they are the same idea
 * -- something a rest gives back -- and {@code /ddc rest} already clears them.
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
        ALREADY_USED("ddc.error.feature_used");

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
    public Either<Failure, Used> channelDivinity(ServerPlayer player) {
        return use(player, ClassFeature.ChannelDivinity.class, ClassFeature.Type.CHANNEL_DIVINITY,
                (sheet, feature) -> {
                    ServerLevel level = player.level();
                    int ticks = feature.seconds() * 20;
                    int turnedCount = 0;
                    for (LivingEntity undead : level.getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(feature.radius()),
                            entity -> entity != player && isUndead(entity))) {
                        undead.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 2));
                        undead.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
                        undead.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
                        turnedCount++;
                    }
                    level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1,
                            player.getZ(), 60, feature.radius() / 2, 1.0, feature.radius() / 2, 0.05);
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                            SoundSource.PLAYERS, 1.0f, 1.4f);
                    return new Used(net.minecraft.network.chat.Component.translatable(
                            "ddc.feature.channel_divinity", turnedCount));
                });
    }

    /** Whether an entity is undead, which is a vanilla notion DDC borrows rather than redefines. */
    private static boolean isUndead(LivingEntity entity) {
        return entity.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD);
    }

    /** The shared shape of every once-per-rest feature: find it, check it is unspent, spend it. */
    private <T extends ClassFeature> Either<Failure, Used> use(ServerPlayer player, Class<T> kind,
            ClassFeature.Type type, java.util.function.BiFunction<CharacterSheet, T, Used> action) {
        CharacterSheet sheet = characters.get(player);
        Optional<CharacterClass> definition = characters.definitionFor(sheet);
        if (definition.isEmpty()) {
            return Either.left(Failure.NO_CLASS);
        }
        Optional<T> feature = definition.get().feature(kind);
        if (feature.isEmpty()) {
            return Either.left(Failure.NOT_YOURS);
        }
        if (sheet.hasUsedFeature(type)) {
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
