package com.ddc.command;

import com.ddc.gm.EncounterService;
import com.ddc.gm.GameMasters;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Encounter;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /ddc spawn <encounter>}: an encounter where the Game Master is looking.
 *
 * <p>The wand already places encounters, and a wand needs a hand. This is the same thing without one,
 * which is what lets a channel-point reward buy an ambush: a viewer cannot right-click for the GM.
 *
 * <p>Where they are looking rather than where they stand, because a GM who wanted the goblins on top
 * of themselves would not have needed to ask.
 */
public final class SpawnCommand {

    private static final String ARG_ENCOUNTER = "encounter";

    /** How far a Game Master can throw a fight. */
    private static final double REACH = 48;

    private static final DynamicCommandExceptionType UNKNOWN_ENCOUNTER = new DynamicCommandExceptionType(
            id -> Component.translatable("ddc.error.unknown_encounter", id));

    private final DataRegistry<Encounter> encounters;
    private final EncounterService spawner;

    public SpawnCommand(DataRegistry<Encounter> encounters, EncounterService spawner) {
        this.encounters = encounters;
        this.spawner = spawner;
    }

    /** The {@code spawn} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("spawn")
                .requires(GameMasters.requirement())
                .then(Commands.argument(ARG_ENCOUNTER, StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                encounters.ids().stream().map(Identifier::toString), builder))
                        .executes(this::spawn));
    }

    private int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer gameMaster = context.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(context, ARG_ENCOUNTER);
        Identifier id = Optional.ofNullable(Identifier.tryParse(key))
                .orElseThrow(() -> UNKNOWN_ENCOUNTER.create(key));
        Encounter encounter = encounters.get(id).orElseThrow(() -> UNKNOWN_ENCOUNTER.create(key));

        if (!(gameMaster.level() instanceof ServerLevel level)) {
            return 0;
        }
        Vec3 at = lookedAt(gameMaster);
        EncounterService.Result result = spawner.spawn(gameMaster, encounter, level, at);
        if (!result.isSuccess()) {
            context.getSource().sendFailure(result.failure().orElseThrow().message());
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("ddc.gm.encounter_spawned",
                encounter.name(), result.spawned()), false);
        return result.spawned();
    }

    /** Where the Game Master is looking, or where they are standing when that is nowhere. */
    private static Vec3 lookedAt(ServerPlayer gameMaster) {
        HitResult hit = gameMaster.pick(REACH, 0, false);
        return hit.getType() == HitResult.Type.MISS ? gameMaster.position() : hit.getLocation();
    }
}
