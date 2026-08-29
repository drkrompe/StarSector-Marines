package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.TestHulls;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A screen is a place aboard, so a ship without that place has no route to it.
 *
 * <p>This is the point of choosing the company ship rather than being given one:
 * a hull that cannot hold a vehicle bay does not acquire one by being refitted,
 * so the player who wants a proper lab has to go and get a ship that can have
 * one. That only reads as a decision if the interface says so — a Mech Lab
 * button that opens an empty room is the same thing as the hull choice not
 * mattering.
 *
 * <p><b>Nothing here asserts what any particular hull holds.</b> Which rooms a
 * ship owes is sizing, and sizing gets tuned: cell density, the threshold for a
 * substantial hull, how much lift buys a range. A test that declared some hull
 * would never have a lab would start failing the first time one of those moved,
 * for a reason having nothing to do with navigation. What is tested instead is
 * that the shell agrees with the deck — whatever the deck turns out to say.
 */
class ShipRoomAvailabilityTest {

    private static final long SEED = 0x5AFE_DECEL;

    /** A spread of hulls, so the agreement is checked against varied programs. */
    private static final List<CompanyShip> FLEET = List.of(
            TestHulls.transport(),
            new CompanyShip(HullClass.CAPITAL, HullRole.TROOP_TRANSPORT, 60, 400, 250, 0.34f),
            new CompanyShip(HullClass.CRUISER, HullRole.WARSHIP, 200, 300, 100, 0.30f),
            new CompanyShip(HullClass.CRUISER, HullRole.FREIGHTER, 120, 120, 800, 0.32f),
            new CompanyShip(HullClass.FRIGATE, HullRole.TROOP_TRANSPORT, 10, 40, 20, 0.40f));

    /** The compartments the shell can frame, which is what has to be answered for. */
    private static final Set<RoomPurpose> FRAMED = EnumSet.copyOf(
            List.of(RoomPurpose.BARRACKS, RoomPurpose.ARMORY, RoomPurpose.VEHICLE_BAY));

    /**
     * Asking whether the ship has a room gives the same answer as looking for
     * it on her deck.
     *
     * <p>The whole mechanism rests on this one equivalence, and it holds
     * whatever the program turns out to be. A room the hull was never given and
     * a room the deck could not fit both come back absent, which is right:
     * from the player's side they are the same fact.
     */
    @Test
    void whatTheShipOffersIsWhatIsOnHerDeck() {
        for (CompanyShip ship : FLEET) {
            CompanyDeck deck = new CompanyDeck(ship, SEED);
            if (!ship.habitable()) continue;
            for (RoomPurpose purpose : RoomPurpose.values()) {
                assertEquals(deck.rooms().largest(purpose) != null, deck.has(purpose),
                        ship + " disagrees with her own deck about " + purpose);
            }
        }
    }

