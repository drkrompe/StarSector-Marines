package com.dillon.starsectormarines.battle.world.gen.road;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedRoadsTest {
    @Test void curveBoundsCannotEraseSeparatePavingOutsideTheActualSweep() {
        var turn = new Route("turn", List.of(new Point(5, 5), new Point(15, 5), new Point(15, 15)),
                5, 0, .4, 12, Material.ASPHALT);
        var allowed = new RoadSurface.Underlay[24][24];
        allowed[5][15] = new RoadSurface.Underlay(GroundKind.STREET, 77, false, false);
        var surface = RoadSurface.generated(List.of(turn), new CellTopology(24, 24), allowed);
        assertNull(surface.patch(5, 15, 0));
        assertNull(surface.underlay(5, 15), "an unrelated road keeps its original surface");
    }

    @Test void teeHasCurvedTurnsOnlyOnItsExistingArms() {
        boolean[][] road = new boolean[32][32];
        fill(road, 2, 8, 29, 12);
        fill(road, 13, 8, 17, 29);
        var routes = GeneratedRoads.routes(road, 12);
        assertEquals(routes, GeneratedRoads.routes(road, 12));
        assertEquals(2, routes.stream().filter(r -> r.points().size() == 2).count());
        assertEquals(2, routes.stream().filter(r -> r.points().size() > 2).count());
        for (var r : routes) for (var p : r.points()) assertTrue(p.y() >= 10, "no phantom southern arm");
        var raster = RoadRasterizer.rasterize(new RoadPlan(32, 32, routes));
        for (int x = 4; x <= 27; x++) assertTrue(raster.road(x, 10), "through carriageway");
        for (int y = 10; y <= 27; y++) assertTrue(raster.road(15, y), "branch stays connected");
        assertFalse(raster.road(15, 5));
        assertTrue(raster.road(12, 13), "curved turn fills the inside corner");
    }

    @Test void prunedGapRemainsUnpavedAndRoundEndsExposeTheirUnderlay() {
        boolean[][] road = new boolean[32][32];
        fill(road, 2, 8, 12, 12);
        fill(road, 20, 8, 29, 12);
        var topology = new CellTopology(32, 32);
        var allowed = new RoadSurface.Underlay[32][32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            if (road[x][y]) allowed[x][y] = new RoadSurface.Underlay(GroundKind.STREET, 77, false, false);
        var surface = RoadSurface.generated(GeneratedRoads.routes(road, 12), topology, allowed);
        assertNotNull(surface.underlay(2, 8), "even the clipped-off cap corner needs underlying ground");
        assertEquals(77, surface.underlay(2, 8).surface());
        var corner = surface.patch(2, 8, 0);
        assertNotNull(corner);
        assertTrue(area(corner.first()) + area(corner.second()) < .9,
                "the round cap does not leave a square asphalt corner");
        assertNotNull(surface.patch(7, 10, 0));
        for (int x = 13; x < 20; x++) assertNull(surface.underlay(x, 10));
    }

    @Test void protectedGroundCannotAcquirePavementEvenInsideACurve() {
        boolean[][] road = new boolean[32][32];
        fill(road, 2, 8, 29, 12);
        fill(road, 13, 8, 17, 29);
        var topology = new CellTopology(32, 32);
        var allowed = new RoadSurface.Underlay[32][32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            allowed[x][y] = new RoadSurface.Underlay(GroundKind.STONE, 0, false, false);
        allowed[12][13] = null;
        var surface = RoadSurface.generated(GeneratedRoads.routes(road, 12), topology, allowed);
        assertNull(surface.patch(12, 13, 0));
        assertNull(surface.underlay(12, 13));
    }

    private static void fill(boolean[][] road, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) road[x][y] = true;
    }

    private static double area(float[] p) {
        if (p == null) return 0;
        double sum = 0;
        for (int i = 0; i < p.length / 2; i++) {
            int j = (i + 1) % (p.length / 2);
            sum += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        return Math.abs(sum) / 2;
    }
}
