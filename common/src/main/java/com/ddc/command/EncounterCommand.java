package com.ddc.command;

import com.ddc.gm.GameMasters;
import com.ddc.gm.GmWandSelection;
import com.ddc.registry.DDCItems;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Encounter;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc encounter <id>}: which encounter the Game Master's wand will place.
 *
 * <p>PRD 3.2 draws the wand's radial menu picking this. The menu is a client thing and the selection
 * is server state, so the wheel picks it the way everything else does -- by sending this. One door,
 * one check.
 */
public final class EncounterCommand {

    private static final String ARG_ENCOUNTER = "encounter";

    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.translatable("ddc.error.unknown_encounter", id));

    private final DataRegistry<Encounter> encounters;

    public EncounterCommand(DataRegistry<Encounter> encounters) {
        this.encounters = encounters;
    }

    /** The {@code encounter} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("encounter")
                .requires(GameMasters.requirement())
                .then(Commands.argument(ARG_ENCOUNTER, IdentifierArgument.id())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggestResource(encounters.ids(), builder))
                        .executes(this::select));
    }

    private int select(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, ARG_ENCOUNTER);
        Encounter encounter = encounters.get(id).orElseThrow(() -> UNKNOWN.create(id));

        // Checked again rather than trusting the branch: a command tree is not a security boundary.
        if (!GameMasters.isGameMaster(player)) {
            context.getSource().sendFailure(Component.translatable("ddc.error.not_gm"));
            return 0;
        }
        GmWandSelection.select(player, id);
        context.getSource().sendSuccess(() -> Component.translatable("ddc.gm.selected",
                encounter.name(), encounter.total()).withStyle(ChatFormatting.GOLD), false);
        return encounter.total();
    }

    /** The wand, so the wheel can tell whether the player is holding one. */
    public static net.minecraft.world.item.Item wand() {
        return DDCItems.GM_WAND.get();
    }
}
