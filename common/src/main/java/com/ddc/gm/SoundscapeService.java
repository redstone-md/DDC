package com.ddc.gm;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The Game Master's other voice: PRD 2's "play soundscapes", which the mod never had.
 *
 * <p>Narration went out as text and nothing else, so a GM describing a dragon's roar was describing
 * it. A table hears the roar now.
 *
 * <p>Played at each listener rather than at a point in the world, because this is the GM's sound, not
 * something happening in a place: the wind rising is everywhere, and a player who walked away from
 * it would hear the story get quieter.
 */
public final class SoundscapeService {

    /**
     * Plays a sound for the whole table.
     *
     * <p>Any sound the game knows, by id, rather than a list DDC keeps: every mod's sounds are
     * already registered, so a modpack's own thunder works here with no code from us. A pack cannot
     * add sounds to DDC, but it never needed to.
     *
     * @return false when nothing is registered under that id, which the command reports rather than
     *         failing silently
     */
    public boolean play(ServerPlayer gameMaster, ResourceLocation sound, float volume, float pitch) {
        if (!GameMasters.isGameMaster(gameMaster)) {
            throw new IllegalArgumentException(
                    "Refusing to play a soundscape for a non-GM: " + gameMaster.getGameProfile().name());
        }
        Optional<SoundEvent> event = BuiltInRegistries.SOUND_EVENT.getOptional(sound);
        if (event.isEmpty() || !(gameMaster.level() instanceof ServerLevel level)) {
            return false;
        }
        for (ServerPlayer listener : level.getServer().getPlayerList().getPlayers()) {
            // At the listener's own feet, in their own world: an ambience that came from where the GM
            // is standing would pan and fade as the party moved, which is not what an atmosphere does,
            // and a GM in the Nether should still be heard by a party in the overworld.
            listener.level().playSound(null, listener.blockPosition(), event.get(),
                    SoundSource.AMBIENT, volume, pitch);
        }
        return true;
    }
}
