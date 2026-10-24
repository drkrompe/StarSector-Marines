package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Plateau;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Ramp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElevationBrushTest {

    /** A round plateau in the middle of a 40x30 field, one ramp climbing it from the west. */
    static ElevationPlan hill(double rampWidth) {
        return new ElevationPlan(40, 30, 1, 0, 7,
                List.of(new Plateau(1, 8, List.of(new Point(24, 15)))),
                List.of(new Ramp(new Point(13, 15), new Point(19, 15), rampWidth)));
    }

    static NavigationGrid field(CellTopology topology) {
        NavigationGrid grid = new NavigationGrid(40, 30);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        return grid;
    }

    @Test void theOnlyWayUpIsTheRamp() {
        CellTopology topology = new CellTopology(40, 30);
        NavigationGrid grid = field(topology);
        ElevationMap map = ElevationBrush.stamp(hill(3), grid, topology);

        assertEquals(1, map.tier(24, 15));
        assertTrue(map.isRamp(18, 15));
        assertEquals(0, map.tier(18, 15), "a ramp sees from its lower tier");
        // The plateau's east edge is a face nobody stands on.
        assertTrue(map.isFace(31, 15));
        int faceX = map.isFace(32, 15) ? 32 : 31;
        assertFalse(grid.isWalkable(faceX, 15));
        assertTrue(grid.hasTag(faceX, 15, NavigationGrid.CellTag.NATURAL_SOLID));

        // From the east, the route to the top goes round to the western ramp.
        int[] path = GridPathfinder.findPath(grid, 38, 15, 24, 15);
        assertNotNull(path);
        boolean usedRamp = false;
        for (int i = 0; i + 1 < path.length; i += 2) {
            if (map.isRamp(path[i], path[i + 1])) usedRamp = true;
        }
        assertTrue(usedRamp, "the route must climb the ramp");
    }

    @Test void noTwoWalkableCellsOnDifferentTiersShareAnOpenEdge() {
        CellTopology topology = new CellTopology(40, 30);
        NavigationGrid grid = field(topology);
        ElevationMap map = ElevationBrush.stamp(hill(3), grid, topology);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x + 1 < 40; x++) {
                if (grid.canTraverseCellStep(x, y, x + 1, y)) {
                    assertFalse(map.separates(grid.index(x, y), grid.index(x + 1, y)), "open cliff at " + x + "," + y);
                }
            }
        }
    }

    @Test void aRampNarrowerThanAChassisIsACliffToIt() {
        CellTopology topology = new CellTopology(40, 30);
        NavigationGrid grid = field(topology);
        ElevationMap map = ElevationBrush.stamp(hill(2), grid, topology);
        VehicleClearance wide = VehicleClearance.erode(grid, 2);
        // Clearance radius 2 needs a 5-cell corridor; the 2-cell ramp passes none.
        for (int x = 17; x <= 19; x++) assertFalse(wide.isPassable(x, 15));
        assertNotNull(GridPathfinder.findPath(grid, 2, 15, 24, 15), "infantry still climb it");
        assertTrue(map.isRamp(18, 15));
    }

    @Test void builtGroundUnderTheTopIsRaisedButNeverUnderAFace() {
        boolean[] reserved = new boolean[40 * 30];
        reserved[15 * 40 + 24] = true;
        CellTopology topology = new CellTopology(40, 30);
        assertThrows(IllegalArgumentException.class,
                () -> ElevationBrush.stamp(hill(3), field(topology), topology, reserved), "reserved ground is refused");

        boolean[] built = new boolean[40 * 30];
        built[15 * 40 + 24] = true;
        CellTopology raised = new CellTopology(40, 30);
        ElevationMap map = ElevationBrush.stamp(hill(3), field(raised), raised, reserved, built);
        assertEquals(1, map.tier(24, 15), "built ground the plan puts on the top is raised with it");

        ElevationLayout layout = ElevationLayout.resolve(hill(3));
        int faceX = layout.isFace(31, 15) ? 31 : 32;
        boolean[] underFace = new boolean[40 * 30];
        underFace[15 * 40 + faceX] = true;
        CellTopology faced = new CellTopology(40, 30);
        assertThrows(IllegalArgumentException.class,
                () -> ElevationBrush.stamp(hill(3), field(faced), faced, underFace, underFace),
                "a face on built ground is refused however it is marked");
    }

    @Test void aPlateauWithoutARampIsRefusedAndTheMapLeftUntouched() {
        CellTopology topology = new CellTopology(40, 30);
        NavigationGrid grid = field(topology);
        ElevationPlan noRamp = new ElevationPlan(40, 30, 1, 0, 7,
                List.of(new Plateau(1, 8, List.of(new Point(24, 15)))), List.of());
        assertThrows(IllegalArgumentException.class, () -> ElevationBrush.stamp(noRamp, grid, topology));
        assertNull(grid.elevation());
        assertTrue(grid.isWalkable(32, 15));
    }

    @Test void aRampMustClimbExactlyOneTier() {
        ElevationPlan flat = new ElevationPlan(40, 30, 1, 0, 7,
                List.of(new Plateau(1, 8, List.of(new Point(24, 15)))),
                List.of(new Ramp(new Point(2, 15), new Point(8, 15), 3)));
        assertThrows(IllegalArgumentException.class, () -> ElevationLayout.resolve(flat));
    }
}
