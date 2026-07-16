package com.ddc.rules;

import com.ddc.core.dice.DiceExpression;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/**
 * A thing a class can do, beyond having hit points and casting spells.
 *
 * <p>PRD 3.1 gives each class a mechanic: the rogue's sneak attack, the fighter's second wind, the
 * cleric's channel divinity. Which class has which, and the numbers behind them, are data:
 *
 * <pre>{@code
 * "features": [
 *   { "type": "ddc:sneak_attack", "dice": "1d6", "levels_per_die": 2 }
 * ]
 * }</pre>
 *
 * <p>The <em>kinds</em> of feature are code, because each one is behaviour rather than numbers, and a
 * data pack cannot describe behaviour that does not exist yet. A pack tunes what is here; a mod that
 * wants a new kind adds one, which is the split ADR-0002 draws.
 */
public sealed interface ClassFeature {

    /** Dispatches on {@code type}, the way Minecraft's own data-driven types do. */
    Codec<ClassFeature> CODEC = Type.CODEC.dispatch("type", ClassFeature::type, Type::codec);

    Type type();

    /** The kinds of feature DDC knows how to run. */
    enum Type {
        SNEAK_ATTACK("ddc:sneak_attack") {
            @Override
            public MapCodec<? extends ClassFeature> codec() {
                return SneakAttack.MAP_CODEC;
            }
        },
        SECOND_WIND("ddc:second_wind") {
            @Override
            public MapCodec<? extends ClassFeature> codec() {
                return SecondWind.MAP_CODEC;
            }
        },
        CHANNEL_DIVINITY("ddc:channel_divinity") {
            @Override
            public MapCodec<? extends ClassFeature> codec() {
                return ChannelDivinity.MAP_CODEC;
            }
        };

        public static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                id -> {
                    for (Type type : values()) {
                        if (type.id.equals(id)) {
                            return DataResult.success(type);
                        }
                    }
                    return DataResult.error(() -> "Unknown class feature: '" + id + "'");
                },
                type -> type.id);

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public abstract MapCodec<? extends ClassFeature> codec();
    }

    /**
     * The rogue's sneak attack: extra dice when the target is at a disadvantage the rogue can exploit.
     *
     * @param dice         the dice one tier is worth
     * @param levelsPerDie how many character levels buy another tier of them
     */
    record SneakAttack(DiceExpression dice, int levelsPerDie) implements ClassFeature {

        static final MapCodec<SneakAttack> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                DDCCodecs.DICE_EXPRESSION.fieldOf("dice").forGetter(SneakAttack::dice),
                Codec.intRange(1, 20).optionalFieldOf("levels_per_die", 2).forGetter(SneakAttack::levelsPerDie)
        ).apply(instance, SneakAttack::new));

        public SneakAttack {
            Objects.requireNonNull(dice, "dice");
        }

        /**
         * The dice this rogue's sneak attack is worth at their level.
         *
         * <p>The SRD gives a rogue 1d6 at level 1 and another every two levels after: 1d6 at 1st and
         * 2nd, 2d6 at 3rd, and so on to 10d6 at 19th.
         */
        public DiceExpression diceAtLevel(int characterLevel) {
            int tiers = Math.max(1, (characterLevel + levelsPerDie - 1) / levelsPerDie);
            return new DiceExpression(
                    dice.pools().stream()
                            .map(pool -> new com.ddc.core.dice.DicePool(pool.count() * tiers, pool.die()))
                            .toList(),
                    dice.modifier() * tiers);
        }

        @Override
        public Type type() {
            return Type.SNEAK_ATTACK;
        }
    }

    /**
     * The fighter's second wind: catch your breath and get some of it back, once per rest.
     *
     * @param dice what it heals, before the level is added
     */
    record SecondWind(DiceExpression dice) implements ClassFeature {

        static final MapCodec<SecondWind> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                DDCCodecs.DICE_EXPRESSION.fieldOf("dice").forGetter(SecondWind::dice)
        ).apply(instance, SecondWind::new));

        public SecondWind {
            Objects.requireNonNull(dice, "dice");
        }

        @Override
        public Type type() {
            return Type.SECOND_WIND;
        }
    }

    /**
     * The cleric's channel divinity: turn the undead away, once per rest.
     *
     * @param radius how far the divinity reaches, in blocks
     * @param seconds how long the turned undead flee for
     */
    record ChannelDivinity(double radius, int seconds) implements ClassFeature {

        static final MapCodec<ChannelDivinity> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.doubleRange(1, 64).optionalFieldOf("radius", 6.0).forGetter(ChannelDivinity::radius),
                Codec.intRange(1, 300).optionalFieldOf("seconds", 30).forGetter(ChannelDivinity::seconds)
        ).apply(instance, ChannelDivinity::new));

        @Override
        public Type type() {
            return Type.CHANNEL_DIVINITY;
        }
    }
}
