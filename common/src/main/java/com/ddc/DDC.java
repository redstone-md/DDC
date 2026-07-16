package com.ddc;

import com.ddc.character.CharacterService;
import com.ddc.character.FeatureService;
import com.ddc.command.CheckCommand;
import com.ddc.command.FeatureCommand;
import com.ddc.combat.CombatListener;
import com.ddc.combat.CombatRules;
import com.ddc.combat.SneakAttackService;
import com.ddc.command.CharacterCommand;
import com.ddc.command.NarrateCommand;
import com.ddc.command.SpellCommand;
import com.ddc.command.RollCommand;
import com.ddc.core.dice.DiceRoller;
import com.ddc.dice.DiceRollService;
import com.ddc.gm.NarrationService;
import com.ddc.gm.WorldControlService;
import com.ddc.command.WorldCommand;
import com.ddc.spell.SpellService;
import com.ddc.network.DDCNetwork;
import com.ddc.registry.DDCEntities;
import com.ddc.registry.DDCItems;
import com.ddc.rules.DDCRegistries;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's shared entry point.
 *
 * <p>Each loader's bootstrap calls {@link #init()} and nothing else, so everything that runs on both
 * Fabric and NeoForge is wired up here exactly once.
 */
public final class DDC {

    public static final String MOD_ID = "ddc";
    public static final String MOD_NAME = "Dungeons, Dragons & Crafting";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private DDC() {
    }

    /** Builds an identifier in the mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Runs on both loaders, on both sides, during mod construction. */
    public static void init() {
        LOGGER.info("Initialising {}", MOD_NAME);
        DDCNetwork.register();
        DDCRegistries.register();
        DDCItems.register();
        DDCEntities.register();

        CharacterService characters = new CharacterService(DDCRegistries.CLASSES);
        DiceRollService diceRolls = DiceRollService.serverSide();
        RollCommand rollCommand = new RollCommand(diceRolls);

        // Attack rolls are hidden, so they never reach the roll log: this roller answers only to the
        // combat listener.
        new CombatListener(new CombatRules(characters), DiceRoller.random(),
                new SneakAttackService(characters, diceRolls)).register();
        SpellService spellService = new SpellService(characters, diceRolls, DiceRoller.random());
        CharacterCommand characterCommand = new CharacterCommand(characters, DDCRegistries.CLASSES,
                DDCRegistries.RACES, new NarrateCommand(new NarrationService()),
                new SpellCommand(characters, DDCRegistries.SPELLS, DDCRegistries.CLASSES, spellService),
                new FeatureCommand(new FeatureService(characters, diceRolls)),
                new CheckCommand(characters, diceRolls),
                new WorldCommand(new WorldControlService()));

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            rollCommand.register(dispatcher);
            characterCommand.register(dispatcher);
        });

        // The client draws its HUD from the synced sheet, so it needs a fresh copy at every point
        // where its own copy may be stale or missing.
        PlayerEvent.PLAYER_JOIN.register(characters::sync);
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, removalReason) -> characters.sync(player));
    }
}
