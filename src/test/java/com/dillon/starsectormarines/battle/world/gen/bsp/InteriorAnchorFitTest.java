package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production-seed scan for the two placement invariants an objective needs in
 * order to be reachable and capturable.
 *
 * <p>Both are map-wide, which is the point: a filler that authors a good anchor
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

    /**
     * Conquest requires every compound to flip, so a compound whose footprint
     * holds no zone is not a blemish — it is an unwinnable mission.
     */
    @Test
    public void everyCompoundFootprintHoldsACaptureZone() {
        forEveryProductionMap((map, label) -> {
            ZoneGraph zones = new ZoneGraph(map.grid);
            zones.rebuild();
            for (TacticalNode node : map.tacticalMap.all()) {
                if (!isCompoundKind(node.kind)) continue;
                assertTrue(hasZonedCell(zones, node),
                        label + ": " + node.kind + " footprint " + node.left + "," + node.top
                                + ".." + node.right + "," + node.bottom
                                + " holds no zoned cell, so the compound can never be captured");
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

    private static boolean hasZonedCell(ZoneGraph zones, TacticalNode node) {
        for (int y = node.top; y <= node.bottom; y++) {
            for (int x = node.left; x <= node.right; x++) {
                if (zones.zoneIdAt(x, y) >= 0) return true;
            }
        }
        return false;
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

    private static boolean isCompoundKind(TacticalNode.Kind kind) {
        return kind == TacticalNode.Kind.COMMAND_POST
                || kind == TacticalNode.Kind.BARRACKS
                || kind == TacticalNode.Kind.ARMORY;
    }
}
