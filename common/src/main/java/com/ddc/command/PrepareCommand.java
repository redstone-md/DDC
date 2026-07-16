package com.ddc.command;

import com.ddc.character.CharacterService;
import com.ddc.character.CharacterSheet;
import com.ddc.registry.DDCItems;
import com.ddc.rules.CharacterClass;
import com.ddc.rules.DataRegistry;
import com.ddc.rules.Spell;
import com.ddc.rules.Spellcasting;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * {@code /ddc prepare <spell>} and {@code /ddc forget <spell>}: writing in the spellbook.
 *
 * <p>PRD 3.1's wizard has to write a spell into a book before casting it. Cantrips do not: the SRD's
 * cantrips are known rather than prepared, and a wizard who had to write down fire bolt every morning
 * would be a wizard nobody wants to play.
 */
public final class PrepareCommand {

    private static final String ARG_SPELL = "spell";

    private static final DynamicCommandExceptionType UNKNOWN_SPELL = new DynamicCommandExceptionType(
            id -> Component.translatable("ddc.error.unknown_spell", id));

    private static final SimpleCommandExceptionType NO_BOOK = new SimpleCommandExceptionType(
            Component.translatable("ddc.error.no_book"));

    private static final SimpleCommandExceptionType CANNOT_CAST = new SimpleCommandExceptionType(
            Component.translatable("ddc.error.cannot_cast"));

    private static final SimpleCommandExceptionType TOO_HIGH = new SimpleCommandExceptionType(
            Component.translatable("ddc.error.spell_too_high"));

    private static final SimpleCommandExceptionType BOOK_FULL = new SimpleCommandExceptionType(
            Component.translatable("ddc.error.book_full"));

    private final CharacterService characters;
    private final DataRegistry<Spell> spells;

    public PrepareCommand(CharacterService characters, DataRegistry<Spell> spells) {
        this.characters = characters;
        this.spells = spells;
    }

    /** The {@code prepare} branch, to hang under {@code /ddc}. */
    public ArgumentBuilder<CommandSourceStack, ?> prepareBranch() {
        return Commands.literal("prepare")
                .then(Commands.argument(ARG_SPELL, IdentifierArgument.id())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggestResource(spells.ids(), builder))
                        .executes(this::prepare));
    }

    /** The {@code forget} branch: scrubbing a spell out to make room. */
    public ArgumentBuilder<CommandSourceStack, ?> forgetBranch() {
        return Commands.literal("forget")
                .then(Commands.argument(ARG_SPELL, IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                characters.get(context.getSource().getPlayerOrException())
                                        .preparedSpells(), builder))
                        .executes(this::forget));
    }

    private int prepare(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, ARG_SPELL);
        Spell spell = spells.get(id).orElseThrow(() -> UNKNOWN_SPELL.create(id));

        if (!isHoldingSpellbook(player)) {
            throw NO_BOOK.create();
        }
        CharacterSheet sheet = characters.get(player);
        Spellcasting casting = characters.definitionFor(sheet)
                .flatMap(CharacterClass::spellcasting)
                .orElseThrow(CANNOT_CAST::create);

        if (spell.isCantrip()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("ddc.spell.cantrip_known", spell.name()), false);
            return 0;
        }
        if (casting.slotsFor(sheet.level(), spell.level()) == 0) {
            throw TOO_HIGH.create();
        }
        if (!sheet.hasPrepared(id)
                && sheet.preparedSpells().size() >= sheet.preparedSpellLimit(casting.ability())) {
            throw BOOK_FULL.create();
        }

        CharacterSheet updated = characters.update(player, current -> current.withPrepared(id));
        context.getSource().sendSuccess(() -> Component.translatable("ddc.spell.prepared",
                spell.name(), updated.preparedSpells().size(),
                updated.preparedSpellLimit(casting.ability())), false);
        return updated.preparedSpells().size();
    }

    private int forget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Identifier id = IdentifierArgument.getId(context, ARG_SPELL);
        if (!isHoldingSpellbook(player)) {
            throw NO_BOOK.create();
        }

        characters.update(player, current -> current.withoutPrepared(id));
        context.getSource().sendSuccess(() -> Component.translatable("ddc.spell.forgotten", id), false);
        return 1;
    }

    /** Either hand: which hand a book is in is not a rule worth having. */
    private static boolean isHoldingSpellbook(ServerPlayer player) {
        return Optional.of(player.getItemInHand(InteractionHand.MAIN_HAND))
                .filter(stack -> stack.is(DDCItems.SPELLBOOK.get()))
                .or(() -> Optional.of(player.getItemInHand(InteractionHand.OFF_HAND))
                        .filter(stack -> stack.is(DDCItems.SPELLBOOK.get())))
                .isPresent();
    }
}
