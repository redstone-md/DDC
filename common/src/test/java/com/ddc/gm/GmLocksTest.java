package com.ddc.gm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ddc.core.character.Ability;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Game Master's seal, as it is written to disk.
 *
 * <p>The store itself needs a server, which a unit test has none of. What it does not need one for is
 * the lock: a seal that failed to save would be a dungeon the GM has to build again after every
 * restart, which is the whole reason this is saved data rather than a map in memory. The character
 * sheet has already shipped one codec that could not write itself; this one is checked.
 */
class GmLocksTest {

    @Test
    @DisplayName("a seal survives being written and read")
    void aLockRoundTrips() {
        GmLocks.Lock lock = new GmLocks.Lock(Ability.DEXTERITY, 15, Optional.of("ddc.check.block.lock"));

        var json = GmLocks.Lock.CODEC.encodeStart(JsonOps.INSTANCE, lock)
                .getOrThrow(message -> new AssertionError(message));
        GmLocks.Lock read = GmLocks.Lock.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(lock, read);
    }

    @Test
    @DisplayName("a seal with nothing to say still saves")
    void theMessageIsOptional() {
        GmLocks.Lock lock = new GmLocks.Lock(Ability.STRENGTH, 20, Optional.empty());

        var json = GmLocks.Lock.CODEC.encodeStart(JsonOps.INSTANCE, lock)
                .getOrThrow(message -> new AssertionError(message));

        assertEquals(lock, GmLocks.Lock.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new AssertionError(message)));
    }

    @Test
    @DisplayName("a seal reads as the same rule a data pack writes")
    void aLockIsABlockCheck() {
        GmLocks.Lock lock = new GmLocks.Lock(Ability.STRENGTH, 18, Optional.empty());

        assertEquals(Ability.STRENGTH, lock.asCheck().ability());
        assertEquals(18, lock.asCheck().dc());
        assertEquals("ddc.check.block.sealed", lock.asCheck().message(),
                "a sealed door says it is sealed, not that it is stiff");
    }

    @Test
    @DisplayName("a seal outside the SRD's range is refused")
    void difficultyHasLimits() {
        assertTrue(GmLocks.Lock.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"ability": "strength", "dc": 40}""")).error().isPresent());
    }
}
