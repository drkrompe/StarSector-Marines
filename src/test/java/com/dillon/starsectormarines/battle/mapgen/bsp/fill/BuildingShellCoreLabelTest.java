package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.map.BuildingKind;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.map.RoomPurpose;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildingShellCoreLabelTest {

    private static final int W = 24;
    private static final int H = 24;

    private static final BuildingShellCore.BuildingConfig ALWAYS_PARTITION_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR, TileManifest.SKYPORT_DOODADS, PointOfInterest.Kind.COMMS,
                    BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.FORTIFIED,
                    new RoomPurpose[]{RoomPurpose.KEEP_THRONE, RoomPurpose.KEEP_ENTRY},
                    new BinaryPartitionStrategy(1.0f));

    private static final BuildingShellCore.BuildingConfig NEVER_PARTITION_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR, TileManifest.SKYPORT_DOODADS, PointOfInterest.Kind.COMMS,
                    BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.FORTIFIED,
                    new RoomPurpose[]{RoomPurpose.KEEP_THRONE, RoomPurpose.KEEP_ENTRY},
                    new BinaryPartitionStrategy(0.0f));

    private static final BuildingShellCore.BuildingConfig PLAIN_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR, TileManifest.RESIDENTIAL_DOODADS, PointOfInterest.Kind.RESIDENTIAL,
                    BuildingLayouts.LayoutRecipe.HOME, BuildingKind.RESIDENTIAL);

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static BlockLeaf leaf(int l, int t, int r, int b) {
        return new BlockLeaf(l, t, r, b, false);
    }

    @Test
    public void partitionedCarveStampsBothLabels() {
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        BlockLeaf b = leaf(4, 4, 16, 16);
        BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                new Random(0), ALWAYS_PARTITION_CONFIG);

        int throne = 0, entry = 0;
        for (int y = 4; y <= 16; y++) {
            for (int x = 4; x <= 16; x++) {
                RoomPurpose p = topology.getRoomPurpose(x, y);
                if (p == RoomPurpose.KEEP_THRONE) throne++;
                if (p == RoomPurpose.KEEP_ENTRY) entry++;
            }
        }
        assertTrue(throne >= 3, "throne chamber has " + throne + " labeled cells (expected ≥3)");
        assertTrue(entry >= 3, "entry chamber has " + entry + " labeled cells (expected ≥3)");
    }

    @Test
    public void singleRoomCarveStampsOnlyThrone() {
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        BlockLeaf b = leaf(4, 4, 16, 16);
        BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                new Random(0), NEVER_PARTITION_CONFIG);

        int throne = 0, entry = 0;
        for (int y = 4; y <= 16; y++) {
            for (int x = 4; x <= 16; x++) {
                RoomPurpose p = topology.getRoomPurpose(x, y);
                if (p == RoomPurpose.KEEP_THRONE) throne++;
                if (p == RoomPurpose.KEEP_ENTRY) entry++;
            }
        }
        assertEquals(0, entry, "single-room carve must not stamp any ENTRY labels");
        assertTrue(throne >= 20,
                "single-room carve should label most interior cells THRONE, got " + throne);
    }

    @Test
    public void plainConfigStampsNoLabels() {
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        BlockLeaf b = leaf(4, 4, 16, 16);
        BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                new Random(0), PLAIN_CONFIG);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(null, topology.getRoomPurpose(x, y),
                        "plain (no-purposes) config must never stamp labels — leaked at (" + x + "," + y + ")");
            }
        }
    }
}
