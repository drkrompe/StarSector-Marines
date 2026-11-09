package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The storm's ground banked against the windward face of a wall ({@link StormDrift}). */
class StormDriftTest {

    private static final int W = 30;
    private static final int H = 20;

    /** Paving everywhere with a north-south wall at x = 15; a street row at y = 2. */
    private static CellTopology yard() {
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            topology.setGroundKind(x, y, y == 2 ? GroundKind.STREET : GroundKind.COURTYARD);
        for (int y = 0; y < H; y++) topology.setWall(15, y, true);
        return topology;
    }

    private static SurfaceField world(SurfacePalette palette) {
        return new SurfaceField(palette, Basin.NONE, W, H, 7);
    }

    @Test
    void aDriftLiesOnTheWindwardFaceOnlyAsASkinOverTheGround() {
        CellTopology topology = yard();
        assertTrue(StormDrift.paint(topology, world(SurfacePalette.ARID), StormWind.Side.WEST) > 0);
        for (int y = 0; y < H; y++) {
            assertEquals("floors.sand", topology.getSurfaceId(14, y), "every windward cell against the wall drifts");
            for (int x = 0; x < 15 - StormDrift.MAX; x++) assertNull(topology.getSurfaceId(x, y), "no deeper than its most");
            for (int x = 16; x < W; x++) assertNull(topology.getSurfaceId(x, y), "the lee is clear");
            for (int x = 0; x < W; x++) if (x != 15)
                assertEquals(y == 2 ? GroundKind.STREET : GroundKind.COURTYARD, topology.getGroundKind(x, y),
                        "the ground under a drift is what it was");
        }
    }

    @Test
    void aStreetDriftsOnlyOnItsApronAndAnAuthoredSurfaceKeepsItsOwn() {
        CellTopology topology = yard();
        int concrete = topology.surfaceIndex("colony.concrete");
        topology.setSurface(14, 8, concrete);
        StormDrift.paint(topology, world(SurfacePalette.ARID), StormWind.Side.WEST);
        for (int x = 0; x < 15 - StormDrift.STREET_APRON; x++) assertNull(topology.getSurfaceId(x, 2), "carriageway clear");
        assertEquals("colony.concrete", topology.getSurfaceId(14, 8));
    }

    @Test
    void eachWorldDriftsItsOwnAndALivingWorldNone() {
        CellTopology frozen = yard();
        StormDrift.paint(frozen, world(SurfacePalette.FROZEN), StormWind.Side.WEST);
        assertEquals("floors.snow", frozen.getSurfaceId(14, 5));
        assertEquals(0, StormDrift.paint(yard(), world(SurfacePalette.VERDANT), StormWind.Side.WEST));
    }

    @Test
    void aFinishedRoadIsNeverDrifted() {
        CellTopology topology = yard();
        RoadSurface.Underlay[][] none = new RoadSurface.Underlay[W][H];
        RoadSurface.Underlay[][] road = new RoadSurface.Underlay[W][H];
        for (int x = 0; x < W; x++) road[x][2] = new RoadSurface.Underlay(GroundKind.STREET, 0, false, false);
        topology.setRoadSurface(RoadSurface.generated(List.of(), topology, none).withUnderlays(road));
        StormDrift.paint(topology, world(SurfacePalette.ARID), StormWind.Side.WEST);
        assertNull(topology.getSurfaceId(14, 2), "the road's asphalt against the wall stays asphalt");
        assertEquals("floors.sand", topology.getSurfaceId(14, 3), "the yard beside it drifts");
    }
}
