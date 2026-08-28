package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Packs authored pieces into an atlas the game can address, and writes the
 * tileset that describes it.
 *
 * <p><b>Each piece is stretched to fill its footprint.</b> A piece is not placed
 * at whatever size it happens to be drawn — it is resampled to exactly the cells
 * it was authored to occupy, because the footprint is a statement about how much
 * deck the thing covers, not about the art's pixel dimensions. A console
 * authored two cells long stretches across two cells whether it was drawn wide
 * or square, and that is intended rather than tolerated.
 *
 * <p>The atlas is addressed in cells of {@code cellPx}, which the tileset
 * declares and rendering honours, so a sheet drawn finer than the game's grid
 * keeps its detail instead of being resampled down to it.
 *
 * <p>A piece is either a <b>doodad</b>, placed wherever it fits, or a member of
 * a <b>block</b>, which is placed as a contiguous patch. A block's cells are
 * addressed by an origin plus the offset its {@link GridLayout} computes, so
 * scattering them across the shelves would make the block unaddressable — the
 * packer reserves the whole patch and reports where it put it. The origin in the
 * exported tileset is therefore a packer output, never a hand-counted number.
 */
public final class TilesetExport {

    private TilesetExport() {}

    /** Cells across the atlas before wrapping to a new shelf. Keeps the sheet roughly square. */
    private static final int ATLAS_COLUMNS = 16;

    /** One authored piece: where it came from, what it is, and how much deck it covers. */
    public static final class Entry {
        public SheetSlicer.Piece piece;
        public String id;
        public int footprintX = 1;
        public int footprintY = 1;
        public String cover = "none";
        public boolean included = true;
        /**
         * The block this piece belongs to, or empty for a doodad. A block member
         * has no id and no footprint of its own: it is one cell of a named block,
         * addressed through that block's layout.
         */
        public String blockId = "";
        /** Which cell of {@link #blockId} this piece is, as a {@link BlockSlots} name. */
        public String slot = "";
        /**
         * What this piece is for, in the author's words — the half of the
         * annotation that carries nuance an id and a cover level cannot.
         */
        public String note = "";
        /** The greppable half: short descriptive tags, lowercase. */
        public List<String> tags = new ArrayList<>();
        /** Assigned by {@link #pack}. */
        public int col;
        public int row;

        public Entry(SheetSlicer.Piece piece, String id) {
            this.piece = piece;
            this.id = id;
        }

        public boolean isBlockMember() {
            return !blockId.isEmpty();
        }
    }

    /**
     * A named autotile block: its layout, and the colour to paint where the
     * layout resolves to nothing.
     *
     * <p>The members are {@link Entry entries} that name this block; the spec
     * carries only what is true of the block as a whole.
     */
    public static final class BlockSpec {
        public String id;
        public GridLayout layout;
        /** {@code 0xRRGGBB} painted for the layout's null case, or null when it has none. */
        public Integer fillRgb;

        public BlockSpec(String id, GridLayout layout, Integer fillRgb) {
            this.id = id;
            this.layout = layout;
            this.fillRgb = fillRgb;
        }
    }

    /** Where the packer put everything: the atlas extent, and each block's origin. */
    public record Packing(int columns, int rows, Map<String, int[]> blockOrigins) {}

