package com.ddc.item;

import com.ddc.rules.DataRegistry;
import com.ddc.rules.Spell;
import com.ddc.spell.SpellService;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A spell scroll: PRD 6.2's "spell scrolls", written once and read once.
 *
 * <p>Which spell a scroll holds is a component on the stack rather than an item per spell. That is
 * what makes the promise in PRD 6.2 keepable: a data pack's own spell can have a scroll, and a mod
 * that registered one item per spell could only ever have scrolls for the spells it shipped with.
 *
 * <p>It is the one way to cast something you have not prepared, which is the SRD's own reason for
 * scrolls to exist, and it costs no slot: the scroll is the slot, and it burns.
 */
public class SpellScrollItem extends Item {

    private final DataRegistry<Spell> spells;
    private final SpellService casting;
    private final DataComponentType<ResourceLocation> spellComponent;

    public SpellScrollItem(Properties properties, DataRegistry<Spell> spells, SpellService casting,
            DataComponentType<ResourceLocation> spellComponent) {
        super(properties);
        this.spells = spells;
        this.casting = casting;
        this.spellComponent = spellComponent;
    }

    /** The spell written on a scroll, if it is a scroll and anything is written on it. */
    public Optional<ResourceLocation> spellOn(ItemStack stack) {
        return Optional.ofNullable(stack.get(spellComponent));
    }

    /**
     * Reads the scroll at whatever was clicked.
     *
     * <p>Through the same spell service as everything else, so a scroll cannot dodge a rule -- but a
     * scroll needs no slot and no preparation, which is what the service's {@code castFromScroll}
     * says out loud rather than this item deciding for itself.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (!(player instanceof ServerPlayer caster)) {
            return InteractionResult.SUCCESS;
        }
        Optional<Spell> spell = spellOn(stack).flatMap(spells::get);
        if (spell.isEmpty()) {
            // A scroll for a spell whose pack is gone. It says so rather than doing nothing, because
            // a blank scroll and a broken one look identical in a hand.
            caster.sendSystemMessage(Component.translatable("ddc.scroll.unreadable")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        return switch (casting.castFromScroll(caster, spell.get(), target)) {
            case SpellService.Either.Left<SpellService.Failure, SpellService.Cast> left -> {
                caster.sendSystemMessage(left.value().message().copy().withStyle(ChatFormatting.RED));
                yield InteractionResult.FAIL;
            }
            case SpellService.Either.Right<SpellService.Failure, SpellService.Cast> right -> {
                // The scroll burns. That is the whole bargain: a spell you did not prepare, once.
                stack.shrink(1);
                caster.swing(hand);
                caster.getCooldowns().addCooldown(stack, 20);
                yield InteractionResult.SUCCESS;
            }
        };
    }

    /** Says what is written on it, since every scroll looks the same in a hand otherwise. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        spellOn(stack).ifPresent(id -> lines.accept(
                spells.get(id)
                        .map(spell -> Component.literal(spell.name()))
                        .orElseGet(() -> Component.literal(id.getPath()))
                        .copy()
                        .withStyle(ChatFormatting.AQUA)));
    }
}
