package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What the shell remembers between visits.
 *
 * <p>The operations panel is built afresh every time the player opens it, so
 * without a place for laid-out decks to live the company's own ship is
 * generated from nothing on every visit. What matters is not that the store
 * holds things — it is what counts as the same hull, since a key that is too
 * loose hands back the wrong ship and one that is too tight never hits.
 *
 * <p>Small hulls throughout: this is about the bookkeeping, not about what a
 * deck comes out like.
 */
class LaidDecksTest {

    private static final long SEED = 0x5AFE_DECEL;

    private static CompanyShip boat(int maxCrew) {
        return new CompanyShip(HullClass.FRIGATE, HullRole.TROOP_TRANSPORT,
                5, maxCrew, 10, 0.5f);
    }

    @BeforeEach
    void freshStart() {
        LaidDecks.forget();
    }

    @Test
    @DisplayName("a hull nobody has looked at is not claimed to be known")
    void unknownUntilRead() {
        assertNull(LaidDecks.known(boat(20), SEED));

        ShipInterior read = LaidDecks.aboard(boat(20), SEED);

        assertNotNull(read);
        assertSame(read, LaidDecks.known(boat(20), SEED));
        assertSame(read, LaidDecks.aboard(boat(20), SEED),
                "asking twice should not lay the same deck out twice");
    }

    @Test
    @DisplayName("a refit is a different ship; the same hull at a different seed is too")
    void keyedOnWhatTheDeckIsGeneratedFrom() {
        ShipInterior read = LaidDecks.aboard(boat(20), SEED);

        assertNull(LaidDecks.known(boat(40), SEED),
                "a hull that now carries twice the crew owes a different program");
        assertNull(LaidDecks.known(boat(20), SEED + 1),
                "the same hull in another company is laid out to another seed");
        assertSame(read, LaidDecks.known(boat(20), SEED),
                "and an equal hull at the same seed is the same deck");
    }

    @Test
    @DisplayName("the company's ship is laid out once, and again when they move")
    void theHomeDeckIsKept() {
        LaidDecks.Laid first = LaidDecks.homeDeck(boat(20), SEED);

        assertSame(first.map(), LaidDecks.homeDeck(boat(20), SEED).map(),
            "opening the panel again should not lay her out again");

        LaidDecks.Laid moved = LaidDecks.homeDeck(boat(40), SEED);
        assertNotSame(first.map(), moved.map());
        assertNotSame(first.map(), LaidDecks.homeDeck(boat(20), SEED).map(),
                "only one deck is kept, so the ship they left is laid out afresh");
    }

    @Test
    @DisplayName("forgetting is complete, so a measurement is of the work and not of the memory")
    void forgettingClearsBoth() {
        LaidDecks.aboard(boat(20), SEED);
        LaidDecks.Laid kept = LaidDecks.homeDeck(boat(20), SEED);

        LaidDecks.forget();

        assertNull(LaidDecks.known(boat(20), SEED));
        assertNotSame(kept.map(), LaidDecks.homeDeck(boat(20), SEED).map());
    }
}
