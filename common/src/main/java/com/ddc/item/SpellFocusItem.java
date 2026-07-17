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
     * Casting at range, which is what a staff is for.
     *
     * <p>This used to hang off clicking a creature, and vanilla only calls that when the creature is
     * within arm's reach -- about three blocks. So a fire bolt with a hundred and twenty feet of range
     * in the rules could be cast at things close enough to hit with the staff instead. It was, as a
     * player put it, no range at all.
     *
     * <p>Now the spell decides. The look vector is followed as far as the spell reaches and the first
     * creature on it is the target, which is what pointing at something means. Everything about
     * whether it may be cast is still the spell service's answer, the same one the command and the
     * wheel get.
     */
    @Override
    public InteractionResult use(net.minecraft.world.level.Level level, Player player,
            InteractionHand hand) {
        if (!(player instanceof ServerPlayer caster)) {
            // The client is told the swing happened; the server decides whether anything else did.
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

        Optional<LivingEntity> target = aimedAt(caster, spell.get());
        if (target.isEmpty()) {
            caster.sendSystemMessage(Component.translatable("ddc.focus.no_target",
                    (int) spell.get().rangeInBlocks()).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        return cast(caster, spell.get(), chosen, target.get());
    }

    /**
     * The first creature along the caster's own line of sight, within the spell's range.
     *
     * <p>Blocks stop it, because a spell through a wall is not aiming, it is a bug report. The caster
     * is skipped for the obvious reason.
     */
    private static Optional<LivingEntity> aimedAt(ServerPlayer caster, Spell spell) {
        double range = spell.rangeInBlocks();
        net.minecraft.world.phys.Vec3 eye = caster.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(caster.getLookAngle().scale(range));

        // A wall between you and it means you are not pointing at it, whatever the maths says.
        net.minecraft.world.phys.BlockHitResult wall = caster.level().clip(
                new net.minecraft.world.level.ClipContext(eye, end,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        net.minecraft.world.phys.Vec3 reach = wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? end
                : wall.getLocation();

        net.minecraft.world.phys.EntityHitResult hit =
                net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                        caster, eye, reach,
                        caster.getBoundingBox().expandTowards(caster.getLookAngle().scale(range)).inflate(1),
                        entity -> entity instanceof LivingEntity && entity != caster && entity.isAlive(),
                        0.0);
        return hit == null || !(hit.getEntity() instanceof LivingEntity living)
                ? Optional.empty()
                : Optional.of(living);
    }

    /**
     * Casting at something you are touching.
     *
     * <p>Kept because vanilla calls this instead of {@link #use} when a creature is under the
     * crosshair and in reach, and a staff that did nothing when you clicked the thing directly in
     * front of you would be a staff that felt broken at exactly the wrong moment.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        return use(player.level(), player, hand);
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
