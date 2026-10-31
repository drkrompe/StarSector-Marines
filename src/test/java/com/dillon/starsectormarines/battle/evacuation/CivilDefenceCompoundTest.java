package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dressing of a walled place finds its gates and stands a gun tower by them
 * on the ground a packed yard actually leaves: buildings against the wall, a
 * building standing on the line of the wall, one way in rather than two.
 */
class CivilDefenceCompoundTest {

    private static final int W = 30, H = 30;
    /** The wall's square, inclusive. */
    private static final int L = 5, T = 5, R = 24, B = 24;

    @Test
    void aSingleGateHemmedInByBuildingsStillMountsBothGuns() {
        BattleSimulation sim = walled();
        // One way in, on the east side, and a building against the wall either side of it.
        open(sim, R, 14, R, 16);
        building(sim, 19, 6, R - 1, 13);
        building(sim, 19, 17, R - 1, B - 1);

        CivilDefenceCompound.Plan plan = CivilDefenceCompound.plan(sim, 12, 15, 10, 15, W, 15, 20);

        assertNotNull(plan, "a closed wall with a gate was not read as one");
        assertEquals(2, plan.guns().size(), "a gate whose flanks both have a building against them "
                + "mounted " + plan.guns().size() + " guns; a compound mounts one per gate it keeps");
        for (int[] gun : plan.guns()) assertEquals(R, gun[0], "a gun tower stood off the gate's wall");
    }

    @Test
    void aGateClosedByBuildingsOnTheWallLineIsAGate() {
        BattleSimulation sim = walled();
        open(sim, 14, T, 16, T);
        // On the south side buildings stand on the line of the wall, and the gap between them is the gate.
        building(sim, 8, B - 4, 12, B);
        building(sim, 16, B - 4, 20, B);
        open(sim, 13, B, 15, B);

        CivilDefenceCompound.Plan plan = CivilDefenceCompound.plan(sim, 15, 15, 15, 12, 15, 0, 20);

        assertNotNull(plan, "a gap closed by buildings' walls was read as a hole in the wall");
        assertEquals(2, plan.guns().size(), "two gates, " + plan.guns().size() + " gun towers");
    }

    @Test
    void anArmedCompoundMountsAGunInEachCornerTowerAndAnUnarmedOneNone() {
        BattleSimulation sim = walled();
        open(sim, 14, T, 16, T);

        CivilDefenceCompound.Plan armed = CivilDefenceCompound.plan(sim, 15, 15, 15, 12, 15, 0, 20, true);
        CivilDefenceCompound.Plan plain = CivilDefenceCompound.plan(sim, 15, 15, 15, 12, 15, 0, 20);

        assertNotNull(armed, "a closed wall with a gate was not read as one");
        assertEquals(4, armed.corners().size(), "an armed compound mounted " + armed.corners().size() + " corner guns");
        for (int[] gun : armed.corners()) {
            assertTrue((gun[0] == L || gun[0] == R) && (gun[1] == T || gun[1] == B),
                    "a corner gun stood at " + gun[0] + "," + gun[1] + ", off the wall's corners");
            assertEquals(CivilDefenceCompound.Kind.GUN, armed.cells().get(CivilDefenceCompound.key(gun[0], gun[1])));
        }
        assertEquals(0, plain.corners().size(), "an unarmed wall's corner towers mounted guns");
    }

    @Test
    void noWallPostStandsOnTheWallsItLays() {
        BattleSimulation sim = walled();
        open(sim, 14, T, 16, T);

        CivilDefenceCompound.Plan plan = CivilDefenceCompound.plan(sim, 15, 15, 15, 12, 15, 0, 20);

        assertNotNull(plan, "a closed wall with a gate was not read as one");
        for (RedoubtWorks.Bag post : plan.posts()) {
            assertFalse(plan.cells().containsKey(CivilDefenceCompound.key(post.x(), post.y())),
                    "a wall post at " + post.x() + "," + post.y() + " is a cell the dressing builds on");
        }
    }

    /** Open ground with a perimeter wall round the square. */
    private static BattleSimulation walled() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        CellTopology topology = new CellTopology(W, H);
        int perimeter = topology.surfaceIndex(InstallationWalls.PERIMETER);
        for (int y = T; y <= B; y++) {
            for (int x = L; x <= R; x++) {
                if (x != L && x != R && y != T && y != B) continue;
                grid.setWalkable(x, y, false);
                topology.setWall(x, y, true);
                topology.setSurface(x, y, perimeter);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    private static void open(BattleSimulation sim, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                sim.getGrid().setWalkable(x, y, true);
                sim.getTopology().setWall(x, y, false);
                sim.getTopology().setSurface(x, y, 0);
            }
        }
    }

    /** A building's shell over the rectangle, its floor inside; over the wall where it reaches it. */
    private static void building(BattleSimulation sim, int x0, int y0, int x1, int y1) {
        CellTopology topology = sim.getTopology();
        int surface = topology.surfaceIndex(InstallationWalls.BUILDING);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                boolean shell = x == x0 || x == x1 || y == y0 || y == y1;
                sim.getGrid().setWalkable(x, y, !shell);
                topology.setWall(x, y, shell);
                topology.setSurface(x, y, shell ? surface : 0);
                topology.setBuildingId(x, y, 1);
            }
        }
    }
}
