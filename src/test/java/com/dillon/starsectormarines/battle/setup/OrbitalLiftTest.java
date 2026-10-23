package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the lift fills in for a mission, and how it folds onto a smaller map. */
class OrbitalLiftTest {

    /** A mission that says nothing flies the ferry: three zones, a pair each. */
    @Test
    void anUnstatedShapeFliesTheFerry() {
        ConquestArrivalConfig resolved =
                OrbitalLift.resolve(ConquestArrivalConfig.DEFAULT);
        assertEquals(OrbitalLift.FERRY_DROP_ZONES, resolved.dropZoneCount());
        assertEquals(1, resolved.shuttlePairsPerZone());
    }

    /** A mission that says three beachheads gets three, untouched. */
    @Test
    void aStatedShapeIsNeverDerivedOver() {
        ConquestArrivalConfig stated = new ConquestArrivalConfig(3, 1, 1f);
        assertSame(stated, OrbitalLift.resolve(stated));
    }

    /** Folding onto a smaller map keeps every craft the lift flies. */
    @Test
    void foldingOntoFewerAreasKeepsThePairCount() {
        ConquestArrivalConfig resolved = ConquestArrivalConfig.DEFAULT
                .withDropZoneCount(6).withShuttlePairsPerZone(1);
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
