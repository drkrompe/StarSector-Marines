package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.tools.mcp.McpServer;
import com.dillon.starsectormarines.tools.mcp.McpToolCatalog;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The two tileset tools that depend on the real checkout, driven against it.
 *
 * <p>{@code tileset_export} derives its destination from what a sheet contains
 * and {@code tileset_map_preview} paints candidate art into the shipped sheets
 * before rendering a generated map. Neither claim can be tested against a
 * fabricated temp project: the first is about the sheets that actually exist in
 * {@code art-source/tilesets/}, and the second needs the shipped catalogs and
 * the sheets under {@code mod/graphics/} that a synthetic root does not have.
 *
 * <p><b>Real inputs, disposable outputs.</b> Export writes into {@code mod/},
 * so every run here is given a project root of its own — a temp directory
 * seeded with the checked-in raw art and its checked-in authoring document —
 * and the checkout's own {@code mod/} is fingerprinted around every test.
 * Exporting a real sheet in place would rewrite ids and atlas coordinates that
 * {@code GenMappingRegistry} references, which is a startup crash rather than a
 * visible diff.
 *
 * <p>Sheets are chosen by the property each test needs rather than by name, so
 * a later ingestion that renames or replaces one does not quietly stop
 * exercising the case.
 */
class ProjectTilesetToolsTest {

    private static final Path PROJECT = Path.of(".").toAbsolutePath().normalize();

    /** Coarse enough to render quickly, wide enough that a city's walls are all over it. */
    private static final int PREVIEW_GRID = 60;
    private static final int PREVIEW_CELL_PX = 8;
    private static final long PREVIEW_SEED = 1L;

    /**
     * The floor a substitution has to clear to count as visible.
     *
     * <p>Well under the ~9% that replacing every wall on a generated city
     * actually repaints, because the exact figure is a property of the map
     * layout at a seed rather than of the mechanism under test.
     */
    private static final double MIN_REPAINTED_FRACTION = 0.02;

    private Map<String, String> shippedContentBefore;

    // ------------------------------------------------------- the standing guard

    @BeforeEach
    void recordTheShippedContent() throws IOException {
        shippedContentBefore = fingerprintShippedContent();
    }

    /**
     * The one failure that would ruin the repository rather than the run.
     *
     * <p>An export whose project root leaked back to the checkout would rewrite
     * a shipped tileset in place, and nothing else here would notice.
     */
    @AfterEach
    void theCheckoutsOwnModIsNeverWrittenTo() throws IOException {
        assertEquals(shippedContentBefore, fingerprintShippedContent(),
                "these tools write into mod/, so a test that exports must do it somewhere "
                        + "disposable; shipped content changed while this test ran");
    }

