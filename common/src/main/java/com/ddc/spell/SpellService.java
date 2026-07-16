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

    /** Why a cast could not happen. Each maps to something the caster can act on. */
    public enum Failure {
        NO_CLASS("You have no class yet. Pick one with /ddc class <id>."),
        CLASS_CANNOT_CAST("Your class cannot cast spells."),
        SPELL_TOO_HIGH("You cannot cast a spell of that level yet."),
        NO_SLOTS("You have no spell slots of that level left. Rest with /ddc rest."),
        OUT_OF_RANGE("That target is out of range.");

        private final String message;

        Failure(String message) {
            this.message = message;
        }

        public String message() {
            return message;
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
    public Either<Failure, Cast> cast(ServerPlayer caster, Spell spell, LivingEntity target) {
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

        Optional<Failure> payment = paySlot(caster, sheet, casting.get(), spell);
        if (payment.isPresent()) {
            return Either.left(payment.get());
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
        Optional<CheckOutcome> save = spell.savingThrow()
                .map(throwSpec -> rollSave(target, throwSpec.ability(), saveDc(sheet, casting)));

        Optional<RollResult> damage = spell.damageDice().map(dice -> rolls.rollPublic(caster, dice,
                com.ddc.core.dice.RollMode.NORMAL));

        int dealt = damage.map(result -> applyDamage(caster, target, spell, save, result.total()))
                .orElse(0);
        return new Cast(spell, damage, save, dealt);
    }

    /** The number a target must beat to resist this caster. */
    public int saveDc(CharacterSheet sheet, Spellcasting casting) {
        return SAVE_DC_BASE + sheet.proficiencyBonus() + sheet.modifier(casting.ability());
    }

    /**
     * The target's saving throw.
     *
     * <p>A player rolls their own ability modifier. A mob has no sheet, so it rolls flat: DDC has no
     * monster stat blocks yet, and inventing one per mob would be a rule nobody wrote down.
     */
    private CheckOutcome rollSave(LivingEntity target, Ability ability, int dc) {
        int modifier = target instanceof ServerPlayer player
                ? characters.get(player).modifier(ability)
                : 0;
        return D20Check.of(modifier, dc).resolve(roller);
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
