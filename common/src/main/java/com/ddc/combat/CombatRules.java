package com.ddc.combat;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import com.ddc.core.character.AbilityScores;
import com.ddc.core.check.D20Check;
import com.ddc.core.combat.ArmorClass;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Turns Minecraft entities into the numbers the SRD needs: an armour class to beat and a bonus to
 * beat it with.
 *
 * <p>Players bring a character sheet. Mobs do not, so their numbers are read off the entity itself.
 * Both mappings are stated where they are made, because neither system defines them.
 */
public final class CombatRules {

    /**
     * The largest to-hit bonus a mob's damage can imply. Without a ceiling a modded boss with a huge
     * attack damage attribute would hit any armour class automatically, which removes the roll.
     */
    static final int MAX_MOB_ATTACK_BONUS = 10;

    private final CharacterService characters;

    public CombatRules(CharacterService characters) {
        this.characters = characters;
    }

    /** The character sheet behind an entity, if it is a player who has picked a class. */
    private Optional<CharacterSheet> sheetOf(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) {
            return Optional.empty();
        }
        CharacterSheet sheet = characters.get(player);
        return sheet.hasClass() ? Optional.of(sheet) : Optional.empty();
    }

    /** Whether DDC's combat rules apply to this pairing at all: someone at the table must be in it. */
    public boolean appliesTo(LivingEntity attacker, LivingEntity target) {
        return sheetOf(attacker).isPresent() || sheetOf(target).isPresent();
    }

    /**
     * The number an attack roll must meet or beat.
     *
     * <p>A player's Dexterity comes from their sheet and is capped by what they are wearing; a mob
     * has no sheet, so it contributes none.
     */
    public int armorClassOf(LivingEntity target) {
        ArmorClass armor = ArmorClass.fromVanillaArmor(target.getArmorValue());
        AbilityScores scores = sheetOf(target)
                .map(CharacterSheet::abilities)
                .orElseGet(AbilityScores::defaults);
        return armor.value(scores);
    }

    /**
     * The attacker's to-hit bonus.
     *
     * <p>A player adds Strength and their proficiency bonus, which assumes proficiency with whatever
     * they are swinging: DDC has no weapon proficiency data yet, and denying it would punish every
     * player for something the rules cannot express.
     *
     * <p>A mob has neither, so its bonus comes from its attack damage: half of it, capped. A zombie
     * (3 damage) attacks at +1, a vindicator (13) at +6, which puts them near the SRD monsters of
     * comparable menace.
     */
    public int attackBonusOf(LivingEntity attacker) {
        return sheetOf(attacker)
                .map(sheet -> sheet.modifier(Ability.STRENGTH) + sheet.proficiencyBonus())
                .orElseGet(() -> mobAttackBonus(attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)));
    }

    static int mobAttackBonus(double attackDamage) {
        return Math.clamp((int) (attackDamage / 2), 0, MAX_MOB_ATTACK_BONUS);
    }

    /** The test this attack has to pass. */
    public D20Check attackCheck(LivingEntity attacker, LivingEntity target) {
        return D20Check.of(attackBonusOf(attacker), armorClassOf(target));
    }

    /** Whether an entity is a player at the table, for wording feedback. */
    public static boolean isPlayer(LivingEntity entity) {
        return entity instanceof Player;
    }
}
