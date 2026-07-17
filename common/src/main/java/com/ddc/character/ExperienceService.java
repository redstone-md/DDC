package com.ddc.character;

import com.ddc.rules.CharacterClass;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Earning levels, which is what the mod had never let anyone do.
 *
 * <p>Every character was level 1 forever: the sheet could hold a level, and nothing outside a test
 * ever set one. Hit points, proficiency and spell slots all read from that level, so the whole
 * progression half of the rules was dead code waiting for a way in. This is the way in.
 *
 * <p>The table belongs to the class the pack defined, so levelling speed is a data pack's to tune --
 * ADR-0002's promise -- and the level is never stored independently of the experience that earned it.
 */
public final class ExperienceService {

    private final CharacterService characters;

    public ExperienceService(CharacterService characters) {
        this.characters = characters;
    }

    /**
     * Awards experience and levels the character up if it has earned it.
     *
     * <p>A character with no class earns nothing: there is no table to measure them against, and a
     * player who has not made a character yet is not playing one. The experience is not banked for
     * later either, because a level 4 wizard conjured out of an afternoon's zombie killing is not a
     * character anyone chose to play.
     */
    public boolean award(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return false;
        }
        CharacterSheet sheet = characters.get(player);
        Optional<CharacterClass> definition = characters.definitionFor(sheet);
        if (definition.isEmpty()) {
            return false;
        }

        CharacterSheet earned = sheet.withExperienceGained(amount, definition.get());
        characters.update(player, current -> earned);

        if (earned.level() > sheet.level()) {
            // Hit points come from the level, so the player's actual health has to grow with it. This
            // is why levelling could not simply set a number on the sheet.
            characters.health().apply(player);
            announce(player, earned.level());
        }
        return true;
    }

    /** Tells the player, and the room, that they levelled. */
    private static void announce(ServerPlayer player, int reached) {
        Component message = Component.translatable("ddc.level.up", player.getDisplayName(), reached)
                .withStyle(ChatFormatting.GOLD);
        // The table hears it: a level is the sort of thing a party congratulates you for, and a
        // private message would make the moment nobody else's. The server comes from the level, since
        // ServerPlayer stopped handing its own out in 26.x.
        if (player.level() instanceof ServerLevel level) {
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }
}
