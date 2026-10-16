package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a bastion's guns stand: nests on the wall's outer face where they can fire out, launchers in the pocket behind. */
class BastionSitesTest {

    /** A filled diamond of radius 8 centred on (10, 10), the battery fort's bastion shape. */
    private static boolean[][] diamond() {
        boolean[][] inside = new boolean[21][21];
        for (int x = 0; x < 21; x++) for (int y = 0; y < 21; y++) inside[x][y] = Math.abs(x - 10) + Math.abs(y - 10) <= 8;
        return inside;
    }

    @Test
    void theEmbrasureOfADiamondIsTheMiddleOfItsOutwardFace() {
        boolean[][] inside = diamond();
        assertArrayEquals(new int[]{6, 6}, BastionSites.embrasure(inside, 10, 10, -1, -1));
        assertArrayEquals(new int[]{14, 14}, BastionSites.embrasure(inside, 10, 10, 1, 1));
        assertTrue(BastionSites.isOuterFace(inside, 6, 6));
    }

    @Test
    void aRoundTowersDiagonalEndsInsideSoTheFaceCellBesideItIsTaken() {
        boolean[][] inside = new boolean[21][21];
        for (int x = 0; x < 21; x++) for (int y = 0; y < 21; y++) {
            inside[x][y] = (x - 10) * (x - 10) + (y - 10) * (y - 10) <= 25;
        }
        int[] cell = BastionSites.embrasure(inside, 10, 10, -1, -1);
        assertTrue(BastionSites.isOuterFace(inside, cell[0], cell[1]), "an embrasure is on the outer face");
    }

    @Test
    void aCentreOutsideTheWorksHasNoEmbrasure() {
        assertNull(BastionSites.embrasure(diamond(), 0, 0, -1, -1));
    }

    @Test
    void theLauncherPocketIsOnTheDiagonalTowardTheTip() {
        assertArrayEquals(new int[]{8, 12}, BastionSites.pocket(10, 10, -1, 1, 2));
    }

    // ----------------------------------------------------------------------------- the real plans' bastions

    private static final List<FortPlan> PLANS = List.of(BatteryFort.PLAN, ShieldFort.PLAN);

    /** A grid of the plan's own works: its wall is solid, everything else open. No stamping involved. */
    private static NavigationGrid worksOf(FortPlan plan) {
        NavigationGrid grid = new NavigationGrid(plan.width(), plan.height());
        for (int x = 0; x < plan.width(); x++) {
            for (int y = 0; y < plan.height(); y++) {
                if (plan.isWall(x, y)) grid.setWalkable(x, y, false);
                else grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }

    /** The share of rays from the cell, out to {@code reach} cells in every fifth degree, that reach their end unblocked. */
    private static double fireShare(NavigationGrid grid, int[] cell, float reach) {
        int clear = 0, total = 0;
        for (int a = 0; a < 360; a += 5) {
            float tx = cell[0] + 0.5f + (float) Math.cos(Math.toRadians(a)) * reach;
            float ty = cell[1] + 0.5f + (float) Math.sin(Math.toRadians(a)) * reach;
            if (tx < 0 || ty < 0 || tx >= grid.getWidth() || ty >= grid.getHeight()) continue;
            total++;
            if (grid.hasLineOfFire(cell[0] + 0.5f, cell[1] + 0.5f, tx, ty)) clear++;
        }
        return total == 0 ? 0 : (double) clear / total;
    }

    @Test
    void everyBastionNestFiresOutOfTheWorksThroughAtLeastAThirdOfTheCompass() {
        for (FortPlan plan : PLANS) {
            NavigationGrid grid = worksOf(plan);
            for (int[] nest : plan.nests()) {
                assertTrue(plan.isWorks(nest[0], nest[1]), plan.kind() + " nest is inside the works");
                assertFalse(plan.isWall(nest[0], nest[1]), plan.kind() + " nest stands in a notch, not in solid wall");
                assertTrue(BastionSites.isOuterFace(insideOf(plan), nest[0], nest[1]), plan.kind() + " nest is on the outer face");
                double share = fireShare(grid, nest, 8f);
                assertTrue(share >= 0.33, plan.kind() + " nest at " + nest[0] + "," + nest[1] + " fires through only " + share);
            }
        }
    }

    private static boolean[][] insideOf(FortPlan plan) {
        boolean[][] inside = new boolean[plan.width()][plan.height()];
        for (int x = 0; x < plan.width(); x++) for (int y = 0; y < plan.height(); y++) inside[x][y] = plan.isWorks(x, y);
        return inside;
    }

    @Test
    void movingTheNestsOutToTheFaceLeavesEveryWayInUnderAtLeastTwoOfThem() {
        for (FortPlan plan : PLANS) {
            for (AssaultApproach approach : plan.approaches()) {
                assertTrue(approach.covering().size() >= 2,
                        plan.kind() + " " + approach.variant() + " is covered by only " + approach.covering());
            }
        }
    }
    @Test
    void theMissileMountsStandInTheBastionsClearOfWallAndNestAndEachOther() {
        for (FortPlan plan : PLANS) {
            List<int[]> batteries = new ArrayList<>(plan.airBatteries());
            assertEquals(2, plan.airBatteries().size(), plan.kind() + " stands two Locust batteries");
            assertEquals(2, plan.airTowers().size(), plan.kind() + " stands two Swarmer towers");
            batteries.addAll(plan.airTowers());
            Set<Long> taken = new HashSet<>();
            for (int[] nest : plan.nests()) taken.add(((long) nest[0] << 32) | nest[1]);
            for (int[] b : batteries) {
                for (int x = b[0] - 1; x <= b[0] + 1; x++) {
                    for (int y = b[1] - 1; y <= b[1] + 1; y++) {
                        assertTrue(plan.isWorks(x, y) && !plan.isWall(x, y), plan.kind() + " battery cell " + x + "," + y + " is open floor");
                        assertTrue(taken.add(((long) x << 32) | y), plan.kind() + " battery cell " + x + "," + y + " is its own");
                    }
                }
            }
        }
    }

    @Test
    void theBatteriesAreSpreadAcrossTheFortNotHuddledInTheYard() {
        for (FortPlan plan : PLANS) {
            List<int[]> batteries = plan.airBatteries();
            double apart = Math.hypot(batteries.get(0)[0] - batteries.get(1)[0], batteries.get(0)[1] - batteries.get(1)[1]);
            assertTrue(apart >= 30, plan.kind() + " batteries stand " + apart + " cells apart");
            List<int[]> towers = plan.airTowers();
            double towersApart = Math.hypot(towers.get(0)[0] - towers.get(1)[0], towers.get(0)[1] - towers.get(1)[1]);
            assertTrue(towersApart >= 30, plan.kind() + " towers stand " + towersApart + " cells apart");
            AirDefenceYard yard = plan.airDefence();
            assertNotNull(yard);
            for (int[] b : batteries) {
                assertTrue(Math.hypot(b[0] - yard.centreX(), b[1] - yard.centreY()) > AirDefenceYard.HALF_WIDTH + 2,
                        plan.kind() + " battery stands outside the control room");
            }
        }
    }
}
