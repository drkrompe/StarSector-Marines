package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.LandingShare;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The derivation, asked directly: the lift is a function of the seats, the
 * share, and a round trip that is a constant.
 */
class OrbitalLiftTest {

    private static final LandingShare SHARE = LandingShare.DEFAULT;

    /** The whole point of the descent: nothing about the map is in the trip. */
    @Test
    void theRoundTripIsMadeOfTheDescentAndTheTurnaroundOnly() {
        float expected = 2f * OrbitalLift.descentSeconds()
                + OrbitalLift.turnaroundSeconds();
        assertEquals(expected, OrbitalLift.roundTripSeconds(), 0.001f);
        assertEquals(ShuttleType.AEROSHUTTLE.capacity
                        * ShuttleType.AEROSHUTTLE.deboardInterval + 8f,
                OrbitalLift.turnaroundSeconds(), 0.001f);
    }

    /** Accelerate, hold, brake — and the short leg is the triangle. */
    @Test
    void aLegIsFlownAtTheCraftsOwnHandling() {
        ShuttleType craft = ShuttleType.AEROSHUTTLE;
        float v = craft.maxSpeed();
        float cruising = OrbitalLift.legSeconds(craft, 100f);
        assertEquals(v / craft.accel() + v / craft.brakingAccel()
                        + (100f - v * v / (2f * craft.accel())
                        - v * v / (2f * craft.brakingAccel())) / v,
                cruising, 0.001f);

        // Too short to reach cruise: strictly slower per cell than the long leg.
        float shortLeg = OrbitalLift.legSeconds(craft, 4f);
        assertTrue(shortLeg / 4f > cruising / 100f,
                "a leg with no cruise in it costs more per cell");
        assertEquals(0f, OrbitalLift.legSeconds(craft, 0f), 0.001f);
    }

    /** Twice the force, twice the craft — the share is what stays fixed. */
    @Test
    void theLiftScalesWithTheSeats() {
        int perCraft = OrbitalLift.sortiesPerCraft(SHARE);
        assertTrue(perCraft > 1, "a craft flies more than one sortie inside the share");

        // 204 seats is reinforced-south, 408 is full-strength-west.
        int south = OrbitalLift.pairsFor(204, SHARE);
        int west = OrbitalLift.pairsFor(408, SHARE);
        assertTrue(west > south,
                "the larger force gets more lift: " + south + " vs " + west);
        int sorties = (int) Math.ceil(408 / (double) ShuttleType.AEROSHUTTLE.capacity);
        int craft = (int) Math.ceil(sorties / (double) perCraft);
        assertEquals((int) Math.ceil(craft / 2.0), west);
    }

    /** Sized so the last load is down inside the window it was sized against. */
    @Test
    void theDerivedLiftLandsTheForceInsideTheShare() {
        for (int seats : new int[]{12, 204, 408, 1008}) {
            int pairs = OrbitalLift.pairsFor(seats, SHARE);
            int sorties = (int) Math.ceil(seats / (double) ShuttleType.AEROSHUTTLE.capacity);
            int perCraft = (int) Math.ceil(sorties / (2.0 * pairs));
            float landsAt = OrbitalLift.descentSeconds()
                    + (perCraft - 1) * OrbitalLift.roundTripSeconds();
            assertTrue(landsAt <= SHARE.windowSeconds() + 0.001f,
                    seats + " seats on " + pairs + " pairs land at " + landsAt
                            + "s, past the " + SHARE.windowSeconds() + "s window");
        }
    }

    /** A tighter share buys more craft for the same force. */
    @Test
    void aTighterShareBuysMoreLift() {
        assertTrue(OrbitalLift.pairsFor(408, new LandingShare(0.15f))
                        > OrbitalLift.pairsFor(408, new LandingShare(0.30f)),
                "halving the share at least doubles nothing quietly");
    }

    /** One pair to a zone, and the map is asked for exactly that many. */
    @Test
    void aDerivedShapeSpreadsOnePairToAZone() {
        ConquestArrivalConfig resolved =
                OrbitalLift.resolve(ConquestArrivalConfig.DEFAULT, 408);
        assertEquals(OrbitalLift.pairsFor(408, SHARE), resolved.dropZoneCount());
        assertEquals(1, resolved.shuttlePairsPerZone());
        assertEquals(OrbitalLift.pairsFor(408, SHARE),
                resolved.playerShuttlePairCount());
    }

    /** A mission that says three beachheads gets three, whatever it commits. */
    @Test
    void aStatedShapeIsNeverDerivedOver() {
        ConquestArrivalConfig stated = new ConquestArrivalConfig(3, 1, 1f);
        assertSame(stated, OrbitalLift.resolve(stated, 100_000));

        ConquestArrivalConfig halfStated =
                ConquestArrivalConfig.DEFAULT.withDropZoneCount(2);
        ConquestArrivalConfig resolved = OrbitalLift.resolve(halfStated, 408);
        assertEquals(2, resolved.dropZoneCount());
        assertEquals(Math.max(1, (int) Math.ceil(
                        OrbitalLift.pairsFor(408, SHARE) / 2.0)),
                resolved.shuttlePairsPerZone());
    }

    /** Folding onto a smaller map keeps every craft that was sized for. */
    @Test
    void foldingOntoFewerAreasKeepsThePairCount() {
        ConquestArrivalConfig resolved =
                OrbitalLift.resolve(ConquestArrivalConfig.DEFAULT, 1008);
        int pairs = resolved.playerShuttlePairCount();
        ConquestArrivalConfig folded = OrbitalLift.foldOntoAvailableAreas(
                resolved, ConquestArrivalConfig.DEFAULT, 3);
        assertEquals(3, folded.dropZoneCount());
        assertTrue(folded.playerShuttlePairCount() >= pairs,
                "folding may round up, never lose a pair");
    }

    /** A stated zone count is a promise; folding must not quietly keep it. */
    @Test
    void aStatedZoneCountIsNotFolded() {
        ConquestArrivalConfig stated = new ConquestArrivalConfig(4, 1, 1f);
        assertSame(stated,
                OrbitalLift.foldOntoAvailableAreas(stated, stated, 2));
    }
}
