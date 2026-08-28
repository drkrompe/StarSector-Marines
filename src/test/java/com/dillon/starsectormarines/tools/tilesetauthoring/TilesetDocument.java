package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One sheet's annotation pass, saved so it can be picked up again.
 *
 * <p>Slicing a sheet is mechanical and repeatable; deciding what each piece
 * <em>is</em> — its id, how much deck it covers, whether it ships at all — is
 * neither, and until this document existed those decisions lived only in the
 * table of an open window. A sheet therefore had to be annotated in one sitting
 * or not at all, which for a sheet of any size means not at all.
 *
 * <p>The document annotates a raw sheet rather than containing it. It stores the
 * slice parameters that produced its pieces alongside the annotations, because a
 * piece list is only meaningful next to the threshold that found it.
 */
public final class TilesetDocument {

    /** Project-relative path to the raw sheet, or absolute when it lies outside the project. */
    public String sheet = "";
    /** Base name for the exported atlas and tileset. */
    public String sheetName = "sheet";
    public String idPrefix = "doodad.sheet";
    public int cellPx = 64;
    public int alphaMin = SheetSlicer.DEFAULT_ALPHA_MIN;
    /** Cell size used to split fused plates and to guess footprints, in source pixels. */
    public int gridCell = 104;
    public List<TilesetExport.Entry> entries = new ArrayList<>();
    /** The named autotile blocks the entries may belong to. */
    public List<TilesetExport.BlockSpec> blocks = new ArrayList<>();

    /** The conventional location of a sheet's authoring document, given the project root. */
    public static Path pathFor(Path projectRoot, String sheetName) {
        return projectRoot.resolve("art-source/tilesets").resolve(sheetName + ".tileset-authoring.json");
    }

    public JSONObject toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (TilesetExport.Entry entry : entries) {
            SheetSlicer.Piece p = entry.piece;
            JSONObject o = new JSONObject();
            o.put("id", entry.id);
            o.put("rect", new JSONArray().put(p.x()).put(p.y()).put(p.width()).put(p.height()));
            o.put("footprintCells", new JSONArray().put(entry.footprintX).put(entry.footprintY));
            o.put("cover", entry.cover);
            o.put("included", entry.included);
            if (entry.isBlockMember()) {
                o.put("block", entry.blockId);
                o.put("slot", entry.slot);
            }
            array.put(o);
        }
        JSONArray blockArray = new JSONArray();
        for (TilesetExport.BlockSpec spec : blocks) {
            JSONObject o = new JSONObject();
            o.put("id", spec.id);
            o.put("layout", TilesetExport.jsonLayout(spec.layout));
            if (spec.fillRgb != null) o.put("fillRgb", String.format("0x%06X", spec.fillRgb));
            blockArray.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("blocks", blockArray);
        root.put("sheet", sheet);
        root.put("sheetName", sheetName);
        root.put("idPrefix", idPrefix);
        root.put("cellPx", cellPx);
        root.put("alphaMin", alphaMin);
        root.put("gridCell", gridCell);
        root.put("entries", array);
        return root;
    }

