package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                new FitGrid(),
                new SplitOnGrid(),
                new SetBlock(),
                new RemoveBlock(),
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

    /** The layout spellings a caller may pass, which are the ones the tileset JSON uses. */
    private static String layoutNames() {
        List<String> names = new ArrayList<>();
        for (GridLayout layout : GridLayout.values()) names.add(TilesetExport.jsonLayout(layout));
        return String.join(", ", names);
    }

    /** The block members a document currently holds, by slot. */
    private static Map<String, TilesetExport.Entry> membersOf(TilesetDocument document,
                                                              String blockId) {
        Map<String, TilesetExport.Entry> held = new LinkedHashMap<>();
        for (TilesetExport.Entry entry : document.entries) {
            if (blockId.equals(entry.blockId)) held.put(entry.slot, entry);
        }
        return held;
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
                    + "you pass apply=true, which saves the reconciled pieces into the document. "
                    + "Applying is refused when it would drop entries that were decided rather "
                    + "than found — a cut plate's cells, or anything annotated — because slicing "
                    + "reads pixels and cannot bring those back.";
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
                    .bool("force", "Apply even though authored entries would be dropped. "
                            + "Default false. Only for a sheet whose annotation you mean to "
                            + "throw away; a cut plate is never that.")
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

            List<TilesetOperations.AtRisk> atRisk =
                    TilesetOperations.atRisk(reconciled.lost(), document.idPrefix);
            if (apply && !atRisk.isEmpty() && !arguments.optBoolean("force", false)) {
                return McpToolResult.failure(name + " at alpha " + alphaMin + ": "
                        + TilesetOperations.discardWarning(atRisk)
                        + " Nothing was written. Pass force=true to apply anyway.");
            }

            String applied = "";
            if (apply) {
                document.alphaMin = alphaMin;
                document.entries = reconciled.entries();
                document.write(TilesetDocument.pathFor(context.projectRoot(), name));
                applied = "\nSaved into "
                        + TilesetDocument.pathFor(context.projectRoot(), name);
            }

            JSONArray atRiskArray = new JSONArray();
            for (TilesetOperations.AtRisk risk : atRisk) {
                atRiskArray.put(new JSONObject().put("id", risk.id()).put("reason", risk.reason()));
            }

            JSONObject structured = new JSONObject();
            structured.put("alphaMin", alphaMin);
            structured.put("sheetWidth", sheet.getWidth());
            structured.put("sheetHeight", sheet.getHeight());
            structured.put("carried", reconciled.carried());
            structured.put("added", reconciled.added());
            structured.put("lost", new JSONArray(reconciled.lostIds()));
            structured.put("atRisk", atRiskArray);
            structured.put("applied", apply);
            structured.put("pieces", pieces);
            return McpToolResult.of(name + " at alpha " + alphaMin + ": "
                    + reconciled.summary() + applied, structured);
        }
    }

    /**
     * Measure where a stated grid actually sits, and move the sheet's cells onto
     * it.
     *
     * <p>Read-only unless asked to apply, and it applies only the axes that
     * measured well. A fit is evidence for an operator, and one that quietly
     * replaced a stated cut with a badly supported measurement would be the tool
     * overruling the person — which is the thing this whole surface is built not
     * to do.
     */
    private static final class FitGrid implements McpTool {

        @Override public String name() { return "tileset_fit_grid"; }

        @Override
        public String description() {
            return "Measure where a sheet's stated grid really sits and re-cut its cells onto "
                    + "it. A cut is the stated cols x rows PLUS an origin and a pitch per axis: "
                    + "generated art sits inside a margin and is rarely drawn to a pitch that "
                    + "divides its own pixel size evenly, so dividing the canvas puts every "
                    + "boundary in the wrong place and slivers of the next cell into every "
                    + "tile. The cell COUNT is never measured — it cannot be read off the "
                    + "pixels — only the placement is. Reports, per axis, how many of the "
                    + "boundaries landed on a real seam in the art and how far those seams sit "
                    + "from the straight line through them. Read-only unless you pass "
                    + "apply=true, which moves each cut cell onto its new rectangle keeping its "
                    + "id, block slot and every annotation, and applies ONLY the axes that "
                    + "measured well unless you also pass force=true.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name")
                    .bool("apply", "Store the measured placement and re-cut the sheet's cells. "
                            + "Default false — look at the residuals before you keep it.")
                    .bool("force", "Apply an axis whose fit is reported as not usable. Only when "
                            + "you have looked at why it was refused and decided anyway.")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            TilesetDocument document = documentFor(context.projectRoot(), name);
            BufferedImage sheet = TilesetOperations.readSheet(context.projectRoot(), document);
            GridCut stated = document.cut(sheet.getWidth(), sheet.getHeight());
            GridFit.Measured measured = GridFit.measure(sheet, stated);

            boolean force = arguments.optBoolean("force", false);
            GridCut fitted = force
                    ? stated.withColumnAxis(measured.columns().origin(), measured.columns().pitch())
                            .withRowAxis(measured.rows().origin(), measured.rows().pitch())
                    : measured.appliedTo(stated);

            StringBuilder text = new StringBuilder(name).append(": ").append(stated.describe())
                    .append("\n").append(measured.describe()).append('\n')
                    .append(axisComparison("columns", measured.columns(),
                            stated.originX(), stated.pitchX(), fitted.originX(), fitted.pitchX()))
                    .append('\n')
                    .append(axisComparison("rows", measured.rows(),
                            stated.originY(), stated.pitchY(), fitted.originY(), fitted.pitchY()));

            boolean apply = arguments.optBoolean("apply", false);
            TilesetOperations.Recut recut = null;
            if (apply) {
                recut = TilesetOperations.recut(document.entries, document.idPrefix, fitted);
                document.setCut(fitted);
                Path path = TilesetDocument.pathFor(context.projectRoot(), name);
                document.write(path);
                text.append("\n").append(recut.summary()).append("\nSaved into ").append(path);
            }

            JSONObject structured = new JSONObject();
            structured.put("stated", describeCut(stated));
            structured.put("fitted", describeCut(fitted));
            structured.put("columns", describeAxis(measured.columns()));
            structured.put("rows", describeAxis(measured.rows()));
            structured.put("applied", apply);
            structured.put("forced", force);
            if (recut != null) {
                structured.put("cellsReCut", recut.moved());
                structured.put("cellsMoved", recut.shifted());
                structured.put("worstShiftPx", recut.maxShift());
                structured.put("outsideTheCut", new JSONArray(recut.outside()));
            }
            return McpToolResult.of(text.toString(), structured);
        }

        /**
         * What each placement costs, measured against the same seams.
         *
         * <p>Both cuts scored against one observed boundary set is the only
         * comparison that means anything: a fit scored against its own peaks
         * always wins.
         */
        private static String axisComparison(String axisName, GridFit.Axis axis,
                                             double statedOrigin, double statedPitch,
                                             double fittedOrigin, double fittedPitch) {
            if (axis.seams().isEmpty()) {
                return "  " + axisName + ": no seams found, so there is nothing to compare";
            }
            return String.format(
                    "  %s: worst boundary %.1f px off the measured seams as stated, %.1f px as "
                            + "fitted", axisName,
                    axis.worstOffsetFrom(statedOrigin, statedPitch),
                    axis.worstOffsetFrom(fittedOrigin, fittedPitch));
        }

        private static JSONObject describeCut(GridCut cut) throws JSONException {
            return new JSONObject()
                    .put("cols", cut.cols()).put("rows", cut.rows())
                    .put("originX", cut.originX()).put("pitchX", cut.pitchX())
                    .put("originY", cut.originY()).put("pitchY", cut.pitchY());
        }

        private static JSONObject describeAxis(GridFit.Axis axis) throws JSONException {
            JSONArray seams = new JSONArray();
            for (GridFit.Seam seam : axis.seams()) {
                seams.put(new JSONObject().put("index", seam.index())
                        .put("position", seam.position()).put("strong", seam.strong()));
            }
            return new JSONObject()
                    .put("count", axis.count())
                    .put("origin", axis.origin())
                    .put("pitch", axis.pitch())
                    .put("strongSeams", axis.strongSeams())
                    .put("maxResidual", axis.maxResidual())
                    .put("rmsResidual", axis.rmsResidual())
                    .put("residualTolerance", axis.residualTolerance())
                    .put("trustworthy", axis.trustworthy())
                    .put("seams", seams);
        }
    }

    private static final class SplitOnGrid implements McpTool {

        @Override public String name() { return "tileset_split_on_grid"; }

        @Override
        public String description() {
            return "Cut a fused plate into the cells of its stated grid. A tileable plate is "
                    + "drawn edge to edge with no gutter, so slicing finds it as one piece and "
                    + "no threshold will ever separate it — the cut has to be stated. Each part "
                    + "becomes a one-cell piece in reading order, left to right then top to "
                    + "bottom, which is the order a block's slots are filled in, and is named "
                    + "for where it sits: <idPrefix>.c<col>r<row>, zero-based and column "
                    + "first, so doodad.urban.c6r1 is the seventh cell of the second row. The grid "
                    + "defaults to the document's own gridCols x gridRows and its cells need "
                    + "not be square. Read-only unless you pass apply=true, which replaces the "
                    + "plate with its parts in the document.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name")
                    .string("entryId", "Which piece to cut. Omit when the sheet has a single "
                            + "piece, which is what a fused plate slices to.")
                    .integer("cols", "Columns of the plate layout. Defaults to the document's "
                            + "gridCols. Stated, never measured.")
                    .integer("rows", "Rows of that layout. Defaults to the document's gridRows.")
                    .bool("apply", "Replace the plate with its parts in the document. "
                            + "Default false — look before you keep.")
                    .build();
        }

        /**
         * The piece to cut, or a stated reason there is no single answer.
         *
         * <p>Defaulting to the lone piece is the whole fused-plate case, but
         * defaulting to <em>the first</em> of several would silently shred a
         * sheet somebody had already annotated.
         */
        private static TilesetExport.Entry target(TilesetDocument document, String entryId) {
            if (!entryId.isEmpty()) {
                for (TilesetExport.Entry entry : document.entries) {
                    if (entry.id.equals(entryId)) return entry;
                }
                throw new IllegalArgumentException("no piece with id '" + entryId
                        + "'. The sheet holds: " + ids(document));
            }
            if (document.entries.size() != 1) {
                throw new IllegalArgumentException("this sheet has " + document.entries.size()
                        + " pieces, so there is no single plate to cut; name one with entryId. "
                        + "The sheet holds: " + ids(document));
            }
            return document.entries.get(0);
        }

        private static String ids(TilesetDocument document) {
            StringBuilder joined = new StringBuilder();
            for (TilesetExport.Entry entry : document.entries) {
                if (joined.length() > 0) joined.append(", ");
                joined.append(entry.id);
            }
            return joined.length() == 0 ? "(nothing)" : joined.toString();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            TilesetDocument document = documentFor(context.projectRoot(), name);
            if (document.entries.isEmpty()) {
                return McpToolResult.failure(name + " has no pieces to cut; slice it first with "
                        + "tileset_slice apply=true, which finds a fused plate as one piece.");
            }
            TilesetExport.Entry plate = target(document, arguments.optString("entryId", "").trim());

            int cols = arguments.optInt("cols", document.gridCols);
            int rows = arguments.optInt("rows", document.gridRows);
            if (cols < 1 || rows < 1) {
                return McpToolResult.failure("a grid needs at least one cell: " + cols + "x" + rows);
            }
            if (cols == 1 && rows == 1) {
                return McpToolResult.failure(TilesetOperations.DEGENERATE_GRID_MESSAGE);
            }

            int before = document.entries.size();
            // A measured placement is a property of the sheet, so cutting uses it
            // where there is one. It was measured for the layout the document
            // states, though, so a caller restating the layout gets the plate
            // divided and has to measure again.
            BufferedImage sheet = TilesetOperations.readSheet(context.projectRoot(), document);
            GridCut cut = document.cut(sheet.getWidth(), sheet.getHeight());
            boolean placed = cols == document.gridCols && rows == document.gridRows
                    && !cut.isDivisionOf(sheet.getWidth(), sheet.getHeight());
            List<TilesetExport.Entry> replaced = placed
                    ? TilesetOperations.splitOnGrid(
                            document.entries, entry -> entry == plate, document.idPrefix, cut)
                    : TilesetOperations.splitOnGrid(
                            document.entries, entry -> entry == plate, document.idPrefix, cols, rows);
            boolean apply = arguments.optBoolean("apply", false);

            // The parts replace the plate where it stood, so they are the run that
            // starts at its old index. Matching on the id prefix instead would
            // also claim a piece somebody had already named that way.
            int at = document.entries.indexOf(plate);
            JSONArray parts = new JSONArray();
            for (TilesetExport.Entry entry : replaced.subList(at, at + cols * rows)) {
                JSONObject described = new JSONObject();
                described.put("id", entry.id);
                described.put("rect", new JSONArray()
                        .put(entry.piece.x()).put(entry.piece.y())
                        .put(entry.piece.width()).put(entry.piece.height()));
                described.put("footprintCells", new JSONArray()
                        .put(entry.footprintX).put(entry.footprintY));
                parts.put(described);
            }

            String applied = "";
            if (apply) {
                // The parts are only meaningful next to the layout they were cut
                // to, so the document keeps the grid that produced them. A
                // placement measured for a different layout is not evidence about
                // this one, so restating the layout drops it.
                if (!placed) {
                    document.gridOriginX = null;
                    document.gridPitchX = null;
                    document.gridOriginY = null;
                    document.gridPitchY = null;
                }
                document.gridCols = cols;
                document.gridRows = rows;
                document.entries = replaced;
                Path path = TilesetDocument.pathFor(context.projectRoot(), name);
                document.write(path);
                applied = "\nSaved into " + path;
            }

            JSONObject structured = new JSONObject();
            structured.put("entryId", plate.id);
            structured.put("cols", cols);
            structured.put("rows", rows);
            structured.put("partCount", parts.length());
            structured.put("entriesBefore", before);
            structured.put("entriesAfter", replaced.size());
            structured.put("applied", apply);
            structured.put("parts", parts);
            return McpToolResult.of("Cut " + plate.id + " into " + parts.length()
                    + " parts on a stated " + cols + "x" + rows + " grid, each one cell"
                    + applied, structured);
        }
    }

    /**
     * The one authoring act nothing downstream can check.
     *
     * <p>Assigning a piece to a slot is what makes a wall a wall, and a mirrored
     * assignment produces a sheet that loads, resolves and renders opaque while
     * every room is inside out. No validation catches it, so this tool spends
     * its result on saying what each slot it filled <em>means</em>. That report
     * is the deliverable; the write is incidental.
     */
    private static final class SetBlock implements McpTool {

        @Override public String name() { return "tileset_set_block"; }

        @Override
        public String description() {
            return "Declare one autotile block on a sheet and assign pieces to the slots of "
                    + "its layout. This is how a wall or a corner set is authored: facing is "
                    + "never a field on a piece, it is which slot of a block's layout the "
                    + "piece fills. A slot name says WHERE THE EXTERIOR IS, not which "
                    + "neighbour is a wall - 'n' is the piece whose exposed face points "
                    + "north, and 'center' is the enclosed cell. Getting that backwards "
                    + "builds a sheet whose rooms are inside out, and it still loads, still "
                    + "resolves and is still opaque, so nothing later can detect it; this "
                    + "tool reports what every slot you filled means so you can check it "
                    + "against the art, and doing that check is the point of calling it. The "
                    + "block's layout and fill are replaced outright, while slots you do not "
                    + "name keep whatever they already hold, so a sheet can be grouped a few "
                    + "slots at a time. Preview by default - pass apply=true to save. Writes "
                    + "only under art-source/tilesets/.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name, as tileset_list reports it")
                    .requiredString("blockId", "Id of the block, e.g. reactor-hall.wall. "
                            + "Naming one that already exists redeclares its layout and fill.")
                    .requiredString("layout", "One of " + layoutNames() + ". wall-3x3 is the "
                            + "usual choice for a wall set: it leaves the fully enclosed case "
                            + "to the fill colour instead of to art.")
                    .string("fillRgb", "0xRRGGBB painted where the layout resolves to nothing "
                            + "- the interior of a wall-3x3, the open middle of a "
                            + "perimeter-3x3. Omit for a layout with no such case.")
                    .object("slots", "Slot name to piece id, e.g. "
                            + "{\"nw\": \"doodad.hall.piece-000\", \"n\": "
                            + "\"doodad.hall.piece-001\"}. A 3x3 layout's slots are nw, n, ne, "
                            + "w, center, e, sw, s, se, each meaning \"the exterior is on this "
                            + "side\"; a single layout has the one slot '" + BlockSlots.ONLY
                            + "'. Piece ids come from tileset_read_document or tileset_slice.",
                            false)
                    .bool("apply", "Save the assignment into the document. Default false - "
                            + "read back what each slot means before you keep it.")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            String blockId = arguments.optString("blockId", "").trim();
            if (blockId.isEmpty()) {
                return McpToolResult.failure("a blockId is required, e.g. " + name + ".wall");
            }

            String layoutName = arguments.optString("layout", "").trim();
            GridLayout layout;
            try {
                layout = GridLayout.fromJson(layoutName);
            } catch (IllegalArgumentException unknown) {
                return McpToolResult.failure("unknown layout '" + layoutName + "'. Use one of "
                        + layoutNames() + ".");
            }

            String fillText = arguments.optString("fillRgb", "").trim();
            Integer fillRgb;
            try {
                fillRgb = fillText.isEmpty() ? null : Integer.decode(fillText);
            } catch (NumberFormatException bad) {
                return McpToolResult.failure("fillRgb must look like 0x060A10, not '"
                        + fillText + "'.");
            }

            TilesetDocument document = documentFor(context.projectRoot(), name);
            List<String> layoutSlots = BlockSlots.of(layout);

            // Requested slots are normalized and checked against the layout before
            // any piece is looked up, so a caller who mistyped a slot is told about
            // the slot rather than about the piece that happened to be named in it.
            Map<String, String> requested = new LinkedHashMap<>();
            JSONObject slots = arguments.optJSONObject("slots");
            List<String> keys = new ArrayList<>();
            if (slots != null) {
                for (Iterator<?> named = slots.keys(); named.hasNext(); ) {
                    keys.add(String.valueOf(named.next()));
                }
            }
            Collections.sort(keys);
            for (String key : keys) {
                String slot = key.trim().toLowerCase();
                if (!BlockSlots.fits(layout, slot)) {
                    return McpToolResult.failure("'" + key + "' is not a slot of " + layoutName
                            + ". Its slots are " + String.join(", ", layoutSlots) + ".");
                }
                if (requested.containsKey(slot)) {
                    return McpToolResult.failure("'" + key + "' and '" + slot
                            + "' are the same slot; name it once.");
                }
                requested.put(slot, slots.optString(key, "").trim());
            }

            Map<String, TilesetExport.Entry> byId = new LinkedHashMap<>();
            for (TilesetExport.Entry entry : document.entries) byId.putIfAbsent(entry.id, entry);

            Map<String, TilesetExport.Entry> bySlot = new LinkedHashMap<>();
            Map<String, String> claimedBy = new LinkedHashMap<>();
            for (String slot : layoutSlots) {
                String pieceId = requested.get(slot);
                if (pieceId == null) continue;
                if (pieceId.isEmpty()) {
                    return McpToolResult.failure("slot '" + slot + "' names no piece. Drop the "
                            + "slot to leave it unfilled, or give it a piece id.");
                }
                TilesetExport.Entry entry = byId.get(pieceId);
                if (entry == null) {
                    return McpToolResult.failure("no piece '" + pieceId + "' in " + name
                            + ", which holds " + document.entries.size() + ". Read them with "
                            + "tileset_read_document, or slice the sheet if it has none yet.");
                }
                if (entry.isBlockMember() && !entry.blockId.equals(blockId)) {
                    return McpToolResult.failure("'" + pieceId + "' is already " + entry.blockId
                            + " / " + entry.slot + ". Dissolve that block with "
                            + "tileset_remove_block, or pick another piece.");
                }
                String already = claimedBy.putIfAbsent(pieceId, slot);
                if (already != null) {
                    return McpToolResult.failure("'" + pieceId + "' is assigned to both '"
                            + already + "' and '" + slot + "'; one piece fills one slot.");
                }
                bySlot.put(slot, entry);
            }

            for (TilesetExport.Entry entry : document.entries) {
                if (!blockId.equals(entry.blockId) || bySlot.containsValue(entry)) continue;
                if (!BlockSlots.fits(layout, entry.slot)) {
                    return McpToolResult.failure(blockId + " already holds '" + entry.id
                            + "' in slot '" + entry.slot + "', which " + layoutName
                            + " does not have. Dissolve the block with tileset_remove_block "
                            + "and author it again, or keep the layout it was built for.");
                }
            }

            List<TilesetExport.Entry> displaced = TilesetOperations.setBlock(
                    document.entries, document.blocks, blockId, layout, fillRgb, bySlot);

            boolean apply = arguments.optBoolean("apply", false);
            Path path = TilesetDocument.pathFor(context.projectRoot(), name);
            if (apply) document.write(path);

            Map<String, TilesetExport.Entry> held = membersOf(document, blockId);
            JSONArray described = new JSONArray();
            JSONArray unfilled = new JSONArray();
            StringBuilder text = new StringBuilder();
            text.append(blockId).append("  ").append(layoutName)
                    .append(fillRgb == null ? "" : String.format("  fill 0x%06X", fillRgb))
                    .append(apply ? "  - saved to " + path : "  - preview, nothing written");
            for (String slot : layoutSlots) {
                TilesetExport.Entry entry = held.get(slot);
                String means = BlockSlots.describe(slot);
                described.put(new JSONObject()
                        .put("slot", slot)
                        .put("means", means)
                        .put("piece", entry == null ? JSONObject.NULL : entry.id)
                        .put("assignedNow", entry != null && bySlot.get(slot) == entry));
                if (entry == null) unfilled.put(slot);
                text.append(String.format("%n  %-6s %-30s %s", slot,
                        entry == null ? "(unfilled)" : entry.id, means));
            }
            text.append("\n\nEach line reads \"the exterior is on this side\". Check it "
                    + "against the art: a mirrored assignment still loads and still resolves, "
                    + "so this is the last point at which it can be caught.");
            JSONArray displacedIds = new JSONArray();
            for (TilesetExport.Entry entry : displaced) {
                displacedIds.put(entry.id);
                text.append("\nDisplaced out of the block: ").append(entry.id);
            }

            JSONObject structured = new JSONObject();
            structured.put("path", path.toString());
            structured.put("blockId", blockId);
            structured.put("layout", layoutName);
            structured.put("fillRgb", fillRgb == null ? JSONObject.NULL
                    : String.format("0x%06X", fillRgb));
            structured.put("applied", apply);
            structured.put("assigned", bySlot.size());
            structured.put("slots", described);
            structured.put("unfilled", unfilled);
            structured.put("displaced", displacedIds);
            return McpToolResult.of(text.toString(), structured);
        }
    }

    private static final class RemoveBlock implements McpTool {

        @Override public String name() { return "tileset_remove_block"; }

        @Override
        public String description() {
            return "Dissolve one autotile block on a sheet. Its declaration is dropped and "
                    + "every piece that filled a slot goes back to being a doodad, keeping its "
                    + "own id, footprint and annotation - only the membership is withdrawn. "
                    + "Use it to re-author a block under a different layout, or to undo a "
                    + "grouping. Reports every member it releases and which slot each held. "
                    + "Preview by default - pass apply=true to save. Writes only under "
                    + "art-source/tilesets/.";
        }

        @Override
        public JSONObject inputSchema() {
            return McpSchema.object()
                    .requiredString("name", "The sheet's base name, as tileset_list reports it")
                    .requiredString("blockId", "Id of the block to dissolve, as "
                            + "tileset_read_document reports it")
                    .bool("apply", "Save the removal into the document. Default false - see "
                            + "what it releases before you keep it.")
                    .build();
        }

        @Override
        public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
            String name = requireSheetName(arguments);
            String blockId = arguments.optString("blockId", "").trim();
            if (blockId.isEmpty()) return McpToolResult.failure("a blockId is required");

            TilesetDocument document = documentFor(context.projectRoot(), name);
            Map<String, TilesetExport.Entry> held = membersOf(document, blockId);
            boolean declared = false;
            List<String> known = new ArrayList<>();
            for (TilesetExport.BlockSpec spec : document.blocks) {
                known.add(spec.id);
                if (spec.id.equals(blockId)) declared = true;
            }
            if (!declared && held.isEmpty()) {
                return McpToolResult.failure("no block '" + blockId + "' in " + name + ". "
                        + (known.isEmpty() ? "It declares none."
                        : "It declares " + String.join(", ", known) + "."));
            }

            // Read out before the release, which is what clears the slots.
            JSONArray released = new JSONArray();
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, TilesetExport.Entry> member : held.entrySet()) {
                String means = BlockSlots.describe(member.getKey());
                released.put(new JSONObject()
                        .put("slot", member.getKey())
                        .put("piece", member.getValue().id)
                        .put("means", means));
                text.append(String.format("%n  %-6s %-30s %s", member.getKey(),
                        member.getValue().id, means));
            }

            TilesetOperations.removeBlock(document.entries, document.blocks, blockId);

            boolean apply = arguments.optBoolean("apply", false);
            Path path = TilesetDocument.pathFor(context.projectRoot(), name);
            if (apply) document.write(path);

            JSONObject structured = new JSONObject();
            structured.put("path", path.toString());
            structured.put("blockId", blockId);
            structured.put("applied", apply);
            structured.put("released", released);
            return McpToolResult.of(blockId + " dissolved, " + held.size()
                    + " pieces back to doodads"
                    + (apply ? " - saved to " + path : " - preview, nothing written")
                    + text, structured);
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
