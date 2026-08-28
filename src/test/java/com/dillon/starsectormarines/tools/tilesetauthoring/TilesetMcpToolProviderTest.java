package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.mcp.McpServer;
import com.dillon.starsectormarines.tools.mcp.McpToolCatalog;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tileset tools' request/response contract, driven through the same
 * {@code tools/call} dispatch a client uses.
 *
 * <p>Deliberately not calling the tool objects directly: what a session depends
 * on is the shape of what comes back over the wire, and a test that reaches
 * past the dispatch cannot see a result that fails to serialize.
 */
class TilesetMcpToolProviderTest {

    private static final int CELL = 16;
    private static final int RESIDUE = 20;   // what a soft key leaves across empty space

    private static McpServer server(Path root) throws Exception {
        return new McpServer(new McpToolCatalog(new TilesetMcpToolProvider().tools()),
                new McpToolContext(root, root.resolve("core")), "test", "0.0.1");
    }

    /** The {@code result} of one tools/call, or a failure if the dispatch errored. */
    private static JSONObject call(Path root, String tool, JSONObject arguments) throws Exception {
        JSONObject params = new JSONObject().put("name", tool);
        if (arguments != null) params.put("arguments", arguments);
        JSONObject request = new JSONObject()
                .put("jsonrpc", "2.0").put("id", 1)
                .put("method", "tools/call").put("params", params);
        JSONObject response = server(root).handle(request);
        assertFalse(response.has("error"),
                "a tool call must never become a protocol error: " + response);
        return response.getJSONObject("result");
    }

    private static String textOf(JSONObject result) throws JSONException {
        return result.getJSONArray("content").getJSONObject(0).getString("text");
    }

