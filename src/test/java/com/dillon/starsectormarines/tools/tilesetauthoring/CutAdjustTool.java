package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.mcp.McpSchema;
import com.dillon.starsectormarines.tools.mcp.McpTool;
import com.dillon.starsectormarines.tools.mcp.McpToolContext;
import com.dillon.starsectormarines.tools.mcp.McpToolResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Move one piece's cut without disturbing any other.
 *
 * <p>The headless half of the same operation the workbench offers. A cut that
 * is two pixels wrong is found by looking at the art, and fixing it is a small
 * exact edit — which is a thing worth being able to do from a script or a model
 * as well as from the window.
 */
final class CutAdjustTool implements McpTool {

    @Override
    public String name() {
        return "tileset_set_cut";
    }

    @Override
    public String description() {
        return "Move one piece's rectangle on its sheet, leaving every other piece alone. "
                + "Slicing keys on alpha and splitting divides by a stated pitch; both are "
                + "right most of the time and wrong for a particular piece - a prop whose "
                + "contact shadow was keyed away with it, a cell whose seam sits a pixel off "
                + "the line through its neighbours. Re-slicing or re-fitting to correct one of "
                + "those moves every piece on the sheet, which is a poor trade for two pixels. "
                + "The piece keeps its id, footprint, block membership and annotation: only "
                + "where the picture is cut from changes. Preview by default - pass apply=true "
                + "to save. Writes only under art-source/tilesets/, and does not export.";
    }

    @Override
    public JSONObject inputSchema() {
        return McpSchema.object()
                .requiredString("name", "The sheet's base name, as tileset_list reports it")
                .requiredString("entryId", "Id of the piece to move, as tileset_read_document "
                        + "reports it")
                .integer("x", "Left edge on the sheet, in pixels. Omit to keep it.")
                .integer("y", "Top edge on the sheet, in pixels. Omit to keep it.")
                .integer("width", "Width in pixels. Omit to keep it.")
                .integer("height", "Height in pixels. Omit to keep it.")
                .bool("apply", "Save the moved cut into the document. Default false - read back "
                        + "what it became before you keep it.")
                .build();
    }

    @Override
    public McpToolResult call(JSONObject arguments, McpToolContext context) throws Exception {
        String name = arguments.optString("name", "").trim();
        if (name.isEmpty()) return McpToolResult.failure("a sheet name is required");
        String entryId = arguments.optString("entryId", "").trim();
        if (entryId.isEmpty()) return McpToolResult.failure("an entryId is required");

        Path projectRoot = context.projectRoot();
        Path documentPath = TilesetDocument.pathFor(projectRoot, name);
        TilesetDocument document = TilesetDocument.read(documentPath);

        TilesetExport.Entry entry = null;
        for (TilesetExport.Entry candidate : document.entries) {
            if (candidate.id.equals(entryId)) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            return McpToolResult.failure("no piece '" + entryId + "' on " + name
                    + ", which holds " + document.entries.size()
                    + ". Read them with tileset_read_document.");
        }

        BufferedImage sheet = TilesetOperations.readSheet(projectRoot, document);
        SheetSlicer.Piece was = entry.piece;
        int x = arguments.optInt("x", was.x());
        int y = arguments.optInt("y", was.y());
        int width = arguments.optInt("width", was.width());
        int height = arguments.optInt("height", was.height());

        try {
            TilesetOperations.setCut(document.entries, entryId, x, y, width, height,
                    sheet.getWidth(), sheet.getHeight());
        } catch (IOException refused) {
            // An off-sheet or empty rectangle is a mistake somebody makes while
            // nudging, not a broken tool. It reads as a refusal, not a trace.
            return McpToolResult.failure(refused.getMessage());
        }
        SheetSlicer.Piece now = entry.piece;

        boolean apply = arguments.optBoolean("apply", false);
        if (apply) document.write(documentPath);

        String text = entryId + (apply ? " moved" : " would move") + " from "
                + describe(was) + " to " + describe(now)
                + (apply ? "\nSaved to " + documentPath
                         + "\nExport the sheet to pack the new cut into the atlas."
                         : "\nNothing written. Pass apply=true to keep it.");
        return McpToolResult.of(text, new JSONObject()
                .put("entryId", entryId)
                .put("applied", apply)
                .put("was", rect(was))
                .put("now", rect(now)));
    }

    private static String describe(SheetSlicer.Piece piece) {
        return piece.x() + "," + piece.y() + " of " + piece.width() + "x" + piece.height();
    }

    private static JSONObject rect(SheetSlicer.Piece piece) throws JSONException {
        return new JSONObject()
                .put("x", piece.x()).put("y", piece.y())
                .put("width", piece.width()).put("height", piece.height());
    }
}
