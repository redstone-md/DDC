package com.ddc.command;

import com.ddc.gm.GameMasters;
import com.ddc.gm.SoundscapeService;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /ddc sound <id> [volume] [pitch]}: the Game Master's soundscape.
 *
 * <p>Not {@code /playsound}, which needs a target selector and a position and plays at a place. This
 * plays for the table, wherever they are, because a GM's sound is the story's rather than the world's.
 *
 * <p>Suggests every sound the game knows, which is how a GM finds {@code minecraft:entity.ender_dragon.growl}
 * without leaving the game to look it up.
 */
public final class SoundCommand {

    private static final String ARG_SOUND = "sound";
    private static final String ARG_VOLUME = "volume";
    private static final String ARG_PITCH = "pitch";

    private final SoundscapeService soundscapes;

    public SoundCommand(SoundscapeService soundscapes) {
        this.soundscapes = soundscapes;
    }

    /** The {@code sound} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("sound")
                .requires(GameMasters.requirement())
                .then(Commands.argument(ARG_SOUND, com.mojang.brigadier.arguments.StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                BuiltInRegistries.SOUND_EVENT.keySet().stream().map(Identifier::toString),
                                builder))
                        .executes(context -> play(context, 1.0f, 1.0f))
                        .then(Commands.argument(ARG_VOLUME, FloatArgumentType.floatArg(0.0f, 4.0f))
                                .executes(context -> play(context,
                                        FloatArgumentType.getFloat(context, ARG_VOLUME), 1.0f))
                                .then(Commands.argument(ARG_PITCH, FloatArgumentType.floatArg(0.5f, 2.0f))
                                        .executes(context -> play(context,
                                                FloatArgumentType.getFloat(context, ARG_VOLUME),
                                                FloatArgumentType.getFloat(context, ARG_PITCH))))));
    }

    private int play(CommandContext<CommandSourceStack> context, float volume, float pitch)
            throws CommandSyntaxException {
        ServerPlayer gameMaster = context.getSource().getPlayerOrException();
        String key = com.mojang.brigadier.arguments.StringArgumentType.getString(context, ARG_SOUND);
        Identifier id = Identifier.tryParse(key);

        if (id == null || !soundscapes.play(gameMaster, id, volume, pitch)) {
            context.getSource().sendFailure(Component.translatable("ddc.error.unknown_sound", key));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("ddc.gm.sound_played", key), false);
        return 1;
    }
}
