package com.dillon.starsectormarines.battle.world.gen.taxonomy;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariants for the {@link TacticalRegionMap} structural-taxonomy artifact,
 * checked across the same seed batch the preview/connectivity tests use. The
 * acceptance gate for the Lever-1 segmentation: a region partitions the
 * walkable space exactly, its attributes stay in range, and the segmentation
 * is reproducible from the seed.
 */
public class TacticalRegionTest {

    private static final long[] LEGACY_SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };
    private static final long[] CONQUEST_SEEDS = { 1L, 42L, 100L, 777L };

    @Test
    void partitionsWalkableSpaceExactly() {
        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : CONQUEST_SEEDS) {
            MapResult map = gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH);
            assertCoverage(gen.getLastTacticalRegions(), map, seed);
        }
        for (long seed : LEGACY_SEEDS) {
            MapResult map = gen.generate(80, 80, seed);
            assertCoverage(gen.getLastTacticalRegions(), map, seed);
        }
    }

    /** Every walkable cell is in exactly one region; every non-walkable cell is in none; areas sum to the walkable count. */
    private static void assertCoverage(TacticalRegionMap regions, MapResult map, long seed) {
        assertNotNull(regions, "seed " + seed + ": no region map");
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        int walkable = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int fx = x, fy = y;
                boolean isWalkable = map.grid.isWalkable(x, y);
                int rid = regions.regionIdAt(x, y);
                if (isWalkable) {
                    walkable++;
                    assertTrue(rid >= 0 && rid < regions.size(),
                            () -> "seed " + seed + ": walkable cell unassigned at " + fx + "," + fy);
                } else {
                    assertEquals(-1, rid,
                            () -> "seed " + seed + ": non-walkable cell assigned at " + fx + "," + fy);
                }
            }
        }
        int areaSum = regions.regions().stream().mapToInt(r -> r.area).sum();
        final int walkableTotal = walkable;
        assertEquals(walkable, areaSum,
                () -> "seed " + seed + ": region areas (" + areaSum + ") != walkable cells (" + walkableTotal + ")");
    }

    @Test
    void attributesInRangeAndNoIslands() {
        BspCityGenerator gen = new BspCityGenerator();
        for (long seed : CONQUEST_SEEDS) {
            MapResult ignored = gen.generate(240, 160, seed, TraversalAxis.SOUTH_TO_NORTH);
            TacticalRegionMap regions = gen.getLastTacticalRegions();
            for (TacticalRegion r : regions.regions()) {
                assertTrue(r.area > 0, () -> "seed " + seed + ": " + r + " has non-positive area");
                assertInUnit(r.coverDensity, "coverDensity", r, seed);
                assertInUnit(r.enclosure, "enclosure", r, seed);
                assertTrue(r.meanExposure >= 0f, () -> "seed " + seed + ": " + r + " negative exposure");
                // With map-wide connectivity holding, any region in a multi-region
                // map must open onto another region — openingCount==0 would mean an
                // isolated walkable pocket (a connectivity bug).
                if (regions.size() > 1) {
                    assertTrue(r.openingCount >= 1,
                            () -> "seed " + seed + ": isolated region (no mouths) " + r);
                }
            }
        }
    }

    private static void assertInUnit(float v, String name, TacticalRegion r, long seed) {
        assertTrue(v >= 0f && v <= 1f, () -> "seed " + seed + ": " + name + "=" + v + " out of [0,1] for " + r);
    }

    @Test
    void axisDrivesDepthBand() {
        BspCityGenerator gen = new BspCityGenerator();
        // Conquest: depth is set and varies across bands (the assault gradient exists).
        gen.generate(240, 160, 42L, TraversalAxis.SOUTH_TO_NORTH);
        EnumSet<DepthBand> bands = EnumSet.noneOf(DepthBand.class);
        for (TacticalRegion r : gen.getLastTacticalRegions().regions()) {
            assertTrue(r.depthBand != DepthBand.UNSET, () -> "conquest region has UNSET depth: " + r);
            bands.add(r.depthBand);
        }
        assertTrue(bands.size() >= 2, "conquest map should span multiple depth bands, saw " + bands);

        // Legacy: no axis → every region UNSET.
        gen.generate(80, 80, 42L);
        for (TacticalRegion r : gen.getLastTacticalRegions().regions()) {
            assertEquals(DepthBand.UNSET, r.depthBand, () -> "legacy region has a depth band: " + r);
            assertEquals(-1f, r.depth01, 0f);
        }
    }

    @Test
    void deterministicFromSeed() {
        BspCityGenerator gen = new BspCityGenerator();
        gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH);
        TacticalRegionMap a = gen.getLastTacticalRegions();
        gen.generate(240, 160, 777L, TraversalAxis.SOUTH_TO_NORTH);
        TacticalRegionMap b = gen.getLastTacticalRegions();

        assertEquals(a.size(), b.size(), "region count not reproducible from seed");
        for (int i = 0; i < a.size(); i++) {
            TacticalRegion ra = a.regions().get(i), rb = b.regions().get(i);
            assertEquals(ra.kind, rb.kind);
            assertEquals(ra.area, rb.area);
            assertEquals(ra.centroidX, rb.centroidX);
            assertEquals(ra.centroidY, rb.centroidY);
            assertEquals(ra.openingCount, rb.openingCount);
            assertEquals(ra.enclosure, rb.enclosure, 0f);
            assertEquals(ra.coverDensity, rb.coverDensity, 0f);
        }
    }
}
