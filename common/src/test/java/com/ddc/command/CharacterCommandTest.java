package com.ddc.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.MinecraftBootstrapExtension;
import com.ddc.character.CharacterService;
import com.ddc.character.FeatureService;
import com.ddc.gm.NarrationService;
import com.ddc.command.SpellCommand;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.DDCRegistries;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Race;
import com.ddc.rules.Spell;
import com.ddc.spell.SpellService;
import com.ddc.dice.DiceRollService;
import com.ddc.core.dice.DiceRoller;
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

/** Covers the {@code /ddc} tree, and in particular that narration is a GM-only branch. */
class CharacterCommandTest {

    private static CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeAll
    static void setUp() {
        MinecraftBootstrapExtension.bootstrap();
        dispatcher = new CommandDispatcher<>();
        DataRegistry<CharacterClass> classes = DDCRegistries.CLASSES;
        DataRegistry<Race> races = DDCRegistries.RACES;
        DataRegistry<Spell> spells = DDCRegistries.SPELLS;
        CharacterService characters = new CharacterService(classes);
        DiceRollService diceRolls = new DiceRollService(DiceRoller.replaying(1L));
        new CharacterCommand(characters, classes, races, new NarrateCommand(new NarrationService()),
                new SpellCommand(characters, spells, classes,
                        new SpellService(characters, diceRolls, DiceRoller.replaying(1L))),
                new FeatureCommand(new FeatureService(characters, diceRolls)),
                new CheckCommand(characters, diceRolls),
                new WorldCommand(new com.ddc.gm.WorldControlService()))
                .register(dispatcher);
    }

    private static CommandSourceStack source(PermissionSet permissions) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permissions,
                "tester", Component.literal("tester"), null, null);
    }

    private static CommandNode<CommandSourceStack> node(String... path) {
        CommandNode<CommandSourceStack> current = dispatcher.getRoot();
        for (String name : path) {
            current = current.getChild(name);
            assertNotNull(current, "missing command node: " + name);
        }
        return current;
    }

    @Test
    void everyPlayerCanReadTheirSheetAndPickAClass() {
        assertTrue(node("ddc", "sheet").canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(node("ddc", "class").canUse(source(PermissionSet.NO_PERMISSIONS)));
    }

    @Test
    @DisplayName("narration is a Game Master branch and no one else sees it")
    void narrationIsGameMasterOnly() {
        CommandNode<CommandSourceStack> narrate = node("ddc", "narrate");

        assertFalse(narrate.canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(narrate.canUse(source(PermissionSet.ALL_PERMISSIONS)));
    }

    /**
     * A branch the source cannot use is simply never entered, so the leftover text stays unread
     * rather than producing a parse exception. That unread remainder is the signal it was refused.
     */
    @Test
    void anOrdinaryPlayerCannotParseANarration() {
        var results = dispatcher.parse("ddc narrate the walls tremble", source(PermissionSet.NO_PERMISSIONS));

        assertTrue(results.getReader().canRead(), "the narration text was never consumed");
        assertEquals(1, results.getContext().getNodes().size(), "parsing stopped at /ddc");
    }

    @Test
    void aGameMastersNarrationParses() {
        var results = dispatcher.parse("ddc narrate the walls tremble", source(PermissionSet.ALL_PERMISSIONS));

        assertTrue(results.getExceptions().isEmpty());
        assertFalse(results.getReader().canRead());
        assertEquals("the walls tremble", results.getContext().getArguments().get("text").getResult(),
                "narration takes the rest of the line, spaces and all");
    }

    @Test
    @DisplayName("world control is a Game Master branch")
    void worldControlIsGameMasterOnly() {
        CommandNode<CommandSourceStack> world = node("ddc", "world");

        assertFalse(world.canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(world.canUse(source(PermissionSet.ALL_PERMISSIONS)));
    }

    @Test
    void everyWorldChangeHasABranch() {
        CommandNode<CommandSourceStack> world = node("ddc", "world");

        for (com.ddc.gm.WorldControlService.Change change : com.ddc.gm.WorldControlService.Change.values()) {
            String name = change.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            assertNotNull(world.getChild(name), "no branch for " + change);
        }
    }

    @Test
    void everyPlayerCanRollAnAbilityCheckAndUseTheirFeatures() {
        assertTrue(node("ddc", "check").canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(node("ddc", "second-wind").canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(node("ddc", "channel-divinity").canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(node("ddc", "cast").canUse(source(PermissionSet.NO_PERMISSIONS)));
        assertTrue(node("ddc", "rest").canUse(source(PermissionSet.NO_PERMISSIONS)));
    }

    @Test
    void theTreeIsRegisteredUnderDdc() {
        assertEquals("ddc", dispatcher.getRoot().getChildren().iterator().next().getName());
    }
}
