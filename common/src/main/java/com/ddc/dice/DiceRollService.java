package com.ddc.dice;

import com.ddc.core.dice.DiceExpression;
import com.ddc.core.dice.DiceRoller;
import com.ddc.core.dice.RollMode;
import com.ddc.core.dice.RollResult;
import com.ddc.network.DiceResultPayload;
import dev.architectury.networking.NetworkManager;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Rolls dice on the server and tells the right people about it.
 *
 * <p>The server is the only thing that ever decides a number. Clients receive the seed and replay it,
 * so a modified client can lie to its own screen but never to the table.
 */
public final class DiceRollService {

    /** How far a public roll carries, in blocks. From the PRD's 32-block roll broadcast. */
    public static final double BROADCAST_RADIUS = 32.0;

    private final DiceRoller roller;

    public DiceRollService(DiceRoller roller) {
        this.roller = Objects.requireNonNull(roller, "roller");
    }

    /** The service the server runs, seeded from the platform's own randomness. */
    public static DiceRollService serverSide() {
        return new DiceRollService(DiceRoller.random());
    }

    /**
     * Rolls for a player and shows the result to everyone within {@link #BROADCAST_RADIUS}.
     *
     * @return the roll, so the caller can react to a critical
     */
    public RollResult rollPublic(ServerPlayer player, DiceExpression expression, RollMode mode) {
        RollResult result = roll(expression, mode);
        List<ServerPlayer> audience = nearbyPlayers(player);
        Component message = chatMessage(player, result);
        for (ServerPlayer viewer : audience) {
            viewer.sendSystemMessage(message);
        }
        NetworkManager.sendToPlayers(audience, DiceResultPayload.of(player.getUUID(), displayName(player), result));
        throwDice(player, result);
        return result;
    }

    /**
     * Puts the dice in the world in front of the roller, for PRD 4.1.
     *
     * <p>The entity carries the seed and nothing else: the faces went out in the payload above, and
     * the tumble comes out of the seed on each client.
     */
    private static void throwDice(ServerPlayer player, RollResult result) {
        Vec3 at = player.position().add(player.getLookAngle().scale(0.8)).add(0, 1.2, 0);
        DiceEntity.spawn(player.level(), at, result.seed());
    }

    /**
     * Rolls for a player and shows the result only to them.
     *
     * <p>This is the GM's hidden roll from the PRD: the dice still render, but only on the roller's
     * screen, so the table never learns what the monster's attack actually was.
     */
    public RollResult rollHidden(ServerPlayer player, DiceExpression expression, RollMode mode) {
        RollResult result = roll(expression, mode);
        player.sendSystemMessage(chatMessage(player, result).copy().append(Component.literal(" (hidden)")));
        NetworkManager.sendToPlayer(player, DiceResultPayload.of(player.getUUID(), displayName(player), result));
        return result;
    }

    private RollResult roll(DiceExpression expression, RollMode mode) {
        return roller.roll(expression, mode);
    }

    private static Component chatMessage(ServerPlayer player, RollResult result) {
        return Component.literal("[" + displayName(player) + "] " + result.describe());
    }

    private static String displayName(ServerPlayer player) {
        return player.getGameProfile().name();
    }

    /** Everyone close enough to see the dice land, including the roller. */
    private List<ServerPlayer> nearbyPlayers(ServerPlayer roller) {
        ServerLevel level = roller.level();
        double radiusSquared = BROADCAST_RADIUS * BROADCAST_RADIUS;
        return level.players().stream()
                .filter(player -> player.distanceToSqr(roller) <= radiusSquared)
                .toList();
    }
}
