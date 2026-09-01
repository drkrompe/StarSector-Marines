package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boats are the company's and the berths are the ship's, which is a rule
 * about what survives a move and what does not.
 *
 * <p>Asked of the deck directly rather than through a hull: the berth count and
 * the pattern are two numbers {@code ShipsBoats} works out, and standing up a
 * ship to produce them would test that derivation instead of this one.
 */
class BoatDeckTest {

    private static final String TRANSPORT = "fleet-member-transport";
    private static final String TENDER = "fleet-member-tender";
    private static final String WARSHIP = "fleet-member-warship";

    @Test
    void aFreshDeckComesUpHoldingTheHullsOwnBoatsAtStandardFit() {
        BoatDeck deck = new BoatDeck();

        BoatDeck.LeftBehind cost = deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);

        assertFalse(cost.any(), "a company that has never moved leaves nothing behind");
        assertEquals(6, deck.berths());
        assertEquals(6, deck.airworthy().size());
        for (CampaignBoat boat : deck.airworthy()) {
            assertEquals(ShuttleType.AEROSHUTTLE, boat.pattern());
            assertSame(BoatFitting.STANDARD_PLATING, boat.plating());
            assertSame(BoatFitting.STANDARD_DRIVE, boat.drive());
        }
    }

    /**
     * The deck is reconciled every time it is read — a screen attaching, a
     * briefing asking what the lift is — so a second look at the same hull must
     * not mint, drop, or renumber anything.
     */
    @Test
    void readingTheDeckAgainstTheSameHullTwiceChangesNothing() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);
        List<String> first = ids(deck);

        BoatDeck.LeftBehind cost = deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);

        assertFalse(cost.any());
        assertEquals(first, ids(deck));
    }

    @Test
    void movingToASmallerHullOfTheSamePatternKeepsTheFirstBoatsAndSaysWhatItCost() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);
        List<String> before = ids(deck);
        // A fit is the thing a player paid for, so the boat that carries one is
        // the case worth watching across the move.
        deck.boatById(before.get(0)).install(BoatFitting.ARMOURED_PLATING);

        BoatDeck.LeftBehind cost = deck.reconcile(TENDER, ShuttleType.AEROSHUTTLE, 3);

        assertEquals(before.subList(0, 3), ids(deck));
        assertSame(BoatFitting.ARMOURED_PLATING,
                deck.boatById(before.get(0)).plating());
        assertEquals(3, cost.count());
        assertEquals(3, deck.leftBehind().count());
    }

    @Test
    void movingToAHullWhoseBaysHoldAnotherPatternLeavesEveryBoatBehind() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);

        BoatDeck.LeftBehind cost = deck.reconcile(WARSHIP, ShuttleType.HERMES, 2);

        assertEquals(6, cost.count());
        assertEquals(2, deck.berths());
        for (CampaignBoat boat : deck.airworthy()) {
            assertEquals(ShuttleType.HERMES, boat.pattern());
            assertSame(BoatFitting.STANDARD_PLATING, boat.plating());
        }
    }

    @Test
    void movingToABiggerHullKeepsEveryBoatAndFillsTheRestAtStandardFit() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile(TENDER, ShuttleType.AEROSHUTTLE, 3);
        List<String> before = ids(deck);

        BoatDeck.LeftBehind cost = deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);

        assertFalse(cost.any());
        assertEquals(6, deck.berths());
        assertEquals(before, ids(deck).subList(0, 3));
    }

    /**
     * A tail number is how the player refers to a boat, so reusing one after a
     * move would name a new boat after the one they just lost.
     */
    @Test
    void aTailNumberIsNeverReusedHoweverOftenTheCompanyMovesShip() {
        BoatDeck deck = new BoatDeck();
        Set<String> seen = new HashSet<>();
        deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);
        collect(deck, seen);
        deck.reconcile(WARSHIP, ShuttleType.HERMES, 2);
        collect(deck, seen);
        deck.reconcile(TRANSPORT, ShuttleType.AEROSHUTTLE, 6);
        collect(deck, seen);

        assertEquals(14, seen.size(), "6 landing craft, 2 gigs, then 6 more landing craft");
        assertTrue(seen.contains("Aeroshuttle 01"));
        assertTrue(seen.contains("Hermes 07"));
        assertTrue(seen.contains("Aeroshuttle 09"));
    }

    private static void collect(BoatDeck deck, Set<String> names) {
        for (CampaignBoat boat : deck.airworthy()) {
            assertTrue(names.add(boat.displayName()),
                    boat.displayName() + " is a tail number the deck has already used");
        }
    }

    private static List<String> ids(BoatDeck deck) {
        List<String> found = new ArrayList<>();
        for (CampaignBoat boat : deck.airworthy()) found.add(boat.id());
        return found;
    }
}
