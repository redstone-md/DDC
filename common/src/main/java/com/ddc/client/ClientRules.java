package com.ddc.client;

import com.ddc.network.RulesPayload;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The rules this client has been told it may pick from.
 *
 * <p>Names and ids, nothing else. It exists so a menu can be drawn; it is never asked whether an
 * action is legal, because that answer lives on the server and this copy could be edited by anyone
 * running the client.
 */
@Environment(EnvType.CLIENT)
public final class ClientRules {

    private static volatile RulesPayload rules =
            new RulesPayload(List.of(), List.of(), List.of(), List.of(), false);

    private ClientRules() {
    }

    /** Takes what the server sent, on join and after every reload. */
    public static void accept(RulesPayload payload) {
        rules = payload;
    }

    /** Forgets everything. Leaving a world must not leave another server's classes on the menu. */
    public static void clear() {
        rules = new RulesPayload(List.of(), List.of(), List.of(), List.of(), false);
    }

    public static List<RulesPayload.Entry> classes() {
        return rules.classes();
    }

    public static List<RulesPayload.Entry> races() {
        return rules.races();
    }

    public static List<RulesPayload.Entry> spells() {
        return rules.spells();
    }

    /** The encounters a Game Master may place. Empty for everyone else. */
    public static List<RulesPayload.Entry> encounters() {
        return rules.encounters();
    }

    /** Whether the server says this player may use the GM tools. */
    public static boolean isGameMaster() {
        return rules.gameMaster();
    }

    /** A race's name, or its id when this client has not been told about it. */
    public static String raceName(net.minecraft.resources.ResourceLocation id) {
        return rules.races().stream()
                .filter(entry -> entry.id().equals(id))
                .map(RulesPayload.Entry::name)
                .findFirst()
                .orElseGet(id::getPath);
    }

    /** A spell's name, or its id when this client has not been told about it. */
    public static String spellName(net.minecraft.resources.ResourceLocation id) {
        return rules.spells().stream()
                .filter(entry -> entry.id().equals(id))
                .map(RulesPayload.Entry::name)
                .findFirst()
                .orElseGet(id::getPath);
    }
}
