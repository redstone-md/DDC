package com.ddc.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.core.dice.DiceRoller;
import com.ddc.dice.DiceRollService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
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

/**
 * Covers the shape of the command tree and the GM gate on hidden rolls.
 *
 * <p>These parse commands rather than execute them: execution needs a live ServerPlayer, which is
 * out of reach of a unit test. Parsing still exercises the branch structure and every {@code
 * requires} predicate, which is where the GM gate lives.
 */
class RollCommandTest {

    private static CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
        dispatcher = new CommandDispatcher<>();
        new RollCommand(new DiceRollService(DiceRoller.replaying(1L))).register(dispatcher);
    }

    /** A source with no world or player behind it: enough to parse, not to execute. */
    private static CommandSourceStack source(PermissionSet permissions) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permissions,
                "tester", Component.literal("tester"), null, null);
    }

    private static ParseResults<CommandSourceStack> parse(String command, PermissionSet permissions) {
        return dispatcher.parse(command, source(permissions));
    }

    private static boolean parsesCleanly(String command, PermissionSet permissions) {
        ParseResults<CommandSourceStack> results = parse(command, permissions);
        return results.getReader().canRead() == false && results.getExceptions().isEmpty();
    }

    @Test
    void acceptsAPlainRoll() {
        assertTrue(parsesCleanly("roll 1d20+3", PermissionSet.NO_PERMISSIONS));
    }

    @Test
    void acceptsAdvantageAndDisadvantage() {
        assertTrue(parsesCleanly("roll 1d20 advantage", PermissionSet.NO_PERMISSIONS));
        assertTrue(parsesCleanly("roll 1d20 disadvantage", PermissionSet.NO_PERMISSIONS));
    }

    @Test
    @DisplayName("a hidden roll is offered to a Game Master")
    void gameMasterMayRollHidden() {
        assertTrue(parsesCleanly("roll 1d20 hidden", PermissionSet.ALL_PERMISSIONS));
        assertTrue(parsesCleanly("roll 1d20 advantage hidden", PermissionSet.ALL_PERMISSIONS));
    }

    @Test
    @DisplayName("a hidden roll is not reachable without the Game Master permission")
    void ordinaryPlayerMayNotRollHidden() {
        assertFalse(parsesCleanly("roll 1d20 hidden", PermissionSet.NO_PERMISSIONS));
        assertFalse(parsesCleanly("roll 1d20 advantage hidden", PermissionSet.NO_PERMISSIONS));
    }

    /**
     * Minecraft builds each client's command tree from {@code canUse}, so a node that rejects the
     * source never reaches that player's completions. Brigadier's own suggestion list does not filter
     * by requirement, which is why this checks the node rather than the suggestions.
     */
    @Test
    @DisplayName("the hidden node is hidden from an ordinary player's command tree")
    void hiddenIsNotOfferedToAnOrdinaryPlayer() {
        CommandNode<CommandSourceStack> hidden = dispatcher.getRoot()
                .getChild("roll").getChild("expression").getChild("hidden");

        assertFalse(hidden.canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(hidden.canUse(source(PermissionSet.ALL_PERMISSIONS)));
    }

    @Test
    @DisplayName("the expression argument does not swallow the literals that follow it")
    void theExpressionStopsAtTheFirstSpace() {
        ParseResults<CommandSourceStack> results = parse("roll 1d20 advantage", PermissionSet.NO_PERMISSIONS);

        assertEquals("1d20", results.getContext().getArguments().get("expression").getResult());
        assertEquals(3, results.getContext().getNodes().size(), "roll, the expression, then advantage");
    }

    @Test
    void theRollCommandIsRegisteredUnderItsName() {
        assertEquals(1, dispatcher.getRoot().getChildren().size());
        assertEquals("roll", dispatcher.getRoot().getChildren().iterator().next().getName());
    }
}
