package com.ddc.item;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Spell;
import com.ddc.spell.SpellSelection;
import com.ddc.spell.SpellService;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A wand or a staff: PRD 3.1's "spells are cast using wands/staffs", which the mod never had.
 *
 * <p>Casting was a command, and then a menu. Both work and both are two steps away from the moment:
 * a wizard at a table points at a thing and says a word. So this is the word -- right-click what you
 * are pointing at, and the spell you have chosen goes off. The menu still picks the spell, and
 * sneak-clicking cycles through them, so choosing and casting are separate the way drawing a sword
 * and swinging it are.
 *
 * <p>A wand carries cantrips, a staff carries anything prepared. That is the difference between them
 * and the only one: a cantrip is magic you always have, and it is worth having an item that never
 * runs dry.
 */
public class SpellFocusItem extends Item {

    /** What this focus is willing to cast. */
    public enum Power {
        /** Cantrips only: magic that costs nothing, in an item that costs little. */
        WAND,
        /** Anything the caster has prepared, which is what a staff is for. */
        STAFF
    }

    private final Power power;
    private final CharacterService characters;
    private final DataRegistry<Spell> spells;
    private final SpellService casting;

    public SpellFocusItem(Properties properties, Power power, CharacterService characters,
            DataRegistry<Spell> spells, SpellService casting) {
        super(properties);
        this.power = power;
        this.characters = characters;
        this.spells = spells;
        this.casting = casting;
    }

    /**
     * Casts at what was clicked, or cycles the choice when sneaking.
     *
     * <p>Everything about whether the spell may be cast -- the slot, the range, the preparation -- is
     * the spell service's answer, the same one the command and the wheel get. This is a way of asking,
     * not a second set of rules.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (!(player instanceof ServerPlayer caster)) {
            return InteractionResult.SUCCESS;
        }
        List<Identifier> castable = castable(caster);
        if (castable.isEmpty()) {
            caster.sendSystemMessage(Component.translatable("ddc.focus.nothing_to_cast")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (caster.isCrouching()) {
            announce(caster, SpellSelection.next(caster, castable));
            return InteractionResult.SUCCESS;
        }

        Identifier chosen = SpellSelection.current(caster, castable);
        Optional<Spell> spell = spells.get(chosen);
        if (spell.isEmpty()) {
            // The pack that defined it is gone since it was chosen. Pick again rather than fail.
            announce(caster, SpellSelection.next(caster, castable));
            return InteractionResult.FAIL;
        }
        return cast(caster, spell.get(), chosen, target);
    }

    private InteractionResult cast(ServerPlayer caster, Spell spell, Identifier id, LivingEntity target) {
        return switch (casting.cast(caster, spell, id, target)) {
            case SpellService.Either.Left<SpellService.Failure, SpellService.Cast> left -> {
                caster.sendSystemMessage(left.value().message().copy().withStyle(ChatFormatting.RED));
                yield InteractionResult.FAIL;
            }
            case SpellService.Either.Right<SpellService.Failure, SpellService.Cast> right -> {
                caster.swing(InteractionHand.MAIN_HAND);
                yield InteractionResult.SUCCESS;
            }
        };
    }

    /**
     * The spells this focus will cast for this character, in a stable order.
     *
     * <p>Sorted, because the order is what sneak-clicking walks through: a cycle that shuffled itself
     * between clicks would be a cycle nobody could learn.
     */
    private List<Identifier> castable(ServerPlayer caster) {
        CharacterSheet sheet = characters.get(caster);
        return spells.ids().stream()
                .filter(id -> spells.get(id).map(spell -> switch (power) {
                    case WAND -> spell.isCantrip();
                    case STAFF -> spell.isCantrip() || sheet.hasPrepared(id);
                }).orElse(false))
                .sorted(Identifier::compareTo)
                .toList();
    }

    /** Says what the focus is now pointed at, above the hotbar where a held item's news belongs. */
    private void announce(ServerPlayer caster, Identifier chosen) {
        Component name = spells.get(chosen)
                .map(spell -> Component.literal(spell.name()))
                .orElseGet(() -> Component.literal(chosen.getPath()));
        caster.sendSystemMessage(Component.translatable("ddc.focus.selected", name)
                .withStyle(ChatFormatting.AQUA), true);
    }
}
