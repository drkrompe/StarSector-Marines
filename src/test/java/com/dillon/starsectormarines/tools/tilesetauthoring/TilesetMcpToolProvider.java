package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.mcp.McpSchema;
import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolProvider;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * The tileset ingest procedure, made callable.
 *
 * <p>The procedure in {@code .claude/skills/ingest-tileset/SKILL.md} splits a
 * sheet's ingestion into a measured half and a judged half and insists the two
 * stay apart. These tools are the measured half and only the measured half: they
 * report what the pixels say, cut where they are told to cut, and write what
 * they are given. Nothing here invents a note, a footprint, a block or an id.
 *
 * <p>Mod-domain, so it lives in the root test source set. The shipped jar never
 * sees any of this, and the generic host in {@code :layer-authoring} never
 * learns what a tileset is.
 */
public final class TilesetMcpToolProvider implements McpToolProvider {

    @Override
    public List<McpTool> tools() {
        return List.of(
                new ListSheets(),
                new MeasureSheet(),
                new ReadDocument(),
                new WriteDocument(),
                new SliceSheet(),
                new ExportTileset(),
                new MapPreview());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A sheet name is a name, not a path.
     *
     * <p>Every write target below is derived from this by convention rather than
     * taken from the caller, so the one thing that must not get through is a
     * name that steers the derivation somewhere else. The caller is a model, and
     * "art-source/tilesets/../../mod/data" is a plausible thing for one to write
     * while trying to be helpful.
     */
    static String requireSheetName(JSONObject arguments) {
        String name = arguments.optString("name", "").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("a sheet name is required");
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException(
                    "'" + name + "' is a path, not a sheet name; pass the base name only, "
                            + "as tileset_list reports it");
        }
        return name;
    }

    /** The document for a named sheet, or a failure that says which names exist. */
    private static TilesetDocument documentFor(Path projectRoot, String name) throws Exception {
        Path path = TilesetDocument.pathFor(projectRoot, name);
        if (!Files.isRegularFile(path)) {
            throw new IOException("no authoring document for '" + name + "' at " + path
                    + ". Use tileset_list to see what is in the project, and "
                    + "tileset_write_document to seed a sheet that has none.");
        }
        return TilesetDocument.read(path);
    }

    private static JSONObject describe(TilesetLibrary.Sheet sheet) throws JSONException {
        JSONObject described = new JSONObject();
        described.put("name", sheet.name());
        described.put("status", sheet.status());
        described.put("annotatedPieces", sheet.annotatedPieces());
        described.put("seeded", sheet.isSeeded());
        described.put("exported", sheet.isExported());
        described.put("rawSheet", sheet.rawSheet() == null ? JSONObject.NULL
                : sheet.rawSheet().toString());
        described.put("document", sheet.document() == null ? JSONObject.NULL
                : sheet.document().toString());
        described.put("exportedTileset", sheet.exportedTileset() == null ? JSONObject.NULL
                : sheet.exportedTileset().toString());
        return described;
    }

    // ------------------------------------------------------------------ tools

    private static final class ListSheets implements McpTool {

        @Override public String name() { return "tileset_list"; }

        @Override
        public String description() {
            return "List every tileset sheet in the project with its state, work-to-do first. "
                    + "A sheet exists in up to three places at once — the raw art under "
                    + "art-source/tilesets/, its authoring document beside it, and the exported "
                    + "tileset under mod/data/tilesets/ — and this pairs them by name. The "
                    + "listing is also the ingestion queue. Start here.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object().build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context)
                throws JSONException {
            List<TilesetLibrary.Sheet> sheets = TilesetLibrary.scan(context.projectRoot());
            JSONArray described = new JSONArray();
            StringBuilder text = new StringBuilder();
            for (TilesetLibrary.Sheet sheet : sheets) {
                described.put(describe(sheet));
                text.append(sheet.name()).append("  (").append(sheet.status()).append(")\n");
            }
            if (sheets.isEmpty()) {
                return McpToolResult.of("No sheets under " + TilesetLibrary.SOURCE_DIR
                        + " or " + TilesetLibrary.EXPORT_DIR + " in " + context.projectRoot(),
                        new JSONObject().put("sheets", described));
            }
            return McpToolResult.of(text.toString().trim(),
                    new JSONObject().put("sheets", described));
        }
    }

    private static final class MeasureSheet implements McpTool {

        @Override public String name() { return "tileset_measure"; }

        @Override
        public String description() {
            return "Measure a raw art sheet and draft the seed that annotates it. Reports the "
                    + "size, whether there is a usable alpha channel, and how many pieces each "
                    + "alpha threshold finds. It deliberately does NOT detect the cell grid — "
                    + "that cannot be read off the pixels — so pass gridCols/gridRows when you "
                    + "know the layout the sheet was generated to; cells need not be square. "
                    + "Writes nothing: the drafted seed comes back for you to fill in a real "
                    + "note and then pass to tileset_write_document.";
        }

