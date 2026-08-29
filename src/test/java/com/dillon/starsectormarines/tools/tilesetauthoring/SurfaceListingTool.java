package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.mcp.McpSchema;
import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The purpose-first entry point.
 *
 * <p>Every other tileset tool starts from a sheet. This one starts from a need,
 * because that is how the work arrives: a wall is wanted, or a floor, and the
 * sheet it might come from is the answer rather than the question. Asking
 * sheet-first means already knowing which of eight documents to open, which is
 * exactly the knowledge a person who needs a wall does not have.
 *
 * <p>Read-only. It writes nothing and changes nothing; the operations it points
 * at — {@code tileset_read_document} to look at a candidate's slicing,
 * {@code tileset_set_block} to author a new one — are the existing sheet-first
 * tools, reached now from the purpose instead of from a filename.
 */
final class SurfaceListingTool implements McpTool {

    @Override
    public String name() {
        return "tileset_surfaces";
    }

    @Override
    public String description() {
        return "Answer \"what can be a wall\" - list what map generation can ask for and every "
                + "block in the project that could fill it. A surface is a GroundKind or a "
                + "SurfaceRole; its candidates are the blocks of the same shape, whichever "
                + "sheet they were packed on, with the one the mapping currently uses marked. "
                + "Each candidate says whether its slicing can be edited: a block declared by "
                + "an authoring document can be re-cut, while one that exists only in an "
                + "exported tileset can be seen and mapped but not re-cut, because nothing "
                + "records which pieces of which raw sheet it was made from. Start here when "
                + "you need a KIND of art; use tileset_list when you already have a sheet to "
                + "ingest. Writes nothing.";
    }

    @Override
    public JSONObject inputSchema() {
        return McpSchema.object()
                .string("surface", "One surface to report in full, e.g. WALL or GRASS. Omit for "
                        + "every surface, one line each.")
                .string("shape", "Only surfaces filled by blocks of this shape, e.g. wall-3x3 "
                        + "or " + SurfaceCatalog.VARIANT_POOL + ". This is the \"show me the "
                        + "walls\" filter: shape is what decides whether one block can stand "
                        + "in for another.")
                .build();
    }

    @Override
    public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
        List<SurfaceCatalog.Purpose> all = SurfaceCatalog.scan(context.projectRoot());

        List<SurfaceCatalog.Purpose> purposes = all;
        String shape = arguments.optString("shape", "").trim();
        if (!shape.isEmpty()) {
            purposes = SurfaceCatalog.withShape(all, shape);
            if (purposes.isEmpty()) {
                return McpToolResult.failure("no surface is filled by a " + shape
                        + " block. In use: " + String.join(", ", SurfaceCatalog.shapes(all)));
            }
        }

        String wanted = arguments.optString("surface", "").trim();
        if (!wanted.isEmpty()) {
            SurfaceCatalog.Purpose one = purposes.stream()
                    .filter(purpose -> purpose.name().equalsIgnoreCase(wanted))
                    .findFirst()
                    .orElse(null);
            if (one == null) {
                return McpToolResult.failure("no surface named '" + wanted
                        + "'. Call with no arguments to see them all.");
            }
            return McpToolResult.of(detail(one), describe(one));
        }

        StringBuilder text = new StringBuilder();
        JSONArray described = new JSONArray();
        for (SurfaceCatalog.Purpose purpose : purposes) {
            described.put(describe(purpose));
            text.append(summarise(purpose)).append(System.lineSeparator());
        }
        return McpToolResult.of(text.toString().trim(),
                new JSONObject().put("surfaces", described));
    }

    private static String summarise(SurfaceCatalog.Purpose purpose) {
        int alternatives = purpose.alternatives().size();
        return String.format("%-12s %-13s %-22s %s",
                purpose.name(),
                purpose.vocabulary(),
                purpose.isUnmapped() ? "unmapped" : purpose.mappedId(),
                alternatives == 0 ? "no alternative"
                        : alternatives + " alternative" + (alternatives == 1 ? "" : "s"));
    }

    private static String detail(SurfaceCatalog.Purpose purpose) {
        String newline = System.lineSeparator();
        StringBuilder text = new StringBuilder();
        text.append(purpose.name()).append("  (").append(purpose.vocabulary()).append(')')
                .append(newline);
        if (purpose.isUnmapped()) {
            text.append("  nothing is mapped here - the renderer special-cases it, or it is "
                    + "not built yet").append(newline);
            return text.toString();
        }
        text.append("  shape: ").append(purpose.shape()).append(newline);
        for (SurfaceCatalog.Candidate candidate : purpose.candidates()) {
            text.append("  ").append(candidate.describe()).append(newline);
            if (candidate.isEditable()) {
                text.append("      look at its slicing: tileset_read_document name=")
                        .append(candidate.sheetName()).append(newline);
            }
        }
        if (purpose.alternatives().isEmpty()) {
            text.append("  nothing else could fill this yet. To add one: cut a sheet, then "
                            + "tileset_set_block layout=")
                    .append(purpose.shape()).append(newline);
        }
        return text.toString();
    }

    private static JSONObject describe(SurfaceCatalog.Purpose purpose) throws JSONException {
        JSONArray candidates = new JSONArray();
        for (SurfaceCatalog.Candidate candidate : purpose.candidates()) {
            JSONArray slots = new JSONArray();
            for (SurfaceCatalog.Slot slot : candidate.slots()) {
                slots.put(new JSONObject()
                        .put("slot", slot.slot())
                        .put("pieceId", slot.pieceId())
                        .put("included", slot.included()));
            }
            candidates.put(new JSONObject()
                    .put("blockId", candidate.blockId())
                    .put("shape", candidate.shape())
                    .put("sheet", candidate.sheetName())
                    .put("document", candidate.document() == null
                            ? JSONObject.NULL : candidate.document().toString())
                    .put("editable", candidate.isEditable())
                    .put("inUse", candidate.inUse())
                    .put("slots", slots));
        }
        return new JSONObject()
                .put("surface", purpose.name())
                .put("vocabulary", purpose.vocabulary())
                .put("mappedId", purpose.mappedId() == null
                        ? JSONObject.NULL : purpose.mappedId())
                .put("shape", purpose.shape() == null ? JSONObject.NULL : purpose.shape())
                .put("candidates", candidates);
    }
}
