package com.ddc.command;

import com.ddc.character.CharacterService;
import com.ddc.character.HealthService;
import com.ddc.character.CharacterSheet;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Spell;
import com.ddc.spell.SpellService;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /ddc cast <spell> <target>} and {@code /ddc rest}.
 *
 * <p>The target is named rather than looked at: an explicit target is what a table wants when the GM
 * is calling shots, and it needs no raycast to argue with.
 */
public final class SpellCommand {

    private static final String ARG_SPELL = "spell";
    private static final String ARG_TARGET = "target";

    private static final DynamicCommandExceptionType UNKNOWN_SPELL = new DynamicCommandExceptionType(
            id -> Component.translatable("ddc.error.unknown_spell", id));

    private static final SimpleCommandExceptionType NOT_A_CREATURE =
            new SimpleCommandExceptionType(Component.translatable("ddc.error.not_a_creature"));

    private final CharacterService characters;
    private final DataRegistry<Spell> spells;
    private final DataRegistry<CharacterClass> classes;
    private final SpellService spellService;

    public SpellCommand(CharacterService characters, DataRegistry<Spell> spells,
            DataRegistry<CharacterClass> classes, SpellService spellService) {
        this.characters = characters;
        this.spells = spells;
        this.classes = classes;
        this.spellService = spellService;
    }

    /** The {@code cast} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> castBranch() {
        return Commands.literal("cast")
                .then(Commands.argument(ARG_SPELL, IdentifierArgument.id())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggestResource(spells.ids(), builder))
                        .then(Commands.argument(ARG_TARGET, EntityArgument.entity())
                                .executes(this::cast)));
    }

    /** The {@code rest} branch: a long rest, which is what gives the slots back. */
    public ArgumentBuilder<CommandSourceStack, ?> restBranch() {
        return Commands.literal("rest").executes(this::rest);
    }

    private int cast(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer caster = context.getSource().getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, ARG_SPELL);
        Spell spell = spells.get(id).orElseThrow(() -> UNKNOWN_SPELL.create(id));

        Entity target = EntityArgument.getEntity(context, ARG_TARGET);
        if (!(target instanceof LivingEntity living)) {
            throw NOT_A_CREATURE.create();
        }

        return switch (spellService.cast(caster, spell, id, living)) {
            case SpellService.Either.Left<SpellService.Failure, SpellService.Cast> left -> {
                context.getSource().sendFailure(left.value().message());
                yield 0;
            }
            case SpellService.Either.Right<SpellService.Failure, SpellService.Cast> right -> {
                context.getSource().sendSuccess(() -> describe(right.value(), living), false);
                yield Math.max(1, right.value().damageDealt());
            }
        };
    }

    /** What the cast did, in the caster's own language. */
    private Component describe(SpellService.Cast cast, LivingEntity target) {
        net.minecraft.network.chat.MutableComponent message = Component.translatable("ddc.spell.cast",
                cast.spell().name(), target.getName());

        cast.save().ifPresent(save -> message.append(" — ")
                .append(Component.translatable(save.isSuccess()
                        ? "ddc.spell.saved" : "ddc.spell.failed_save"))
                .append(" (" + save.total() + " / DC " + save.difficultyClass() + ")"));
        if (cast.damageDealt() > 0) {
            message.append(" ").append(Component.translatable("ddc.spell.damage", cast.damageDealt()));
        }
        return message;
    }

    private int rest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CharacterSheet sheet = characters.get(player);
        if (!sheet.hasClass()) {
            context.getSource().sendFailure(Component.translatable("ddc.error.no_class"));
            return 0;
        }
        characters.update(player, CharacterSheet::rested);
        characters.health().applyAndHeal(player);

        context.getSource().sendSuccess(() -> Component.translatable("ddc.rest.taken"), false);
        return HealthService.currentHitPoints(player);
    }
}
