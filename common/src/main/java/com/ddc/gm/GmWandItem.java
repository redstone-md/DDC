package com.ddc.gm;

import com.ddc.rules.DDCRegistries;
import com.ddc.rules.Encounter;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * The Game Master's wand: point at the ground, place an encounter.
 *
 * <p>PRD 3.2's wand. Right-click a block to spawn the selected encounter; sneak-right-click to step
 * to the next one; right-click a creature to possess it. The full radial menu the PRD describes needs
 * a screen, which this does not have yet, so the selection is announced in the action bar instead.
 *
 * <p>Holding the item grants nothing. Every use re-checks with {@link GameMasters}, because an item
 * can be dropped, put in a chest, or handed to a player, and none of that may confer GM powers.
 */
public class GmWandItem extends Item {

    private final EncounterService encounters = new EncounterService();
    private final PossessionService possessions;

    public GmWandItem(Properties properties, PossessionService possessions) {
        super(properties);
        this.possessions = possessions;
    }

    /**
     * Right-clicking a creature possesses it.
     *
     * <p>Checked here as well as in the service: an item can be dropped, chested or handed over, and
     * none of that may confer GM powers.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target,
            InteractionHand hand) {
        if (!(user instanceof ServerPlayer player)) {
            return InteractionResult.CONSUME;
        }
        Optional<PossessionService.Failure> failure = possessions.possess(player, target);
        failure.ifPresent(reason -> player.sendSystemMessage(
                reason.message().copy().withStyle(ChatFormatting.RED), true));
        return failure.isEmpty() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(context.getLevel() instanceof ServerLevel level)) {
            // The client is told nothing happened; the server decides what did.
            return InteractionResult.CONSUME;
        }
        if (!GameMasters.isGameMaster(player)) {
            player.sendSystemMessage(EncounterService.Failure.NOT_A_GAME_MASTER.message()
                    .copy().withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        List<Identifier> available = availableEncounters();
        if (available.isEmpty()) {
            player.sendSystemMessage(Component.translatable("ddc.error.no_encounters")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            return cycle(player, available);
        }
        return spawn(player, level, context.getClickLocation(), available);
    }

    /** The encounters this server knows, in a stable order so cycling is predictable. */
    private static List<Identifier> availableEncounters() {
        return DDCRegistries.ENCOUNTERS.ids().stream().sorted(Identifier::compareTo).toList();
    }

    private InteractionResult cycle(ServerPlayer player, List<Identifier> available) {
        Identifier next = GmWandSelection.next(player, available);
        DDCRegistries.ENCOUNTERS.get(next).ifPresent(encounter ->
                player.sendSystemMessage(Component.translatable("ddc.gm.selected",
                        encounter.name(), encounter.total()).withStyle(ChatFormatting.GOLD), true));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult spawn(ServerPlayer player, ServerLevel level, Vec3 at,
            List<Identifier> available) {
        Identifier selected = GmWandSelection.current(player, available);
        Optional<Encounter> encounter = DDCRegistries.ENCOUNTERS.get(selected);
        if (encounter.isEmpty()) {
            return InteractionResult.FAIL;
        }

        EncounterService.Result result = encounters.spawn(player, encounter.get(), level, at);
        Component message = result.isSuccess()
                ? Component.translatable("ddc.gm.spawned", encounter.get().name(), result.spawned())
                        .withStyle(ChatFormatting.GOLD)
                : result.failure().orElseThrow().message().copy().withStyle(ChatFormatting.RED);
        player.sendSystemMessage(message, true);
        return result.isSuccess() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}
