package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.DoodadDef.WallSide;
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
    /**
     * The plate layout this sheet was drawn to, as columns and rows.
     *
     * <p>Stated rather than measured: it is what the sheet was generated to, and
     * it cannot be read off the pixels. Cells need not be square — a 20-frame
     * strip is {@code 20 x 1} — and every cell size is derived from this and the
     * sheet's own size rather than the other way round.
     *
     * <p>{@code 1 x 1} means the sheet is not a plate: a cut-out sheet's pieces
     * are found by alpha and their footprints are authored rather than guessed.
     */
    public int gridCols = 1;
    public int gridRows = 1;
    /**
     * Where the stated grid actually sits, per axis, in sheet pixels.
     *
     * <p>Null until something has measured the sheet, in which case the cut
     * falls back to dividing the canvas — see {@link #cut}. The two halves of an
     * axis are set together or not at all; the two axes are independent, because
     * a plate's columns can be measurable while its rows are not. See
     * {@link GridCut} for why a cut is an origin and a pitch rather than a
     * rectangle, and {@link GridFit} for what may and may not be measured.
     */
    public Double gridOriginX;
    public Double gridPitchX;
    public Double gridOriginY;
    public Double gridPitchY;
    /**
     * Where the packed atlas is written, relative to {@code mod/}. Empty derives
     * it from what the sheet contains — see {@link #defaultOutputSheet}.
     */
    public String outputSheet = "";
    /**
     * A standing note about the sheet itself, shown when it is opened.
     *
     * <p>Slice settings say how to cut a sheet up but not what is true of it —
     * that it has no alpha channel and arrives as one fused plate, that it is a
     * patch composited into another sheet rather than a tileset, that its frames
     * are not square. A seed written before anyone has opened the sheet is
     * exactly where that belongs, and without somewhere to put it the next
     * reader re-derives it by failing.
     */
    public String note = "";
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
            if (entry.ballisticHalfHeight != null) {
                o.put("ballisticHalfHeight", entry.ballisticHalfHeight.doubleValue());
            }
            if (!entry.preferredWallSide.isEmpty()) {
                o.put("preferredWallSide", entry.preferredWallSide);
            }
            o.put("included", entry.included);
            if (entry.isBlockMember()) {
                o.put("block", entry.blockId);
                o.put("slot", entry.slot);
            }
            if (!entry.note.isEmpty()) o.put("note", entry.note);
            if (!entry.standsInFor.isEmpty()) o.put("standsInFor", entry.standsInFor);
            if (!entry.tags.isEmpty()) o.put("tags", new JSONArray(entry.tags));
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
        if (!note.isEmpty()) root.put("note", note);
        root.put("sheetName", sheetName);
        root.put("idPrefix", idPrefix);
        root.put("cellPx", cellPx);
        root.put("alphaMin", alphaMin);
        root.put("gridCols", gridCols);
        root.put("gridRows", gridRows);
        JSONObject cut = new JSONObject();
        if (gridOriginX != null && gridPitchX != null) {
            cut.put("originX", gridOriginX);
            cut.put("pitchX", gridPitchX);
        }
        if (gridOriginY != null && gridPitchY != null) {
            cut.put("originY", gridOriginY);
            cut.put("pitchY", gridPitchY);
        }
        if (cut.length() > 0) root.put("cut", cut);
        if (!outputSheet.isEmpty()) root.put("outputSheet", outputSheet);
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
        doc.gridCols = Math.max(1, root.optInt("gridCols", 1));
        doc.gridRows = Math.max(1, root.optInt("gridRows", 1));
        JSONObject cut = root.optJSONObject("cut");
        if (cut != null) {
            if (cut.has("originX") && cut.has("pitchX")) {
                doc.gridOriginX = cut.getDouble("originX");
                doc.gridPitchX = cut.getDouble("pitchX");
            }
            if (cut.has("originY") && cut.has("pitchY")) {
                doc.gridOriginY = cut.getDouble("originY");
                doc.gridPitchY = cut.getDouble("pitchY");
            }
        }
        doc.outputSheet = root.optString("outputSheet", "");
        doc.note = root.optString("note", "");
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
            if (o.has("ballisticHalfHeight")) {
                double height = o.getDouble("ballisticHalfHeight");
                if (!Double.isFinite(height) || height < 0) {
                    throw new JSONException("piece '" + entry.id + "' has an impossible "
                            + "ballisticHalfHeight " + height);
                }
                entry.ballisticHalfHeight = height;
            }
            // Parsed on read so a side the game would reject cannot reach a
            // document: the write path proves a document reads back, and this is
            // the only place that check can happen.
            String wallSide = o.optString("preferredWallSide", "").trim();
            if (!wallSide.isEmpty()) {
                entry.preferredWallSide = WallSide.fromJson(wallSide).name();
            }
            entry.included = o.optBoolean("included", true);
            entry.blockId = o.optString("block", "");
            entry.slot = o.optString("slot", "");
            entry.note = o.optString("note", "");
            entry.standsInFor = o.optString("standsInFor", "");
            JSONArray tags = o.optJSONArray("tags");
            for (int t = 0; tags != null && t < tags.length(); t++) {
                entry.tags.add(tags.getString(t));
            }
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

    /**
     * What a re-slice did to the annotations that were already there.
     *
     * <p>{@code lost} holds the prior entries themselves rather than their names,
     * because what a caller has to decide about them is whether they were
     * authored — see {@link TilesetOperations#atRisk} — and an id alone cannot
     * answer that.
     */
    public record Reconciliation(List<TilesetExport.Entry> entries, int carried, int added,
                                 List<TilesetExport.Entry> lost) {

        public List<String> lostIds() {
            List<String> ids = new ArrayList<>();
            for (TilesetExport.Entry entry : lost) ids.add(entry.id);
            return ids;
        }

        public String summary() {
            String text = carried + " kept, " + added + " new";
            if (lost.isEmpty()) return text;
            return text + ", " + lost.size() + " no longer found: "
                    + String.join(", ", lostIds());
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
     * prior entry that matches nothing is reported rather than dropped quietly —
     * it is the case where the operator needs to look.
     */
    public static Reconciliation reconcile(List<SheetSlicer.Piece> pieces,
                                           List<TilesetExport.Entry> prior,
                                           String idPrefix, int cellPxX, int cellPxY) {
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
                entry.footprintX = guessFootprint(piece.width(), cellPxX);
                entry.footprintY = guessFootprint(piece.height(), cellPxY);
                taken.add(entry.id);
                added++;
            }
            result.add(entry);
        }
        List<TilesetExport.Entry> lost = new ArrayList<>();
        for (int p = 0; p < prior.size(); p++) {
            if (!claimed[p]) lost.add(prior.get(p));
        }
        return new Reconciliation(result, carried, added, lost);
    }

    /**
     * A first guess only: how many cells the art spans on the sheet's own grid,
     * rounded. Anything near the middle of two cell counts is exactly the case a
     * human has to settle.
     *
     * <p>Guessed per axis, because a sheet's cells are not square in general.
     */
    public static int guessFootprint(int pixels, int cellPx) {
        if (cellPx <= 0) return 1;
        return Math.max(1, Math.round(pixels / (float) cellPx));
    }

    /**
     * The cut this document states, placed on a sheet of the given size.
     *
     * <p>An axis nobody has measured falls back to dividing the canvas, which is
     * what a stated layout alone can say. An axis that has been measured keeps
     * its measured origin and pitch, so re-cutting the sheet reproduces the same
     * cells without measuring it again.
     */
    public GridCut cut(int sheetWidth, int sheetHeight) {
        GridCut cut = GridCut.dividing(sheetWidth, sheetHeight,
                Math.max(1, gridCols), Math.max(1, gridRows));
        if (gridOriginX != null && gridPitchX != null && gridPitchX > 0) {
            cut = cut.withColumnAxis(gridOriginX, gridPitchX);
        }
        if (gridOriginY != null && gridPitchY != null && gridPitchY > 0) {
            cut = cut.withRowAxis(gridOriginY, gridPitchY);
        }
        return cut;
    }

    /**
     * Record a cut, keeping only the placement.
     *
     * <p>The counts stay where they are: they are stated by an operator and a
     * measurement never revises them.
     */
    public void setCut(GridCut cut) {
        gridOriginX = cut.originX();
        gridPitchX = cut.pitchX();
        gridOriginY = cut.originY();
        gridPitchY = cut.pitchY();
    }

    /** One cell's width in source pixels, given the sheet this document annotates. */
    public int cellPxX(int sheetWidth) {
        return whole(gridPitchX, sheetWidth, gridCols);
    }

    /** One cell's height in source pixels. Not the same number as {@link #cellPxX}. */
    public int cellPxY(int sheetHeight) {
        return whole(gridPitchY, sheetHeight, gridRows);
    }

    /** A measured pitch where there is one, and the canvas division where there is not. */
    private static int whole(Double measured, int extent, int count) {
        double pitch = measured != null && measured > 0
                ? measured : extent / (double) Math.max(1, count);
        return Math.max(1, (int) Math.round(pitch));
    }

    /**
     * Where a sheet's atlas belongs when the document does not say.
     *
     * <p>A sheet that declares autotile blocks is terrain and belongs with the
     * tilesets; one that is only props belongs with the doodads. The old fixed
     * {@code graphics/doodads/} destination was right when the tool could only
     * make props and wrong the moment it could author a wall.
     */
    public static String defaultOutputSheet(String sheetName, boolean hasBlocks) {
        return (hasBlocks ? "graphics/tilesets/" : "graphics/doodads/") + sheetName + ".png";
    }

    /** The atlas destination this document asks for, relative to {@code mod/}. */
    public String resolvedOutputSheet(boolean hasBlocks) {
        return outputSheet.isEmpty() ? defaultOutputSheet(sheetName, hasBlocks) : outputSheet;
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
