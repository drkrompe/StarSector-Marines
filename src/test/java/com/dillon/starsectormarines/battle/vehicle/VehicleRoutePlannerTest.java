package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-1 acceptance for {@link VehicleRoutePlanner} and the {@link GridPathfinder}
 * cost-field overload that backs it.
 *
 * <p>Two concerns, tested separately:
 * <ul>
 *   <li><b>Cost bias</b> (the A* overload) — roads are the cheap baseline, so the
 *       search hugs them, crosses open ground only when the distance win pays for
 *       the dearer terrain, and never threads a gap the clearance mask excludes.
 *       Asserted on the raw cell path so cost behaviour isn't muddied by the
 *       (deliberately cost-blind, slice-4) string-pull.</li>
 *   <li><b>String-pull</b> (the polyline) — collapses to endpoints on a straight
 *       run, keeps a vertex at a real bend, and every returned segment stays
 *       inside the clearance mask (no corner cut through a sub-clearance cell).</li>
 * </ul>
 */
public class VehicleRoutePlannerTest {

    // ----- fixture helpers -----

    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                grid.setWalkableFloor(x, y);
    }

    private static void fillKind(CellTopology topo, GroundKind kind) {
        for (int y = 0; y < topo.getHeight(); y++)
            for (int x = 0; x < topo.getWidth(); x++)
                topo.setGroundKind(x, y, kind);
    }

    private static void paintKind(CellTopology topo, int x0, int y0, int x1, int y1, GroundKind kind) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                topo.setGroundKind(x, y, kind);
    }

    private static boolean pathContainsKind(int[] path, CellTopology topo, GroundKind kind) {
        for (int i = 0; i < path.length; i += 2)
            if (topo.getGroundKind(path[i], path[i + 1]) == kind) return true;
        return false;
    }

    private static boolean pathAllKind(int[] path, CellTopology topo, GroundKind kind) {
        for (int i = 0; i < path.length; i += 2)
            if (topo.getGroundKind(path[i], path[i + 1]) != kind) return false;
        return path.length > 0;
    }

    /** Fine-sample every segment of the polyline; true if any sample cell is (x, y). */
    private static boolean routeCovers(float[][] poly, int cellX, int cellY) {
        float[] xs = poly[0], ys = poly[1];
        for (int i = 0; i < xs.length - 1; i++) {
            float ax = xs[i], ay = ys[i], bx = xs[i + 1], by = ys[i + 1];
            float dist = (float) Math.hypot(bx - ax, by - ay);
            int steps = Math.max(1, (int) Math.ceil(dist / 0.05f));
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                int cx = (int) Math.floor(ax + (bx - ax) * t);
                int cy = (int) Math.floor(ay + (by - ay) * t);
                if (cx == cellX && cy == cellY) return true;
            }
        }
        return false;
    }

    /** Every cell any segment of the polyline crosses must be vehicle-passable. */
    private static void assertRouteClear(float[][] poly, VehicleClearance clearance) {
        float[] xs = poly[0], ys = poly[1];
        for (int i = 0; i < xs.length - 1; i++) {
            float ax = xs[i], ay = ys[i], bx = xs[i + 1], by = ys[i + 1];
            float dist = (float) Math.hypot(bx - ax, by - ay);
            int steps = Math.max(1, (int) Math.ceil(dist / 0.05f));
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                int cx = (int) Math.floor(ax + (bx - ax) * t);
                int cy = (int) Math.floor(ay + (by - ay) * t);
                assertTrue(clearance.isPassable(cx, cy),
                        "segment " + i + " crosses sub-clearance cell (" + cx + "," + cy + ")");
            }
        }
    }

    /**
     * Border-road city: full-walkable {@code 12×14}, base GRASS, a road that runs
     * up the left column, across {@code topRow}, and down the right column. The
     * bottom row is a straight grass shortcut between the two bottom corners
     * (which are road). Smaller {@code topRow} = a longer road detour.
     */
    private static Object[] borderRoadCity(int topRow) {
        NavigationGrid grid = new NavigationGrid(12, 14);
        carve(grid, 0, 0, 11, 13);
        CellTopology topo = new CellTopology(12, 14);
        fillKind(topo, GroundKind.GRASS);
        paintKind(topo, 1, topRow, 1, 12, GroundKind.STREET);   // left column
        paintKind(topo, 1, topRow, 10, topRow, GroundKind.STREET); // top row
        paintKind(topo, 10, topRow, 10, 12, GroundKind.STREET); // right column
        return new Object[] { grid, topo };
    }

    // ----- cost bias (A* overload) -----

    @Test
    public void crossesOpenGroundWhenTheShortcutPaysForItself() {
        Object[] f = borderRoadCity(2); // deep detour: road ~29, grass shortcut ~25
        NavigationGrid grid = (NavigationGrid) f[0];
        CellTopology topo = (CellTopology) f[1];
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        int[] path = GridPathfinder.findPath(grid, 1, 12, 10, 12, cost.costArray(), clr.passableArray());

        assertTrue(path.length >= 4, "a route must exist");
        assertTrue(pathContainsKind(path, topo, GroundKind.GRASS),
                "a large shortcut should make the search cross open ground");
    }

    @Test
    public void staysOnRoadWhenTheShortcutIsOnlyMarginal() {
        Object[] f = borderRoadCity(10); // shallow detour: road ~13, grass shortcut ~25
        NavigationGrid grid = (NavigationGrid) f[0];
        CellTopology topo = (CellTopology) f[1];
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        int[] path = GridPathfinder.findPath(grid, 1, 12, 10, 12, cost.costArray(), clr.passableArray());

        assertTrue(path.length >= 4, "a route must exist");
        assertTrue(pathAllKind(path, topo, GroundKind.STREET),
                "a marginal shortcut shouldn't lure the search off the road");
    }

    // ----- string-pull geometry (route) -----

    @Test
    public void straightRoadCollapsesToEndpoints() {
        NavigationGrid grid = new NavigationGrid(12, 5);
        carve(grid, 0, 0, 11, 4);
        CellTopology topo = new CellTopology(12, 5);
        fillKind(topo, GroundKind.GRASS);
        paintKind(topo, 0, 2, 11, 2, GroundKind.STREET);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        float[][] poly = VehicleRoutePlanner.route(1, 2, 10, 2, grid, cost, clr);

        assertNotNull(poly);
        assertEquals(2, poly[0].length, "a straight run should string-pull to just its endpoints");
        assertTrue(routeCovers(poly, 5, 2), "the segment should run along the road");
        assertRouteClear(poly, clr);
    }

    @Test
    public void bendAroundBlockKeepsACorner() {
        NavigationGrid grid = new NavigationGrid(11, 11);
        carve(grid, 0, 0, 10, 10);
        // Knock a solid 3×3 block out of the middle — the S→G diagonal can't go straight.
        for (int y = 4; y <= 6; y++)
            for (int x = 4; x <= 6; x++)
                grid.setWalkable(x, y, false);
        CellTopology topo = new CellTopology(11, 11);
        fillKind(topo, GroundKind.GRASS);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        float[][] poly = VehicleRoutePlanner.route(1, 1, 9, 9, grid, cost, clr);

        assertNotNull(poly);
        assertTrue(poly[0].length >= 3, "a route bending around the block needs an interior vertex");
        assertFalse(routeCovers(poly, 5, 5), "the route must not cut through the block");
        assertRouteClear(poly, clr);
    }

    // ----- clearance gating -----

    @Test
    public void oneWideCrackGivesNoRouteForAVehicleThatCantFit() {
        // Two rooms joined only by a 1-wide crack.
        NavigationGrid grid = new NavigationGrid(11, 7);
        carve(grid, 1, 1, 3, 5);   // room A
        carve(grid, 7, 1, 9, 5);   // room B
        carve(grid, 4, 3, 6, 3);   // 1-wide crack on row 3
        CellTopology topo = new CellTopology(11, 7);
        fillKind(topo, GroundKind.GRASS);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 1);

        assertNull(VehicleRoutePlanner.route(2, 3, 8, 3, grid, cost, clr),
                "a 1-wide crack is impassable at radius 1, so there is no route");
    }

    @Test
    public void routesThroughTheWideCorridorNotTheCrack() {
        // Rooms joined by a 3-wide corridor up top AND a 1-wide crack in the middle.
        NavigationGrid grid = new NavigationGrid(11, 11);
        carve(grid, 1, 1, 3, 9);   // room A
        carve(grid, 7, 1, 9, 9);   // room B
        carve(grid, 4, 1, 6, 3);   // 3-wide corridor (rows 1..3)
        carve(grid, 4, 6, 6, 6);   // 1-wide crack (row 6)
        CellTopology topo = new CellTopology(11, 11);
        fillKind(topo, GroundKind.GRASS);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 1);

        float[][] poly = VehicleRoutePlanner.route(2, 8, 8, 8, grid, cost, clr);

        assertNotNull(poly, "the wide corridor offers a clearance-respecting route");
        assertRouteClear(poly, clr);
        assertFalse(routeCovers(poly, 5, 6), "the route must avoid the 1-wide crack");
    }

    @Test
    public void avoidingRegionForcesADetour() {
        // Two horizontal lanes (rows 2 and 8) joined at both ends by vertical
        // links, so start (1,2) → goal (9,2) can go straight across row 2 OR
        // detour down to row 8 and back. Avoiding a disc on row 2 forces the lap.
        NavigationGrid grid = new NavigationGrid(12, 11);
        carve(grid, 1, 1, 10, 3);   // top lane (rows 1..3, so radius-0/centerline at row 2)
        carve(grid, 1, 7, 10, 9);   // bottom lane
        carve(grid, 1, 1, 3, 9);    // left link
        carve(grid, 8, 1, 10, 9);   // right link
        CellTopology topo = new CellTopology(12, 11);
        fillKind(topo, GroundKind.GRASS);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        float[][] direct = VehicleRoutePlanner.route(1, 2, 9, 2, grid, cost, clr);
        assertNotNull(direct);
        assertTrue(routeCovers(direct, 5, 2), "the unobstructed route runs straight across the top lane");

        float[][] detour = VehicleRoutePlanner.routeAvoiding(1, 2, 9, 2, grid, cost, clr, 5, 2, 2f);
        assertNotNull(detour, "a lap via the bottom lane exists");
        assertFalse(routeCovers(detour, 5, 2), "the re-route avoids the blocked spot");
        float maxY = 0f;
        for (float yy : detour[1]) maxY = Math.max(maxY, yy);
        assertTrue(maxY >= 7f, "the lap dips into the bottom lane (rows 7-9), was maxY=" + maxY);
        assertRouteClear(detour, clr);
    }

    @Test
    public void avoidingTheOnlyPathReturnsNull() {
        // Single straight corridor; blocking its middle disconnects the goal.
        NavigationGrid grid = new NavigationGrid(12, 5);
        carve(grid, 0, 1, 11, 3);
        CellTopology topo = new CellTopology(12, 5);
        fillKind(topo, GroundKind.GRASS);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        assertNull(VehicleRoutePlanner.routeAvoiding(1, 2, 10, 2, grid, cost, clr, 6, 2, 2f),
                "avoiding the only corridor leaves no route → null (caller gives up)");
    }

    @Test
    public void avoidingDiscClippingTheStartStillRoutes() {
        // The production shape: the avoid disc sits just ahead of the body and
        // clips the start cell. Without the start-exemption the cloned mask blanks
        // the start → findPath has no valid start → null and the truck can never
        // escape. With it, the start stays passable and the search laps around.
        NavigationGrid grid = new NavigationGrid(12, 12);
        carve(grid, 0, 0, 11, 11);
        CellTopology topo = new CellTopology(12, 12);
        fillKind(topo, GroundKind.GRASS);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        // Disc centered at (7,5) r=2 covers the start (5,5) (dx=2 → on the rim),
        // but the start's left/up/down neighbours stay clear to escape through.
        float[][] re = VehicleRoutePlanner.routeAvoiding(5, 5, 10, 5, grid, cost, clr, 7, 5, 2f);
        assertNotNull(re, "the start is exempted from the disc, so a lap exists");
        assertFalse(routeCovers(re, 7, 5), "the route still avoids the disc centre");
        assertRouteClear(re, clr);
    }

    @Test
    public void unreachableGoalGivesNoRoute() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        carve(grid, 1, 1, 2, 2); // isolated pocket
        carve(grid, 5, 5, 6, 6); // separate pocket
        CellTopology topo = new CellTopology(8, 8);
        fillKind(topo, GroundKind.GRASS);
        TerrainCostField cost = TerrainCostField.from(topo);
        VehicleClearance clr = VehicleClearance.erode(grid, 0);

        assertNull(VehicleRoutePlanner.route(1, 1, 6, 6, grid, cost, clr),
                "disconnected pockets have no route");
    }
}