    /**
     * Shelf-pack the included entries, assigning each a cell origin.
     *
     * <p>Doodads are placed individually at their footprint size. A block's
     * members are placed together as one patch the size of its layout's span, so
     * that the block's own origin plus a layout offset lands on the right cell.
     * A block whose members do not fill every slot still reserves the whole
     * patch: the unfilled cells stay transparent, which is exactly what a hollow
     * layout's fill colour is for.
     */
    public static Packing pack(List<Entry> entries, List<BlockSpec> blocks) {
        Map<String, BlockSpec> specs = new LinkedHashMap<>();
        for (BlockSpec spec : blocks) specs.put(spec.id, spec);

        // One unit per doodad and per block, in the order their first piece appears,
        // so re-packing an unchanged document lays out identically.
        record Unit(String blockId, int width, int height, List<Entry> members) {}
        Map<String, List<Entry>> members = new LinkedHashMap<>();
        List<Unit> units = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.included) continue;
            if (!entry.isBlockMember()) {
                units.add(new Unit(null, entry.footprintX, entry.footprintY, List.of(entry)));
                continue;
            }
            if (members.containsKey(entry.blockId)) {
                members.get(entry.blockId).add(entry);
                continue;
            }
            List<Entry> group = new ArrayList<>();
            group.add(entry);
            members.put(entry.blockId, group);
            BlockSpec spec = specs.get(entry.blockId);
            int span = spec == null ? 3 : spec.layout.span();
            units.add(new Unit(entry.blockId, span, span, group));
        }

        Map<String, int[]> origins = new LinkedHashMap<>();
        int cursorX = 0;
        int cursorY = 0;
        int shelfHeight = 0;
        int widest = 0;
        for (Unit unit : units) {
            if (cursorX + unit.width() > ATLAS_COLUMNS && cursorX > 0) {
                cursorX = 0;
                cursorY += shelfHeight;
                shelfHeight = 0;
            }
            if (unit.blockId() == null) {
                Entry entry = unit.members().get(0);
                entry.col = cursorX;
                entry.row = cursorY;
            } else {
                origins.put(unit.blockId(), new int[]{cursorX, cursorY});
                for (Entry entry : unit.members()) {
                    int[] offset = BlockSlots.offset(entry.slot);
                    entry.col = cursorX + offset[0];
                    entry.row = cursorY + offset[1];
                }
            }
            cursorX += unit.width();
            shelfHeight = Math.max(shelfHeight, unit.height());
            widest = Math.max(widest, cursorX);
        }
        return new Packing(Math.max(1, widest), Math.max(1, cursorY + shelfHeight), origins);
    }

    /** Draw every included entry into its packed slot, stretched to fill it. */
    public static BufferedImage atlas(BufferedImage source, List<Entry> entries,
                                      List<BlockSpec> blocks, int cellPx) {
        Packing packing = pack(entries, blocks);
        BufferedImage atlas = new BufferedImage(
                packing.columns() * cellPx, packing.rows() * cellPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (Entry entry : entries) {
            if (!entry.included) continue;
            SheetSlicer.Piece p = entry.piece;
            // A block cell is one cell by definition; only a doodad claims deck.
            int width = entry.isBlockMember() ? 1 : entry.footprintX;
            int height = entry.isBlockMember() ? 1 : entry.footprintY;
            g.drawImage(
                    source.getSubimage(p.x(), p.y(), p.width(), p.height()),
                    entry.col * cellPx, entry.row * cellPx,
                    width * cellPx, height * cellPx, null);
        }
        g.dispose();
        return atlas;
    }

    /** The tileset document describing {@code sheetPath}'s blocks and doodads. */
    public static JSONObject tileset(String sheetPath, int cellPx, List<Entry> entries,
                                     List<BlockSpec> blocks) throws JSONException {
        Packing packing = pack(entries, blocks);

        JSONArray doodads = new JSONArray();
        for (Entry entry : entries) {
            if (!entry.included || entry.isBlockMember()) continue;
            JSONObject o = new JSONObject();
            o.put("id", entry.id);
            o.put("col", entry.col);
            o.put("row", entry.row);
            o.put("cover", entry.cover);
            if (entry.footprintX != 1 || entry.footprintY != 1) {
                o.put("footprintCells", new JSONArray().put(entry.footprintX).put(entry.footprintY));
            }
            doodads.put(o);
        }

        JSONArray blockArray = new JSONArray();
        for (BlockSpec spec : blocks) {
            int[] origin = packing.blockOrigins().get(spec.id);
            if (origin == null) continue;   // every member excluded — the block is not in this sheet
            JSONObject o = new JSONObject();
            o.put("id", spec.id);
            o.put("origin", new JSONArray().put(origin[0]).put(origin[1]));
            o.put("layout", jsonLayout(spec.layout));
            if (spec.fillRgb != null) o.put("fillRgb", String.format("0x%06X", spec.fillRgb));
            blockArray.put(o);
        }

        JSONObject root = new JSONObject();
        root.put("sheet", sheetPath);
        root.put("cellPx", cellPx);
        if (blockArray.length() > 0) root.put("blocks", blockArray);
        root.put("doodads", doodads);
        JSONArray cells = cells(entries);
        if (cells.length() > 0) root.put("cells", cells);
        return root;
    }

    /**
     * Per-cell labels for every packed piece.
     *
     * <p>The tileset schema already has a place for descriptive annotation that
     * generation and combat never read, keyed by the cell it describes. That is
     * exactly the authority a usage hint should have, so the note and tags go
     * there rather than becoming new fields on a doodad or a block — which would
     * make a doc string look like something the game acts on.
     */
    private static JSONArray cells(List<Entry> entries) throws JSONException {
        JSONArray cells = new JSONArray();
        for (Entry entry : entries) {
            if (!entry.included) continue;
            String name = entry.isBlockMember()
                    ? entry.blockId + " " + entry.slot
                    : entry.id;
            if (entry.note.isEmpty() && entry.tags.isEmpty() && entry.isBlockMember()) {
                // An unannotated block cell is still worth naming: it says which
                // facing the cell is, which is what a reader of the atlas wants.
                cells.put(labelCell(entry, name, BlockSlots.describe(entry.slot)));
                continue;
            }
            cells.put(labelCell(entry, name, entry.note));
        }
        return cells;
    }

    private static JSONObject labelCell(Entry entry, String name, String description)
            throws JSONException {
        JSONObject o = new JSONObject();
        o.put("col", entry.col);
        o.put("row", entry.row);
        o.put("name", name);
        if (!description.isEmpty()) o.put("description", description);
        if (!entry.tags.isEmpty()) o.put("tags", new JSONArray(entry.tags));
        return o;
    }

    /** The {@code layout} spelling {@link GridLayout#fromJson} reads back. */
    public static String jsonLayout(GridLayout layout) {
        return layout.name().toLowerCase().replace("_3x3", "-3x3");
    }

    /**
     * Write atlas and tileset together, each replaced atomically.
     *
     * <p>Both or neither: a tileset naming cells that the atlas beside it does
     * not contain is a startup crash, and half-written art is worse than none.
     */
    public static void write(BufferedImage atlas, JSONObject tileset,
                             Path atlasPath, Path tilesetPath)
            throws IOException, JSONException {
        Files.createDirectories(atlasPath.getParent());
        Files.createDirectories(tilesetPath.getParent());
        Path atlasTemp = atlasPath.resolveSibling(atlasPath.getFileName() + ".tmp");
        Path tilesetTemp = tilesetPath.resolveSibling(tilesetPath.getFileName() + ".tmp");
        try {
            ImageIO.write(atlas, "png", atlasTemp.toFile());
            Files.writeString(tilesetTemp, tileset.toString(2), StandardCharsets.UTF_8);
            Files.move(atlasTemp, atlasPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tilesetTemp, tilesetPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(atlasTemp);
            Files.deleteIfExists(tilesetTemp);
        }
    }
}
