package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RoadSurfaceStageTest {
    @Test void finishingRoadsPreservesPlacementSemanticsAndProtectsStructuresAndPads() {
        var grid = new NavigationGrid(32, 24);
        var topology = new CellTopology(32, 24);
        var ctx = new GenContext(grid, topology, new Random(42), 32, 24, 42);
        int[][] road = new int[32][24], claim = new int[32][24];
        for (int y = 0; y < 24; y++) for (int x = 0; x < 32; x++) {
            road[x][y] = y >= 8 && y <= 12 ? 0 : -1;
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, road[x][y] == 0 ? GroundKind.STREET : GroundKind.STONE);
            topology.setSurface(x, y, topology.surfaceIndex(road[x][y] == 0
                    ? "pavement.asphalt.worn" : "pavement.concrete.worn"));
        }
        ctx.put(BspKeys.PRECINCT_ROAD, road);
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
        grid.setWalkable(10, 10, false);
        grid.setWallHp(10, 10, 900);
        topology.setWall(10, 10, true);
        topology.setSurface(10, 10, topology.surfaceIndex("wall.authored"));
        grid.setDoorway(11, 10, true);
        topology.setGroundKind(11, 10, GroundKind.STRIPED);
        ctx.markMadeGround(12, 10);
        topology.setSurface(12, 10, topology.surfaceIndex("pad.authored"));
        topology.setGroundKind(13, 10, GroundKind.INDOOR);
        // Even a stale pavement override cannot turn an interior into a road.
        var kinds = topology.copyGroundKinds();
        int[][] materials = new int[32][24];
        for (int y = 0; y < 24; y++) for (int x = 0; x < 32; x++) materials[x][y] = topology.getSurface(x, y);

        new RoadSurfaceStage().run(ctx);

        assertNotNull(topology.roadSurface());
        assertNotNull(topology.roadSurface().patch(16, 10, 0));
        for (int x = 10; x <= 13; x++) {
            assertNull(topology.roadSurface().underlay(x, 10));
            assertNull(topology.roadSurface().patch(x, 10, 0));
        }
        assertArrayEquals(kinds, topology.copyGroundKinds());
        for (int y = 0; y < 24; y++) for (int x = 0; x < 32; x++) {
            assertEquals(materials[x][y], topology.getSurface(x, y));
            assertEquals(x != 10 || y != 10, grid.isWalkable(x, y));
        }
        assertEquals(900, grid.getWallHp(10, 10));
        assertTrue(grid.isDoorway(11, 10));
        assertEquals(new Random(42).nextLong(), ctx.rng.nextLong());
        var surface = topology.roadSurface();
        new RoadSurfaceStage().run(ctx);
        assertSame(surface, topology.roadSurface(), "never replace an existing road surface");
    }

    /** A street five wide along a building: asphalt over the carriageway, paving left at the kerb. */
    private static GenContext city() {
        var grid = new NavigationGrid(40, 30);
        var topology = new CellTopology(40, 30);
        var ctx = new GenContext(grid, topology, new Random(7), 40, 30, 7);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 40; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, y >= 4 && y <= 8 ? GroundKind.STREET : GroundKind.GRASS);
        }
        for (int x = 0; x < 40; x++) { grid.setWalkable(x, 9, false); topology.setWall(x, 9, true); }
        // A square of open paving, broader than any street.
        for (int y = 14; y <= 27; y++) for (int x = 10; x <= 25; x++) topology.setGroundKind(x, y, GroundKind.STREET);
        return ctx;
    }

    @Test void cityStreetsTakeAsphaltOverTheCarriagewayOnly() {
        var ctx = city();
        var kinds = ctx.topology.copyGroundKinds();

        new RoadSurfaceStage().run(ctx);

        var surface = ctx.topology.roadSurface();
        assertNotNull(surface);
        assertNotNull(surface.patch(20, 6, 0), "the carriageway is swept");
        assertNotNull(surface.underlay(20, 6));
        assertTrue(ctx.topology.hasTag(20, 6, CellTopology.Tag.ROAD_EDGE_BLEND));
        assertEquals(0, area(surface.patch(20, 8, 0)), 1e-6, "no asphalt on the kerb beside the wall");
        assertTrue(concrete(ctx, surface, 20, 8), "the kerb is the sidewalk");
        assertEquals(0, area(surface.patch(20, 4, 0)), 1e-6, "beside grass the sidewalk comes out of the street");
        assertTrue(concrete(ctx, surface, 20, 4));
        assertNull(surface.patch(20, 4, 1), "a sidewalk is ground, never a second painted layer");
        assertNull(surface.underlay(20, 2), "grass beyond the sidewalk keeps its own ground");
        assertFalse(concrete(ctx, surface, 18, 20), "a square away from the road keeps its paving");
        assertNull(surface.patch(18, 20, 0), "a square is not a street");
        assertArrayEquals(kinds, ctx.topology.copyGroundKinds());
        assertEquals(0, ctx.topology.getSurface(20, 6), "a finish, not a repaving");
        assertEquals(new Random(7).nextLong(), ctx.rng.nextLong());
    }

    @Test void aStreetIsPaddedWithSidewalkOutToWhateverItMeets() {
        var ctx = city();
        new RoadSurfaceStage().run(ctx);
        var surface = ctx.topology.roadSurface();
        // The street's paving runs on two cells past its kerb, up to a marked apron.
        for (int x = 0; x < 40; x++) {
            ctx.grid.setWalkable(x, 9, true);
            ctx.topology.setWall(x, 9, false);
            ctx.topology.setGroundKind(x, 9, GroundKind.STREET);
            ctx.topology.setGroundKind(x, 10, GroundKind.STREET);
            ctx.topology.setGroundKind(x, 11, GroundKind.STRIPED);
        }
        boolean[][] footway = new boolean[40][30];
        for (int x = 0; x < 40; x++) for (int y = 0; y < 30; y++) footway[x][y] = RoadSurfaceStage.footway(ctx, x, y);

        var sidewalk = RoadSurfaceStage.sidewalks(ctx, surface, footway);

        for (int y = 8; y <= 10; y++) {
            assertNotNull(sidewalk[20][y], "a bare paving strip at " + y);
            assertTrue(ctx.topology.surfaceId(sidewalk[20][y].surface()).startsWith("pavement.concrete."));
        }
        assertNull(sidewalk[20][11], "the apron keeps its own ground");
    }

    @Test void cityRoadsSwitchIsTheControl() {
        var ctx = city();
        System.setProperty("battle.mapgen.cityRoads", "false");
        try {
            new RoadSurfaceStage().run(ctx);
        } finally {
            System.clearProperty("battle.mapgen.cityRoads");
        }
        assertNull(ctx.topology.roadSurface());
    }

    @Test void aSideStreetCrossesTheBoulevardsFlankToReachIt() {
        var grid = new NavigationGrid(48, 40);
        var topology = new CellTopology(48, 40);
        var ctx = new GenContext(grid, topology, new Random(5), 48, 40, 5);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 48; x++) {
            grid.setWalkableFloor(x, y);
            GroundKind kind = GroundKind.GRASS;
            if (y >= 10 && y <= 12) kind = GroundKind.STREET;                 // boulevard core
            else if (y == 13 || y == 14) kind = GroundKind.SIDEWALK;          // its flank
            else if (y >= 15 && x >= 20 && x <= 24) kind = GroundKind.STREET; // side street
            topology.setGroundKind(x, y, kind);
        }

        new RoadSurfaceStage().run(ctx);

        var surface = ctx.topology.roadSurface();
        assertNotNull(surface.patch(22, 13, 0), "the mouth crosses the flank");
        assertNotNull(surface.patch(22, 14, 0));
        assertNull(surface.patch(35, 14, 0), "the flank elsewhere stays paved");
    }

    private static double area(RoadSurface.Patch patch) {
        return patch == null ? 0 : patch.area();
    }

    private static boolean concrete(GenContext ctx, RoadSurface surface, int x, int y) {
        var underlay = surface.underlay(x, y);
        String id = underlay == null ? null : ctx.topology.surfaceId(underlay.surface());
        return id != null && id.startsWith("pavement.concrete.");
    }
}
