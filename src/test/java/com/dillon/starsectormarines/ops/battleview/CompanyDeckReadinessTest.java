package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ship is got ready away from the frame, and says so until she is.
 *
 * <p>The thing worth pinning is not that it is quick — it is that nothing runs
 * early. A deck being laid out has no clock to run and no rooms to answer for,
 * and a shell that ticked her anyway would be reading a ship that does not
 * exist yet.
 *
 * <p>The smallest hull that has an interior at all: this is about the order
 * things happen in, not about what a deck comes out like.
 */
class CompanyDeckReadinessTest {

    private static final long SEED = 0x5AFE_DECEL;

    private static final CompanyShip BOAT = new CompanyShip(
            HullClass.FRIGATE, HullRole.TROOP_TRANSPORT, 5, 20, 10, 0.5f);

    @BeforeEach
    void freshStart() {
        LaidDecks.forget();
    }

    @Test
    @DisplayName("she is not built by whoever asks for her, and her clock starts at zero")
    void sheIsGotReadyElsewhere() {
        CompanyDeck ship = CompanyDeck.home(BOAT, SEED, null, null, null);

        // Asking for her costs the asker nothing and starts nothing running.
        assertFalse(ship.live(), "she was crewed on the thread that named her");
        assertEquals(0f, ship.elapsedSeconds());

        while (!ship.ready()) Thread.onSpinWait();

        // Becoming ready is not the same as having been run: her clock belongs
        // to whoever advances her, and nothing has.
        assertEquals(0f, ship.elapsedSeconds(),
                "her clock ran while she was still being got ready");

        ship.advance(1f / 60f);
        assertTrue(ship.live(), "she is not crewed after saying she was ready");
        assertTrue(ship.elapsedSeconds() > 0f, "her clock did not start");
    }

    /**
     * Asking outright still works. The routes to the pages that would have to
     * cope with nothing are closed until she is ready, so anybody who asks
     * anyway has decided they would rather wait than be told no.
     */
    @Test
    @DisplayName("a caller that insists gets the ship rather than nothing")
    void askingOutrightWaits() {
        CompanyDeck ship = CompanyDeck.home(BOAT, SEED, null, null, null);

        assertTrue(ship.scene().simulation().getRoster().liveCount() > 0,
                "she came back with nobody aboard");
        assertTrue(ship.ready(), "she is not ready after being waited for");
    }

    /**
     * A hull nobody can live in is never ready and never blocks. She has no
     * deck to lay out, so there is nothing to wait for and nothing to say.
     */
    @Test
    @DisplayName("an unboardable hull is not ready and does not become ready")
    void anUnboardableHullIsNeverReady() {
        CompanyDeck fighter = CompanyDeck.home(
                new CompanyShip(HullClass.FIGHTER, HullRole.WARSHIP, 1, 2, 0, 0.9f),
                SEED, null, null, null);

        assertFalse(fighter.ready());
        fighter.advance(1f / 60f);
        assertFalse(fighter.ready());
        assertEquals(0f, fighter.elapsedSeconds());
    }
}
