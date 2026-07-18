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
import net.minecraft.resources.ResourceLocation;
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

    /**
     * How far off the crosshair a creature may sit and still be what you meant, in blocks.
     *
     * <p>Generous on purpose. A spell is aimed by a person at a screen, and the difference between
     * this and vanilla's exact-hitbox test is the difference between casting and fighting the mouse.
     */
    private static final double AIM_FORGIVENESS = 1.25;

    /**
     * How long after a cast before the next one, in ticks.
     *
     * <p>The SRD says a cantrip costs no slot, and it is right: it costs your <em>turn</em>, and a
     * table's turn is a minute of everyone waiting. Minecraft has no turns, so DDC had nothing at all
     * -- a fire bolt every tick for as long as you can hold the button, which is not a wizard, it is a
     * machine gun.
     *
     * <p>Six seconds is the SRD's round, which is exactly what a cast is worth: one action. A cantrip
     * is quicker because it is the small thing you do when you have nothing better to spend.
     */
    private static final int CANTRIP_COOLDOWN = 20;
    private static final int SPELL_COOLDOWN = 120;

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
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        // 1.21.1's use() returns a held-stack result; the decision is made in InteractionResult terms
        // and wrapped, so the body reads the same as everything else in the mod.
        return new net.minecraft.world.InteractionResultHolder<>(
                useResult(level, player, hand), player.getItemInHand(hand));
    }

    private InteractionResult useResult(net.minecraft.world.level.Level level, Player player,
            InteractionHand hand) {
        if (!(player instanceof ServerPlayer caster)) {
            // The client is told the swing happened; the server decides whether anything else did.
            return InteractionResult.SUCCESS;
        }
        List<ResourceLocation> castable = castable(caster);
        if (castable.isEmpty()) {
            caster.sendSystemMessage(Component.translatable("ddc.focus.nothing_to_cast")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (caster.isCrouching()) {
            announce(caster, SpellSelection.next(caster, castable));
            return InteractionResult.SUCCESS;
        }

        ResourceLocation chosen = SpellSelection.current(caster, castable);
        Optional<Spell> spell = spells.get(chosen);
        if (spell.isEmpty()) {
            // The pack that defined it is gone since it was chosen. Pick again rather than fail.
            announce(caster, SpellSelection.next(caster, castable));
            return InteractionResult.FAIL;
        }

        // Nothing about a cooldown is a rule the SRD wrote, so nothing about it is on the sheet: it is
        // the cost of a game that runs in real time rather than in turns.
        if (caster.getCooldowns().isOnCooldown(stackIn(caster, hand).getItem())) {
            return InteractionResult.FAIL;
        }

        Optional<LivingEntity> target = aimedAt(caster, spell.get());
        if (target.isEmpty()) {
            // Above the hotbar, not in chat. A miss is a thing you glance at and forget; a line of
            // chat is a thing that stays, and a player clicking a staff makes a lot of them. The
            // screenshot that taught me this had eighteen.
            caster.sendSystemMessage(Component.translatable("ddc.focus.no_target",
                    (int) spell.get().rangeInBlocks()).withStyle(ChatFormatting.RED), true);
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
        net.minecraft.world.phys.Vec3 look = caster.getLookAngle();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : caster.level().getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(range),
                entity -> entity != caster && entity.isAlive())) {
            net.minecraft.world.phys.Vec3 toward = candidate.getBoundingBox().getCenter().subtract(eye);
            double distance = toward.length();
            if (distance > range) {
                continue;
            }
            // How far off the crosshair it sits, in blocks rather than in degrees: a zombie two
            // blocks off at forty blocks is a miss, and the same two blocks at arm's length is not.
            // Aiming with a point at a box and demanding they meet exactly is asking a player to be a
            // machine -- which is exactly what a screenshot of eighteen "nothing to cast at" lines is
            // a picture of.
            double offAxis = toward.subtract(look.scale(toward.dot(look))).length();
            if (toward.dot(look) <= 0 || offAxis > AIM_FORGIVENESS + candidate.getBbWidth() / 2) {
                continue;
            }
            if (!canSee(caster, eye, candidate)) {
                continue;
            }
            // Nearest to the line first, then nearest to the caster: pointing at the thing behind the
            // thing should still pick the thing you are pointing at.
            double score = offAxis * 4 + distance;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether there is a wall in the way. A spell through stone is not aiming, it is a bug report. */
    private static boolean canSee(ServerPlayer caster, net.minecraft.world.phys.Vec3 eye,
            LivingEntity target) {
        net.minecraft.world.phys.BlockHitResult wall = caster.level().clip(
                new net.minecraft.world.level.ClipContext(eye, target.getBoundingBox().getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        return wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
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
        return useResult(player.level(), player, hand);
    }

    private InteractionResult cast(ServerPlayer caster, Spell spell, ResourceLocation id, LivingEntity target) {
        return switch (casting.cast(caster, spell, id, target)) {
            case SpellService.Either.Left<SpellService.Failure, SpellService.Cast> left -> {
                // Above the hotbar: a refusal is a glance, not a conversation.
                caster.sendSystemMessage(left.value().message().copy().withStyle(ChatFormatting.RED), true);
                yield InteractionResult.FAIL;
            }
            case SpellService.Either.Right<SpellService.Failure, SpellService.Cast> right -> {
                caster.swing(InteractionHand.MAIN_HAND);
                // Vanilla's own cooldown, so the hotbar draws the sweep: the game already has a way to
                // say "not yet", and a second one would be a second thing to learn.
                caster.getCooldowns().addCooldown(stackIn(caster, InteractionHand.MAIN_HAND).getItem(),
                        cooldownFor(spell));
                yield InteractionResult.SUCCESS;
            }
        };
    }

    /** What a spell costs in seconds, since it cannot cost a turn. */
    static int cooldownFor(Spell spell) {
        return spell.isCantrip() ? CANTRIP_COOLDOWN : SPELL_COOLDOWN;
    }

    private static ItemStack stackIn(ServerPlayer caster, InteractionHand hand) {
        return caster.getItemInHand(hand);
    }

    /**
     * The spells this focus will cast for this character, in a stable order.
     *
     * <p>Sorted, because the order is what sneak-clicking walks through: a cycle that shuffled itself
     * between clicks would be a cycle nobody could learn.
     */
    private List<ResourceLocation> castable(ServerPlayer caster) {
        CharacterSheet sheet = characters.get(caster);
        return spells.ids().stream()
                .filter(id -> spells.get(id).map(spell -> switch (power) {
                    case WAND -> spell.isCantrip();
                    case STAFF -> spell.isCantrip() || sheet.hasPrepared(id);
                }).orElse(false))
                .sorted(ResourceLocation::compareTo)
                .toList();
    }

    /** Says what the focus is now pointed at, above the hotbar where a held item's news belongs. */
    private void announce(ServerPlayer caster, ResourceLocation chosen) {
        Component name = spells.get(chosen)
                .map(spell -> Component.literal(spell.name()))
                .orElseGet(() -> Component.literal(chosen.getPath()));
        caster.sendSystemMessage(Component.translatable("ddc.focus.selected", name)
                .withStyle(ChatFormatting.AQUA), true);
    }
}
