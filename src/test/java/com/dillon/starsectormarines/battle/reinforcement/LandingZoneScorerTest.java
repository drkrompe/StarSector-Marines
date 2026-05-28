package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-3a coverage for {@link LandingZoneScorer}: the viability rule
 * (walkable + not inside a building), quality (clearance + open-ground bonus),
 * the min-clearance pad gate, proximity-primary selection, and the two entry
 * points ({@code bestNear} ring-search, {@code bestAmong} caller candidates),
 * plus edge cases.
 */
public class LandingZoneScorerTest {

    private static final int W = 14;
    private static final int H = 14;

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
    public void qualityRewardsOpenGroundBonus() {
        NavigationGrid g = openGrid();
        CellTopology t = new CellTopology(W, H);
        t.setGroundKind(3, 3, CellTopology.GroundKind.STREET);
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        // (3,3) and (8,8) are both interior cells with full clearance (8); they
        // differ only by the open-ground bonus on the STREET cell.
        assertEquals(s.clearance(3, 3), s.clearance(8, 8), "fixture: equal clearance");
        assertTrue(s.quality(3, 3) > s.quality(8, 8), "open street outranks default-indoor at equal clearance");
    }

    @Test
    public void qualityRewardsClearance() {
        NavigationGrid g = openGrid();
        CellTopology t = new CellTopology(W, H);
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        // Interior cell (clearance 8) vs map corner (clearance 3), same ground.
        assertTrue(s.quality(5, 5) > s.quality(0, 0), "more open neighbours → higher quality");
    }

    @Test
    public void bestNearSnapsOutOfBuildingToOpenGround() {
        NavigationGrid g = openGrid();
        CellTopology t = new CellTopology(W, H);
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
    public void bestNearRadiusZeroConsidersOnlyHint() {
        CellTopology t = new CellTopology(W, H);
        NavigationGrid g = new NavigationGrid(W, H);
        g.setWalkableFloor(5, 5);
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        assertArrayEquals(new int[]{5, 5}, s.bestNear(5, 5, 0), "radius 0 + viable hint → the hint");
        assertNull(s.bestNear(6, 6, 0), "radius 0 + non-viable hint → null");
    }

    @Test
    public void bestNearMinClearanceGateRejectsCrampedCells() {
        // Hint (5,5) is viable but cramped (clearance 1); an open 3x3 pad sits
        // a few tiles away.
        NavigationGrid g = new NavigationGrid(W, H);
        CellTopology t = new CellTopology(W, H);
        g.setWalkableFloor(5, 5);
        g.setWalkableFloor(5, 4); // gives (5,5) clearance 1
        for (int y = 7; y <= 9; y++) {
            for (int x = 4; x <= 6; x++) g.setWalkableFloor(x, y);
        }
        LandingZoneScorer s = new LandingZoneScorer(g, t);

        // No gate: proximity-primary picks the cramped hint itself.
        assertArrayEquals(new int[]{5, 5}, s.bestNear(5, 5, 6, 0),
                "without a clearance gate the nearest viable cell (the hint) wins");

        // With a pad gate: the cramped hint is rejected; an open-pad cell is chosen.
        int[] lz = s.bestNear(5, 5, 6, 5);
        assertNotNull(lz);
        assertFalse(lz[0] == 5 && lz[1] == 5, "cramped hint rejected by the clearance gate");
        assertTrue(s.clearance(lz[0], lz[1]) >= 5, "chosen cell satisfies the requested pad size");
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
                "in-building candidate rejected; closest viable to the hint wins");
    }

    @Test
    public void bestAmongEmptyReturnsNull() {
        NavigationGrid g = openGrid();
        LandingZoneScorer s = new LandingZoneScorer(g, new CellTopology(W, H));
        assertNull(s.bestAmong(new int[]{}, 5, 5));
    }

    @Test
    public void bestAmongRejectsOddLengthArray() {
        NavigationGrid g = openGrid();
        LandingZoneScorer s = new LandingZoneScorer(g, new CellTopology(W, H));
        assertThrows(IllegalArgumentException.class, () -> s.bestAmong(new int[]{1, 2, 3}, 0, 0),
                "odd-length candidate array is a caller bug, not silently dropped");
    }
}
