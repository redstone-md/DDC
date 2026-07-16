package com.ddc.gm;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.server.permissions.Permissions;
import java.util.function.Predicate;

/**
 * Who is allowed to act as a Game Master.
 *
 * <p>The single gate for every GM capability. ADR-0003 specifies operator level 2 or higher;
 * Minecraft 26 replaced numeric operator levels with named permissions, and the level-2 equivalent is
 * {@link Permissions#COMMANDS_GAMEMASTER}. A server running a permissions plugin grants that through
 * its own provider, which covers the ADR's "or specified by permission plugins" with no extra work.
 *
 * <p>The server is the authority. Never ask a client whether it is a GM.
 */
public final class GameMasters {

    private static final PermissionCheck CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

    private GameMasters() {
    }

    /** Whether this player may use GM tools. The check every GM packet handler must run. */
    public static boolean isGameMaster(ServerPlayer player) {
        return CHECK.check(player.permissions());
    }

    /**
     * The requirement for a GM-only command branch, which also hides the branch from the completions
     * of players who cannot use it.
     */
    public static <T extends PermissionSetSupplier> Predicate<T> requirement() {
        return Commands.hasPermission(CHECK);
    }
}