    /**
     * Size and timestamp of every shipped data and art file.
     *
     * <p>{@code mod/jars} and {@code mod/sounds} are build outputs written by
     * other Gradle tasks in the same invocation, so they are not evidence of
     * anything this test did.
     */
    private static Map<String, String> fingerprintShippedContent() throws IOException {
        Map<String, String> fingerprint = new LinkedHashMap<>();
        for (String directory : List.of("mod/data", "mod/graphics")) {
            Path root = PROJECT.resolve(directory);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                    fingerprint.put(PROJECT.relativize(path).toString(),
                            Files.size(path) + "@" + Files.getLastModifiedTime(path));
                }
            }
        }
        return fingerprint;
    }

    // ------------------------------------------------------------------ export

    @Test
    void exportingASheetThatDeclaresBlocksLandsWithTheTilesets(@TempDir Path root)
            throws Exception {
        String name = aSheetDeclaringBlocks();
        seedFromCheckout(root, name);
        call(root, "tileset_slice", forSheet(name).put("apply", true));

        JSONObject exported = structured(call(root, "tileset_export", forSheet(name)));

        assertEquals("graphics/tilesets/" + name + ".png", exported.getString("sheetPath"),
                "a sheet that declares autotile blocks is terrain, and terrain ships with "
                        + "the tilesets rather than with the props");
        assertExportIsSelfConsistent(root, name, exported);
    }

    @Test
    void exportingASheetOfOnlyPropsLandsWithTheDoodads(@TempDir Path root) throws Exception {
        String name = anAlphaKeyedSheetOfOnlyProps();
        seedFromCheckout(root, name);
        call(root, "tileset_slice", forSheet(name).put("apply", true));

        JSONObject exported = structured(call(root, "tileset_export", forSheet(name)));

        assertEquals("graphics/doodads/" + name + ".png", exported.getString("sheetPath"));
        assertTrue(exported.getInt("doodads") > 1,
                "every piece the slicer kept should be packed, not just the first: " + name);
        assertExportIsSelfConsistent(root, name, exported);
    }

    /**
     * The three written files agree with each other and with the packer.
     *
     * <p>An atlas whose extent disagrees with the tileset beside it addresses
     * cells that are not there, which the game discovers at load rather than
     * here.
     */
    private static void assertExportIsSelfConsistent(Path root, String name, JSONObject exported)
            throws Exception {
        Path atlasPath = Path.of(exported.getString("atlas"));
        Path tilesetPath = Path.of(exported.getString("tileset"));
        Path cardPath = Path.of(exported.getString("card"));

        assertEquals(root.resolve("mod").resolve(exported.getString("sheetPath")), atlasPath);
        assertEquals(root.resolve(TilesetLibrary.EXPORT_DIR).resolve(name + ".tileset.json"),
                tilesetPath);
        assertEquals(tilesetPath.resolveSibling(name + ".tileset.md"), cardPath);
        for (Path written : List.of(atlasPath, tilesetPath, cardPath)) {
            assertTrue(Files.isRegularFile(written), "not written: " + written);
        }

        TilesetDocument document = TilesetDocument.read(TilesetDocument.pathFor(root, name));
        BufferedImage atlas = ImageIO.read(atlasPath.toFile());
        assertNotNull(atlas, "the atlas should be a readable PNG: " + atlasPath);
        assertEquals(exported.getInt("columns") * document.cellPx, atlas.getWidth(),
                "the atlas is addressed in cells, so its width is the packer's columns");
        assertEquals(exported.getInt("rows") * document.cellPx, atlas.getHeight());

        // Ingested by the game's own catalog reader rather than merely parsed:
        // a tileset that reads back as JSON but not as a tileset is still broken.
        JSONObject written = new JSONObject(Files.readString(tilesetPath));
        TileRegistry reread = new TileRegistry();
        reread.ingestSheet(written);
        assertEquals(exported.getString("sheetPath"), written.getString("sheet"));
        assertEquals(document.cellPx, written.getInt("cellPx"));

        String card = Files.readString(cardPath);
        for (TilesetExport.Entry entry : document.entries) {
            if (!entry.included || entry.isBlockMember()) continue;
            assertNotNull(reread.doodad(entry.id),
                    entry.id + " was packed but the exported tileset does not declare it");
            assertTrue(card.contains("`" + entry.id + "`"),
                    "the card is what makes an id usable by a reader who cannot open the "
                            + "atlas, so every exported id has to appear on it: " + entry.id);
        }
    }

    // ------------------------------------------------------------ map preview

    @Test
    void previewingACandidateOverAShippedIdRepaintsTheMapItWouldReplaceItOn(@TempDir Path root)
            throws Exception {
        String shippedId = "urban.wall";
        assertNotNull(TileRegistry.installed().block(shippedId),
                shippedId + " is the shipped block this preview stands in for");

        String name = anAlphaKeyedSheetOfOnlyProps();
        seedFromCheckout(root, name);
        // The preview reads the shipped sheets it paints over out of the project
        // root it is given, so the redirected root gets a copy of the real ones.
        copyTree(PROJECT.resolve("mod"), root.resolve("mod"));
        call(root, "tileset_slice", forSheet(name).put("apply", true));

        // Both halves in one test on purpose: the claim is that the binding is
        // what makes the difference, and a second copy of mod/ to say so is not
        // worth the seconds.
        JSONObject unbound = structured(call(root, "tileset_map_preview", previewArgs(name)));
        BufferedImage unboundBaseline = readWritten(unbound, "baseline");
        BufferedImage unboundSubstituted = readWritten(unbound, "substituted");
        assertEquals(0, differingPixels(unboundBaseline, unboundSubstituted),
                "with nothing bound there is nothing to substitute, so the comparison is "
                        + "the same image twice: " + unbound.getJSONArray("notes"));

        bindFirstIncludedEntry(root, name, shippedId);
        JSONObject bound = structured(call(root, "tileset_map_preview", previewArgs(name)));
        BufferedImage baseline = readWritten(bound, "baseline");
        BufferedImage substituted = readWritten(bound, "substituted");

        assertEquals(baseline.getWidth(), substituted.getWidth());
        assertEquals(baseline.getHeight(), substituted.getHeight());
        assertEquals(0, differingPixels(unboundBaseline, baseline),
                "the baseline is the same map whether or not a candidate is bound; if it "
                        + "were not, the substitution would have changed what the generator "
                        + "picked and the comparison would be worthless");

        long total = (long) baseline.getWidth() * baseline.getHeight();
        double repainted = differingPixels(baseline, substituted) / (double) total;
        System.out.printf("tileset_map_preview: %s standing in for %s repainted %.1f%% "
                + "of a %d-cell map%n", name, shippedId, repainted * 100, PREVIEW_GRID);
        assertTrue(repainted > MIN_REPAINTED_FRACTION,
                "substituting a shipped wall should be visible across the map, but only "
                        + String.format("%.3f%%", repainted * 100) + " of pixels changed");
        assertTrue(repainted < 1.0,
                "a substitution repaints the cells one id occupies, not the whole frame");
    }

    private static JSONObject previewArgs(String name) throws JSONException {
        return forSheet(name)
                .put("seed", PREVIEW_SEED)
                .put("gridCells", PREVIEW_GRID)
                .put("cellPx", PREVIEW_CELL_PX);
    }

    /** The PNG the tool says it wrote, read back off disk rather than out of the result. */
    private static BufferedImage readWritten(JSONObject structured, String key) throws Exception {
        Path path = Path.of(structured.getString(key));
        assertTrue(Files.isRegularFile(path), "the preview should have written " + path);
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, "not a readable PNG: " + path);
        return image;
    }

    private static long differingPixels(BufferedImage a, BufferedImage b) {
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        long differing = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) differing++;
            }
        }
        return differing;
    }

    /** Bind a candidate to a shipped id, the way the page's "stands in for" column does. */
    private static void bindFirstIncludedEntry(Path root, String name, String shippedId)
            throws Exception {
        Path path = TilesetDocument.pathFor(root, name);
        TilesetDocument document = TilesetDocument.read(path);
        for (TilesetExport.Entry entry : document.entries) {
            if (!entry.included) continue;
            entry.standsInFor = shippedId;
            document.write(path);
            return;
        }
        fail(name + " sliced into nothing that could stand in for " + shippedId);
    }

    // ------------------------------------------------------- choosing a sheet

    /** Every project sheet that has both raw art and a document, in library order. */
    private static List<TilesetLibrary.Sheet> annotatedProjectSheets() {
        List<TilesetLibrary.Sheet> found = new ArrayList<>();
        for (TilesetLibrary.Sheet sheet : TilesetLibrary.scan(PROJECT)) {
            if (sheet.rawSheet() != null && sheet.isAnnotated()) found.add(sheet);
        }
        return found;
    }

    private static String aSheetDeclaringBlocks() throws Exception {
        for (TilesetLibrary.Sheet sheet : annotatedProjectSheets()) {
            if (!TilesetDocument.read(sheet.document()).blocks.isEmpty()) return sheet.name();
        }
        return fail("no checked-in sheet declares autotile blocks, so the tilesets "
                + "destination cannot be exercised against real art");
    }

    /**
     * A keyed sheet of props: it slices into several pieces by alpha and, having
     * no blocks, exports to the doodads destination.
     *
     * <p>Several pieces rather than one, so that "the card names every exported
     * id" is a claim about a list rather than about a single row.
     */
    private static String anAlphaKeyedSheetOfOnlyProps() throws Exception {
        for (TilesetLibrary.Sheet sheet : annotatedProjectSheets()) {
            TilesetDocument document = TilesetDocument.read(sheet.document());
            if (!document.blocks.isEmpty()) continue;
            BufferedImage raw = TilesetOperations.readSheet(PROJECT, document);
            SheetMeasurement.Measurement measured = SheetMeasurement.measure(raw);
            if (measured.hasAlpha() && measured.suggestedPieceCount() > 1) return sheet.name();
        }
        return fail("no checked-in sheet is alpha-keyed props, so neither the doodads "
                + "destination nor a substitution candidate can be taken from real art");
    }

    // ------------------------------------------------------ redirected project

    /** Copy one sheet's real raw art and real authoring document into a throwaway root. */
    private static void seedFromCheckout(Path root, String name) throws Exception {
        TilesetLibrary.Sheet sheet = annotatedProjectSheets().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst().orElseThrow();
        Path sourceDir = root.resolve(TilesetLibrary.SOURCE_DIR);
        Files.createDirectories(sourceDir);
        // The document stores its sheet path project-relative, so the raw art has
        // to keep its own file name for the copy to resolve.
        Files.copy(sheet.rawSheet(), sourceDir.resolve(sheet.rawSheet().getFileName()));
        Files.copy(sheet.document(), TilesetDocument.pathFor(root, name));
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path path : walk.toList()) {
                Path target = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }

    // ------------------------------------------------------------- tool calls

    private static JSONObject forSheet(String name) throws JSONException {
        return new JSONObject().put("name", name);
    }

    /** One {@code tools/call}, through the same dispatch a client uses. */
    private static JSONObject call(Path root, String tool, JSONObject arguments) throws Exception {
        McpServer server = new McpServer(
                new McpToolCatalog(new TilesetMcpToolProvider().tools()),
                new McpToolContext(root, root.resolve("core")), "test", "0.0.1");
        JSONObject response = server.handle(new JSONObject()
                .put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
                .put("params", new JSONObject().put("name", tool).put("arguments", arguments)));
        assertFalse(response.has("error"), "protocol error from " + tool + ": " + response);
        JSONObject result = response.getJSONObject("result");
        assertFalse(result.optBoolean("isError", false), tool + " failed: "
                + result.getJSONArray("content").getJSONObject(0).getString("text"));
        return result;
    }

    private static JSONObject structured(JSONObject result) throws JSONException {
        return result.getJSONObject("structuredContent");
    }
}
