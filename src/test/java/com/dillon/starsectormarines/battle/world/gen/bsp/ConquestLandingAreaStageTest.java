package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.ConquestLandingAreaStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invariants for the terminal, RNG-free Conquest paired-arrival stage. */
class ConquestLandingAreaStageTest {

    private static final long[] SEEDS = {0L, 1L, 42L, 777L, 9999L};

    @Test
    void canonicalMapsPublishClearDistinctTwoBerthBeachAreas() {
        BspCityGenerator generator = new BspCityGenerator();
        for (TraversalAxis axis : TraversalAxis.values()) {
            for (long seed : SEEDS) {
                MapResult map = generator.generate(
                        BattleSetup.CONQUEST_GRID_W,
                        BattleSetup.CONQUEST_GRID_H,
                        seed, axis);

                assertTrue(map.landingAreas.size() >= 4,
                        context(seed, axis) + " must support four paired arrival areas");
                Set<String> ids = new HashSet<>();
                for (LandingArea area : map.landingAreas) {
                    assertTrue(ids.add(area.id), context(seed, axis) + " duplicate " + area.id);
                    assertEquals(LandingArea.BERTH_COUNT, area.berths().size());
                    LandingPad first = area.berth(0);
                    LandingPad second = area.berth(1);
                    assertFalse(first.centerX == second.centerX
                                    && first.centerY == second.centerY,
                            context(seed, axis) + " coincident berths in " + area.id);
                    assertEquals(expectedApproach(axis), area.approach);
                    assertBerth(map, first, area, seed, axis);
                    assertBerth(map, second, area, seed, axis);
                    assertAreaClear(map, area, seed, axis);
                    int dx = first.centerX - second.centerX;
                    int dy = first.centerY - second.centerY;
                    assertTrue(dx * dx + dy * dy >= 64,
                            context(seed, axis) + " insufficient berth separation in " + area.id);
                }
                assertAreasDoNotOverlap(map.landingAreas, seed, axis);
            }
        }
    }

    @Test
    void sameRequestPublishesIdenticalOrderedAreas() {
        BspCityGenerator generator = new BspCityGenerator();
        for (TraversalAxis axis : TraversalAxis.values()) {
            MapResult first = generator.generate(240, 160, 4242L, axis);
            MapResult second = generator.generate(240, 160, 4242L, axis);
            assertEquals(signatures(first.landingAreas), signatures(second.landingAreas));
        }
    }

    @Test
    void legacyRecipePublishesNoConquestArrivalAreas() {
        MapResult map = new BspCityGenerator().generate(80, 80, 42L);
        assertTrue(map.landingAreas.isEmpty());
    }

    @Test
    void stageDoesNotConsumeTheGenerationRandomStream() {
        int width = 80;
        int height = 40;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        long randomSeed = 778899L;
        Random actual = new Random(randomSeed);
        Random expected = new Random(randomSeed);
        GenContext context = new GenContext(
                grid, topology, actual, width, height, 12L);
        context.put(BspKeys.AXIS, TraversalAxis.SOUTH_TO_NORTH);
        context.put(BspKeys.BIOME_MAP, new BiomeMap(
                width, height, TraversalAxis.SOUTH_TO_NORTH, new Random(12L)));

        new ConquestLandingAreaStage().run(context);

        assertEquals(expected.nextLong(), actual.nextLong());
    }

    private static void assertBerth(MapResult map, LandingPad berth,
                                    LandingArea area, long seed,
                                    TraversalAxis axis) {
        String context = context(seed, axis) + " " + area.id;
        assertEquals(LandingPad.Purpose.CONQUEST_ARRIVAL, berth.purpose);
        assertEquals(area.approach, berth.approach);
        assertTrue(berth.isClear(map.grid, map.topology), context + " berth not clear");
        assertTrue(area.contains(berth.left(), berth.bottom()));
        assertTrue(area.contains(berth.right(), berth.top()));
        for (int y = berth.bottom(); y <= berth.top(); y++) {
            for (int x = berth.left(); x <= berth.right(); x++) {
                assertEquals(BiomeKind.BEACH, map.biomeMap.biomeAt(x, y),
                        context + " berth leaves BEACH at " + x + "," + y);
            }
        }
    }

    private static void assertAreasDoNotOverlap(List<LandingArea> areas,
                                                long seed,
                                                TraversalAxis axis) {
        for (int i = 0; i < areas.size(); i++) {
            for (int j = i + 1; j < areas.size(); j++) {
                LandingArea a = areas.get(i);
                LandingArea b = areas.get(j);
                boolean overlap = a.left <= b.right && a.right >= b.left
                        && a.bottom <= b.top && a.top >= b.bottom;
                assertFalse(overlap, context(seed, axis) + " overlapping areas "
                        + a.id + " and " + b.id);
            }
        }
    }

    private static void assertAreaClear(MapResult map, LandingArea area,
                                        long seed, TraversalAxis axis) {
        String context = context(seed, axis) + " " + area.id;
        for (int y = area.top; y <= area.bottom; y++) {
            for (int x = area.left; x <= area.right; x++) {
                assertTrue(map.grid.isWalkable(x, y),
                        context + " blocked area cell at " + x + "," + y);
                assertEquals(0, map.topology.getBuildingId(x, y),
                        context + " building in area at " + x + "," + y);
                assertEquals(BiomeKind.BEACH, map.biomeMap.biomeAt(x, y),
                        context + " area leaves BEACH at " + x + "," + y);
            }
        }
    }

    private static List<String> signatures(List<LandingArea> areas) {
        return areas.stream().map(area -> area.id + ':'
                + area.left + ',' + area.bottom + ',' + area.right + ',' + area.top + ':'
                + area.berth(0).centerX + ',' + area.berth(0).centerY + ':'
                + area.berth(1).centerX + ',' + area.berth(1).centerY + ':'
                + area.approach).toList();
    }

    private static LandingPad.Approach expectedApproach(TraversalAxis axis) {
        return axis == TraversalAxis.SOUTH_TO_NORTH
                ? LandingPad.Approach.SOUTH : LandingPad.Approach.WEST;
    }

    private static String context(long seed, TraversalAxis axis) {
        return "seed=" + seed + ", axis=" + axis;
    }
}
