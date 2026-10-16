package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
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

/**
 * The terminal, RNG-free Conquest paired-arrival stage, asked of open ground and
 * a biome map. Its output on whole generated cities is
 * {@code ConquestLandingAreaEvidence}.
 */
class ConquestLandingAreaStageTest {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 120;

    private static final class Staged {
        final GenContext context;
        final BiomeMap biomes;

        Staged(TraversalAxis axis, Random random) {
            NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
            }
            context = new GenContext(grid, new CellTopology(WIDTH, HEIGHT), random,
                    WIDTH, HEIGHT, 12L);
            biomes = new BiomeMap(WIDTH, HEIGHT, axis, new Random(12L));
            context.put(BspKeys.AXIS, axis);
            context.put(BspKeys.BIOME_MAP, biomes);
        }
    }

    private static Staged run(TraversalAxis axis) {
        Staged staged = new Staged(axis, new Random(778899L));
        new ConquestLandingAreaStage().run(staged.context);
        return staged;
    }

    @Test
    void openBeachPublishesClearDistinctTwoBerthAreasOnBothAxes() {
        for (TraversalAxis axis : TraversalAxis.values()) {
            Staged staged = run(axis);
            List<LandingArea> areas = staged.context.landingAreas;
            assertTrue(areas.size() >= 1, axis + " published no arrival areas on open beach");

            Set<String> ids = new HashSet<>();
            for (LandingArea area : areas) {
                assertTrue(ids.add(area.id), axis + " duplicate " + area.id);
                assertEquals(LandingArea.BERTH_COUNT, area.berths().size());
                assertEquals(axis == TraversalAxis.SOUTH_TO_NORTH
                        ? LandingPad.Approach.SOUTH : LandingPad.Approach.WEST, area.approach);
                LandingPad first = area.berth(0);
                LandingPad second = area.berth(1);
                int dx = first.centerX - second.centerX;
                int dy = first.centerY - second.centerY;
                assertTrue(dx * dx + dy * dy >= 64,
                        axis + " insufficient berth separation in " + area.id);
                for (LandingPad berth : List.of(first, second)) {
                    assertEquals(LandingPad.Purpose.CONQUEST_ARRIVAL, berth.purpose);
                    assertEquals(area.approach, berth.approach);
                    assertTrue(berth.isClear(staged.context.grid, staged.context.topology));
                    assertTrue(area.contains(berth.left(), berth.bottom()));
                    assertTrue(area.contains(berth.right(), berth.top()));
                }
                for (int y = area.top; y <= area.bottom; y++) {
                    for (int x = area.left; x <= area.right; x++) {
                        assertEquals(BiomeKind.BEACH, staged.biomes.biomeAt(x, y),
                                area.id + " leaves BEACH at " + x + "," + y);
                    }
                }
            }
            for (int i = 0; i < areas.size(); i++) {
                for (int j = i + 1; j < areas.size(); j++) {
                    LandingArea a = areas.get(i);
                    LandingArea b = areas.get(j);
                    assertFalse(a.left <= b.right && a.right >= b.left
                                    && a.bottom <= b.top && a.top >= b.bottom,
                            axis + " overlapping areas " + a.id + " and " + b.id);
                }
            }
        }
    }

    @Test
    void sameRequestPublishesIdenticalOrderedAreas() {
        for (TraversalAxis axis : TraversalAxis.values()) {
            assertEquals(signatures(run(axis).context.landingAreas),
                    signatures(run(axis).context.landingAreas));
        }
    }

    @Test
    void stageDoesNotConsumeTheGenerationRandomStream() {
        long randomSeed = 778899L;
        Random actual = new Random(randomSeed);
        Random expected = new Random(randomSeed);
        Staged staged = new Staged(TraversalAxis.SOUTH_TO_NORTH, actual);

        new ConquestLandingAreaStage().run(staged.context);

        assertEquals(expected.nextLong(), actual.nextLong());
    }

    private static List<String> signatures(List<LandingArea> areas) {
        return areas.stream().map(area -> area.id + ':'
                + area.left + ',' + area.bottom + ',' + area.right + ',' + area.top + ':'
                + area.berth(0).centerX + ',' + area.berth(0).centerY + ':'
                + area.berth(1).centerX + ',' + area.berth(1).centerY + ':'
                + area.approach).toList();
    }
}