        /**
         * Resolve either spelling of a sheet.
         *
         * <p>{@code tileset_list} reports base names and this is the tool a session
         * reaches for next, so a bare name has to work; a path still does, for raw
         * art that the library does not list.
         */
        private static Path rawSheet(McpToolContext context, String sheet) throws IOException {
            if (sheet.isBlank()) return null;
            if (!sheet.contains("/") && !sheet.contains("\\")) {
                for (TilesetLibrary.Sheet known : TilesetLibrary.scan(context.projectRoot())) {
                    if (known.name().equals(sheet) && known.rawSheet() != null) {
                        return known.rawSheet();
                    }
                }
            }
            return context.resolveInsideProject(sheet);
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("sheet", "The sheet's base name as tileset_list reports "
                            + "it, e.g. reactor-hall; or a project-relative path to any raw "
                            + "sheet, e.g. art-source/tilesets/reactor-hall.raw.png")
                    .integer("gridCols", "Columns of the plate layout the sheet was drawn to. "
                            + "Stated, never guessed. Omit if unknown.")
                    .integer("gridRows", "Rows of that layout. Omit if unknown.")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            Path sheetPath = rawSheet(context, arguments.optString("sheet", ""));
            if (sheetPath == null || !Files.isRegularFile(sheetPath)) {
                return McpToolResult.failure("no such sheet: " + arguments.optString("sheet", "")
                        + ". Use a base name as tileset_list reports it, or a project-relative "
                        + "path.");
            }
            BufferedImage image = ImageIO.read(sheetPath.toFile());
            if (image == null) return McpToolResult.failure("not an image: " + sheetPath);

            SheetMeasurement.Measurement measurement = SheetMeasurement.measure(image);
            int cols = arguments.optInt("gridCols", 0);
            int rows = arguments.optInt("gridRows", 0);
            SheetMeasurement.Grid grid = (cols > 0 && rows > 0)
                    ? SheetMeasurement.statedGrid(measurement, cols, rows) : null;

            String fileName = sheetPath.getFileName().toString();
            String sheetName = fileName.endsWith(".raw.png")
                    ? fileName.substring(0, fileName.length() - ".raw.png".length())
                    : fileName.replaceFirst("\\.png$", "");
            String relative = context.projectRoot().relativize(sheetPath).toString()
                    .replace('\\', '/');

            TilesetDocument seed =
                    SheetMeasurement.draftSeed(relative, sheetName, measurement, grid);
            String report = SheetMeasurement.report(fileName, measurement, grid);

            JSONObject structured = new JSONObject();
            structured.put("report", report);
            structured.put("draftSeed", seed.toJson());
            return McpToolResult.of(report
                            + "\nDrafted seed (replace the placeholder note before writing it):\n"
                            + seed.toJson().toString(2),
                    structured);
        }
    }

    private static final class ReadDocument implements McpTool {

        @Override public String name() { return "tileset_read_document"; }

        @Override
        public String description() {
            return "Read one sheet's authoring document — its slice settings, its standing note, "
                    + "its blocks, and every annotated piece. This is the record of judgements "
                    + "that cannot be re-derived mechanically, so read it before changing "
                    + "anything about the sheet.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name, as tileset_list reports it")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            Path path = TilesetDocument.pathFor(context.projectRoot(), name);
            TilesetDocument document = documentFor(context.projectRoot(), name);

            JSONObject structured = new JSONObject();
            structured.put("path", path.toString());
            structured.put("document", document.toJson());
            structured.put("entryCount", document.entries.size());
            structured.put("blockCount", document.blocks.size());
            return McpToolResult.of(document.toJson().toString(2), structured);
        }
    }

    private static final class WriteDocument implements McpTool {

        @Override public String name() { return "tileset_write_document"; }

        @Override
        public String description() {
            return "Replace a sheet's authoring document. Use it to seed a sheet (settings and a "
                    + "real note, no entries) or to save annotations onto sliced pieces. The "
                    + "document is validated by reading it back before it replaces the old one, "
                    + "and it is written atomically. Note: a seed whose note is missing or still "
                    + "says TODO fails ProjectTilesetSeedsTest — the note is the deliverable, "
                    + "not a formality. Writes only under art-source/tilesets/.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name. The document is written to "
                            + "art-source/tilesets/<name>.tileset-authoring.json")
                    .object("document", "The complete document, in the same shape "
                            + "tileset_read_document returns. Partial documents are not merged: "
                            + "read, edit, write back.", true)
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            JSONObject body = arguments.optJSONObject("document");
            if (body == null) throw new IllegalArgumentException("a document object is required");

