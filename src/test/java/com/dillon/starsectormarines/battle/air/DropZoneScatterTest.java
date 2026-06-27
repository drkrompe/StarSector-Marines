package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for {@link DropZoneScatter} — the drop-zone landing-cell picker. The engine is pure and
 * dependency-inverted (walkable + threat are lambdas), so these tests exercise it with no sim: a flat
 * grid, a synthetic threat field, and a seeded RNG for reproducibility.
 */
public class DropZoneScatterTest {

    private static final int CX = 50;
    private static final int CY = 50;

    private static final DropZoneScatter.CellWalkable ALL_WALKABLE = (x, y) -> true;
    private static final DropZoneScatter.CellThreat NO_THREAT = (x, y) -> 0f;

    @Test
    public void picksCountCellsAllWithinRadiusAndSpacedApart() {
        float radius = 20f, minSpacing = 5f;
        int count = 5;
        List<int[]> cells = DropZoneScatter.sample(CX, CY, radius, count, minSpacing,
                ALL_WALKABLE, NO_THREAT, new Random(42));

        assertEquals(count, cells.size(), "open zone should supply the full wave");
        for (int[] c : cells) {
            int dx = c[0] - CX, dy = c[1] - CY;
            assertTrue(dx * dx + dy * dy <= radius * radius, "pick must lie inside the zone disc");
        }
        // Every pair at least minSpacing apart (reject is strictly-closer-than, so >= holds).
        for (int i = 0; i < cells.size(); i++) {
            for (int j = i + 1; j < cells.size(); j++) {
                int dx = cells.get(i)[0] - cells.get(j)[0];
                int dy = cells.get(i)[1] - cells.get(j)[1];
                assertTrue(dx * dx + dy * dy >= minSpacing * minSpacing,
                        "landing cells must scatter at least minSpacing apart");
            }
        }
    }

    @Test
    public void prefersLowThreatGround() {
        // Left half (x < CX) is dangerous; right half is safe. Jitter (<0.5) can't flip the 100-gap,
        // so every pick should land in the safe half.
        DropZoneScatter.CellThreat splitThreat = (x, y) -> x < CX ? 100f : 0f;
        List<int[]> cells = DropZoneScatter.sample(CX, CY, 15f, 4, 4f,
                ALL_WALKABLE, splitThreat, new Random(7));

        assertFalse(cells.isEmpty(), "safe half is roomy enough to land the wave");
        for (int[] c : cells) {
            assertTrue(c[0] >= CX, "drops must avoid the high-threat half");
        }
    }

    @Test
    public void neverLandsOnNonWalkableCells() {
        // Block every even column; the picker must skip them entirely.
        DropZoneScatter.CellWalkable oddColumns = (x, y) -> (x & 1) == 1;
        List<int[]> cells = DropZoneScatter.sample(CX, CY, 12f, 6, 3f,
                oddColumns, NO_THREAT, new Random(1));

        assertFalse(cells.isEmpty());
        for (int[] c : cells) {
            assertTrue((c[0] & 1) == 1, "pick must be on a walkable cell");
        }
    }

    @Test
    public void returnsEmptyForDegenerateRequests() {
        assertTrue(DropZoneScatter.sample(CX, CY, 10f, 0, 5f, ALL_WALKABLE, NO_THREAT, new Random(1)).isEmpty(),
                "count 0 → no cells");
        assertTrue(DropZoneScatter.sample(CX, CY, 0f, 5, 5f, ALL_WALKABLE, NO_THREAT, new Random(1)).isEmpty(),
                "radius 0 → no cells");
    }
}
