package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.cordon.CordonGapLaw;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sounding keeps every installation reachable from the landing ground, its three approaches open on their own
 * ground, and the post's vehicles a lane their hulls can drive.
 */
class BlindWatchSoundingTest {

    private static final TargetProfile WARION = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.ROCK,
            SettlementLink.NONE);

    @BeforeAll
    static void catalogs() throws Exception {
        DiskRegistries.install();
    }

    @Test
    void everyInstallationIsReachedFromTheLandingGroundByEachOfItsApproaches() {
        BlindWatchSounding.Layout layout = BlindWatchSounding.generate(7150, WARION);
        NavigationGrid grid = layout.map().grid;
        boolean[] reached = BlindWatchSounding.reach(grid, layout.companyStart(), null);
        List<BlindWatchSounding.Cell> sites = new ArrayList<>(List.of(layout.console(), layout.knollRamp()));
        layout.sites().values().forEach(site -> sites.add(site.plant()));
        layout.posts().values().forEach(sites::addAll);
        sites.addAll(layout.riggers());
        sites.addAll(layout.signalsTechs());
        sites.addAll(layout.rilleCrossings());
        for (BlindWatchSounding.Cell site : sites) assertTrue(reached[grid.index(site.x(), site.y())], site.toString());
        for (BlindWatchSounding.Approach approach : BlindWatchSounding.Approach.values())
            assertTrue(BlindWatchSounding.approachOpen(grid, layout.approaches().get(approach)), approach.name());
        assertTrue(BlindWatchSounding.approachOpen(grid, layout.riggersWalkOut()));
        // The mast and its guns stand on the rim, a tier above the flat they watch.
        BlindWatchSounding.Cell mast = layout.sites().get(BlindWatchSounding.Installation.MAST).plant();
        assertEquals(1, grid.elevation().tier(mast.x(), mast.y()));
        for (BlindWatchSounding.Nest nest : layout.nests()) assertEquals(1, grid.elevation().tier(nest.cell().x(), nest.cell().y()));
        // Buildings are held places only; the map brings no capture point of its own.
        for (TacticalNode node : layout.map().tacticalMap.all())
            assertEquals(TacticalNode.Kind.INNER_POSITION, node.kind);
    }

    /**
     * The west rim gun watches the boulder approach and the rim's west ramp, never the flat: every cell of the flat
     * approach's corridor, the rover track included, is beyond its gun's reach or out of its sight (the grid's own
     * line, which lets the high ground see down). The flat is the mortar's.
     */
    @Test
    void theWestRimGunHasNoLineOverTheFlatApproach() {
        BlindWatchSounding.Layout layout = BlindWatchSounding.generate(7150, WARION);
        NavigationGrid grid = layout.map().grid;
        BlindWatchSounding.Cell gun = layout.nests().get(0).cell();
        List<BlindWatchSounding.Cell> flat = layout.approaches().get(BlindWatchSounding.Approach.FLAT);
        for (int y = 0; y < grid.getHeight(); y++) for (int x = 0; x < grid.getWidth(); x++) {
            if (!grid.isWalkable(x, y) || Math.hypot(x - gun.x(), y - gun.y()) > CordonGapLaw.REACH
                    || !nearLine(x, y, flat, BlindWatchSounding.APPROACH_CORRIDOR)) continue;
            assertFalse(grid.hasLineOfSight(gun.x(), gun.y(), x, y), "west rim gun sees the flat at " + x + "," + y);
        }
    }

    private static boolean nearLine(int x, int y, List<BlindWatchSounding.Cell> line, double within) {
        for (int i = 1; i < line.size(); i++) {
            BlindWatchSounding.Cell a = line.get(i - 1), b = line.get(i);
            double dx = b.x() - a.x(), dy = b.y() - a.y(), length = dx * dx + dy * dy;
            double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.x()) * dx + (y - a.y()) * dy) / length));
            if (Math.hypot(x - a.x() - t * dx, y - a.y() - t * dy) <= within) return true;
        }
        return false;
    }

    @Test
    void theRilleStopsAWalkerButNotASightLine() {
        BlindWatchSounding.Layout layout = BlindWatchSounding.generate(7150, WARION);
        NavigationGrid grid = layout.map().grid;
        // Between the two crossings, on the rille's line.
        assertFalse(grid.isWalkable(82, 23));
        assertTrue(grid.isSeeThrough(82, 23));
        for (BlindWatchSounding.Cell crossing : layout.rilleCrossings()) assertTrue(grid.isWalkable(crossing.x(), crossing.y()));
    }

    @Test
    void theMortarCarrierAndTheRocketTruckHaveLanesTheirHullsCanDrive() {
        BlindWatchSounding.Layout layout = BlindWatchSounding.generate(7150, WARION);
        NavigationGrid grid = layout.map().grid;
        TerrainCostField cost = TerrainCostField.from(layout.map().topology);
        assertDrivable(grid, cost, VehicleType.FIRE_SUPPORT_APC, layout.mortarLane());
        // The rocket truck drives up the knoll's ramp to every one of its stands.
        for (BlindWatchSounding.Cell stand : layout.technicalStands())
            assertDrivable(grid, cost, VehicleType.SRM_TECHNICAL, List.of(layout.knollRamp(), stand));
    }

    private static void assertDrivable(NavigationGrid grid, TerrainCostField cost, VehicleType type,
                                       List<BlindWatchSounding.Cell> stops) {
        VehicleClearance clearance = VehicleClearance.erode(grid, VehicleClearance.radiusForWidth(type.visualWidthCells));
        for (int i = 1; i < stops.size(); i++) {
            BlindWatchSounding.Cell a = stops.get(i - 1), b = stops.get(i);
            assertTrue(clearance.isPassable(a.x(), a.y()), type + " does not fit at " + a);
            assertTrue(clearance.isPassable(b.x(), b.y()), type + " does not fit at " + b);
            assertNotNull(VehicleRoutePlanner.routeDrivable(a.x(), a.y(), b.x(), b.y(), grid, cost, clearance, type),
                    type + " has no lane from " + a + " to " + b);
        }
    }
}
