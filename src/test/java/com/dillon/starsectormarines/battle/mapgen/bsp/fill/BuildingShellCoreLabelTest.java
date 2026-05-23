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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end coverage for {@link BuildingShellCore}'s room-purpose
 * labeling pass. The {@link com.dillon.starsectormarines.battle.mapgen.bsp.KeepEntryChamberStamper}
 * test exercises the stamper with hand-stamped labels; this test
 * exercises the OTHER end of the contract — actually running
 * {@link BuildingShellCore#carve} with a labeling config and verifying
 * the labels land where the stamper expects them. Lives in the
 * {@code mapgen.bsp.fill} package so it can see the package-private
 * {@link BuildingShellCore.BuildingConfig} type.
 *
 * <p>Partition activation is probabilistic ({@code MULTI_ROOM_CHANCE}),
 * so the tests iterate seeds to find both partitioned and
 * non-partitioned outcomes and assert the contract holds in both.
 */
public class BuildingShellCoreLabelTest {

    private static final int W = 24;
    private static final int H = 24;

    /** Keep-COMMAND-equivalent config — same shape as {@code MilitaryBaseFiller.COMMAND_CONFIG}. */
    private static final BuildingShellCore.BuildingConfig COMMAND_LIKE_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR, TileManifest.SKYPORT_DOODADS, PointOfInterest.Kind.COMMS,
                    BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.FORTIFIED,
                    new RoomPurpose[]{RoomPurpose.KEEP_THRONE, RoomPurpose.KEEP_ENTRY});

    /** Plain residential-style config — no labeling. Asserts the null-purposes path stays no-op. */
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
        // Iterate seeds until a partition fires (MULTI_ROOM_CHANCE = 0.65,
        // expected within ~5 attempts). Once it fires, both THRONE and
        // ENTRY labels should be present, both should have ≥3 cells each
        // (above MIN_CHAMBER_CELLS), and they should be disjoint (no cell
        // gets both labels).
        for (long seed = 0; seed < 20; seed++) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            // Building large enough on both axes to qualify for partition.
            // 11×11 interior — partition rolls vertical or horizontal.
            BlockLeaf b = leaf(4, 4, 16, 16);
            BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                    new Random(seed), COMMAND_LIKE_CONFIG);

            int throne = 0, entry = 0;
            for (int y = 4; y <= 16; y++) {
                for (int x = 4; x <= 16; x++) {
                    RoomPurpose p = topology.getRoomPurpose(x, y);
                    if (p == RoomPurpose.KEEP_THRONE) throne++;
                    if (p == RoomPurpose.KEEP_ENTRY) entry++;
                }
            }
            if (throne > 0 && entry > 0) {
                assertTrue(throne >= 3,
                        "seed " + seed + ": throne chamber has " + throne + " labeled cells (expected ≥3)");
                assertTrue(entry >= 3,
                        "seed " + seed + ": entry chamber has " + entry + " labeled cells (expected ≥3)");
                return;
            }
        }
        fail("Tried 20 seeds without hitting a partitioned outcome — MULTI_ROOM_CHANCE seems broken");
    }

    @Test
    public void singleRoomCarveStampsOnlyThrone() {
        // Iterate seeds until the partition does NOT fire (35% chance per
        // call). With no partition, every walkable interior cell gets the
        // anchor-side purpose (KEEP_THRONE) and no ENTRY cells exist —
        // the stamper would emit nothing for this building.
        for (long seed = 0; seed < 20; seed++) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            BlockLeaf b = leaf(4, 4, 16, 16);
            BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                    new Random(seed), COMMAND_LIKE_CONFIG);

            int throne = 0, entry = 0;
            for (int y = 4; y <= 16; y++) {
                for (int x = 4; x <= 16; x++) {
                    RoomPurpose p = topology.getRoomPurpose(x, y);
                    if (p == RoomPurpose.KEEP_THRONE) throne++;
                    if (p == RoomPurpose.KEEP_ENTRY) entry++;
                }
            }
            if (entry == 0 && throne > 0) {
                // Found a no-partition seed. THRONE should cover most of the
                // interior — at least 20 cells in an 11×11 interior (a wide
                // margin for doorway exclusions + layout doodad cells, which
                // remain walkable but the labeler does include them).
                assertTrue(throne >= 20,
                        "seed " + seed + ": single-room carve should label most interior cells THRONE");
                return;
            }
        }
        fail("Tried 20 seeds without hitting a non-partitioned outcome — MULTI_ROOM_CHANCE seems stuck at 1.0");
    }

    @Test
    public void plainConfigStampsNoLabels() {
        // The default (5-arg) BuildingConfig leaves chamberPurposesByAnchorDistance
        // null. labelRooms must early-return and not touch any cells — every
        // interior cell remains label-null. Pins that the existing
        // residential / commercial / industrial / dense-block / dense-quarter /
        // gated-housing / military barracks-armory-vehicle-bay fillers stay
        // unlabeled (none of them pass purposes).
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
