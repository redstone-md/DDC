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

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "tester", Component.literal("tester"), null, null);
    }

    private static boolean parsesCleanly(String command, int permission) {
        var results = dispatcher.parse(command, source(permission));
        return !results.getReader().canRead() && results.getExceptions().isEmpty();
    }

    @Test
    void anyPlayerCanCallForTheirOwnCheck() {
        assertTrue(parsesCleanly("ddc check dexterity 15", 0));
        assertTrue(parsesCleanly("ddc check str 10", 0));
    }

    @Test
    @DisplayName("only a Game Master can call for someone else's roll")
    void callingForAnothersRollIsGameMasterOnly() {
        CommandNode<CommandSourceStack> node = dispatcher.getRoot()
                .getChild("ddc").getChild("check").getChild("ability").getChild("dc").getChild("player");
        assertNotNull(node, "the GM branch is missing");

        assertFalse(node.canUse(source(0)));
        assertTrue(node.canUse(source(4)));
    }

    @Test
    void aPlayerCannotRollSomeoneElsesDice() {
        var results = dispatcher.parse("ddc check dexterity 15 Steve", source(0));

        assertTrue(results.getReader().canRead(), "the player argument was never consumed");
    }

    @Test
    @DisplayName("the difficulty stays inside the SRD's range")
    void refusesADifficultyOutsideTheTable() {
        assertFalse(parsesCleanly("ddc check dexterity 0", 0));
        assertFalse(parsesCleanly("ddc check dexterity 31", 0));
        assertTrue(parsesCleanly("ddc check dexterity 30", 0));
    }

    @Test
    void everyAbilityIsSuggested() {
        var suggestions = dispatcher.getCompletionSuggestions(
                        dispatcher.parse("ddc check ", source(0)))
                .join().getList();

        assertEquals(6, suggestions.size(), "all six abilities");
        assertTrue(suggestions.stream().anyMatch(s -> s.getText().equals("charisma")));
    }
}
