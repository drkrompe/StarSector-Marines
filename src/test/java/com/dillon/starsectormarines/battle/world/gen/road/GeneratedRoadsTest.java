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

    @Test void streetsRefuseBulgesAndPills() {
        boolean[][] road = new boolean[48][32];
        fill(road, 2, 10, 45, 12);   // a street three wide ...
        fill(road, 20, 13, 27, 14);  // ... widening to five where a frontage steps back
        fill(road, 10, 20, 13, 29);  // a short gap between buildings, touching nothing
        var streets = GeneratedRoads.streets(road, road, null, 3, 9, GeneratedRoads.StreetStyle.ROUND_BARE).routes();
        var straight = streets.stream().filter(r -> r.points().size() == 2).toList();
        assertEquals(1, straight.size(), "only the street itself: " + streets);
        assertEquals(3, straight.get(0).width());
        assertTrue(GeneratedRoads.routes(road, 3).stream().anyMatch(r -> r.width() == 5),
                "the plain sweep takes the bulge; streets do not");
        assertTrue(GeneratedRoads.routes(road, 3).stream().anyMatch(r -> r.width() == 4), "and the pill");
        assertEquals(GeneratedRoads.routes(road, 3), GeneratedRoads.streets(road, new boolean[48][32], null, 3, 9,
                        new GeneratedRoads.StreetStyle(true, true, true)).routes(),
                "roads holding no city street are swept as roads");
    }

    @Test void streetsEndSquareAndMakeRoomForASidewalk() {
        boolean[][] road = new boolean[40][24];
        fill(road, 4, 8, 35, 12);    // a street five wide, open ground either side
        var style = new GeneratedRoads.StreetStyle(true, true, false);
        var routes = GeneratedRoads.streets(road, road, new boolean[40][24], 3, 9, style).routes();
        assertEquals(1, routes.size(), "one asphalt sweep and nothing painted over it: " + routes);
        var asphalt = routes.get(0);
        assertEquals(Material.ASPHALT, asphalt.material());
        assertEquals(RoadPlan.EndCap.BUTT, asphalt.endCap(), "square, not a brush end");
        assertEquals(3, asphalt.width(), "with no footway beside it, the sidewalk comes out of the street");
        assertEquals(3.5, asphalt.points().get(0).x(), 1e-9, "flush with the band's first cell");

        boolean[][] kerbs = new boolean[40][24];
        fill(kerbs, 4, 7, 35, 13);   // a kerb row on both sides
        var footed = GeneratedRoads.streets(road, road, kerbs, 3, 9, style).routes();
        assertEquals(5, footed.get(0).width(), "a footway beside the street keeps its asphalt whole");
    }

    @Test void aCentreLineIsDashedAndBreaksAtEveryJunction() {
        boolean[][] road = new boolean[60][40];
        fill(road, 2, 18, 57, 22);   // a street five wide ...
        fill(road, 28, 2, 32, 37);   // ... crossed by another
        var paint = GeneratedRoads.streets(road, road, new boolean[60][40], 3, 9,
                new GeneratedRoads.StreetStyle(true, false, true)).markings();
        assertFalse(paint.isEmpty());
        for (var m : paint) {
            boolean along = m.a().y() == m.b().y();
            double lo = along ? Math.min(m.a().x(), m.b().x()) : Math.min(m.a().y(), m.b().y());
            double hi = along ? Math.max(m.a().x(), m.b().x()) : Math.max(m.a().y(), m.b().y());
            double crossLo = along ? 28 : 18, crossHi = along ? 32 : 22;
            assertTrue(hi <= crossLo - 1 || lo >= crossHi + 1, "no paint in the junction: " + m);
            assertEquals(along ? 20 : 30, along ? m.a().y() : m.a().x(), 1e-9, "on the centre line");
        }
    }
}
