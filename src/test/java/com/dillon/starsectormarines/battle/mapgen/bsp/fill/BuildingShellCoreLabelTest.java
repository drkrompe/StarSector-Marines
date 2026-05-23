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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildingShellCoreLabelTest {

    private static final int W = 30;
    private static final int H = 30;

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

    private static final BuildingShellCore.BuildingConfig TERNARY_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR, TileManifest.SKYPORT_DOODADS, PointOfInterest.Kind.COMMS,
                    BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.FORTIFIED,
                    new RoomPurpose[]{RoomPurpose.KEEP_THRONE, RoomPurpose.KEEP_INNER, RoomPurpose.KEEP_ENTRY},
                    new TernaryPartitionStrategy(1.0f));

    private static final BuildingShellCore.BuildingConfig TERNARY_FALLBACK_CONFIG =
            new BuildingShellCore.BuildingConfig(
                    GroundKind.INDOOR, TileManifest.SKYPORT_DOODADS, PointOfInterest.Kind.COMMS,
                    BuildingLayouts.LayoutRecipe.SHOP, BuildingKind.FORTIFIED,
                    new RoomPurpose[]{RoomPurpose.KEEP_THRONE, RoomPurpose.KEEP_INNER, RoomPurpose.KEEP_ENTRY},
                    new TernaryPartitionStrategy(1.0f));

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
    public void ternaryCarveStampsThreeChambers() {
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        // 18×10 building — long axis (18) qualifies for ternary (MIN_DIM_LONG=14)
        BlockLeaf b = leaf(2, 5, 19, 14);
        BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                new Random(42), TERNARY_CONFIG);

        int throne = 0, inner = 0, entry = 0;
        for (int y = 5; y <= 14; y++) {
            for (int x = 2; x <= 19; x++) {
                RoomPurpose p = topology.getRoomPurpose(x, y);
                if (p == RoomPurpose.KEEP_THRONE) throne++;
                if (p == RoomPurpose.KEEP_INNER) inner++;
                if (p == RoomPurpose.KEEP_ENTRY) entry++;
            }
        }
        assertTrue(throne >= 3, "ternary: throne has " + throne + " cells (expected ≥3)");
        assertTrue(inner >= 3, "ternary: inner has " + inner + " cells (expected ≥3)");
        assertTrue(entry >= 3, "ternary: entry has " + entry + " cells (expected ≥3)");
    }

    @Test
    public void ternaryRobustAcrossSeeds() {
        for (long seed = 0; seed < 50; seed++) {
            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            BlockLeaf b = leaf(2, 5, 19, 14);
            BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                    new Random(seed), TERNARY_CONFIG);

            int throne = 0, inner = 0, entry = 0;
            for (int y = 5; y <= 14; y++) {
                for (int x = 2; x <= 19; x++) {
                    RoomPurpose p = topology.getRoomPurpose(x, y);
                    if (p == RoomPurpose.KEEP_THRONE) throne++;
                    if (p == RoomPurpose.KEEP_INNER) inner++;
                    if (p == RoomPurpose.KEEP_ENTRY) entry++;
                }
            }
            assertTrue(throne >= 3, "seed " + seed + ": throne=" + throne);
            assertTrue(inner >= 3, "seed " + seed + ": inner=" + inner);
            assertTrue(entry >= 3, "seed " + seed + ": entry=" + entry);
        }
    }

    @Test
    public void ternaryFallsBackToBinaryWhenTooSmall() {
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        // 11×11 building — long axis (11) is below MIN_DIM_LONG (14),
        // so ternary falls back to binary. With [THRONE, INNER, ENTRY]
        // purposes, binary produces chambers at distance 0 (THRONE)
        // and distance 1 (INNER); ENTRY is never used.
        BlockLeaf b = leaf(4, 4, 14, 14);
        BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                new Random(0), TERNARY_FALLBACK_CONFIG);

        int throne = 0, inner = 0, entry = 0;
        for (int y = 4; y <= 14; y++) {
            for (int x = 4; x <= 14; x++) {
                RoomPurpose p = topology.getRoomPurpose(x, y);
                if (p == RoomPurpose.KEEP_THRONE) throne++;
                if (p == RoomPurpose.KEEP_INNER) inner++;
                if (p == RoomPurpose.KEEP_ENTRY) entry++;
            }
        }
        assertTrue(throne >= 3, "fallback: throne has " + throne + " cells (expected ≥3)");
        assertTrue(inner >= 3, "fallback: inner has " + inner + " cells (expected ≥3)");
        assertEquals(0, entry, "fallback to binary should not reach distance 2 (ENTRY)");
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

    @Test
    public void renderTernaryPreviewSheet() throws Exception {
        Path outDir = Path.of("build/zone-previews");
        Files.createDirectories(outDir);

        int cellPx = 12;
        int seeds = 8;
        int cols = 4;
        int rows = (seeds + cols - 1) / cols;
        // Each panel: building leaf(2,2,19,14) → grid 22×17, plus label row
        int panelW = 22;
        int panelH = 17;
        int gap = 2;
        int imgW = cols * (panelW * cellPx + gap * cellPx) - gap * cellPx;
        int imgH = rows * (panelH * cellPx + gap * cellPx + 16) - gap * cellPx;
        BufferedImage sheet = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(40, 40, 40));
        g.fillRect(0, 0, imgW, imgH);

        for (int i = 0; i < seeds; i++) {
            int col = i % cols;
            int row = i / cols;
            int ox = col * (panelW * cellPx + gap * cellPx);
            int oy = row * (panelH * cellPx + gap * cellPx + 16);

            NavigationGrid grid = openGrid();
            CellTopology topology = new CellTopology(W, H);
            BlockLeaf b = leaf(2, 2, 19, 14);
            BuildingShellCore.carve(b, grid, topology, new ArrayList<>(),
                    new Random(i), TERNARY_CONFIG);

            for (int cy = 0; cy < panelH; cy++) {
                for (int cx = 0; cx < panelW; cx++) {
                    Color c;
                    if (!grid.isWalkable(cx, cy)) {
                        c = new Color(30, 30, 30);
                    } else if (grid.isDoorway(cx, cy)) {
                        c = new Color(180, 140, 80);
                    } else {
                        RoomPurpose p = topology.getRoomPurpose(cx, cy);
                        if (p == RoomPurpose.KEEP_THRONE)     c = new Color(60, 90, 180);
                        else if (p == RoomPurpose.KEEP_INNER) c = new Color(200, 180, 60);
                        else if (p == RoomPurpose.KEEP_ENTRY) c = new Color(180, 60, 60);
                        else                                  c = new Color(120, 120, 120);
                    }
                    g.setColor(c);
                    g.fillRect(ox + cx * cellPx, oy + cy * cellPx, cellPx - 1, cellPx - 1);
                }
            }
            g.setColor(Color.WHITE);
            g.drawString("seed=" + i, ox, oy + panelH * cellPx + 12);
        }

        g.dispose();
        Path out = outDir.resolve("ternary-partition-labels.png");
        ImageIO.write(sheet, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }
}
