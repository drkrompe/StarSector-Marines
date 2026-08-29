package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The purpose-first view over the project's art.
 *
 * <p>Two kinds of check. The small ones ask the join's own rules directly — what
 * shape a block has, whether a candidate's slicing can be edited. The last one
 * runs the real scan over the real project, because the thing worth guarding is
 * that the three files a purpose is described across still line up: rename a
 * block in a tileset and the mapping that points at it goes quietly nowhere.
 */
public class SurfaceCatalogTest {

    private static Path projectRoot() {
        return Paths.get("").toAbsolutePath();
    }

    private static GridBlockDef wall(String id) {
        return new GridBlockDef(id, "graphics/tilesets/urban-tileset.png", 32, 3, 0,
                GridLayout.WALL_3X3, 0x060A10);
    }

    @Test
    void aBlockIsShapedByItsLayout() {
        assertEquals("wall-3x3", SurfaceCatalog.shapeOf(wall("urban.wall")));
        assertEquals("floor-3x3", SurfaceCatalog.shapeOf(
                new GridBlockDef("x", "s.png", 32, 0, 0, GridLayout.FLOOR_3X3, null)));
        assertEquals("single", SurfaceCatalog.shapeOf(
                new GridBlockDef("x", "s.png", 32, 0, 0, GridLayout.SINGLE, null)));
    }

    /** A pool has no layout to be shaped by, so its shape is being a pool. */
    @Test
    void aVariantPoolIsItsOwnShape() {
        GridBlockDef pool = GridBlockDef.variantPool("floors.grass", "s.png", 16,
                new int[][]{{1, 10}, {2, 10}, {3, 10}});
        assertEquals(SurfaceCatalog.VARIANT_POOL, SurfaceCatalog.shapeOf(pool));
    }

    @Test
    void aSheetIsNamedByItsFileNotItsPath() {
        assertEquals("urban-tileset",
                SurfaceCatalog.sheetNameOf("graphics/tilesets/urban-tileset.png"));
        assertEquals("nature-tiles", SurfaceCatalog.sheetNameOf("nature-tiles.png"));
        assertEquals("", SurfaceCatalog.sheetNameOf(null));
    }

    /**
     * The distinction the listing exists to report. A block with no document
     * behind it can be swapped in but not re-cut, and saying so up front beats
     * opening a sheet to find nothing there.
     */
    @Test
    void onlyABlockWithAuthoredSlotsCanBeReCut() {
        SurfaceCatalog.Candidate shipped = new SurfaceCatalog.Candidate(
                "floors.grass", SurfaceCatalog.VARIANT_POOL, "Floors_Tiles",
                null, List.of(), true);
        assertFalse(shipped.isEditable());
        assertTrue(shipped.describe().contains("shipped only"));

        SurfaceCatalog.Candidate authored = new SurfaceCatalog.Candidate(
                "urban.wall", "wall-3x3", "urban-tileset",
                Paths.get("art-source/tilesets/urban-tileset.tileset-authoring.json"),
                List.of(new SurfaceCatalog.Slot("doodad.urban.c3r0", "nw", true)), true);
        assertTrue(authored.isEditable());
        assertTrue(authored.describe().contains("1 slots authored"));
    }

    /**
     * The real join over the real project: the wall the mapping names is found,
     * it is the shape a wall has to be, and its nine slots are traceable back to
     * the document they were cut in.
     */
    @Test
    void theProjectsWallIsFoundWithItsSlicing() throws Exception {
        List<SurfaceCatalog.Purpose> purposes = SurfaceCatalog.scan(projectRoot());

        SurfaceCatalog.Purpose wall = purposes.stream()
                .filter(purpose -> purpose.name().equals("WALL"))
                .findFirst().orElse(null);
        assertNotNull(wall, "WALL is a surface role and must be listed");
        assertEquals(SurfaceCatalog.SURFACE_ROLE, wall.vocabulary());
        assertEquals("wall-3x3", wall.shape());

        SurfaceCatalog.Candidate inUse = wall.inUse();
        assertNotNull(inUse, "the mapping names a wall, so one candidate must be in use");
        assertEquals("urban.wall", inUse.blockId());
        assertTrue(inUse.isEditable(), "the shipped wall was cut from an authoring document");
        assertEquals(9, inUse.slots().size(), "a wall-3x3 fills nine slots");
    }

    /**
     * Every purpose the mapping fills resolves to art that exists. This is the
     * cross-file check: the mapping, the tilesets and the documents are three
     * files that can each be edited alone.
     */
    @Test
    void everyMappedPurposeFindsItsBlock() throws Exception {
        for (SurfaceCatalog.Purpose purpose : SurfaceCatalog.scan(projectRoot())) {
            if (purpose.isUnmapped()) continue;
            // STREET maps to a sliced tile rather than a block, so it has no
            // shape and no candidates - that is a shape of mapping, not a break.
            if (purpose.shape() == null) continue;
            assertNotNull(purpose.inUse(),
                    purpose.name() + " maps to '" + purpose.mappedId()
                            + "' but no block of that id was found");
        }
    }

    /**
     * The handoff's row matching. A slot names a piece by the id it had when the
     * block was declared, and re-slicing can rename pieces, so the match has to
     * survive a slot whose piece is gone rather than selecting the wrong row.
     */
    @Test
    void theHandoffSelectsOnlyTheSlotsThatStillExist() {
        List<TilesetExport.Entry> entries = List.of(
                entry("piece-a"), entry("piece-b"), entry("piece-c"));

        assertArrayEquals(new int[]{0, 2},
                TilesetAuthoringPage.rowsFor(entries, List.of("piece-a", "piece-c")));
        assertArrayEquals(new int[]{1},
                TilesetAuthoringPage.rowsFor(entries, List.of("piece-b", "piece-renamed")),
                "a slot whose piece was renamed away is dropped, not mismatched");
        assertArrayEquals(new int[0], TilesetAuthoringPage.rowsFor(entries, List.of()));
        assertArrayEquals(new int[0], TilesetAuthoringPage.rowsFor(List.of(), List.of("piece-a")));
    }

    private static TilesetExport.Entry entry(String id) {
        return new TilesetExport.Entry(new SheetSlicer.Piece(0, 0, 1, 1), id);
    }

    /** Asking by shape is the "show me the walls" query, and it finds the wall. */
    @Test
    void theShapeFilterFindsThePurposesAWallCanFill() throws Exception {
        List<SurfaceCatalog.Purpose> walls =
                SurfaceCatalog.withShape(SurfaceCatalog.scan(projectRoot()), "wall-3x3");
        assertEquals(1, walls.size(), "one purpose is filled by a wall-3x3 today");
        assertEquals("WALL", walls.get(0).name());
    }
}
