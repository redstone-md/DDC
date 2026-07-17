package com.ddc.spell;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import com.ddc.core.check.CheckOutcome;
import com.ddc.core.check.D20Check;
import com.ddc.core.dice.DiceRoller;
import com.ddc.core.dice.RollResult;
import com.ddc.dice.DiceRollService;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.Spell;
import com.ddc.rules.Spellcasting;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Casts spells.
 *
 * <p>The one door to magic on the server: every cast is checked, paid for, rolled and applied here,
 * so no path can spend a slot without casting or cast without spending one.
 *
 * <p>Damage rolls are public, unlike attack rolls. A fireball is the table's business.
 */
public final class SpellService {

    /** The SRD's save DC: 8 + proficiency + the casting ability's modifier. */
    private static final int SAVE_DC_BASE = 8;

    private final CharacterService characters;
    private final DiceRollService rolls;
    private final DiceRoller roller;

    public SpellService(CharacterService characters, DiceRollService rolls, DiceRoller roller) {
        this.characters = characters;
        this.rolls = rolls;
        this.roller = roller;
    }

    /**
      * Why a cast could not happen. Each maps to something the caster can act on.
      *
      * <p>A key rather than a sentence: the server decides what happened, and only the player's own
      * client knows what language to say it in.
      */
    public enum Failure {
        NO_CLASS("ddc.error.no_class"),
        CLASS_CANNOT_CAST("ddc.error.cannot_cast"),
        SPELL_TOO_HIGH("ddc.error.spell_too_high"),
        NO_SLOTS("ddc.error.no_slots"),
        NOT_PREPARED("ddc.error.not_prepared"),
        OUT_OF_RANGE("ddc.error.out_of_range");

        private final String key;

        Failure(String key) {
            this.key = key;
        }

        public net.minecraft.network.chat.Component message() {
            return net.minecraft.network.chat.Component.translatable(key);
        }
    }

    /** What a successful cast did, for the caster's feedback. */
    public record Cast(Spell spell, Optional<RollResult> damage, Optional<CheckOutcome> save, int damageDealt) {
    }

    /**
     * Casts a spell at a target.
     *
     * @return the cast, or the reason it could not happen; nothing has changed in the failure case
     */
    public Either<Failure, Cast> cast(ServerPlayer caster, Spell spell, Identifier spellId,
            LivingEntity target) {
        CharacterSheet sheet = characters.get(caster);
        Optional<CharacterClass> definition = characters.definitionFor(sheet);
        if (definition.isEmpty()) {
            return Either.left(Failure.NO_CLASS);
        }
        Optional<Spellcasting> casting = definition.get().spellcasting();
        if (casting.isEmpty()) {
            return Either.left(Failure.CLASS_CANNOT_CAST);
        }
        if (caster.distanceTo(target) > spell.rangeInBlocks()) {
            return Either.left(Failure.OUT_OF_RANGE);
        }
        // A cantrip is known rather than prepared, which is the SRD's own rule and the reason a
        // wizard is not made to write down fire bolt every morning.
        if (!spell.isCantrip() && !sheet.hasPrepared(spellId)) {
            return Either.left(Failure.NOT_PREPARED);
        }

        Optional<Failure> payment = paySlot(caster, sheet, casting.get(), spell);
        if (payment.isPresent()) {
            return Either.left(payment.get());
        }
        return Either.right(resolve(caster, sheet, casting.get(), spell, target));
    }

    /**
     * Casts from a scroll: no slot, no preparation, every other rule.
     *
     * <p>That is what a scroll is for in the SRD -- it carries the preparation with it, and burns.
     * Range still applies, because a scroll does not lengthen your arm, and the class still has to be
     * able to cast at all: reading magic is a thing wizards learn, and a fighter holding a scroll is
     * a fighter holding paper.
     */
    public Either<Failure, Cast> castFromScroll(ServerPlayer caster, Spell spell, LivingEntity target) {
        CharacterSheet sheet = characters.get(caster);
        Optional<CharacterClass> definition = characters.definitionFor(sheet);
        if (definition.isEmpty()) {
            return Either.left(Failure.NO_CLASS);
        }
        Optional<Spellcasting> casting = definition.get().spellcasting();
        if (casting.isEmpty()) {
            return Either.left(Failure.CLASS_CANNOT_CAST);
        }
        if (caster.distanceTo(target) > spell.rangeInBlocks()) {
            return Either.left(Failure.OUT_OF_RANGE);
        }
        return Either.right(resolve(caster, sheet, casting.get(), spell, target));
    }