    public static TilesetDocument fromJson(JSONObject root) throws JSONException {
        TilesetDocument doc = new TilesetDocument();
        doc.sheet = root.getString("sheet");
        doc.sheetName = root.optString("sheetName", "sheet");
        doc.idPrefix = root.optString("idPrefix", "doodad." + doc.sheetName);
        doc.cellPx = root.optInt("cellPx", 64);
        doc.alphaMin = root.optInt("alphaMin", SheetSlicer.DEFAULT_ALPHA_MIN);
        doc.gridCell = root.optInt("gridCell", 104);
        JSONArray blockArray = root.optJSONArray("blocks");
        for (int i = 0; blockArray != null && i < blockArray.length(); i++) {
            JSONObject o = blockArray.getJSONObject(i);
            doc.blocks.add(new TilesetExport.BlockSpec(
                    o.getString("id"),
                    GridLayout.fromJson(o.getString("layout")),
                    o.has("fillRgb") ? Integer.decode(o.getString("fillRgb")) : null));
        }
        JSONArray array = root.optJSONArray("entries");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            JSONArray rect = o.getJSONArray("rect");
            SheetSlicer.Piece piece = new SheetSlicer.Piece(
                    rect.getInt(0), rect.getInt(1), rect.getInt(2), rect.getInt(3));
            TilesetExport.Entry entry = new TilesetExport.Entry(piece, o.getString("id"));
            JSONArray footprint = o.optJSONArray("footprintCells");
            if (footprint != null && footprint.length() == 2) {
                entry.footprintX = Math.max(1, footprint.getInt(0));
                entry.footprintY = Math.max(1, footprint.getInt(1));
            }
            entry.cover = o.optString("cover", "none");
            entry.included = o.optBoolean("included", true);
            entry.blockId = o.optString("block", "");
            entry.slot = o.optString("slot", "");
            doc.entries.add(entry);
        }
        return doc;
    }

    public static TilesetDocument read(Path path) throws IOException, JSONException {
        return fromJson(new JSONObject(Files.readString(path, StandardCharsets.UTF_8)));
    }

    /**
     * Replace the document atomically, after proving it reads back.
     *
     * <p>A half-written document is worse than a missing one: it is the record of
     * work that cannot be redone mechanically.
     */
    public void write(Path path) throws IOException, JSONException {
        JSONObject json = toJson();
        fromJson(json);
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temp, json.toString(2), StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** What a re-slice did to the annotations that were already there. */
    public record Reconciliation(List<TilesetExport.Entry> entries, int carried, int added,
                                 List<String> lost) {

        public String summary() {
            String text = carried + " kept, " + added + " new";
            if (lost.isEmpty()) return text;
            return text + ", " + lost.size() + " no longer found: " + String.join(", ", lost);
        }
    }

    /**
     * Overlap below which two boxes are different pieces rather than the same
     * piece found at a different threshold. Raising the alpha threshold shrinks
     * every box a little, so the match cannot require identical bounds; lowering
     * it can fuse two pieces into one, which should read as a loss rather than
     * as a silent inheritance of one of their annotations.
     */
    private static final double MIN_OVERLAP = 0.5;

    /**
     * Carry existing annotations onto a freshly sliced piece list.
     *
     * <p>Re-slicing is how a sheet's threshold gets tuned, and tuning it must not
     * cost the annotations already made. Pieces are matched by how much of their
     * area they share, best pair first, each prior annotation claimed once. A
     * prior entry that matches nothing is reported by id rather than dropped
     * quietly — it is the case where the operator needs to look.
     */
    public static Reconciliation reconcile(List<SheetSlicer.Piece> pieces,
                                           List<TilesetExport.Entry> prior,
                                           String idPrefix, int gridCell) {
        record Pair(int freshIndex, int priorIndex, double overlap) {}
        List<Pair> pairs = new ArrayList<>();
        for (int f = 0; f < pieces.size(); f++) {
            for (int p = 0; p < prior.size(); p++) {
                double overlap = overlap(pieces.get(f), prior.get(p).piece);
                if (overlap >= MIN_OVERLAP) pairs.add(new Pair(f, p, overlap));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::overlap).reversed());

        TilesetExport.Entry[] matched = new TilesetExport.Entry[pieces.size()];
        boolean[] claimed = new boolean[prior.size()];
        for (Pair pair : pairs) {
            if (matched[pair.freshIndex()] != null || claimed[pair.priorIndex()]) continue;
            matched[pair.freshIndex()] = prior.get(pair.priorIndex());
            claimed[pair.priorIndex()] = true;
        }

        Set<String> taken = new HashSet<>();
        for (TilesetExport.Entry entry : matched) {
            if (entry != null) taken.add(entry.id);
        }
        List<TilesetExport.Entry> result = new ArrayList<>();
        int carried = 0;
        int added = 0;
        for (int f = 0; f < pieces.size(); f++) {
            SheetSlicer.Piece piece = pieces.get(f);
            TilesetExport.Entry entry;
            if (matched[f] != null) {
                entry = matched[f];
                entry.piece = piece;
                carried++;
            } else {
                entry = new TilesetExport.Entry(piece, freeId(idPrefix, f, taken));
                entry.footprintX = guessFootprint(piece.width(), gridCell);
                entry.footprintY = guessFootprint(piece.height(), gridCell);
                taken.add(entry.id);
                added++;
            }
            result.add(entry);
        }
        List<String> lost = new ArrayList<>();
        for (int p = 0; p < prior.size(); p++) {
            if (!claimed[p]) lost.add(prior.get(p).id);
        }
        return new Reconciliation(result, carried, added, lost);
    }

    /**
     * A first guess only: how many cells the art spans on the sheet's own grid,
     * rounded. Anything near the middle of two cell counts is exactly the case a
     * human has to settle.
     */
    public static int guessFootprint(int pixels, int gridCell) {
        return Math.max(1, Math.round(pixels / (float) gridCell));
    }

    /** Reading-order id, stepped past any id a carried annotation already holds. */
    private static String freeId(String idPrefix, int index, Set<String> taken) {
        for (int n = index; ; n++) {
            String id = String.format("%s.piece-%03d", idPrefix, n);
            if (!taken.contains(id)) return id;
        }
    }

    /** Shared area over combined area — 1 for identical boxes, 0 for disjoint ones. */
    private static double overlap(SheetSlicer.Piece a, SheetSlicer.Piece b) {
        int left = Math.max(a.x(), b.x());
        int top = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        if (right < left || bottom < top) return 0;
        long shared = (long) (right - left + 1) * (bottom - top + 1);
        long combined = (long) a.width() * a.height() + (long) b.width() * b.height() - shared;
        return combined == 0 ? 0 : shared / (double) combined;
    }
}
