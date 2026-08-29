package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the shell will and will not send the player.
 *
 * <p>A room view is a camera on a deck, so there is nothing for it to be a
 * camera on until the ship has been laid out and crewed. That is guarded where
 * routing happens rather than at each button, because there is more than one
 * way into a room — the navigation shell, headquarters' own tiles — and the
 * next one somebody adds will not know to check.
 *
 * <p>It is also what lets a button that is merely waiting keep its action: the
 * pages are built once and a ship gets ready seconds later, so the refusal has
 * to be asked fresh rather than baked into a page nobody rebuilds.
 *
 * <p>Without a running game there is no company ship at all, which is the same
 * branch a ship still being laid out takes — the shell has no deck to frame
 * either way.
 */
class MarineOpsRoutingTest {

    @Test
    @DisplayName("a page that is somewhere aboard declares itself as one")
    void theRoomsAboardAreTheGatedOnes() {
        assertTrue(ScreenId.BARRACKS.aboard());
        assertTrue(ScreenId.MECH_LAB.aboard());
        assertTrue(ScreenId.SHIP_VIEW.aboard());

        // The company rather than a compartment, and the choice of which vessel
        // the rest of the shell is aboard. Gating either would leave a player
        // with a ship being laid out unable to navigate at all.
        assertFalse(ScreenId.COMPANY_HQ.aboard());
        assertFalse(ScreenId.SHIP_TRANSFER.aboard());
        assertFalse(ScreenId.MISSION_SELECT.aboard());
    }

    @Test
    @DisplayName("no route into the ship until there is a ship to be in")
    void roomsAreRefusedWithoutAReadyShip() {
        MarineOpsContext context = new MarineOpsContext(null);
        ScreenId opened = context.getCurrentScreen();

        context.goTo(ScreenId.BARRACKS);
        assertEquals(opened, context.getCurrentScreen());
        context.goTo(ScreenId.MECH_LAB);
        assertEquals(opened, context.getCurrentScreen());
        context.goTo(ScreenId.SHIP_VIEW);
        assertEquals(opened, context.getCurrentScreen());
    }

    @Test
    @DisplayName("everywhere that is not aboard stays reachable")
    void theRestOfTheShellIsNeverGated() {
        MarineOpsContext context = new MarineOpsContext(null);

        context.goTo(ScreenId.SHIP_TRANSFER);
        assertEquals(ScreenId.SHIP_TRANSFER, context.getCurrentScreen(),
                "a company with nowhere to live could not go and choose somewhere");

        context.goTo(ScreenId.COMPANY_HQ);
        assertEquals(ScreenId.COMPANY_HQ, context.getCurrentScreen(),
                "headquarters is the company, not a wardroom");
    }
}
