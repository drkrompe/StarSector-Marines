package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Basin;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The world and the years on a dead site ({@link RuinsWeather}): look only, kept ground left alone, mouths first. */
class RuinsWeatherTest {

    private static final int W = 60, H = 20;
    /** The corridor's rows, running east from a mouth in the hull's west face at x = 20. */
    private static final int Y0 = 8, Y1 = 10, HULL = 20;
    private static final Area LANDING = new Area(0, 0, 5, H - 1);

    /** Open courtyard west of x = 20, solid hull east of it with one corridor carved through, floored in concrete. */
    private static CellTopology site(NavigationGrid grid) {
        CellTopology topology = new CellTopology(W, H);
        int floor = topology.surfaceIndex(RuinsWeather.INDOOR_FLOOR);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                topology.setGroundKind(x, y, GroundKind.COURTYARD);
                boolean corridor = x >= HULL && y >= Y0 && y <= Y1 && x < W - 1;
                boolean wall = x >= HULL && !corridor;
                topology.setWall(x, y, wall);
                grid.setWalkable(x, y, !wall);
                if (corridor) topology.setSurface(x, y, floor);
            }
        }
        return topology;
    }

    private static RuinsDelve delve() {
        Area door = new Area(HULL, Y0, HULL, Y1);
        RuinsDelve.Vault vault = new RuinsDelve.Vault(new Area(W - 4, 1, W - 2, 3), new Cell(W - 4, 2),
                new Cell(W - 3, 2), new Cell(W - 5, 2), new Cell(W - 3, 2));
        RuinsDelve.Interior interior = new RuinsDelve.Interior(RuinsDelve.INTERIOR, new Area(HULL, 0, W - 1, H - 1), 0,
                List.of(door), List.of());
        return new RuinsDelve(1, LANDING, List.of(), vault, List.of(), null, List.of(), null, null, interior);
    }

    private static int[] mouthDistance(CellTopology topology, NavigationGrid grid) {
        boolean[] kept = RuinsWeather.keptClear(delve(), W, H);
        return RuinsWeather.mouthDistance(topology, grid, delve(), new Area(HULL, 0, W - 1, H - 1),
                topology.surfaceIndex(RuinsWeather.INDOOR_FLOOR), kept);
    }

    @Test
    void weatherIsLookOnlyAndLeavesTheLandingBare() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = site(grid);
        List<Doodad> doodads = new ArrayList<>();
        RuinsWeather.Report report = RuinsWeather.apply(topology, grid, delve(),
                new SurfaceField(SurfacePalette.FROZEN, Basin.NONE, W, H, 7), null, doodads);
        assertTrue(report.reclaimed() > 0, "the world takes some of the dead town back");
        assertTrue(report.blownIn() > 0, "the world blows in through the mouth");
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean corridor = x >= HULL && y >= Y0 && y <= Y1 && x < W - 1;
                assertEquals(GroundKind.COURTYARD, topology.getGroundKind(x, y), "the ground kind is what it was");
                assertEquals(x >= HULL && !corridor, topology.isWall(x, y), "no wall moves");
                assertEquals(!(x >= HULL && !corridor), grid.isWalkable(x, y), "walkability is what it was");
                if (LANDING.contains(x, y)) assertNull(topology.getSurfaceId(x, y), "the landing ground stays bare");
            }
        }
        assertTrue(doodads.isEmpty(), "no registry, no decals");
    }

    @Test
    void theWorldBlowsInOnlyAsFarAsItsReach() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = site(grid);
        int[] dist = mouthDistance(topology, grid);
        RuinsWeather.apply(topology, grid, delve(), new SurfaceField(SurfacePalette.FROZEN, Basin.NONE, W, H, 7),
                null, new ArrayList<>());
        String blown = RuinsWeather.blowIn(SurfacePalette.FROZEN);
        boolean near = false;
        for (int y = Y0; y <= Y1; y++) {
            for (int x = HULL + 1; x < W - 1; x++) {
                if (!blown.equals(topology.getSurfaceId(x, y))) continue;
                assertTrue(dist[y * W + x] <= RuinsWeather.REACH, "no drift past the reach at " + x + "," + y);
                near = true;
            }
        }
        assertTrue(near, "a drift lies inside the mouth");
    }

    @Test
    void everyWorldHasItsGround() {
        for (SurfacePalette world : SurfacePalette.values()) {
            assertNotNull(RuinsWeather.reclaim(world), world.name());
            assertNotNull(RuinsWeather.blowIn(world), world.name());
            assertNotNull(RuinsWeather.bank(world), world.name());
            assertNotNull(RuinsWeather.damp(world), world.name());
            assertNotNull(RuinsWeather.tongue(world), world.name());
        }
    }
}
