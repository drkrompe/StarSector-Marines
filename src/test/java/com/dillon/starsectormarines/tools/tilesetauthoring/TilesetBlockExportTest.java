package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wall authored from a raw sheet has to come out the other end resolving to
 * the art it was assigned to — which is only provable by asking a real
 * {@link TileRegistry} for each facing and looking at the packed pixels.
 */
class TilesetBlockExportTest {

    private static final int CELL = 8;
    private static final String BLOCK = "test.wall";
    private static final int FILL = 0x060A10;

    /** A distinct opaque colour per slot, so a cell can be traced back to its piece. */
    private static int colourOf(int index) {
        return 0xFF000000 | (0x101010 * (index + 1)) | (index + 1);
    }

    /** A sheet of eleven flat squares in a row: nine wall pieces, then two props. */
    private static BufferedImage sheet(int pieces) {
        BufferedImage image = new BufferedImage(pieces * CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < pieces; i++) {
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) {
                    image.setRGB(i * CELL + x, y, colourOf(i));
                }
            }
        }
        return image;
    }

    private static TilesetExport.Entry piece(int index, String id) {
        return new TilesetExport.Entry(
                new SheetSlicer.Piece(index * CELL, 0, CELL, CELL), id);
    }

    /** Nine pieces assigned to the nine slots, in the order a split plate arrives. */
    private static List<TilesetExport.Entry> wallMembers() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        List<String> slots = BlockSlots.of(GridLayout.WALL_3X3);
        for (int i = 0; i < slots.size(); i++) {
            TilesetExport.Entry entry = piece(i, "unused");
            entry.blockId = BLOCK;
            entry.slot = slots.get(i);
            entries.add(entry);
        }
        return entries;
    }

    private static int sample(BufferedImage atlas, int col, int row) {
        return atlas.getRGB(col * CELL + CELL / 2, row * CELL + CELL / 2);
    }

    @Test
    void everyFacingResolvesToThePieceItWasAssigned() throws Exception {
        List<TilesetExport.Entry> entries = wallMembers();
        TilesetExport.Entry crate = piece(9, "doodad.test.crate");
        crate.cover = "med";
        entries.add(crate);
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec(BLOCK, GridLayout.WALL_3X3, FILL));

        BufferedImage source = sheet(10);
        BufferedImage atlas = TilesetExport.atlas(source, entries, blocks, CELL);
        JSONObject tileset = TilesetExport.tileset("graphics/test.png", CELL, entries, blocks);

        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(tileset);
        registry.validateReferences();
        GridBlockDef block = registry.block(BLOCK);
        assertNotNull(block, "the authored block must load");
        assertEquals(FILL, block.fillRgb, "the fill colour survives the round trip");

        // Each flag means "the exterior is on this side" — the mask the game builds.
        assertEquals(colourOf(0), facing(atlas, block, true, false, false, true), "nw");
        assertEquals(colourOf(1), facing(atlas, block, true, false, false, false), "n");
        assertEquals(colourOf(2), facing(atlas, block, true, false, true, false), "ne");
        assertEquals(colourOf(3), facing(atlas, block, false, false, false, true), "w");
        assertEquals(colourOf(5), facing(atlas, block, false, false, true, false), "e");
        assertEquals(colourOf(6), facing(atlas, block, false, true, false, true), "sw");
        assertEquals(colourOf(7), facing(atlas, block, false, true, false, false), "s");
        assertEquals(colourOf(8), facing(atlas, block, false, true, true, false), "se");

        assertNull(block.resolve(true, true, true, true),
                "a fully enclosed wall cell falls to the block's fill, not to art");
    }

    private static int facing(BufferedImage atlas, GridBlockDef block,
                              boolean n, boolean s, boolean e, boolean w) {
        int[] cell = block.resolve(n, s, e, w);
        assertNotNull(cell, "this mask should resolve to art");
        return sample(atlas, cell[0], cell[1]);
    }

    /**
     * The export is the only place a doodad's combat data can be lost, and
     * losing it is invisible: the sheet still packs, every id still resolves,
     * and every prop quietly drops to its cover bucket's default silhouette.
     * So this asks a real registry what it got, rather than the JSON what it
     * wrote.
     */
    @Test
    void anAuthoredSilhouetteReachesTheLoadedDoodad() throws Exception {
        TilesetExport.Entry sofa = piece(0, "doodad.test.sofa");
        sofa.cover = "med";
        sofa.ballisticHalfHeight = 0.42;
        sofa.preferredWallSide = "N";
        TilesetExport.Entry crate = piece(1, "doodad.test.crate");
        crate.cover = "med";
        List<TilesetExport.Entry> entries = List.of(sofa, crate);

        JSONObject tileset =
                TilesetExport.tileset("graphics/test.png", CELL, entries, List.of());
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(tileset);

        assertEquals(0.42f, registry.doodad("doodad.test.sofa").ballisticHalfHeight, 1e-6f);
        assertEquals(DoodadDef.WallSide.N,
                registry.doodad("doodad.test.sofa").preferredWallSide);

        // And an unjudged piece still takes the bucket default rather than zero.
        assertEquals(DoodadCover.MED.defaultBallisticHalfHeight(),
                registry.doodad("doodad.test.crate").ballisticHalfHeight, 1e-6f);
        assertNull(registry.doodad("doodad.test.crate").preferredWallSide);
    }

    @Test
    void aBlockIsPackedAsOnePatchAndItsOriginIsThePackersAnswer() {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        // A doodad ahead of the block, so the patch cannot start at the origin.
        entries.add(piece(9, "doodad.test.crate"));
        entries.addAll(wallMembers());
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec(BLOCK, GridLayout.WALL_3X3, FILL));

        TilesetExport.Packing packing = TilesetExport.pack(entries, blocks);

        int[] origin = packing.blockOrigins().get(BLOCK);
        assertNotNull(origin);
        assertEquals(1, origin[0], "the patch starts after the doodad");
        assertEquals(0, origin[1]);
        assertEquals(4, packing.columns());
        assertEquals(3, packing.rows(), "a 3x3 patch makes the shelf three cells tall");

        for (TilesetExport.Entry entry : entries) {
            if (!entry.isBlockMember()) continue;
            int[] offset = BlockSlots.offset(entry.slot);
            assertEquals(origin[0] + offset[0], entry.col, entry.slot);
            assertEquals(origin[1] + offset[1], entry.row, entry.slot);
        }
    }

    @Test
    void anUnfilledSlotIsLeftToTheFillRatherThanClosingTheGap() throws Exception {
        // The centre is the case a hollow wall never draws, so its piece is
        // routinely absent. The patch must still reserve the cell.
        List<TilesetExport.Entry> entries = new ArrayList<>(wallMembers());
        entries.removeIf(entry -> entry.slot.equals("center"));
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec(BLOCK, GridLayout.WALL_3X3, FILL));

        BufferedImage atlas = TilesetExport.atlas(sheet(9), entries, blocks, CELL);
        TilesetExport.Packing packing = TilesetExport.pack(entries, blocks);

        assertEquals(3, packing.columns());
        assertEquals(3, packing.rows());
        assertEquals(0, sample(atlas, 1, 1) >>> 24, "the unfilled centre stays transparent");
        assertEquals(colourOf(7), sample(atlas, 1, 2), "the south piece keeps its own cell");

        JSONObject tileset = TilesetExport.tileset("graphics/test.png", CELL, entries, blocks);
        assertEquals("wall-3x3", tileset.getJSONArray("blocks").getJSONObject(0).getString("layout"));
        assertEquals(0, tileset.getJSONArray("doodads").length());
    }

    @Test
    void excludingEveryMemberDropsTheBlockFromTheSheet() throws Exception {
        List<TilesetExport.Entry> entries = wallMembers();
        entries.forEach(entry -> entry.included = false);
        TilesetExport.Entry crate = piece(9, "doodad.test.crate");
        entries.add(crate);
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec(BLOCK, GridLayout.WALL_3X3, FILL));

        JSONObject tileset = TilesetExport.tileset("graphics/test.png", CELL, entries, blocks);

        assertTrue(tileset.optJSONArray("blocks") == null
                        || tileset.getJSONArray("blocks").length() == 0,
                "a block with no packed cells must not name an origin it does not own");
        assertEquals(1, tileset.getJSONArray("doodads").length());

        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(tileset);
        registry.validateReferences();
    }

    @Test
    void slotNamesAreTheExteriorSideNotTheNeighbour() {
        assertEquals("exterior on the north", BlockSlots.describe("n"));
        assertEquals("exterior on the north and west", BlockSlots.describe("nw"));
        assertEquals("enclosed — no exterior on any side", BlockSlots.describe("center"));
        assertEquals("se", BlockSlots.name(2, 2));
        assertTrue(BlockSlots.fits(GridLayout.WALL_3X3, "ne"));
        assertTrue(BlockSlots.fits(GridLayout.SINGLE, BlockSlots.ONLY));
        assertTrue(!BlockSlots.fits(GridLayout.SINGLE, "ne"),
                "a one-cell block has no corners");
    }
}
