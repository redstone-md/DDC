package com.ddc.command;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.core.character.Ability;
import com.ddc.rules.CharacterClassRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
            id -> Component.literal("No loaded data pack defines the class '" + id + "'"));

    private final CharacterService characters;
    private final CharacterClassRegistry classes;
    private final NarrateCommand narration;

    public CharacterCommand(CharacterService characters, CharacterClassRegistry classes,
            NarrateCommand narration) {
        this.characters = characters;
        this.classes = classes;
        this.narration = narration;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ddc")
                .then(Commands.literal("sheet").executes(this::showSheet))
                .then(Commands.literal("class")
                        .then(Commands.argument(ARG_CLASS, IdentifierArgument.id())
                                .suggests(classSuggestions())
                                .executes(this::chooseClass)))
                .then(narration.branch()));
    }

    private SuggestionProvider<CommandSourceStack> classSuggestions() {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(classes.ids(), builder);
    }

    private int chooseClass(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, ARG_CLASS);

        CharacterSheet sheet = characters.chooseClass(player, id)
                .orElseThrow(() -> UNKNOWN_CLASS.create(id));

        context.getSource().sendSuccess(() -> Component.literal(
                "You are now a " + characters.definitionFor(sheet).orElseThrow().name()
                        + " with " + sheet.currentHitPoints() + " hit points."), false);
        return sheet.currentHitPoints();
    }

    private int showSheet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CharacterSheet sheet = characters.get(player);

        context.getSource().sendSuccess(() -> Component.literal(describe(sheet)), false);
        return sheet.level();
    }

    private String describe(CharacterSheet sheet) {
        String className = characters.definitionFor(sheet)
                .map(definition -> definition.name())
                .orElse("no class yet, pick one with /ddc class <id>");
        StringBuilder sb = new StringBuilder(className)
                .append(" - level ").append(sheet.level())
                .append(", HP ").append(sheet.currentHitPoints());
        characters.maxHitPoints(sheet).ifPresent(max -> sb.append('/').append(max));
        sb.append(", proficiency +").append(sheet.proficiencyBonus()).append('\n');
        for (Ability ability : Ability.values()) {
            int modifier = sheet.modifier(ability);
            sb.append(ability.abbreviation()).append(' ').append(sheet.scores().get(ability))
                    .append(" (").append(modifier >= 0 ? "+" : "").append(modifier).append(") ");
        }
        return sb.toString().trim();
    }
}
