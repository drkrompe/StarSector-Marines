package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ship's boats are a fitting of the hull, not property brought aboard.
 *
 * <p>They are hers the way her boat bay is hers: you do not bring them with you,
 * you cannot take them when you leave, and a company that changes ships changes
 * boats. So what is in her bays follows from what she is, exactly as her room
 * program does — and the deck that lays the bays is what says so, rather than
 * the host that puts the boats out deciding for itself and being right by
 * accident.
 *
 * <p>This replaces an older framing in which a lift was assembled from
 * transports out of the player's fleet. Under that reading the boats were things
 * you owned and a Valkyrie was one of them; under this one the Valkyrie is the
 * ship you are standing in, and her boats are inside her.
 */
class AHullCarriesHerOwnBoatsTest {

    /** A hull that exists to put a ground force somewhere carries landing craft. */
    @Test
    void aTroopTransportCarriesLandingCraft() {
        assertEquals(ShuttleType.AEROSHUTTLE, ShipsBoats.carriedBy(HullRole.TROOP_TRANSPORT));
        assertEquals(ShuttleType.AEROSHUTTLE, ShipsBoats.carriedBy(HullRole.CARRIER));
    }

    /**
     * Every other hull carries a gig: ship's business rather than a landing.
     * The distinction is what the hull is <em>for</em>, which is why it is read
     * off the role rather than invented.
     */
    @Test
    void aHullWithNoGroundForceCarriesAGig() {
        for (HullRole role : new HullRole[]{HullRole.WARSHIP, HullRole.FREIGHTER,
                HullRole.TANKER, HullRole.LINER}) {
            assertNotEquals(ShuttleType.AEROSHUTTLE, ShipsBoats.carriedBy(role),
                    role + " carries an assault lander for no reason");
        }
    }

    /**
     * A boat is small. The shuttle catalogue spans both ends of a range it once
     * had to — a Valkyrie in that list is a transport that flew a detachment
     * down from a fleet — and a bay stocked with one would be a ship carrying
     * herself.
     */
    @Test
    void aBoatIsNotAnotherShip() {
        for (HullRole role : HullRole.values()) {
            ShuttleType boat = ShipsBoats.carriedBy(role);
            assertEquals(1, boat.teams,
                    role + " keeps " + boat + " in her bays, which is a ship rather than a boat");
        }
    }

    /** The plan a hull implies says what her bays hold, beside the rooms that hold them. */
    @Test
    void thePlanCarriesWhatTheBaysHold() {
        DeckSizing.DeckPlan transport = DeckSizing.planFor(HullClass.CRUISER,
                HullRole.TROOP_TRANSPORT, 10, 250, 50, 0.28f);
        DeckSizing.DeckPlan freighter = DeckSizing.planFor(HullClass.CRUISER,
                HullRole.FREIGHTER, 10, 120, 400, 0.28f);

        assertEquals(ShipsBoats.carriedBy(HullRole.TROOP_TRANSPORT), transport.boats());
        assertNotEquals(transport.boats(), freighter.boats(),
                "two hulls built for different things keep the same boat");
    }

    /** And a deck with no hull behind it carries none, because there is no ship. */
    @Test
    void aDeckWithNoHullBehindItCarriesNoBoats() {
        assertNull(new DeckSizing.DeckPlan(96, 28, List.of()).boats(),
                "an infrastructure fixture was given a ship's boats");
    }

    /**
     * A troop transport carries six boats, which is what the missions are
     * written around.
     *
     * <p>A number rather than a shape, and pinned because it is a requirement
     * from outside this model: mission design assumes a lift of six, and a bay
     * that quietly held two fewer would show up as missions that cannot be
     * flown rather than as a sizing change anybody made on purpose.
     */
    @Test
    void aTroopTransportCarriesSixBoats() {
        assertEquals(6, ShipsBoats.aboard(TestHulls.transport()).size(),
                "the company ship's establishment is not six boats");
    }

    /**
     * The census matches the berths a generated deck actually lays.
     *
     * <p>This is the whole licence for counting boats off a hull's room program
     * instead of off a deck: a briefing has no deck, and generating one to
     * answer a question about lift would be an odd bill to pay. The two are the
     * same arithmetic asked with and without a floor, so the only way they part
     * company is silently.
     */
    @Test
    void theCensusMatchesTheBerthsTheDeckLays() {
        CompanyShip ship = TestHulls.transport();
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(
                DeckSizing.planFor(ship.hullClass(), ship.role(), ship.minCrew(),
                        ship.maxCrew(), ship.cargo(), ship.aspect()),
                11L, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), 11L, null)) {
            assertEquals(scene.simulation().getAirfieldService().berths().size(),
                    ShipsBoats.aboard(ship).size(),
                    "what the hull says she carries is not what her deck berths");
        }
    }

    /**
     * End to end: what the hull says goes into her berths.
     *
     * <p>A whole deck, because that is the claim — the plan states it, the
     * generator carries it, and the host stocks with it. Each link is trivial
     * and the failure would be one of them quietly using its own answer.
     *
     * <p><b>Asked of a freighter on purpose.</b> A host that ignored the hull
     * entirely would fall back to a lander, which is what a troop transport
     * carries anyway — so the transport cannot tell the two apart and this test
     * passed under a control that had the host deciding for itself. A hull whose
     * boats differ from the fallback is the only one that measures anything.
     */
    @Test
    void herBaysAreStockedWithHerOwnBoats() {
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(
                DeckSizing.planFor(HullClass.CRUISER, HullRole.FREIGHTER,
                        10, 120, 400, 0.28f),
                11L, null);
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(
                deck, generator.getLastDeckGraph(), 11L, null)) {
            AirfieldService bays = scene.simulation().getAirfieldService();
            assertTrue(!bays.berths().isEmpty(), "the freighter registered no boat berths");
            for (AirfieldService.Berth berth : bays.berths()) {
                assertEquals(ShipsBoats.carriedBy(HullRole.FREIGHTER), berth.airframe,
                        "a berth was stocked with something the hull does not carry");
            }
        }
    }
}
