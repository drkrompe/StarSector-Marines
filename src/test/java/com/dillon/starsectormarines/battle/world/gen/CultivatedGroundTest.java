package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The colony's lawn grows, and its dirt lots stay dirt, only where the planet grows things ({@link CultivatedGround}). */
class CultivatedGroundTest {

    private static final int W = 40;
    private static final int H = 24;

    /** A lawn with a paved path through it: every cell grass but one row of stone. */
    private static CellTopology lawn() {
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                topology.setGroundKind(x, y, y == H / 2 ? GroundKind.STONE : GroundKind.GRASS);
            }
        }
        return topology;
    }

    private static int count(CellTopology topology, GroundKind kind) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (topology.getGroundKind(x, y) == kind) n++;
        return n;
    }

    private static CellTopology settled(SurfacePalette world, Basin basin) {
        CellTopology topology = lawn();
        CultivatedGround.settle(topology, new SurfaceField(world, basin, W, H, 7), CultivatedGround.MadeGround.NONE);
        return topology;
    }

    @Test
    void aLivingWorldKeepsItsLawn() {
        CellTopology topology = settled(SurfacePalette.VERDANT, Basin.NONE);
        assertEquals(W * (H - 1), count(topology, GroundKind.GRASS));
    }

    @Test
    void aDeadWorldsLawnIsTheWorldsOwnGround() {
        GroundKind[] grounds = {GroundKind.SAND, GroundKind.STONE, GroundKind.SNOW};
        SurfacePalette[] worlds = {SurfacePalette.ARID, SurfacePalette.ROCK, SurfacePalette.FROZEN};
        for (int i = 0; i < worlds.length; i++) {
            CellTopology topology = settled(worlds[i], Basin.NONE);
            assertEquals(0, count(topology, GroundKind.GRASS), worlds[i] + " grows no lawn");
            int path = grounds[i] == GroundKind.STONE ? W : 0;
            assertEquals(W * (H - 1) + path, count(topology, grounds[i]), worlds[i] + "'s lawn is its own ground");
        }
        assertEquals(W, count(settled(SurfacePalette.ARID, Basin.NONE), GroundKind.STONE),
                "ground that was not lawn is untouched");
    }

    @Test
    void aCellWithAnAuthoredSurfaceKeepsItsGround() {
        CellTopology topology = lawn();
        topology.setSurface(3, 3, 1);
        CultivatedGround.settle(topology, new SurfaceField(SurfacePalette.ROCK, Basin.NONE, W, H, 7),
                CultivatedGround.MadeGround.NONE);
        assertEquals(GroundKind.GRASS, topology.getGroundKind(3, 3), "a farm's surfaced field is left as laid");
        assertEquals(GroundKind.STONE, topology.getGroundKind(4, 3));
    }

    @Test
    void aLivingBasinOnADesertKeepsItsLawnAndItsRimDoesNot() {
        SurfaceField field = new SurfaceField(SurfacePalette.ARID, new Basin(SurfacePalette.VERDANT, .6f, .6f), W, H, 7);
        CellTopology topology = lawn();
        CultivatedGround.settle(topology, field, CultivatedGround.MadeGround.NONE);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (y == H / 2) continue;
                GroundKind expected = field.at(x, y) == SurfacePalette.VERDANT ? GroundKind.GRASS : GroundKind.SAND;
                assertEquals(expected, topology.getGroundKind(x, y), "cell " + x + "," + y);
            }
        }
        assertEquals(GroundKind.GRASS, topology.getGroundKind(W / 2, H / 2 - 1), "the basin's middle is lawn");
        assertEquals(GroundKind.SAND, topology.getGroundKind(0, 0), "the desert corner is not");
    }

    @Test
    void anUnworkedLotIsTheWorldsGroundAndDugEarthKeepsItsDirt() {
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) topology.setGroundKind(x, y, GroundKind.DIRT);
        topology.setSurface(5, 5, topology.surfaceIndex("floors.dirt"));
        CultivatedGround.settle(topology, new SurfaceField(SurfacePalette.FROZEN, Basin.NONE, W, H, 7),
                (x, y) -> x == 9 && y == 9);
        assertEquals(GroundKind.SNOW, topology.getGroundKind(0, 0), "an open lot is the world's snow");
        assertEquals(GroundKind.DIRT, topology.getGroundKind(9, 9), "made ground keeps its dirt");
        assertEquals(GroundKind.DIRT, topology.getGroundKind(5, 5), "worked earth keeps its dirt");

        CellTopology living = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) living.setGroundKind(x, y, GroundKind.DIRT);
        CultivatedGround.settle(living, new SurfaceField(SurfacePalette.VERDANT, Basin.NONE, W, H, 7),
                CultivatedGround.MadeGround.NONE);
        assertEquals(W * H, count(living, GroundKind.DIRT), "a living world's lots stay dirt");
    }
}
