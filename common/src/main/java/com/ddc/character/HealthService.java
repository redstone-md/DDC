package com.ddc.character;

import com.ddc.DDC;
import com.ddc.rules.CharacterClass;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Makes a character's hit points the health they actually have.
 *
 * <p>Until this existed, a sheet's hit points were a number in a panel: {@code /ddc sheet} said 44/44
 * while the player walked around with vanilla's 20 and died at 20. Two sources of truth, and the one
 * the rules cared about was the fiction.
 *
 * <p>Now there is one. Vanilla health is the health; the class's hit die decides how much of it there
 * is, through a modifier on {@link Attributes#MAX_HEALTH}. The sheet no longer stores current hit
 * points at all, because the player already carries them.
 *
 * <p>The modifier is transient and applied on every join, respawn and change. A permanent one would
 * be written into the player's data and outlive the mod: uninstall DDC and a fighter would keep 44
 * hit points forever, which is not DDC's to leave behind.
 */
public final class HealthService {

    /**
     * The id the modifier is stored under. Stable, so re-applying updates the same modifier rather
     * than stacking a second one on top of it.
     */
    private static final ResourceLocation MODIFIER_ID = DDC.id("hit_die");

    /** Minecraft's own maximum health, which the modifier adjusts away from. */
    private static final double VANILLA_MAX_HEALTH = 20.0;

    private final CharacterService characters;

    public HealthService(CharacterService characters) {
        this.characters = characters;
    }

    /**
     * Points a player's health at their sheet.
     *
     * <p>A player with no class keeps vanilla's health: DDC has no hit die to give them, and taking
     * their hearts away for not having filled in a character sheet would be a strange welcome.
     */
    public void apply(ServerPlayer player) {
        CharacterSheet sheet = characters.get(player);
        Optional<CharacterClass> definition = characters.definitionFor(sheet);

        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        if (definition.isEmpty()) {
            maxHealth.removeModifier(MODIFIER_ID);
            return;
        }

        int target = sheet.maxHitPoints(definition.get());
        maxHealth.addOrUpdateTransientModifier(new AttributeModifier(
                MODIFIER_ID, target - VANILLA_MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE));

        // Lowering the maximum can leave a player above it -- a levelled-down character, or a race
        // change that costs Constitution -- and Minecraft will not clamp that for us.
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Points the health at the sheet and fills it. For picking a class, and for a long rest. */
    public void applyAndHeal(ServerPlayer player) {
        apply(player);
        player.setHealth(player.getMaxHealth());
    }

    /** What the player's health is worth right now, for the sheet and for feedback. */
    public static int currentHitPoints(ServerPlayer player) {
        return Math.round(player.getHealth());
    }

    public static int maxHitPoints(ServerPlayer player) {
        return Math.round(player.getMaxHealth());
    }
}
