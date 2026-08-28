package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Packs the fortress program into a bare envelope and draws the result, so the
 * interior can be judged before any wall is put round it.
 *
 * <p>A schematic rather than a sprite render: the question here is whether the
 * program lands in coherent wards with roadways that reach it, and tile art
 * would make that harder to see rather than easier.
 */
class FortressInteriorPreviewTest {

    private static final Path OUT_DIR = Paths.get("build/map-previews");
    /** Envelope sized from the program, not chosen for it. See FortressProgram#envelopeArea. */
    private static final int W;
    private static final int H;
    private static final int CELL_PX = 9;
    /** Deeper than it is wide, so wards read as bands across the attacker's approach. */
    private static final float ASPECT = 1.35f;

    static {
        int ground = FortressProgram.envelopeArea(FortressProgram.garrison());
        int side = (int) Math.ceil(Math.sqrt(ground / ASPECT));
        W = (int) Math.ceil(side * ASPECT) + 4;
        H = side + 4;
    }

    private static final Map<RoomPurpose, Color> KEY = Map.ofEntries(
            Map.entry(RoomPurpose.KEEP_THRONE, new Color(0xB0, 0x3A, 0x3A)),
            Map.entry(RoomPurpose.ARMORY, new Color(0xC8, 0x7A, 0x28)),
            Map.entry(RoomPurpose.ENGINE_ROOM, new Color(0x7A, 0x4A, 0xA8)),
            Map.entry(RoomPurpose.VEHICLE_BAY, new Color(0xD8, 0x8B, 0x2F)),
            Map.entry(RoomPurpose.KEEP_ENTRY, new Color(0xE0, 0xC8, 0x40)),
            Map.entry(RoomPurpose.CONTROL_ROOM, new Color(0x50, 0xA0, 0xC0)),
            Map.entry(RoomPurpose.BARRACKS, new Color(0x40, 0x90, 0x58)),
            Map.entry(RoomPurpose.MESS_HALL, new Color(0x60, 0xB0, 0x80)),
            Map.entry(RoomPurpose.PARTS_CAGE, new Color(0x88, 0x88, 0x50)),
            Map.entry(RoomPurpose.STOCKROOM, new Color(0x70, 0x70, 0x90)));

    @Test
    void packFortressInterior() throws Exception {
        Files.createDirectories(OUT_DIR);
        int worstUnplaced = 0;
        for (long seed : new long[] { 1L, 5L, 9L }) {
            NavigationGrid grid = new NavigationGrid(W, H);
            CellTopology topology = new CellTopology(W, H);
            GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);

            boolean[][] buildable = new boolean[W][H];
            for (int x = 2; x < W - 2; x++) {
                for (int y = 2; y < H - 2; y++) buildable[x][y] = true;
            }
            // One approach from the attacker's side, standing in for the gate the
            // wall will later be given. Everything else is the packer's to shape.
            boolean[][] muster = new boolean[W][H];
            for (int y = 2; y < 10; y++) {
                muster[W / 2][y] = true;
                muster[W / 2 + 1][y] = true;
            }

            FortressInterior.Result result = FortressInterior.pack(
                    ctx, buildable, muster, TraversalAxis.SOUTH_TO_NORTH,
                    FortressProgram.garrison());
            worstUnplaced = Math.max(worstUnplaced, result.unplaced().size());

            System.out.println("  seed " + seed + " placed " + result.placed().size()
                    + " unplaced " + result.unplaced().size());
            ImageIO.write(draw(grid, topology, result), "PNG",
                    OUT_DIR.resolve("fortress-interior-" + seed + ".png").toFile());
        }
        // The envelope is sized from the program, so a building left over is a
        // sizing defect rather than bad luck with a seed.
        assertEquals(0, worstUnplaced,
                "an envelope sized from the program must hold all of it");
    }

    private static BufferedImage draw(NavigationGrid grid, CellTopology topology,
                                      FortressInterior.Result result) {
        BufferedImage img = new BufferedImage(W * CELL_PX, H * CELL_PX, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x1A, 0x1C, 0x20));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        // Ground first, told apart so the made-up roadways read against the yard
        // they cross rather than merging into one grey field.
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (!grid.isWalkable(x, y)) continue;
                g.setColor(topology.getGroundKind(x, y) == CellTopology.GroundKind.STONE
                        ? new Color(0x55, 0x5A, 0x64)
                        : new Color(0x33, 0x30, 0x2A));
                g.fillRect(x * CELL_PX, flip(y), CELL_PX, CELL_PX);
            }
        }
        for (RoomPacker.Placed room : result.placed()) {
            g.setColor(KEY.getOrDefault(room.purpose(), Color.GRAY));
            for (int[] cell : room.shape().filled()) {
                g.fillRect((room.originX() + cell[0]) * CELL_PX, flip(room.originY() + cell[1]),
                        CELL_PX, CELL_PX);
            }
            g.setColor(Color.WHITE);
            for (var door : room.doors()) {
                g.fillRect(door.x() * CELL_PX, flip(door.y()), CELL_PX, CELL_PX);
            }
        }
        g.dispose();
        return img;
    }

    /** Draw cell y=0 along the bottom, matching the battle renderer. */
    private static int flip(int y) {
        return (H - 1 - y) * CELL_PX;
    }
}
