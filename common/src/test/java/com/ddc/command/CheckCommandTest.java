package com.ddc.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.character.CharacterService;
import com.ddc.core.dice.DiceRoller;
import com.ddc.dice.DiceRollService;
import com.ddc.rules.DDCRegistries;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers the ability check tree, and who may call for whose roll. */
class CheckCommandTest {

    private static CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
        dispatcher = new CommandDispatcher<>();
        CharacterService characters = new CharacterService(DDCRegistries.CLASSES);
        dispatcher.register(net.minecraft.commands.Commands.literal("ddc")
                .then(new CheckCommand(new com.ddc.check.CheckService(characters,
                        new DiceRollService(DiceRoller.replaying(1L)))).branch()));
    }

    private static CommandSourceStack source(PermissionSet permissions) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permissions,
                "tester", Component.literal("tester"), null, null);
    }

    private static boolean parsesCleanly(String command, PermissionSet permissions) {
        var results = dispatcher.parse(command, source(permissions));
        return !results.getReader().canRead() && results.getExceptions().isEmpty();
    }

    @Test
    void anyPlayerCanCallForTheirOwnCheck() {
        assertTrue(parsesCleanly("ddc check dexterity 15", PermissionSet.NO_PERMISSIONS));
        assertTrue(parsesCleanly("ddc check str 10", PermissionSet.NO_PERMISSIONS));
    }

    @Test
    @DisplayName("only a Game Master can call for someone else's roll")
    void callingForAnothersRollIsGameMasterOnly() {
        CommandNode<CommandSourceStack> node = dispatcher.getRoot()
                .getChild("ddc").getChild("check").getChild("ability").getChild("dc").getChild("player");
        assertNotNull(node, "the GM branch is missing");

        assertFalse(node.canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(node.canUse(source(PermissionSet.ALL_PERMISSIONS)));
    }

    @Test
    void aPlayerCannotRollSomeoneElsesDice() {
        var results = dispatcher.parse("ddc check dexterity 15 Steve", source(PermissionSet.NO_PERMISSIONS));

        assertTrue(results.getReader().canRead(), "the player argument was never consumed");
    }

    @Test
    @DisplayName("the difficulty stays inside the SRD's range")
    void refusesADifficultyOutsideTheTable() {
        assertFalse(parsesCleanly("ddc check dexterity 0", PermissionSet.NO_PERMISSIONS));
        assertFalse(parsesCleanly("ddc check dexterity 31", PermissionSet.NO_PERMISSIONS));
        assertTrue(parsesCleanly("ddc check dexterity 30", PermissionSet.NO_PERMISSIONS));
    }

    @Test
    void everyAbilityIsSuggested() {
        var suggestions = dispatcher.getCompletionSuggestions(
                        dispatcher.parse("ddc check ", source(PermissionSet.NO_PERMISSIONS)))
                .join().getList();

        assertEquals(6, suggestions.size(), "all six abilities");
        assertTrue(suggestions.stream().anyMatch(s -> s.getText().equals("charisma")));
    }
}