    /**
     * The shell offers exactly the rooms the ship has.
     *
     * <p>Checked per hull against that hull's own deck rather than against a
     * list of rooms somebody expected, so this says the navigation follows the
     * ship without saying anything about what any ship holds.
     */
    @Test
    void theShellOffersExactlyWhatTheShipHas() {
        for (CompanyShip ship : FLEET) {
            if (!ship.habitable()) continue;
            CompanyDeck deck = new CompanyDeck(ship, SEED);
            Map<String, Object> props = new LinkedHashMap<>();
            MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ, laidOut(deck),
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> { });

            for (MarineOpsPageNav.Page page : MarineOpsPageNav.Page.values()) {
                if (page.room() == null) continue;
                boolean shown = !"page-nav-absent".equals(props.get(prop(page)));
                assertEquals(deck.has(page.room()), shown,
                        ship + " offers " + page + " but " + (shown ? "has" : "lacks")
                                + " no " + page.room());
            }
        }
    }

    /**
     * A hull with no interior at all answers rather than failing.
     *
     * <p>Structural, not sizing: a fighter is carried rather than entered, so
     * there is no deck to generate. Asking must still be safe, because the
     * answer decides whether a button works.
     */
    @Test
    void anUnboardableHullOffersNothing() {
        CompanyShip fighter = new CompanyShip(
                HullClass.FIGHTER, HullRole.TROOP_TRANSPORT, 1, 2, 0, 0.6f);
        assertFalse(fighter.habitable(), "a fighter acquired a playable interior");
        CompanyDeck deck = new CompanyDeck(fighter, SEED);
        for (RoomPurpose purpose : RoomPurpose.values()) {
            assertFalse(deck.has(purpose), "a fighter claims to have a " + purpose);
        }
    }

    /**
     * The button survives; the route does not. Removing it outright would leave
     * the player wondering where a page went, which is a worse answer than
     * showing them the berth their hull does not have.
     *
     * <p>Driven by a stated set rather than a generated ship, so it tests the
     * rule and not a sizing decision.
     */
    @Test
    void aRoomTheShipLacksIsShownButNotReachable() {
        boolean[] opened = new boolean[3];
        Map<String, Object> props = new LinkedHashMap<>();
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ,
                only(RoomPurpose.ARMORY),
                () -> { }, () -> { },
                () -> opened[0] = true, () -> opened[1] = true, () -> opened[2] = true);

        assertEquals("page-nav-absent", props.get("barracksClasses"),
                "berthing reads as available on a ship with no berthing");
        assertEquals("page-nav-absent", props.get("mechLabClasses"),
                "the lab reads as available on a ship with no bay");
        assertEquals("", props.get("armoryClasses"),
                "the armory this ship does have was shown as absent");

        ((Runnable) props.get("barracksAction")).run();
        ((Runnable) props.get("mechLabAction")).run();
        ((Runnable) props.get("armoryAction")).run();
        assertFalse(opened[0], "clicking berthing the ship lacks navigated anyway");
        assertFalse(opened[2], "clicking the lab the ship lacks navigated anyway");
        assertTrue(opened[1], "the armory the ship has would not open");
    }

    /**
     * A ship still being laid out is not a ship without rooms.
     *
     * <p>Both would be a dead button, and they are opposite things to be told:
     * a hull that cannot hold a bay never will, and this is a wait of a second
     * or two while a capital's deck is packed. Shown as absence it would tell
     * the player their own ship had no mech bay, and they would have no reason
     * to doubt it.
     *
     * <p><b>So the waiting page keeps its route while the absent one loses
     * it.</b> These properties are read once, when the page is built, and a
     * ship gets ready some seconds later — an action dropped here would stay
     * dropped on the page the player is looking at, long after the room was
     * available. Routing is what refuses the trip meanwhile, and it stops
     * refusing on its own; a room the hull does not have is refused here,
     * because that never changes.
     */
    @Test
    void aShipStillBeingLaidOutIsNotAShipWithoutRooms() {
        boolean[] opened = new boolean[3];
        Map<String, Object> props = new LinkedHashMap<>();
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ,
                purpose -> MarineOpsPageNav.Aboard.UNKNOWN,
                () -> { }, () -> { },
                () -> opened[0] = true, () -> opened[1] = true, () -> opened[2] = true);

        assertEquals("page-nav-waiting", props.get("barracksClasses"));
        assertEquals("page-nav-waiting", props.get("armoryClasses"));
        assertEquals("page-nav-waiting", props.get("mechLabClasses"));

        ((Runnable) props.get("barracksAction")).run();
        ((Runnable) props.get("armoryAction")).run();
        ((Runnable) props.get("mechLabAction")).run();
        for (boolean went : opened) {
            assertTrue(went, "a room that is merely being laid out lost its route "
                    + "and would never get it back");
        }
    }

    /**
     * The way to the whole ship is the one route on headquarters that is not a
     * navigation button, so it carries the same states itself — and, being the
     * only place the wait is described in words, it says which half of the work
     * she is in and how long it has been.
     */
    @Test
    void theWayAboardSaysWhatSheIsDoing() {
        CompanyHqViewModel.ShipView ready = CompanyHqViewModel.shipViewReady();
        CompanyHqViewModel.ShipView laying = CompanyHqViewModel.shipViewWorking(
                CompanyDeck.Work.LAYING_OUT, 4.2f);
        CompanyHqViewModel.ShipView mustering = CompanyHqViewModel.shipViewWorking(
                CompanyDeck.Work.MUSTERING, 9.6f);

        assertEquals("WALK THE SHIP", ready.label());
        assertFalse(ready.classes().contains("waiting"),
                "a ship that is ready was still shown as being got ready");
        assertFalse(ready.labelClasses().contains("waiting"));

        assertTrue(laying.label().startsWith("LAYING OUT HER DECK"), laying.label());
        assertTrue(mustering.label().startsWith("MUSTERING HER WATCH"), mustering.label());
        assertTrue(laying.classes().contains("hq-ship-waiting"));
        assertTrue(laying.labelClasses().contains("hq-ship-waiting-label"),
                "the wording dimmed with the tile but the text it is written in did not");

        // Whole seconds, so the wait reads as a count rather than a flicker.
        assertTrue(laying.label().endsWith("4S"), laying.label());
        assertTrue(mustering.label().endsWith("9S"), mustering.label());
    }

    /**
     * The loader sweeps rather than filling to a percentage.
     *
     * <p>There is no honest percentage to show: half of laying a deck out is a
     * circulation pass that runs until no further cut earns itself, so there is
     * no total to count against. What the bar has to say is only that the work
     * is alive — so what is pinned is that it moves, and that it keeps moving
     * rather than parking at full.
     */
    @Test
    void theLoaderSweepsRatherThanFillingUp() {
        Set<String> widths = new LinkedHashSet<>();
        for (int frame = 0; frame < 60; frame++) {
            widths.add(CompanyHqViewModel.shipViewWorking(
                    CompanyDeck.Work.LAYING_OUT, frame / 30f).fillStyle());
        }

        assertTrue(widths.size() > 8, "the loader barely moved across two seconds");
        assertEquals("width: 0%;", CompanyHqViewModel.shipViewWorking(
                        CompanyDeck.Work.LAYING_OUT, 0f).fillStyle(),
                "the sweep does not start empty");
        assertTrue(widths.contains("width: 0%;"),
                "the sweep never returns, so a long wait ends up parked at full");

        // A ready ship's track is a hole in the tile rather than an empty bar.
        assertEquals("width: 0%;", CompanyHqViewModel.shipViewReady().fillStyle());
        assertEquals("hq-ship-track-idle", CompanyHqViewModel.shipViewReady().trackClasses());
        assertEquals("hq-ship-track", CompanyHqViewModel.shipViewWorking(
                CompanyDeck.Work.LAYING_OUT, 1f).trackClasses());
    }

    /** What the shell asks of a ship whose deck is in hand. */
    private static Function<RoomPurpose, MarineOpsPageNav.Aboard> laidOut(CompanyDeck deck) {
        return purpose -> deck.has(purpose)
                ? MarineOpsPageNav.Aboard.YES : MarineOpsPageNav.Aboard.NO;
    }

    /** A ship with exactly these rooms and no others. */
    private static Function<RoomPurpose, MarineOpsPageNav.Aboard> only(RoomPurpose... rooms) {
        Set<RoomPurpose> has = Set.of(rooms);
        return purpose -> has.contains(purpose)
                ? MarineOpsPageNav.Aboard.YES : MarineOpsPageNav.Aboard.NO;
    }

    /**
     * Headquarters is never a room. Gating it would let a hull exist that the
     * player cannot navigate at all, and the company is not a compartment.
     */
    @Test
    void headquartersIsReachableOnAnyHull() {
        Map<String, Object> props = new LinkedHashMap<>();
        boolean[] wentHome = new boolean[1];
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BARRACKS,
                purpose -> MarineOpsPageNav.Aboard.NO,
                () -> { }, () -> wentHome[0] = true,
                () -> { }, () -> { }, () -> { });
        ((Runnable) props.get("hqAction")).run();
        assertTrue(wentHome[0], "a ship with no rooms at all stranded the player");
        assertEquals("", props.get("hqClasses"), "headquarters was shown as absent");
    }

    /**
     * Every page that is somewhere aboard says where, and the shell's home says
     * nothing. This is what makes the next room screen gated without anybody
     * remembering to gate it.
     */
    @Test
    void everyRoomPageDeclaresItsCompartment() {
        // The pages that are not places aboard. Headquarters is the company
        // rather than a wardroom; choosing the ship is a decision about which
        // vessel the rest of the shell is aboard; and the ship view is the
        // whole vessel rather than a room in her. Gating any of them on a
        // compartment would let a hull exist the player cannot navigate.
        Set<MarineOpsPageNav.Page> notRooms = EnumSet.of(
                MarineOpsPageNav.Page.HQ, MarineOpsPageNav.Page.SHIP_TRANSFER,
                MarineOpsPageNav.Page.SHIP_VIEW);
        for (MarineOpsPageNav.Page page : MarineOpsPageNav.Page.values()) {
            if (notRooms.contains(page)) {
                assertEquals(null, page.room(),
                        page + " was made a compartment, which can strand the player");
                continue;
            }
            assertTrue(FRAMED.contains(page.room()),
                    page + " frames " + page.room() + ", which no room view claims");
        }
    }

    private static String prop(MarineOpsPageNav.Page page) {
        return page.button() + "Classes";
    }
}