    /**
     * Spends the slot the spell costs. A cantrip costs nothing.
     *
     * @return the reason the slot could not be paid, or empty once it has been
     */
    private Optional<Failure> paySlot(ServerPlayer caster, CharacterSheet sheet, Spellcasting casting,
            Spell spell) {
        if (spell.isCantrip()) {
            return Optional.empty();
        }
        int available = casting.slotsFor(sheet.level(), spell.level());
        if (available == 0) {
            return Optional.of(Failure.SPELL_TOO_HIGH);
        }
        if (sheet.usedSlots(spell.level()) >= available) {
            return Optional.of(Failure.NO_SLOTS);
        }
        characters.update(caster, current -> current.withSlotSpent(spell.level()));
        return Optional.empty();
    }

    /** Rolls the spell's damage and its save, and applies what survives. */
    private Cast resolve(ServerPlayer caster, CharacterSheet sheet, Spellcasting casting, Spell spell,
            LivingEntity target) {
        throwBolt(caster, spell, target);
        Optional<CheckOutcome> save = spell.savingThrow()
                .map(throwSpec -> rollSave(target, throwSpec.ability(), saveDc(sheet, casting)));

        Optional<RollResult> damage = spell.damageDice().map(dice -> rolls.rollPublic(caster, dice,
                com.ddc.core.dice.RollMode.NORMAL));

        int dealt = damage.map(result -> hurt(caster, target, spell, save, result.total())).orElse(0);
        return new Cast(spell, damage, save, dealt);
    }