            Path path = TilesetDocument.pathFor(context.projectRoot(), name);
            int before = Files.isRegularFile(path)
                    ? TilesetDocument.read(path).entries.size() : -1;

            TilesetDocument document = TilesetDocument.fromJson(body);
            Path sheetPath = TilesetOperations.resolve(context.projectRoot(), document.sheet);
            if (!Files.isRegularFile(sheetPath)) {
                return McpToolResult.failure("the document names a sheet that is not there: "
                        + document.sheet);
            }
            document.write(path);

            JSONObject structured = new JSONObject();
            structured.put("path", path.toString());
            structured.put("entriesBefore", before);
            structured.put("entriesAfter", document.entries.size());
            return McpToolResult.of(
                    (before < 0 ? "Created " : "Replaced ") + path + " — "
                            + document.entries.size() + " entries, "
                            + document.blocks.size() + " blocks"
                            + (before < 0 ? "" : " (was " + before + " entries)"),
                    structured);
        }
    }

    private static final class SliceSheet implements McpTool {

        @Override public String name() { return "tileset_slice"; }

        @Override
        public String description() {
            return "Slice a sheet's raw art and report the pieces found, carrying any existing "
                    + "annotations onto them. This is how a threshold gets tuned: a piece list "
                    + "is only meaningful next to the threshold that found it. Reports what was "
                    + "kept, what is new, and what no longer matches anything. Read-only unless "
                    + "you pass apply=true, which saves the reconciled pieces into the document.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name")
                    .integer("alphaMin", "Alpha at or above which a pixel counts as art. "
                            + "Defaults to the document's own setting. Raise it when a soft key "
                            + "fuses the sheet into one piece.")
                    .bool("apply", "Save the reconciled pieces into the document. "
                            + "Default false — look before you keep.")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            TilesetDocument document = documentFor(context.projectRoot(), name);
            BufferedImage sheet = TilesetOperations.readSheet(context.projectRoot(), document);
            int alphaMin = arguments.optInt("alphaMin", document.alphaMin);
            boolean apply = arguments.optBoolean("apply", false);

            TilesetDocument.Reconciliation reconciled =
                    TilesetOperations.slice(sheet, document, alphaMin);

            JSONArray pieces = new JSONArray();
            for (TilesetExport.Entry entry : reconciled.entries()) {
                JSONObject described = new JSONObject();
                described.put("id", entry.id);
                described.put("rect", new JSONArray()
                        .put(entry.piece.x()).put(entry.piece.y())
                        .put(entry.piece.width()).put(entry.piece.height()));
                described.put("footprintCells", new JSONArray()
                        .put(entry.footprintX).put(entry.footprintY));
                described.put("included", entry.included);
                if (entry.isBlockMember()) {
                    described.put("block", entry.blockId);
                    described.put("slot", entry.slot);
                }
                pieces.put(described);
            }

            String applied = "";
            if (apply) {
                document.alphaMin = alphaMin;
                document.entries = reconciled.entries();
                document.write(TilesetDocument.pathFor(context.projectRoot(), name));
                applied = "\nSaved into "
                        + TilesetDocument.pathFor(context.projectRoot(), name);
            }

            JSONObject structured = new JSONObject();
            structured.put("alphaMin", alphaMin);
            structured.put("sheetWidth", sheet.getWidth());
            structured.put("sheetHeight", sheet.getHeight());
            structured.put("carried", reconciled.carried());
            structured.put("added", reconciled.added());
            structured.put("lost", new JSONArray(reconciled.lost()));
            structured.put("applied", apply);
            structured.put("pieces", pieces);
            return McpToolResult.of(name + " at alpha " + alphaMin + ": "
                    + reconciled.summary() + applied, structured);
        }
    }

    private static final class ExportTileset implements McpTool {

        @Override public String name() { return "tileset_export"; }

        @Override
        public String description() {
            return "Pack a sheet's included pieces into an atlas and write the tileset the game "
                    + "loads, plus the catalog card that says what each id is. Writes three "
                    + "files under mod/: the atlas, mod/data/tilesets/<name>.tileset.json and "
                    + "its .tileset.md card. Only kept pieces are packed, and each is stretched "
                    + "to the footprint it was authored to occupy. Exporting over a shipped "
                    + "tileset rewrites ids and coordinates that GenMappingRegistry references, "
                    + "so check the sheet's note first.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            TilesetDocument document = documentFor(context.projectRoot(), name);
            if (document.entries.isEmpty()) {
                return McpToolResult.failure(name + " is seeded but has no pieces yet; "
                        + "slice it before exporting. Its note says: "
                        + (document.note.isBlank() ? "(nothing)" : document.note));
            }
            BufferedImage sheet = TilesetOperations.readSheet(context.projectRoot(), document);
            TilesetOperations.ExportResult exported =
                    TilesetOperations.export(context.projectRoot(), document, sheet);

            JSONObject structured = new JSONObject();
            structured.put("atlas", exported.atlasPath().toString());
            structured.put("tileset", exported.tilesetPath().toString());
            structured.put("card", exported.cardPath().toString());
            structured.put("sheetPath", exported.sheetPath());
            structured.put("columns", exported.columns());
            structured.put("rows", exported.rows());
            structured.put("doodads", exported.doodads());
            structured.put("blocks", exported.blocks());
            return McpToolResult.of("Wrote " + exported.atlasPath() + ", "
                    + exported.tilesetPath() + " and " + exported.cardPath()
                    + " — " + exported.columns() + "x" + exported.rows() + " cells, "
                    + exported.doodads() + " doodads, " + exported.blocks() + " blocks",
                    structured);
        }
    }

    private static final class MapPreview implements McpTool {

        @Override public String name() { return "tileset_map_preview"; }

        @Override
        public String description() {
            return "Render a generated map twice — as it ships, and with this sheet's art "
                    + "standing in for the content it might replace — and write both to PNG so "
                    + "you can look at them. Whether a wall reads at a distance or a crate "
                    + "disappears against the deck are questions about a map, not about a "
                    + "contact sheet. Bindings come from each piece's standsInFor field; a "
                    + "sheet with none renders the baseline twice and says so. Writes nothing "
                    + "into mod/.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name")
                    .integer("seed", "Map seed. The same seed gives the same map, "
                            + "which is what makes the two images comparable.")
                    .integer("gridCells", "Map size in cells. Default "
                            + TilesetMapPreview.DEFAULT_GRID + ".")
                    .integer("cellPx", "Pixels per cell. Default "
                            + TilesetMapPreview.DEFAULT_CELL_PX + ".")
                    .string("outputDir", "Where to write the two PNGs, project-relative. "
                            + "Defaults to build/tileset-map-preview/<name>.")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            // Both arguments are checked before any work: generating and
            // rendering two maps is expensive, and a refused destination is not
            // worth paying for it first.
            String requestedDir = arguments.optString("outputDir", "");
            Path outputDir = requestedDir.isBlank()
                    ? context.projectRoot().resolve("build/tileset-map-preview").resolve(name)
                    : context.resolveInsideProject(requestedDir);

            TilesetDocument document = documentFor(context.projectRoot(), name);
            if (document.entries.isEmpty()) {
                return McpToolResult.failure(name + " has no pieces to substitute; "
                        + "slice it first.");
            }
            BufferedImage sheet = TilesetOperations.readSheet(context.projectRoot(), document);
            BufferedImage atlas = TilesetExport.atlas(
                    sheet, document.entries, document.blocks, document.cellPx);
            List<TilesetMapPreview.Substitution> bindings =
                    TilesetOperations.bindings(document.entries, document.blocks);

            long seed = arguments.optLong("seed", 1L);
            int gridCells = arguments.optInt("gridCells", TilesetMapPreview.DEFAULT_GRID);
            int cellPx = arguments.optInt("cellPx", TilesetMapPreview.DEFAULT_CELL_PX);
            Files.createDirectories(outputDir);

            TilesetMapPreview.Result result = TilesetMapPreview.render(
                    context.projectRoot(), atlas, document.cellPx, bindings,
                    seed, gridCells, cellPx);

            Path baseline = outputDir.resolve(name + "-baseline.png");
            Path substituted = outputDir.resolve(name + "-substituted.png");
            ImageIO.write(result.baseline(), "png", baseline.toFile());
            ImageIO.write(result.substituted(), "png", substituted.toFile());

            JSONArray bound = new JSONArray();
            for (TilesetMapPreview.Substitution substitution : bindings) {
                bound.put(substitution.shippedId());
            }
            JSONObject structured = new JSONObject();
            structured.put("baseline", baseline.toString());
            structured.put("substituted", substituted.toString());
            structured.put("seed", seed);
            structured.put("boundIds", bound);
            structured.put("notes", new JSONArray(result.notes()));

            StringBuilder text = new StringBuilder();
            text.append("Wrote ").append(baseline).append(" and ").append(substituted);
            text.append("\nBound ids: ").append(bound.length() == 0 ? "(none)" : bound.toString());
            for (String note : result.notes()) text.append('\n').append(note);
            return McpToolResult.of(text.toString(), structured);
        }
    }
}
