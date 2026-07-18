package com.ddc.spell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seam addons cast through, on its own -- no server, no spell, just the contract.
 *
 * <p>What matters here is the promise {@link SpellService} leans on: a presentation that takes a spell
 * stops the offer, a presentation that throws is skipped rather than fatal, and nothing an addon
 * registers survives into the next thing that asks.
 */
class SpellPresentationsTest {

    @BeforeEach
    @AfterEach
    void reset() {
        SpellPresentations.clear();
    }

    @Test
    @DisplayName("a presentation that takes the spell stops the offer")
    void oneThatTakesItWins() {
        AtomicBoolean secondAsked = new AtomicBoolean(false);
        SpellPresentations.register((caster, spell, id, target) -> true);
        SpellPresentations.register((caster, spell, id, target) -> {
            secondAsked.set(true);
            return true;
        });

        assertTrue(SpellPresentations.present(null, null, null, null), "the first should take it");
        assertFalse(secondAsked.get(), "once one takes the spell the rest are not asked");
    }

    @Test
    @DisplayName("no presentation means DDC keeps the spell")
    void noneMeansFalse() {
        assertFalse(SpellPresentations.present(null, null, null, null));
        SpellPresentations.register((caster, spell, id, target) -> false);
        assertFalse(SpellPresentations.present(null, null, null, null), "a declining hook still leaves it to DDC");
    }

    @Test
    @DisplayName("a presentation that throws is skipped, not fatal")
    void aThrowingHookIsSkipped() {
        AtomicBoolean fellThrough = new AtomicBoolean(false);
        SpellPresentations.register((caster, spell, id, target) -> {
            throw new RuntimeException("an addon's bug");
        });
        SpellPresentations.register((caster, spell, id, target) -> {
            fellThrough.set(true);
            return true;
        });

        assertTrue(SpellPresentations.present(null, null, null, null),
                "the throwing hook must not stop the next one taking the spell");
        assertTrue(fellThrough.get(), "the offer should reach the hook after the one that threw");
    }

    @Test
    @DisplayName("clear forgets every presentation")
    void clearForgetsThem() {
        SpellPresentations.register((caster, spell, id, target) -> true);
        SpellPresentations.clear();
        assertFalse(SpellPresentations.present(null, null, null, null), "nothing should survive clear");
    }
}
