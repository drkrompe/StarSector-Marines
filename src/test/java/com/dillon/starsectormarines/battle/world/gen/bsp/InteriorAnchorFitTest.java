package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production-seed scan for the placement invariant an objective needs in order
 * to be reachable and capturable.
 *
 * <p>It is map-wide, which is the point: a filler that authors a good anchor
 * cannot keep it good, because later stages keep stamping over the map. Only a
 * scan of finished maps proves the guarantee survived the whole pipeline.
 */
public class InteriorAnchorFitTest {

    private static final int LEGACY_W = 160;
    private static final int LEGACY_H = 120;
    private static final int SEEDS = 25;

    /**
     * Mission layouts filter candidate sites on interior-anchor walkability and
     * zone-scoped objectives resolve a room from the anchor, so an anchor that
     * is not standable either drops the building from the objective pool or —
     * worse — anchors an objective in no room at all.
     */
    @Test
    public void everyPointOfInterestWithAnOpenInteriorAnchorsInIt() {
        forEveryProductionMap((map, label) -> {
            for (PointOfInterest poi : map.pointsOfInterest) {
                if (!enclosesStandableCell(map.grid, poi)) continue;
                assertTrue(standable(map.grid, poi.interiorAnchorX, poi.interiorAnchorY),
                        label + ": " + poi.kind + " footprint " + poi.left + "," + poi.top
                                + ".." + poi.right + "," + poi.bottom
                                + " encloses a standable cell but anchors its interior at ("
                                + poi.interiorAnchorX + "," + poi.interiorAnchorY + ")");
                assertTrue(withinFootprint(poi, poi.interiorAnchorX, poi.interiorAnchorY),
                        label + ": " + poi.kind + " interior anchor ("
                                + poi.interiorAnchorX + "," + poi.interiorAnchorY
                                + ") escaped its own footprint");
            }
        });
    }

    private interface MapCheck {
        void check(MapResult map, String label);
    }

    /** Both Conquest traversal axes plus the legacy district recipe, at their production sizes. */
    private static void forEveryProductionMap(MapCheck check) {
        BspCityGenerator generator = new BspCityGenerator();
        for (TraversalAxis axis : TraversalAxis.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                check.check(generator.generate(BattleSetup.CONQUEST_GRID_W,
                        BattleSetup.CONQUEST_GRID_H, seed, axis),
                        "conquest seed=" + seed + " axis=" + axis);
            }
        }
        for (long seed = 0; seed < SEEDS; seed++) {
            check.check(generator.generate(LEGACY_W, LEGACY_H, seed, null),
                    "legacy seed=" + seed);
        }
    }

    private static boolean enclosesStandableCell(NavigationGrid grid, PointOfInterest poi) {
        for (int y = poi.top; y <= poi.bottom; y++) {
            for (int x = poi.left; x <= poi.right; x++) {
                if (grid.inBounds(x, y) && standable(grid, x, y)) return true;
            }
        }
        return false;
    }

    private static boolean withinFootprint(PointOfInterest poi, int x, int y) {
        return x >= poi.left && x <= poi.right && y >= poi.top && y <= poi.bottom;
    }

    /** Doorways are walkable but belong to no zone, so they are no better than walls for anchoring an objective. */
    private static boolean standable(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y) && !grid.isDoorway(x, y);
    }
}
