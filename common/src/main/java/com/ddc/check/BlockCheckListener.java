package com.ddc.check;

import com.ddc.core.check.CheckOutcome;
import com.ddc.rules.BlockCheck;
import com.ddc.rules.DataRegistry;
import dev.architectury.event.events.common.InteractionEvent;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The check that happens because a player tried something, not because they typed something.
 *
 * <p>This is PRD 3.1's headline and the mod never had it: picking a locked chest or shouldering a
 * door was a command a player had to remember to run, which is exactly the bookkeeping DDC exists to
 * take off the table. Now the door asks.
 *
 * <p>A failed roll cancels the interaction, so the door stays shut and the chest stays closed. A
 * passed one is remembered, because rolling again every time you open the same chest turns a dramatic
 * moment into a chore -- and because a player who could reroll a failure by clicking twice was never
 * really rolling at all.
 */
public final class BlockCheckListener {

    private final DataRegistry<BlockCheck> checks;
    private final CheckService checkService;

    /** Which blocks each player has already got past, by position. */
    private final Map<UUID, java.util.Set<BlockPos>> passed = new ConcurrentHashMap<>();

    public BlockCheckListener(DataRegistry<BlockCheck> checks, CheckService checkService) {
        this.checks = checks;
        this.checkService = checkService;
    }

    public void register() {
        InteractionEvent.RIGHT_CLICK_BLOCK.register(this::onRightClick);
        // A player who leaves takes their successes with them: the map must not grow forever, and a
        // fresh session is a fresh attempt.
        dev.architectury.event.events.common.PlayerEvent.PLAYER_QUIT.register(
                player -> passed.remove(player.getUUID()));
    }

    private dev.architectury.event.EventResult onRightClick(net.minecraft.world.entity.player.Player player,
            InteractionHand hand, BlockPos pos, net.minecraft.core.Direction face) {
        if (!(player instanceof ServerPlayer server) || hand != InteractionHand.MAIN_HAND) {
            return dev.architectury.event.EventResult.pass();
        }
        Optional<BlockCheck> check = checkFor(server, pos);
        if (check.isEmpty() || hasPassed(server, pos)) {
            return dev.architectury.event.EventResult.pass();
        }

        CheckOutcome outcome = checkService.rollAndAnnounce(server, check.get().ability(), check.get().dc());
        if (!outcome.isSuccess()) {
            server.sendSystemMessage(Component.translatable(check.get().message()));
            // Stops the interaction dead: the point of a check is that failing it stops you.
            return dev.architectury.event.EventResult.interruptFalse();
        }
        passed.computeIfAbsent(server.getUUID(), id -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
        return dev.architectury.event.EventResult.pass();
    }

    /**
     * What this block asks for: the Game Master's own seal first, then whatever a pack said about
     * blocks of its kind.
     *
     * <p>The GM wins, because a GM who sealed this door meant this door. A pack saying iron doors are
     * DC 15 is a rule about iron doors in general; a seal is a rule about the one in front of you, and
     * the specific answer is the one a table wants.
     */
    private Optional<BlockCheck> checkFor(ServerPlayer player, BlockPos pos) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            Optional<BlockCheck> sealed = com.ddc.gm.GmLocks.of(level).at(level, pos)
                    .map(com.ddc.gm.GmLocks.Lock::asCheck);
            if (sealed.isPresent()) {
                return sealed;
            }
        }
        BlockState state = player.level().getBlockState(pos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return checks.get(id);
    }

    private boolean hasPassed(ServerPlayer player, BlockPos pos) {
        return passed.getOrDefault(player.getUUID(), java.util.Set.of()).contains(pos);
    }
}
