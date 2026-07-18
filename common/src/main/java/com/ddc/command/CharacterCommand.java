package com.ddc.command;

import com.ddc.character.CharacterService;
import com.ddc.character.HealthService;
import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Race;
import com.ddc.rules.Spellcasting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc sheet} and {@code /ddc class <id>}: read your character, and pick a class.
 *
 * <p>Class ids are suggested from the loaded data packs, so an addon's classes appear in completions
 * the moment its pack is present, with no code here knowing their names.
 */
public final class CharacterCommand {

    private static final String ARG_CLASS = "class";

    private static final DynamicCommandExceptionType UNKNOWN_CLASS = new DynamicCommandExceptionType(
            id -> Component.translatable("ddc.error.unknown_class", id));

    private static final String ARG_RACE = "race";

    private static final DynamicCommandExceptionType UNKNOWN_RACE = new DynamicCommandExceptionType(
            id -> Component.translatable("ddc.error.unknown_race", id));

    private final CharacterService characters;
    private final DataRegistry<CharacterClass> classes;
    private final DataRegistry<Race> races;
    private final NarrateCommand narration;
    private final SpellCommand spells;
    private final FeatureCommand features;
    private final CheckCommand checks;
    private final WorldCommand world;
    private final PrepareCommand preparation;
    private final EncounterCommand encounters;
    private final ExperienceCommand experience;
    private final SoundCommand sounds;
    private final LockCommand locks = new LockCommand();
    private final SpawnCommand spawns;
    private final GameMasterCommand gameMaster = new GameMasterCommand();
    private final QuestCommand quests = new QuestCommand();

    public CharacterCommand(CharacterService characters, DataRegistry<CharacterClass> classes,
            DataRegistry<Race> races, NarrateCommand narration, SpellCommand spells,
            FeatureCommand features, CheckCommand checks, WorldCommand world,
            PrepareCommand preparation, EncounterCommand encounters, ExperienceCommand experience, SoundCommand sounds,
            SpawnCommand spawns) {
        this.characters = characters;
        this.classes = classes;
        this.races = races;
        this.narration = narration;
        this.spells = spells;
        this.features = features;
        this.checks = checks;
        this.world = world;
        this.preparation = preparation;
        this.encounters = encounters;
        this.experience = experience;
        this.sounds = sounds;
        this.spawns = spawns;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ddc")
                .then(Commands.literal("sheet").executes(this::showSheet))
                .then(Commands.literal("class")
                        .then(Commands.argument(ARG_CLASS, ResourceLocationArgument.id())
                                .suggests(classSuggestions())
                                .executes(this::chooseClass)))
                .then(Commands.literal("race")
                        .then(Commands.argument(ARG_RACE, ResourceLocationArgument.id())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggestResource(races.ids(), builder))
                                .executes(this::chooseRace)))
                .then(preparation.prepareBranch())
                .then(preparation.forgetBranch())
                .then(spells.castBranch())
                .then(spells.restBranch())
                .then(checks.branch())
                .then(features.secondWindBranch())
                .then(features.channelDivinityBranch())
                .then(features.actionSurgeBranch())
                .then(features.maneuverBranch())
                .then(narration.branch())
                .then(world.branch())
                .then(encounters.branch())
                .then(experience.branch())
                .then(sounds.branch())
                .then(locks.lockBranch())
                .then(locks.unlockBranch())
                .then(spawns.branch())
                .then(gameMaster.branch())
                .then(quests.branch()));
    }

    private SuggestionProvider<CommandSourceStack> classSuggestions() {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(classes.ids(), builder);
    }

    private int chooseClass(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, ARG_CLASS);

        CharacterSheet sheet = characters.chooseClass(player, id)
                .orElseThrow(() -> UNKNOWN_CLASS.create(id));

        int hitPoints = HealthService.maxHitPoints(player);
        context.getSource().sendSuccess(() -> Component.translatable("ddc.class.chosen",
                characters.definitionFor(sheet).orElseThrow().name(), hitPoints), false);
        return hitPoints;
    }

    private int chooseRace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, ARG_RACE);
        Race race = races.get(id).orElseThrow(() -> UNKNOWN_RACE.create(id));

        // The race being replaced hands its bonuses back, so picking human twice does not pay twice.
        boolean isNew = !characters.get(player).race().equals(java.util.Optional.of(id));
        characters.update(player, sheet -> sheet.withRace(id, race, sheet.race().flatMap(races::get)));
        if (isNew) {
            // Only for a race they did not already have: re-picking the same one must not restock it.
            com.ddc.character.RaceItems.give(player, race);
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("ddc.race.chosen", race.name(), race.speed()), false);
        return 1;
    }

    private int showSheet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CharacterSheet sheet = characters.get(player);

        // Two lines rather than one with a newline in it: chat shows a component's newline as the
        // two characters, which is how "proficiency +2\nSTR 10" reached a player's screen.
        context.getSource().sendSuccess(() -> headline(sheet, player), false);
        context.getSource().sendSuccess(() -> abilities(sheet), false);
        return sheet.level();
    }

    /** The first line: who the character is and how they are doing. */
    private Component headline(CharacterSheet sheet, ServerPlayer player) {
        MutableComponent line = Component.literal(characters.definitionFor(sheet)
                        .map(CharacterClass::name)
                        .orElse(Component.translatable("ddc.screen.no_class").getString()))
                .append(" · ")
                .append(Component.translatable("ddc.screen.hit_points",
                        HealthService.currentHitPoints(player), HealthService.maxHitPoints(player)))
                .append(" · ")
                .append(Component.translatable("ddc.screen.proficiency", sheet.proficiencyBonus()));

        characters.definitionFor(sheet).flatMap(CharacterClass::spellcasting).ifPresent(casting ->
                line.append(" · ").append(describeSlots(sheet, casting)));
        return line;
    }

    /** The second line: the six abilities, the way a paper sheet reads them. */
    private static Component abilities(CharacterSheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (Ability ability : Ability.values()) {
            int modifier = sheet.modifier(ability);
            sb.append(ability.abbreviation()).append(' ').append(sheet.scores().get(ability))
                    .append(" (").append(modifier >= 0 ? "+" : "").append(modifier).append(")  ");
        }
        return Component.literal(sb.toString().trim());
    }

    /** Slots as remaining-of-total per spell level, so a caster can see what they have left. */
    private static String describeSlots(CharacterSheet sheet, Spellcasting casting) {
        StringBuilder sb = new StringBuilder();
        for (int spellLevel = 1; spellLevel <= casting.highestSlotLevel(sheet.level()); spellLevel++) {
            int total = casting.slotsFor(sheet.level(), spellLevel);
            if (total == 0) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(spellLevel).append(':').append(total - sheet.usedSlots(spellLevel))
                    .append('/').append(total);
        }
        return sb.isEmpty() ? "none" : sb.toString();
    }
}