    /**
     * Hurts what the spell caught: the target, and everything around it for a spell with an area.
     *
     * <p>The save is rolled once, against the target, rather than per victim. It is a simplification
     * and worth naming: a fireball in the SRD asks every creature in it for its own save, and asking
     * a d20 of nine skeletons one at a time would bury the table in dice for a moment that is
     * supposed to be one loud noise.
     */
    private int hurt(ServerPlayer caster, LivingEntity target, Spell spell,
            Optional<CheckOutcome> save, int rolled) {
        int dealt = applyDamage(caster, target, spell, save, rolled);
        if (!spell.isAreaOfEffect()) {
            return dealt;
        }
        for (LivingEntity caught : target.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(spell.areaInBlocks()))) {
            // Not the caster, and not the target twice. A wizard standing in their own fireball is a
            // rule for another day; killing yourself with a menu button is not the day's lesson.
            if (caught != target && caught != caster && caught.isAlive()) {
                applyDamage(caster, caught, spell, save, rolled);
            }
        }
        return dealt;
    }

    /**
     * Spells that take time land later, which is what PRD 4.4's runes are warning about.
     *
     * <p>The runes were drawn and the spell went off in the same tick, so the warning arrived with
     * the thing it was warning about. Now the ground lights up, and the table has the seconds the
     * pack asked for to do something about it.
     */
    private record Pending(ServerPlayer caster, CharacterSheet sheet, Spellcasting casting, Spell spell,
            LivingEntity target, long dueTick) {
    }

    private final java.util.List<Pending> pending = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Resolves the spells whose casting time has run out. */
    public void register() {
        dev.architectury.event.events.common.TickEvent.SERVER_POST.register(server -> {
            long now = server.getTickCount();
            for (Pending cast : pending) {
                if (now < cast.dueTick()) {
                    continue;
                }
                pending.remove(cast);
                // A caster who died or left, or a target that did, takes the spell with them: a
                // fireball from a corpse is nobody's idea of a rule.
                if (cast.caster().isAlive() && cast.target().isAlive()) {
                    announce(cast.caster(), resolve(cast.caster(), cast.sheet(), cast.casting(),
                            cast.spell(), cast.target()), cast.target());
                }
            }
        });
    }

    /** Says what a delayed spell did, since the command that started it has long since answered. */
    private static void announce(ServerPlayer caster, Cast cast, LivingEntity target) {
        caster.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "ddc.spell.landed", cast.spell().name(), target.getDisplayName(), cast.damageDealt()));
    }

    /**
     * Throws the bolt that shows the table what just happened.
     *
     * <p>Only for a spell with reach: a touch spell has nothing to cross, and a bolt travelling two
     * feet is a flicker nobody reads. The colour comes from the school, because a fireball and a
     * sacred flame should not look like the same magic.
     *
     * <p>It carries no rules -- the roll and the save are already made, and the damage is applied by
     * the caller on this same tick. The bolt is a picture; see {@link SpellBoltEntity}.
     */
    private static void throwBolt(ServerPlayer caster, Spell spell, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level) || spell.rangeInBlocks() < 2) {
            return;
        }
        level.addFreshEntity(SpellBoltEntity.between(level, caster, target, colourOf(spell)));
        level.playSound(null, caster.blockPosition(), net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    /**
     * What colour a school of magic is.
     *
     * <p>The schools are the SRD's own, and a pack writes whichever it likes; an unknown one gets the
     * mod's brass rather than a crash, because a pack inventing a school is a pack doing what packs
     * are for.
     */
    private static int colourOf(Spell spell) {
        return switch (spell.school().toLowerCase(java.util.Locale.ROOT)) {
            case "evocation" -> 0xFF7B29;
            case "necromancy" -> 0x6B3FA0;
            case "abjuration" -> 0x4FA3E3;
            case "conjuration" -> 0x54C46A;
            case "enchantment" -> 0xE86AA6;
            case "divination" -> 0xF2E27A;
            case "illusion" -> 0x9B7BE8;
            case "transmutation" -> 0x3FB6A8;
            default -> 0xC9973F;
        };
    }

    /** The number a target must beat to resist this caster. */
    public int saveDc(CharacterSheet sheet, Spellcasting casting) {
        return SAVE_DC_BASE + sheet.proficiencyBonus() + sheet.modifier(casting.ability());
    }

    /**
     * The target's saving throw.
     *
     * <p>A player rolls their ability modifier, plus their proficiency bonus when their class is
     * proficient in that save -- which is the whole point of a class listing its saving throws, and
     * is what makes a wizard hard to out-think and a fighter hard to knock down.
     *
     * <p>A mob has no sheet, so it rolls flat: DDC has no monster stat blocks yet, and inventing one
     * per mob would be a rule nobody wrote down.
     */
    private CheckOutcome rollSave(LivingEntity target, Ability ability, int dc) {
        if (!(target instanceof ServerPlayer player)) {
            return D20Check.of(0, dc).resolve(roller);
        }
        CharacterSheet sheet = characters.get(player);
        D20Check check = D20Check.ability(sheet.abilities(), ability, dc);
        boolean proficient = characters.definitionFor(sheet)
                .filter(definition -> definition.isProficientInSave(ability))
                .isPresent();
        return (proficient ? check.plusProficiency(sheet.level()) : check).resolve(roller);
    }

    private int applyDamage(ServerPlayer caster, LivingEntity target, Spell spell,
            Optional<CheckOutcome> save, int rolled) {
        int damage = rolled;
        if (save.filter(CheckOutcome::isSuccess).isPresent()) {
            damage = switch (spell.savingThrow().orElseThrow().effectOnSuccess()) {
                case HALF_DAMAGE -> rolled / 2;
                case NONE -> 0;
            };
        }
        if (damage > 0 && target.level() instanceof ServerLevel level) {
            target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
        }
        return damage;
    }

    /** A result or a reason it is not one. Java has no such type and this needs one exactly once. */
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