    /**
     * A sheet of three opaque squares over faint background residue.
     *
     * <p>Each square is comfortably above {@link SheetSlicer#DEFAULT_MIN_AREA}
     * and the gutters are wider than the slicer's eight-connected reach, so what
     * it finds is a property of the art rather than of a threshold this test
     * happened to land on.
     */
    private static BufferedImage keyedSheet() {
        BufferedImage image = new BufferedImage(CELL * 6, CELL * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, (RESIDUE << 24) | 0x202020);
            }
        }
        for (int piece = 0; piece < 3; piece++) {
            for (int y = 4; y < CELL * 2 - 4; y++) {
                for (int x = 4; x < CELL * 2 - 4; x++) {
                    image.setRGB(piece * CELL * 2 + x, y, 0xFFC0C0C0);
                }
            }
        }
        return image;
    }

    /** A fully opaque sheet: the fused-plate case the ingest procedure calls out. */
    private static BufferedImage fusedPlate() {
        BufferedImage image = new BufferedImage(CELL * 4, CELL * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, 0xFF303030);
            }
        }
        return image;
    }

    private static Path writeSheet(Path root, String name, BufferedImage image) throws Exception {
        Path path = root.resolve(TilesetLibrary.SOURCE_DIR).resolve(name + ".raw.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private static TilesetDocument seed(Path root, String name) throws Exception {
        TilesetDocument document = new TilesetDocument();
        document.sheet = TilesetLibrary.SOURCE_DIR + "/" + name + ".raw.png";
        document.sheetName = name;
        document.idPrefix = "doodad." + name;
        document.cellPx = CELL;
        document.note = "A synthetic sheet used by the MCP contract test.";
        document.write(TilesetDocument.pathFor(root, name));
        return document;
    }

    // --------------------------------------------------------------- listing

    @Test
    void everyToolIsDiscoverableWithASchemaAndADescription(@TempDir Path root) throws Exception {
        JSONObject listing = server(root).handle(new JSONObject()
                        .put("jsonrpc", "2.0").put("id", 1).put("method", "tools/list"))
                .getJSONObject("result");
        JSONArray tools = listing.getJSONArray("tools");

        assertEquals(11, tools.length(), "the tileset provider contributes eleven tools: " + tools);
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            assertTrue(tool.getString("name").startsWith("tileset_"),
                    "a tileset tool should be findable by prefix: " + tool.getString("name"));
            assertFalse(tool.getString("description").isBlank());
            assertEquals("object", tool.getJSONObject("inputSchema").getString("type"));
        }
    }

    @Test
    void listingReportsEachSheetsStateAndItsThreePaths(@TempDir Path root) throws Exception {
        writeSheet(root, "hangar", keyedSheet());
        writeSheet(root, "reactor", keyedSheet());
        seed(root, "reactor");

        JSONObject result = call(root, "tileset_list", new JSONObject());
        JSONArray sheets = result.getJSONObject("structuredContent").getJSONArray("sheets");

        assertEquals(2, sheets.length());
        // The listing is the ingestion queue: unstarted work sorts first.
        assertEquals("hangar", sheets.getJSONObject(0).getString("name"));
        assertEquals("raw — not annotated", sheets.getJSONObject(0).getString("status"));
        assertEquals("seeded — see note", sheets.getJSONObject(1).getString("status"));
        assertTrue(sheets.getJSONObject(1).getBoolean("seeded"));
    }

    @Test
    void anEmptyProjectListsNothingRatherThanFailing(@TempDir Path root) throws Exception {
        JSONObject result = call(root, "tileset_list", new JSONObject());

        assertFalse(result.getBoolean("isError"),
                "having no sheets yet is a state, not a failure");
        assertEquals(0, result.getJSONObject("structuredContent")
                .getJSONArray("sheets").length());
    }

    // ------------------------------------------------------------- measuring

    @Test
    void measuringAKeyedSheetReportsTheThresholdLadder(@TempDir Path root) throws Exception {
        writeSheet(root, "hangar", keyedSheet());

        JSONObject result = call(root, "tileset_measure", new JSONObject()
                .put("sheet", TilesetLibrary.SOURCE_DIR + "/hangar.raw.png"));
        String report = result.getJSONObject("structuredContent").getString("report");

        assertTrue(report.contains("alpha channel: yes"), report);
        assertTrue(report.contains("pieces by alpha threshold"),
                "the ladder is what tells an operator where the sheet stops fusing: " + report);
    }

    @Test
    void measuringAnOpaqueSheetSaysItIsAFusedPlateAndNeedsAGrid(@TempDir Path root)
            throws Exception {
        writeSheet(root, "plate", fusedPlate());

        String report = call(root, "tileset_measure", new JSONObject()
                        .put("sheet", TilesetLibrary.SOURCE_DIR + "/plate.raw.png"))
                .getJSONObject("structuredContent").getString("report");

        assertTrue(report.contains("alpha channel: NO"), report);
        assertTrue(report.contains("grid: NOT STATED"),
                "an unkeyed sheet cannot be cut without a stated grid, and the report "
                        + "is the only place that can say so: " + report);
    }

    @Test
    void aStatedGridIsVerifiedRatherThanGuessed(@TempDir Path root) throws Exception {
        // 64x32 split 4x2 gives 16x16 cells. The tool must never infer 4x2 by
        // itself: seam energy and autocorrelation both read noise on real art.
        writeSheet(root, "plate", fusedPlate());

        String report = call(root, "tileset_measure", new JSONObject()
                        .put("sheet", TilesetLibrary.SOURCE_DIR + "/plate.raw.png")
                        .put("gridCols", 4).put("gridRows", 2))
                .getJSONObject("structuredContent").getString("report");

        assertTrue(report.contains("4x2 stated"), report);
        assertTrue(report.contains("16.0 x 16.0 px per cell"), report);
    }

    @Test
    void theDraftedSeedCarriesThePlaceholderNoteRatherThanInventingOne(@TempDir Path root)
            throws Exception {
        // The note is the judged half. Leaving the placeholder loud is what
        // makes ProjectTilesetSeedsTest fail for a seed nobody finished.
        writeSheet(root, "hangar", keyedSheet());

        JSONObject seed = call(root, "tileset_measure", new JSONObject()
                        .put("sheet", TilesetLibrary.SOURCE_DIR + "/hangar.raw.png"))
                .getJSONObject("structuredContent").getJSONObject("draftSeed");

        assertEquals("hangar", seed.getString("sheetName"));
        assertTrue(seed.getString("note").contains("TODO"),
                "a drafted note must announce that it is unfinished");
    }

    @Test
    void measuringSomethingOutsideTheProjectIsRefused(@TempDir Path root) throws Exception {
        JSONObject result = call(root, "tileset_measure",
                new JSONObject().put("sheet", "../../elsewhere.png"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("escapes the project root"), textOf(result));
    }

    // --------------------------------------------------------------- reading

    @Test
    void readingADocumentReturnsItVerbatim(@TempDir Path root) throws Exception {
        writeSheet(root, "reactor", keyedSheet());
        seed(root, "reactor");

        JSONObject result = call(root, "tileset_read_document",
                new JSONObject().put("name", "reactor"));
        JSONObject document = result.getJSONObject("structuredContent").getJSONObject("document");

        assertEquals("reactor", document.getString("sheetName"));
        assertEquals("A synthetic sheet used by the MCP contract test.",
                document.getString("note"),
                "the note is the record of what cannot be re-derived; it must survive a read");
        assertEquals(0, result.getJSONObject("structuredContent").getInt("entryCount"));
    }

    @Test
    void readingASheetWithNoDocumentSaysWhereToLook(@TempDir Path root) throws Exception {
        JSONObject result = call(root, "tileset_read_document",
                new JSONObject().put("name", "absent"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("tileset_list"),
                "an error a model can act on names the tool that would answer it: "
                        + textOf(result));
    }

    @Test
    void aSheetNameThatIsReallyAPathIsRefused(@TempDir Path root) throws Exception {
        // Every write target below is derived from the name by convention, so a
        // name that steers the derivation elsewhere is the thing to stop.
        for (String hostile : List.of("../../mod/data/tilesets/urban-tileset",
                "sub/dir/name", "a\\b")) {
            JSONObject result = call(root, "tileset_read_document",
                    new JSONObject().put("name", hostile));
            assertTrue(result.getBoolean("isError"), hostile + " should be refused");
            assertTrue(textOf(result).contains("is a path, not a sheet name"),
                    "the refusal should say what a sheet name is: " + textOf(result));
        }
    }

    // --------------------------------------------------------------- writing

    @Test
    void writingASeedCreatesTheDocumentBesideItsSheet(@TempDir Path root) throws Exception {
        writeSheet(root, "hangar", keyedSheet());
        JSONObject document = new JSONObject()
                .put("sheet", TilesetLibrary.SOURCE_DIR + "/hangar.raw.png")
                .put("sheetName", "hangar")
                .put("idPrefix", "doodad.hangar")
                .put("cellPx", CELL)
                .put("alphaMin", 40)
                .put("gridCols", 1).put("gridRows", 1)
                .put("note", "Three cut-out props keyed over a soft background.");

        JSONObject result = call(root, "tileset_write_document",
                new JSONObject().put("name", "hangar").put("document", document));

        assertFalse(result.getBoolean("isError"), textOf(result));
        Path written = TilesetDocument.pathFor(root, "hangar");
        assertTrue(Files.isRegularFile(written));
        assertEquals("Three cut-out props keyed over a soft background.",
                TilesetDocument.read(written).note);
        assertEquals(-1, result.getJSONObject("structuredContent").getInt("entriesBefore"),
                "a created document reports that there was nothing before it");
    }

    @Test
    void aDocumentNamingASheetThatIsNotThereIsRefused(@TempDir Path root) throws Exception {
        // Writing it would put a document in the library that fails the moment
        // anyone opens it, and ProjectTilesetSeedsTest would fail the build.
        JSONObject document = new JSONObject()
                .put("sheet", TilesetLibrary.SOURCE_DIR + "/imaginary.raw.png")
                .put("sheetName", "imaginary");

        JSONObject result = call(root, "tileset_write_document",
                new JSONObject().put("name", "imaginary").put("document", document));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("names a sheet that is not there"), textOf(result));
        assertFalse(Files.exists(TilesetDocument.pathFor(root, "imaginary")));
    }

    // --------------------------------------------------------------- slicing

    @Test
    void slicingReportsThePiecesFoundWithoutTouchingTheDocument(@TempDir Path root)
            throws Exception {
        writeSheet(root, "hangar", keyedSheet());
        seed(root, "hangar");

        JSONObject structured = call(root, "tileset_slice",
                new JSONObject().put("name", "hangar")).getJSONObject("structuredContent");

        assertEquals(3, structured.getJSONArray("pieces").length());
        assertEquals(3, structured.getInt("added"));
        assertFalse(structured.getBoolean("applied"));
        assertEquals(0, TilesetDocument.read(TilesetDocument.pathFor(root, "hangar"))
                        .entries.size(),
                "a slice is a look, not a commitment: the document is untouched until apply");
    }

    @Test
    void applyingASliceSavesThePiecesAndTheThresholdThatFoundThem(@TempDir Path root)
            throws Exception {
        // A piece list is only meaningful next to its threshold, so keeping one
        // without the other would make the saved document self-contradictory.
        writeSheet(root, "hangar", keyedSheet());
        seed(root, "hangar");

        call(root, "tileset_slice", new JSONObject()
                .put("name", "hangar").put("alphaMin", 96).put("apply", true));

        TilesetDocument saved = TilesetDocument.read(TilesetDocument.pathFor(root, "hangar"));
        assertEquals(3, saved.entries.size());
        assertEquals(96, saved.alphaMin);
    }

    @Test
    void reSlicingCarriesExistingAnnotationsAndNamesWhatWasLost(@TempDir Path root)
            throws Exception {
        writeSheet(root, "hangar", keyedSheet());
        TilesetDocument document = seed(root, "hangar");
        call(root, "tileset_slice", new JSONObject().put("name", "hangar").put("apply", true));

        // Rename a piece, then re-slice: tuning a threshold must not cost the
        // annotations already made.
        document = TilesetDocument.read(TilesetDocument.pathFor(root, "hangar"));
        document.entries.get(0).id = "doodad.hangar.crate";
        document.write(TilesetDocument.pathFor(root, "hangar"));

        JSONObject structured = call(root, "tileset_slice",
                new JSONObject().put("name", "hangar")).getJSONObject("structuredContent");

        assertEquals(3, structured.getInt("carried"));
        assertEquals(0, structured.getJSONArray("lost").length());
        assertEquals("doodad.hangar.crate",
                structured.getJSONArray("pieces").getJSONObject(0).getString("id"));
    }

    // -------------------------------------------------------------- splitting

    /** A seeded, sliced fused plate: one piece covering the sheet, with its layout stated. */
    private static void statedPlate(Path root, String name, int cols, int rows) throws Exception {
        writeSheet(root, name, fusedPlate());
        TilesetDocument document = seed(root, name);
        document.gridCols = cols;
        document.gridRows = rows;
        document.write(TilesetDocument.pathFor(root, name));
        call(root, "tileset_slice", new JSONObject().put("name", name).put("apply", true));
    }

    /** Every part, laid end to end, must reconstruct the plate exactly. */
    private static void assertTilesTheSheet(JSONArray parts, int cols, int rows)
            throws JSONException {
        assertEquals(cols * rows, parts.length());
        long area = 0;
        for (int i = 0; i < parts.length(); i++) {
            JSONArray rect = parts.getJSONObject(i).getJSONArray("rect");
            area += (long) rect.getInt(2) * rect.getInt(3);
            if (i % cols + 1 < cols) {
                JSONArray next = parts.getJSONObject(i + 1).getJSONArray("rect");
                assertEquals(rect.getInt(0) + rect.getInt(2), next.getInt(0),
                        "no gap or overlap between columns");
            }
        }
        assertEquals((long) CELL * 4 * CELL * 2, area, "the parts must tile the plate exactly");
    }

    @Test
    void splittingAFusedPlateCutsTheGridTheDocumentStates(@TempDir Path root) throws Exception {
        // No threshold separates a plate — its cells are drawn edge to edge — so
        // the layout the document states is the only thing that can cut it.
        statedPlate(root, "plate", 4, 2);

        JSONObject structured = call(root, "tileset_split_on_grid",
                new JSONObject().put("name", "plate")).getJSONObject("structuredContent");

        assertEquals(8, structured.getInt("partCount"));
        assertTilesTheSheet(structured.getJSONArray("parts"), 4, 2);
        JSONArray parts = structured.getJSONArray("parts");
        assertEquals("doodad.plate.c0r0", parts.getJSONObject(0).getString("id"),
                "a cut cell is named for where it sits on the plate");
        assertEquals("doodad.plate.c3r0", parts.getJSONObject(3).getString("id"));
        assertEquals("doodad.plate.c0r1", parts.getJSONObject(4).getString("id"),
                "the fifth cell of a four-column plate starts the second row");
    }

    @Test
    void aSplitIsALookUntilItIsApplied(@TempDir Path root) throws Exception {
        statedPlate(root, "plate", 4, 2);
        Path path = TilesetDocument.pathFor(root, "plate");
        byte[] before = Files.readAllBytes(path);

        call(root, "tileset_split_on_grid", new JSONObject().put("name", "plate"));

        assertArrayEquals(before, Files.readAllBytes(path),
                "a preview must not touch the document at all");
    }

    @Test
    void applyingASplitReplacesThePlateWithOneCellParts(@TempDir Path root) throws Exception {
        statedPlate(root, "plate", 4, 2);

        call(root, "tileset_split_on_grid",
                new JSONObject().put("name", "plate").put("apply", true));

        TilesetDocument saved = TilesetDocument.read(TilesetDocument.pathFor(root, "plate"));
        assertEquals(8, saved.entries.size());
        for (TilesetExport.Entry entry : saved.entries) {
            assertEquals(1, entry.footprintX, entry.id + " is one cell of the plate");
            assertEquals(1, entry.footprintY, entry.id);
        }
        assertEquals(4, saved.gridCols,
                "the parts are only meaningful next to the layout they were cut to");
    }

    @Test
    void aStatedGridOverridesTheDocumentsOwn(@TempDir Path root) throws Exception {
        statedPlate(root, "plate", 4, 2);

        JSONObject structured = call(root, "tileset_split_on_grid", new JSONObject()
                .put("name", "plate").put("cols", 8).put("rows", 1))
                .getJSONObject("structuredContent");

        assertTilesTheSheet(structured.getJSONArray("parts"), 8, 1);
        assertEquals(CELL * 2, structured.getJSONArray("parts").getJSONObject(0)
                        .getJSONArray("rect").getInt(3),
                "a one-row strip's frames are the full height of the plate");
    }

    @Test
    void aOneByOneGridIsRefusedBecauseTheLayoutIsStated(@TempDir Path root) throws Exception {
        // 1 x 1 is the default that means "not a plate", so reaching here is a
        // caller that has not stated the layout rather than one asking for one part.
        statedPlate(root, "plate", 1, 1);

        JSONObject result = call(root, "tileset_split_on_grid",
                new JSONObject().put("name", "plate"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("20-frame strip is 20 x 1"),
                "the refusal should say what a stated layout looks like: " + textOf(result));
    }

    @Test
    void aSheetWithSeveralPiecesWillNotGuessWhichOneToCut(@TempDir Path root) throws Exception {
        // Cutting the first of several would shred a sheet somebody had already
        // annotated, and the caller cannot see the table.
        writeSheet(root, "hangar", keyedSheet());
        seed(root, "hangar");
        call(root, "tileset_slice", new JSONObject().put("name", "hangar").put("apply", true));

        JSONObject result = call(root, "tileset_split_on_grid", new JSONObject()
                .put("name", "hangar").put("cols", 2).put("rows", 2));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("entryId"),
                "the refusal names the way through: " + textOf(result));
        assertTrue(textOf(result).contains("doodad.hangar.piece-000"),
                "and says what there was to choose from: " + textOf(result));
    }

    @Test
    void anUnslicedSheetIsToldToSliceRatherThanSplit(@TempDir Path root) throws Exception {
        writeSheet(root, "plate", fusedPlate());
        seed(root, "plate");

        JSONObject result = call(root, "tileset_split_on_grid", new JSONObject()
                .put("name", "plate").put("cols", 4).put("rows", 2));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("tileset_slice"), textOf(result));
    }

    // -------------------------------------------------------------- exporting

    @Test
    void exportingWritesTheAtlasItsTilesetAndItsCatalogCard(@TempDir Path root) throws Exception {
        writeSheet(root, "hangar", keyedSheet());
        seed(root, "hangar");
        call(root, "tileset_slice", new JSONObject().put("name", "hangar").put("apply", true));

        JSONObject structured = call(root, "tileset_export",
                new JSONObject().put("name", "hangar")).getJSONObject("structuredContent");

        assertTrue(Files.isRegularFile(Path.of(structured.getString("atlas"))));
        assertTrue(Files.isRegularFile(Path.of(structured.getString("tileset"))));
        assertTrue(Files.isRegularFile(Path.of(structured.getString("card"))),
                "an id says nothing about what a piece is; the card is what makes the "
                        + "sheet usable by a reader who cannot open the atlas");
        assertEquals("graphics/doodads/hangar.png", structured.getString("sheetPath"),
                "a sheet with no blocks is props, and props belong with the doodads");
        assertEquals(3, structured.getInt("doodads"));
    }

    @Test
    void exportingASeededSheetWithNoPiecesRefusesAndReadsBackItsNote(@TempDir Path root)
            throws Exception {
        // A seed exists to say what its sheet is; some say the sheet is not a
        // straight ingest at all, which is exactly what to surface here.
        writeSheet(root, "reactor", keyedSheet());
        seed(root, "reactor");

        JSONObject result = call(root, "tileset_export", new JSONObject().put("name", "reactor"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("A synthetic sheet used by the MCP contract test."),
                textOf(result));
    }

    // ---------------------------------------------------------- map preview

    @Test
    void aPreviewDestinationOutsideTheProjectIsRefusedBeforeAnyRendering(@TempDir Path root)
            throws Exception {
        // Generating and rendering two maps is expensive, so a refused
        // destination has to be caught before it is paid for. Reaching the
        // renderer at all here would need the shipped catalogs, which a temp
        // project does not have — so this failing on the path is the assertion.
        writeSheet(root, "hangar", keyedSheet());
        seed(root, "hangar");
        call(root, "tileset_slice", new JSONObject().put("name", "hangar").put("apply", true));

        JSONObject result = call(root, "tileset_map_preview", new JSONObject()
                .put("name", "hangar").put("outputDir", "../../escaped"));

        assertTrue(result.getBoolean("isError"));
        assertTrue(textOf(result).contains("escapes the project root"), textOf(result));
    }

    @Test
    void exportedContentGoesOnlyToTheTwoPlacesTheWorkbenchWritesTo(@TempDir Path root)
            throws Exception {
        writeSheet(root, "hangar", keyedSheet());
        seed(root, "hangar");
        call(root, "tileset_slice", new JSONObject().put("name", "hangar").put("apply", true));

        JSONObject structured = call(root, "tileset_export",
                new JSONObject().put("name", "hangar")).getJSONObject("structuredContent");

        // mod/ is synced wholesale into every install, so where an export lands
        // is a shipping decision rather than a convenience.
        assertTrue(Path.of(structured.getString("atlas"))
                        .startsWith(root.resolve("mod/graphics")),
                structured.getString("atlas"));
        assertTrue(Path.of(structured.getString("tileset"))
                        .startsWith(root.resolve(TilesetLibrary.EXPORT_DIR)),
                structured.getString("tileset"));
        assertFalse(Files.exists(root.resolve("mod").resolve(TilesetLibrary.SOURCE_DIR)),
                "no pre-pack input may follow the export into mod/");
    }

    @Test
    void measureAcceptsTheNameThatTilesetListReported() throws Exception {
        // list-then-measure is the sequence a session actually runs, so the
        // second tool has to accept what the first one handed it. Run against
        // the real checkout, because that pairing is what is being protected.
        Path project = Path.of(".").toAbsolutePath().normalize();

        JSONObject measured = call(project, "tileset_measure",
                new JSONObject().put("sheet", "urban-tileset"));

        assertFalse(measured.optBoolean("isError", false),
                "a bare name should resolve: " + textOf(measured));
        assertTrue(textOf(measured).contains("1254x1254"), textOf(measured));
    }

    @Test
    void measureStillAcceptsAProjectRelativePath() throws Exception {
        Path project = Path.of(".").toAbsolutePath().normalize();

        JSONObject measured = call(project, "tileset_measure", new JSONObject()
                .put("sheet", "art-source/tilesets/urban-tileset.raw.png"));

        assertFalse(measured.optBoolean("isError", false), textOf(measured));
        assertTrue(textOf(measured).contains("1254x1254"), textOf(measured));
    }

    @Test
    void anUnknownSheetSaysWhichSpellingsWork() throws Exception {
        Path project = Path.of(".").toAbsolutePath().normalize();

        JSONObject measured = call(project, "tileset_measure",
                new JSONObject().put("sheet", "not-a-sheet"));

        assertTrue(measured.optBoolean("isError", false), textOf(measured));
        assertTrue(textOf(measured).contains("base name"), textOf(measured));
    }
}
