package com.ddc.gm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Predicate;

/**
 * Who is allowed to act as a Game Master.
 *
 * <p>The single gate for every GM capability. ADR-0003 specifies operator level 2 or higher, which on
 * 1.21.1 is a numeric permission level: a server running a permissions plugin grants level 2 through
 * its own provider, which covers the ADR's "or specified by permission plugins" with no extra work.
 *
 * <p>The server is the authority. Never ask a client whether it is a GM.
 */
public final class GameMasters {

    /** Operator level 2: command blocks, /gamemode, and the GM tools. */
    public static final int GAMEMASTER_LEVEL = 2;

    private GameMasters() {
    }

    /** Whether this player may use GM tools. The check every GM packet handler must run. */
    public static boolean isGameMaster(ServerPlayer player) {
        return player.hasPermissions(GAMEMASTER_LEVEL);
    }

    /**
     * The requirement for a GM-only command branch, which also hides the branch from the completions
     * of players who cannot use it.
     */
    public static Predicate<CommandSourceStack> requirement() {
        return source -> source.hasPermission(GAMEMASTER_LEVEL);
    }
}
