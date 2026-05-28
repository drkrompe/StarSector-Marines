package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-3a coverage for {@link LandingZoneScorer}: the viability rule
 * (walkable + not inside a building), quality ranking (clearance + open
 * ground), and the two selection entry points ({@code bestNear} for a hint
 * ring-search, {@code bestAmong} for caller-supplied candidates).
 */
public class LandingZoneScorerTest {

    private static final int W = 12;
    private static final int H = 12;

    private static NavigationGrid openGrid() {
        NavigationGrid g = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) g.setWalkableFloor(x, y);
        }
        return g;
    }

    @Test
    public void isViableRejectsUnwalkableAndBuildings() {
        NavigationGrid g = new NavigationGrid(W, H); // nothing walkable yet
        CellTopology t = new CellTopology(W, H);
        g.setWalkableFloor(5, 5);
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        assertTrue(s.isViable(5, 5), "walkable, no building → viable");
        assertFalse(s.isViable(6, 5), "non-walkable cell is not a viable dropoff");

        t.setBuildingId(5, 5, 7);
        assertFalse(s.isViable(5, 5), "walkable cell inside a building footprint is not viable");
    }

    @Test
    public void bestNearSnapsOutOfBuildingToOpenGround() {
        NavigationGrid g = openGrid();
        CellTopology t = new CellTopology(W, H);
        // 3x3 building footprint centered on the hint.
        for (int y = 4; y <= 6; y++) {
            for (int x = 4; x <= 6; x++) t.setBuildingId(x, y, 1);
        }
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        int[] lz = s.bestNear(5, 5, 4);
        assertNotNull(lz, "open ground exists around the building");
        assertEquals(0, t.getBuildingId(lz[0], lz[1]), "LZ must not be inside the building");
        assertTrue(s.isViable(lz[0], lz[1]));
    }

    @Test
    public void bestNearReturnsNullWhenNothingViable() {
        NavigationGrid g = new NavigationGrid(W, H); // all non-walkable
        CellTopology t = new CellTopology(W, H);
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        assertNull(s.bestNear(5, 5, 3), "no walkable cell in range → no LZ");
    }

    @Test
    public void qualityPrefersOpenGroundAndClearance() {
        NavigationGrid g = openGrid();
        CellTopology t = new CellTopology(W, H);
        t.setGroundKind(3, 3, CellTopology.GroundKind.STREET);
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        assertTrue(s.quality(3, 3) > s.quality(8, 8),
                "an open street cell outranks a default-indoor cell with equal clearance");
    }

    @Test
    public void bestAmongPicksClosestViableCandidate() {
        NavigationGrid g = openGrid();
        CellTopology t = new CellTopology(W, H);
        t.setBuildingId(2, 2, 1); // first candidate sits in a building → rejected
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        int[] pick = s.bestAmong(new int[]{2, 2, 9, 9, 6, 5}, 5, 5);
        assertNotNull(pick);
        assertArrayEquals(new int[]{6, 5}, pick,
                "the in-building candidate is rejected; closest viable to the hint wins");
    }
}
