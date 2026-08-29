package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        /**
         * How high this piece stops a shot, as a symmetric half-height in cells,
         * or null to take the height
         * {@link com.dillon.starsectormarines.battle.world.tiles.DoodadCover}
         * gives its cover level.
         *
         * <p>Cover and height are two different statements and a sheet needs
         * both: a chest and a shelf are equally worth hiding behind on the deck
         * and are nothing alike to shoot over. Leaving it null is not the same as
         * writing the default down — the default is a compatibility value the
         * consumer substitutes, and a piece that has been looked at should say
         * what it is rather than inherit a bucket's average.
         */
        public Double ballisticHalfHeight;
        /**
         * The edge that naturally backs onto a wall — {@code N}, {@code S},
         * {@code E}, {@code W} — or empty where the piece has no such edge.
         *
         * <p>A sofa's back and a bed's head are drawn against something. Placing
         * one in open floor, or against a wall the wrong way round, is a
         * placement no amount of cover data prevents.
         */
        public String preferredWallSide = "";
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
        /**
         * A shipped id this piece is a candidate replacement for.
         *
         * <p>Preview only: it says what to paint over when the map preview asks
         * "what would this look like instead of that", and is never exported.
         * A tileset describes what content is; it does not describe what some
         * other content might have been.
         */
        public String standsInFor = "";
        /**
         * Sliced sheets only: which layer the tile draws on, {@code ground} or
         * {@code overlay}. A ground tile is inset before it is drawn so its
         * neighbours do not show a sampler seam; an overlay is a standalone
         * sprite and is drawn whole.
         */
        public String layer = "ground";
        /** Sliced sheets only: whether a unit may stand on this tile. */
        public boolean passable = true;
        /**
         * Sliced sheets only: which bases this tile may be drawn over. Empty for
         * a ground tile, which overlays nothing. See
         * {@link com.dillon.starsectormarines.battle.world.tiles.TileDef#validOn}.
         */
        public List<String> validOn = new ArrayList<>();
        /**
         * Sliced sheets only: the short label the tileset carries as a tile's
         * {@code name}.
         *
         * <p>Not the id and not derivable from it. The id is what code selects
         * with and is namespaced; the label is what a reader of the catalog sees
         * beside the picture. {@link #note} is its long form and exports as the
         * tile's {@code description}.
         */
        public String label = "";
        /**
         * How deep this piece's own drawn border runs, in exported pixels,
         * across and down — {@code 0} where the art has none.
         *
         * <p>A generated field sprite is a slab: a lit rim along its top, a
         * shadowed skirt along its bottom, a darker column down each side. Those
         * belong to the <em>sprite</em> and not to the <em>surface</em>, and a
         * surface the map paves with has no boundary at all — so where the field
         * repeats they read as a dark lattice ruled across the ground, every
         * cell, everywhere the map uses it. The border is replaced on export
         * with the interior mirrored back out through it, which keeps the local
         * texture instead of laying down a flat stripe.
         *
         * <p>Two numbers rather than one because the skirt is deeper than the
         * sides: what a sprite puts under itself is a shadow and what it puts
         * beside itself is an outline.
         *
         * <p>Authored, not measured. How far in the border runs is a judgement
         * about where the surface starts, and the same reading of the same
         * pixels is a rim on a field and the drawn edge of a paving slab that
         * has to keep it. Nothing about a piece says which it is.
         *
         * <p>Colour only. Alpha was decided when the sheet was keyed, and moving
         * it here would move the frame boxes the loader finds.
         */
        public int spriteBorderX;
        public int spriteBorderY;
        /**
         * The tileable material file this frame's picture comes from, or empty
         * where it is a crop of the plate.
         *
         * <p>Some fields are not drawn: they are a surface taken from a material
         * library and repeated. Such a frame's picture is not on the sheet its
         * document annotates, and a document that cannot say so cannot reproduce
         * the atlas — exporting puts back whatever the plate has underneath,
         * which is a valid image of the right size and wrong art. See
         * {@code nature-tiles-material-provenance.md}.
         *
         * <p>Project-relative, like a document's own {@code sheet}.
         *
         * <p>A material-backed frame takes its <em>size</em> from the material
         * and not from the strip's scale, because a material is a surface rather
         * than a picture drawn at a size: resampling a seamless texture to fit a
         * frame either loses its seams or has to wrap-pad to keep them. The frame
         * is the material plus {@link #MATERIAL_GUARD_PX} of wrap on every side.
         */
        public String material = "";
        /** Assigned by {@link #pack}. */
        public int col;
        public int row;
        /** Assigned by {@link #packStrip}: this frame's pixel box on the strip. */
        public int frameX;
        public int frameY;
        public int frameWidth;
        public int frameHeight;

        public Entry(SheetSlicer.Piece piece, String id) {
            this.piece = piece;
            this.id = id;
        }

        public boolean isBlockMember() {
            return !blockId.isEmpty();
        }

        /** Whether this frame's picture comes from a material rather than from the plate. */
        public boolean hasMaterial() {
            return !material.isEmpty();
        }
    }

    /**
     * How much wrapped material surrounds a material-backed frame's surface.
     *
     * <p>Taken from the renderer rather than restated, because it is the same
     * number for the same reason: a ground tile is drawn inset by this much so
     * neighbouring cells do not sample across a frame boundary, so a material
     * placed with exactly this much of itself wrapped around it is cropped back
     * to exactly the material when it is drawn. Any other width would either
     * clamp the sampler onto a seam or hide part of the surface.
     */
    public static final int MATERIAL_GUARD_PX = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;

    /**
     * The pictures for the frames that do not take one from the plate.
     *
     * <p>A plate is handed to {@link #stripAtlas} as an image, and a material is
     * another image the same export needs; resolving one is reading a file, which
     * belongs to the caller rather than to the packer. {@link #NONE} resolves
     * nothing, so a caller that forgets its materials is refused loudly instead
     * of quietly sizing a frame off the plate underneath.
     */
    @FunctionalInterface
    public interface Materials {

        /** The material at a document-declared path, or null when it is not resolved. */
        BufferedImage image(String source);

        /** No materials at all: every declared one is unresolvable. */
        Materials NONE = source -> null;
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
        /**
         * The autotile geometry this block resolves through, or {@code null}
         * when it is a {@linkplain #isPool() variant pool}.
         */
        public GridLayout layout;
        /** {@code 0xRRGGBB} painted for the layout's null case, or null when it has none. */
        public Integer fillRgb;

        public BlockSpec(String id, GridLayout layout, Integer fillRgb) {
            this.id = id;
            this.layout = layout;
            this.fillRgb = fillRgb;
        }

        /**
         * Whether this block is a pool of interchangeable variants rather than
         * an autotile.
         *
         * <p>The two are different shapes, not settings of one. An autotile
         * answers "which cell for this neighbour mask" and therefore occupies a
         * fixed patch its origin plus an offset addresses. A pool answers
         * "any of these", is picked by hashing the cell's coordinate, and has
         * no geometry at all — so it is written as an explicit list of cells
         * and the packer is free to lay it out as a plain run.
         */
        public boolean isPool() {
            return layout == null;
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
            if (spec != null && spec.isPool()) {
                // Width is filled in below once every member has been seen: a
                // pool's extent is how many variants it has, which is not known
                // at its first one.
                units.add(new Unit(entry.blockId, 0, 1, group));
                continue;
            }
            int span = spec == null ? 3 : spec.layout.span();
            units.add(new Unit(entry.blockId, span, span, group));
        }
        // A pool reserves one cell per variant, so its unit is sized after the
        // sweep that collects them.
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.blockId() != null && unit.width() == 0) {
                units.set(i, new Unit(unit.blockId(), unit.members().size(), 1, unit.members()));
            }
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
                BlockSpec spec = specs.get(unit.blockId());
                if (spec != null && spec.isPool()) {
                    // Ordinal, not geometric: a pool's cells are a list laid in a
                    // run, and a gap in its slot numbering would leave a hole in
                    // it. Placed in slot order rather than document order so v1
                    // is the run's first cell whichever order the pieces were cut.
                    List<Entry> ordered = new ArrayList<>(unit.members());
                    ordered.sort(Comparator.comparingInt(e -> BlockSlots.variantIndex(e.slot)));
                    int offset = 0;
                    for (Entry entry : ordered) {
                        entry.col = cursorX + offset++;
                        entry.row = cursorY;
                    }
                } else {
                    for (Entry entry : unit.members()) {
                        int[] offset = BlockSlots.offset(entry.slot);
                        entry.col = cursorX + offset[0];
                        entry.row = cursorY + offset[1];
                    }
                }
            }
            cursorX += unit.width();
            shelfHeight = Math.max(shelfHeight, unit.height());
            widest = Math.max(widest, cursorX);
        }
        return new Packing(Math.max(1, widest), Math.max(1, cursorY + shelfHeight), origins);
    }

    /**
     * Draw every included entry into its packed slot, stretched to fill it.
     *
     * <p>A piece that says it is a repeating surface gets the surface
     * treatment; anything else is drawn as it was cut. The two markers are the
     * same ones the strip shape already uses: a {@code material} means the
     * picture comes from a tileable file rather than from the plate, and a
     * {@code spriteBorderPx} means the plate's own art carries a drawn rim that
     * has to be mirrored away or it tiles as a lattice.
     *
     * <p>The treatment is authored per piece rather than applied to every
     * frame, and that is not a convenience. Area-averaging and sharpening a
     * whole sheet changes art that was never a repeating field: measured on
     * {@code urban-tileset}, 38% of the sheet moves and the wall panel's rivets
     * and a crate's edges go soft. A piece that has not claimed to be a surface
     * is left alone.
     */
    public static BufferedImage atlas(BufferedImage source, List<Entry> entries,
                                      List<BlockSpec> blocks, int cellPx,
                                      Materials materials) {
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
            // Drawn straight rather than area-averaged. The strip path pairs
            // resample with sharpen, and box-averaging a 123px cut cell into 32
            // without that second half is visibly mushy: the wall panel's lines
            // and a crate's edges go soft. Bringing both halves here would
            // change every grid sheet already exported, so it belongs with the
            // ground sheets that need it rather than with a packing change.
            int frameWidth = width * cellPx;
            int frameHeight = height * cellPx;
            if (entry.hasMaterial()) {
                g.drawImage(wrapped(requireMaterial(entry, materials)),
                        entry.col * cellPx, entry.row * cellPx,
                        frameWidth, frameHeight, null);
            } else if (entry.spriteBorderX > 0 || entry.spriteBorderY > 0) {
                g.drawImage(
                        mirrorSpriteBorder(
                                sharpen(fitted(source, p, frameWidth, frameHeight)),
                                entry.spriteBorderX, entry.spriteBorderY),
                        entry.col * cellPx, entry.row * cellPx, null);
            } else {
                g.drawImage(
                        source.getSubimage(p.x(), p.y(), p.width(), p.height()),
                        entry.col * cellPx, entry.row * cellPx,
                        frameWidth, frameHeight, null);
            }
        }
        g.dispose();
        return atlas;
    }

    /**
     * How a sheet exports as an <b>auto-strip</b>: frames in a row, found again
     * at load by the gaps between them.
     *
     * <p>A sliced sheet is a second shape a tileset can have, not a variant of
     * the grid one. Its pieces are not cells of anything: they are drawn at
     * whatever size and aspect the art has, laid out left to right, and
     * addressed by their position in that row. Nothing about a sliced sheet is
     * a {@code (col, row)}, and giving it a {@code cellPx} would be inventing a
     * grid the art does not have — a bench 17px wide beside a paver 39px wide
     * are both one tile.
     *
     * <p>{@code mode}, {@code alphaThreshold} and {@code minGap} are what the
     * exported tileset says about itself and must survive a round trip.
     * {@code scale}, {@code gutterPx} and {@code marginPx} are how this atlas
     * gets packed: the raw art is drawn several times larger than it ships, and
     * the single divisor between the two is the judgement that says how large
     * this sheet's tiles are on the deck. Deriving it from the previous atlas
     * instead is exactly the circularity the alpha law forbids.
     *
     * @param mode           the slicing the runtime performs; only {@code auto-strip} exists
     * @param alphaThreshold alpha at or above which a pixel is content
     * @param minGap         transparent columns that separate two frames
     * @param scale          raw pixels per exported pixel
     * @param gutterPx       transparent columns the packer leaves between frames
     * @param marginPx       transparent border round the whole strip
     */
    public record StripSpec(String mode, int alphaThreshold, int minGap,
                            double scale, int gutterPx, int marginPx) {

        public static final String AUTO_STRIP = "auto-strip";

        public StripSpec {
            if (!AUTO_STRIP.equals(mode)) {
                throw new IllegalArgumentException("unknown slice mode '" + mode + "'");
            }
            if (!(scale > 0) || !Double.isFinite(scale)) {
                throw new IllegalArgumentException("a strip's scale must be positive: " + scale);
            }
            if (minGap < 1) throw new IllegalArgumentException("minGap must be at least 1");
            // A gutter narrower than the gap the loader splits on would fuse two
            // frames into one, which renumbers every frame after it and is
            // invisible in the tileset the export writes beside the atlas.
            if (gutterPx < minGap) {
                throw new IllegalArgumentException("a gutter of " + gutterPx + "px cannot separate "
                        + "frames the loader splits on " + minGap + " transparent columns");
            }
            if (marginPx < 0) throw new IllegalArgumentException("marginPx cannot be negative");
        }

        /** The shipped defaults: what {@code SpriteSheetSlicer} looks for. */
        public static StripSpec of(double scale) {
            return new StripSpec(AUTO_STRIP, 16, 4, scale, 8, 2);
        }
    }

    /** The strip's extent, and the frames on it in the order the loader finds them. */
    public record StripPacking(int width, int height, List<Entry> frames) {}

    /**
     * Lay the included pieces out in a row, each scaled by the strip's own
     * divisor, and record where each landed.
     *
     * <p>Order is the document's, because on a sliced sheet order <em>is</em>
     * the address: a tile pins the frame index the loader will hand it, so
     * moving a piece renames every piece after it.
     */
    public static StripPacking packStrip(List<Entry> entries, StripSpec spec) {
        return packStrip(entries, spec, Materials.NONE);
    }

    /**
     * As {@link #packStrip(List, StripSpec)}, sizing material-backed frames from
     * the materials {@code materials} resolves.
     *
     * <p>A frame whose material cannot be resolved is refused rather than sized
     * off the plate: the plate still holds the art the material replaced, so
     * falling back would export a valid strip of the wrong pictures.
     */
    public static StripPacking packStrip(List<Entry> entries, StripSpec spec,
                                         Materials materials) {
        List<Entry> frames = new ArrayList<>();
        int cursor = spec.marginPx();
        int tallest = 0;
        for (Entry entry : entries) {
            if (!entry.included) continue;
            if (!frames.isEmpty()) cursor += spec.gutterPx();
            if (entry.hasMaterial()) {
                BufferedImage material = requireMaterial(entry, materials);
                entry.frameWidth = material.getWidth() + 2 * MATERIAL_GUARD_PX;
                entry.frameHeight = material.getHeight() + 2 * MATERIAL_GUARD_PX;
            } else {
                entry.frameWidth =
                        Math.max(1, (int) Math.round(entry.piece.width() / spec.scale()));
                entry.frameHeight =
                        Math.max(1, (int) Math.round(entry.piece.height() / spec.scale()));
            }
            entry.frameX = cursor;
            entry.frameY = spec.marginPx();
            cursor += entry.frameWidth;
            tallest = Math.max(tallest, entry.frameHeight);
            frames.add(entry);
        }
        return new StripPacking(Math.max(1, cursor + spec.marginPx()),
                Math.max(1, tallest + 2 * spec.marginPx()), frames);
    }

    /**
     * The material a frame declares, refusing the declarations that cannot mean
     * what they say.
     *
     * <p>A material is a repeating surface, so it belongs to a ground frame and
     * to no other kind: an overlay is drawn whole and uninset, and the wrap that
     * makes the guard invisible under a ground tile would simply be two pixels of
     * the far side of the texture drawn round a prop. A sprite border is the
     * complementary contradiction — it is the treatment for art drawn as a slab,
     * and a material has no border to remove, so honouring both would mean
     * silently ignoring one.
     */
    private static BufferedImage requireMaterial(Entry entry, Materials materials) {
        if (!"ground".equals(entry.layer)) {
            throw new IllegalArgumentException(entry.id + " is a '" + entry.layer + "' frame and "
                    + "cannot take its picture from a material: a material is a repeating "
                    + "surface, and only a ground frame is drawn inset enough to crop its "
                    + "wrapped guard away");
        }
        if (entry.spriteBorderX > 0 || entry.spriteBorderY > 0) {
            throw new IllegalArgumentException(entry.id + " declares both a material and a "
                    + "sprite border of " + entry.spriteBorderX + "x" + entry.spriteBorderY
                    + "; a border is the repair for art drawn as a slab and a material has "
                    + "none, so one of the two would be silently ignored");
        }
        BufferedImage material = materials.image(entry.material);
        if (material == null) {
            throw new IllegalArgumentException(entry.id + " takes its picture from the material "
                    + entry.material + ", which has not been resolved, so this strip cannot be "
                    + "sized. Exporting from the plate alone would put back the art the "
                    + "material replaced.");
        }
        return material;
    }

    /** Draw every included piece into its packed frame. */
    public static BufferedImage stripAtlas(BufferedImage source, List<Entry> entries,
                                           StripSpec spec) {
        return stripAtlas(source, entries, spec, Materials.NONE);
    }

    /**
     * As {@link #stripAtlas(BufferedImage, List, StripSpec)}, drawing a
     * material-backed frame from its material rather than from the plate.
     *
     * <p>A material is placed as it is: no reduction, no sharpen and no border
     * treatment. Those are all repairs for art drawn several times larger than it
     * ships and drawn as a sprite; a material already is the surface at the size
     * it ships at, and resampling it would only cost it its seams.
     */
    public static BufferedImage stripAtlas(BufferedImage source, List<Entry> entries,
                                           StripSpec spec, Materials materials) {
        StripPacking packing = packStrip(entries, spec, materials);
        BufferedImage atlas = new BufferedImage(
                packing.width(), packing.height(), BufferedImage.TYPE_INT_ARGB);
        for (Entry entry : packing.frames()) {
            BufferedImage frame = entry.hasMaterial()
                    ? wrapped(requireMaterial(entry, materials))
                    : mirrorSpriteBorder(
                            sharpen(resample(source, entry.piece,
                                    entry.frameWidth, entry.frameHeight)),
                            entry.spriteBorderX, entry.spriteBorderY);
            for (int y = 0; y < entry.frameHeight; y++) {
                for (int x = 0; x < entry.frameWidth; x++) {
                    int argb = frame.getRGB(x, y);
                    if (argb >>> 24 != 0) atlas.setRGB(entry.frameX + x, entry.frameY + y, argb);
                }
            }
        }
        return atlas;
    }

    /**
     * A material with {@link #MATERIAL_GUARD_PX} of itself wrapped round it.
     *
     * <p>Periodic rather than clamped or mirrored: the guard exists so that a
     * sampler reaching just outside the drawn cell finds the surface continuing,
     * which for a tileable texture is the opposite edge of the texture itself.
     * A clamp would smear the rim and a mirror would double it, and either shows
     * as a line at every join once the tile repeats.
     */
    private static BufferedImage wrapped(BufferedImage material) {
        int width = material.getWidth();
        int height = material.getHeight();
        BufferedImage out = new BufferedImage(width + 2 * MATERIAL_GUARD_PX,
                height + 2 * MATERIAL_GUARD_PX, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            int sy = Math.floorMod(y - MATERIAL_GUARD_PX, height);
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, material.getRGB(Math.floorMod(x - MATERIAL_GUARD_PX, width), sy));
            }
        }
        return out;
    }

    /**
     * A cut piece at the size its frame needs, by whichever filter suits the
     * direction.
     *
     * <p>{@link #resample} averages every source pixel into its destination,
     * which is what reduction needs and what enlargement cannot use: asked to
     * grow 49 pixels into 56 it finds one source pixel per destination pixel and
     * degenerates to nearest neighbour, which is blocky. A plate cut smaller
     * than its cell is the ordinary case for a fixed-grid ground sheet, so both
     * directions have to be right.
     */
    private static BufferedImage fitted(BufferedImage source, SheetSlicer.Piece piece,
                                        int width, int height) {
        if (piece.width() >= width && piece.height() >= height) {
            return resample(source, piece, width, height);
        }
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source.getSubimage(piece.x(), piece.y(), piece.width(), piece.height()),
                0, 0, width, height, null);
        g.dispose();
        return out;
    }

    /**
     * Replace a frame's own sprite border with the interior mirrored out
     * through it, so a repeating field has no boundary.
     *
     * <p>Mirroring rather than smearing: a flat stripe of one colour is as
     * visible a lattice as the dark one it replaces, while a reflection carries
     * the field's own grain right up to the edge. Corners take the mirrored
     * interior corner, so neither axis overwrites the other's work.
     *
     * <p>Alpha is left exactly as the key decided it. See
     * {@link Entry#spriteBorderX}.
     */
    private static BufferedImage mirrorSpriteBorder(BufferedImage frame, int borderX, int borderY) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        if (borderX <= 0 && borderY <= 0) return frame;
        // A border is mirrored from the interior immediately inside it, so the
        // deepest row it reads is 2*border-1. Three times the border has to fit
        // in the frame or the reflection lands in the border at the other end
        // and copies the very pixels it is replacing.
        if (width < borderX * 3 || height < borderY * 3) {
            throw new IllegalArgumentException("a sprite border of " + borderX + "x" + borderY
                    + " leaves nothing inside a " + width + "x" + height + " frame to mirror: "
                    + "the reflection would reach the border at the far side");
        }
        int[] columns = mirrored(width, borderX);
        int[] rows = mirrored(height, borderY);
        int[] src = frame.getRGB(0, 0, width, height, null, 0, width);
        int[] out = src.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int from = src[rows[y] * width + columns[x]];
                out[y * width + x] = (src[y * width + x] & 0xFF000000) | (from & 0xFFFFFF);
            }
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, out, 0, width);
        return result;
    }

    /** Identity, except that each end's {@code border} entries reflect inward. */
    private static int[] mirrored(int extent, int border) {
        int[] index = new int[extent];
        for (int i = 0; i < extent; i++) index[i] = i;
        for (int i = 0; i < border; i++) {
            index[i] = 2 * border - 1 - i;
            index[extent - 1 - i] = extent - 2 * border + i;
        }
        return index;
    }

    /** Gaussian weight at one pixel's distance for the sharpen blur's radius. */
    private static final double SHARPEN_NEIGHBOUR = 0.19;
    /** How much of the detail the blur removed is added back. */
    private static final double SHARPEN_AMOUNT = 1.0;
    /** Detail below this many levels is grain, and amplifying it is amplifying noise. */
    private static final int SHARPEN_THRESHOLD = 2;

    /**
     * Put back the local contrast the reduction averaged away.
     *
     * <p>Six source pixels to one is an average, and the average of a cobbled
     * surface is a flat one. Measured against the sheet this replaces, an
     * unsharpened reduction carries about three quarters of its neighbour-to-
     * neighbour contrast — visibly softer at 39px, where a paver has only a few
     * pixels to say it is made of stones with. Sharpening is therefore part of
     * reducing rather than a treatment applied to it.
     *
     * <p>Only opaque pixels take part, in the blur as well as the result: a
     * transparent neighbour has no colour, and letting one average in would ring
     * a dark halo round every silhouette. Alpha itself is never touched — it was
     * decided when the sheet was keyed, and a sharpen that moved it would move
     * the frame boxes the loader finds.
     */
    private static BufferedImage sharpen(BufferedImage frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] src = frame.getRGB(0, 0, width, height, null, 0, width);
        int[] out = src.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int at = y * width + x;
                if (src[at] >>> 24 == 0) continue;
                int packed = 0xFF000000;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    double sum = 0;
                    double weight = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                            int neighbour = src[ny * width + nx];
                            if (neighbour >>> 24 == 0) continue;
                            double w = Math.pow(SHARPEN_NEIGHBOUR, Math.abs(dx) + Math.abs(dy));
                            sum += w * ((neighbour >> shift) & 0xFF);
                            weight += w;
                        }
                    }
                    int value = (src[at] >> shift) & 0xFF;
                    double blurred = weight == 0 ? value : sum / weight;
                    double detail = value - blurred;
                    int sharpened = Math.abs(detail) <= SHARPEN_THRESHOLD
                            ? value
                            : (int) Math.round(value + SHARPEN_AMOUNT * detail);
                    packed |= Math.max(0, Math.min(255, sharpened)) << shift;
                }
                out[at] = packed;
            }
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, out, 0, width);
        return result;
    }

    /**
     * Area-average one piece down into its frame, then take alpha to 0 or 255.
     *
     * <p>A strip is reduced by five or six to one, where sampling four source
     * pixels out of thirty throws away most of a cobbled surface and keeps
     * whichever stones the grid happened to land on. Averaging the whole
     * footprint is what a reduction of that size needs.
     *
     * <p>Colour is weighted by alpha, so a keyed edge averages the art it is
     * part of instead of pulling the matte's black in behind it. Alpha itself
     * is then hard: a strip is found again by alpha at load, and a soft halo is
     * both a seam between two ground tiles and dust the slicer can mistake for
     * another frame.
     */
    private static BufferedImage resample(BufferedImage source, SheetSlicer.Piece piece,
                                          int dstWidth, int dstHeight) {
        int srcWidth = piece.width();
        int srcHeight = piece.height();
        int[] src = source.getRGB(piece.x(), piece.y(), srcWidth, srcHeight, null, 0, srcWidth);
        BufferedImage frame = new BufferedImage(dstWidth, dstHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < dstHeight; y++) {
            int y0 = (int) ((long) y * srcHeight / dstHeight);
            int y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * srcHeight / dstHeight));
            for (int x = 0; x < dstWidth; x++) {
                int x0 = (int) ((long) x * srcWidth / dstWidth);
                int x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * srcWidth / dstWidth));
                long alphaSum = 0;
                long red = 0;
                long green = 0;
                long blue = 0;
                int count = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int argb = src[sy * srcWidth + sx];
                        int alpha = argb >>> 24;
                        alphaSum += alpha;
                        red += (long) alpha * ((argb >> 16) & 0xFF);
                        green += (long) alpha * ((argb >> 8) & 0xFF);
                        blue += (long) alpha * (argb & 0xFF);
                        count++;
                    }
                }
                if (count == 0 || alphaSum * 2 < (long) count * 255) continue;
                int packed = 0xFF000000
                        | (int) (red / alphaSum) << 16
                        | (int) (green / alphaSum) << 8
                        | (int) (blue / alphaSum);
                frame.setRGB(x, y, packed);
            }
        }
        return frame;
    }

    /** The tileset document describing {@code sheetPath}'s sliced frames. */
    public static JSONObject slicedTileset(String sheetPath, List<Entry> entries, StripSpec spec)
            throws JSONException {
        JSONObject slice = new JSONObject();
        slice.put("mode", spec.mode());
        slice.put("alphaThreshold", spec.alphaThreshold());
        slice.put("minGap", spec.minGap());

        // Frame indices, not frame boxes: what a tile pins is its position in
        // the row, so this needs the order the pieces ship in and nothing the
        // packer works out. Asking the packer would drag a material lookup into
        // writing a tileset that never mentions one.
        JSONArray tiles = new JSONArray();
        int frame = 0;
        for (Entry entry : entries) {
            if (!entry.included) continue;
            JSONObject o = new JSONObject();
            o.put("id", entry.id);
            o.put("frame", frame++);
            o.put("layer", entry.layer);
            if (!"none".equals(entry.cover)) o.put("cover", entry.cover);
            if (!entry.passable) o.put("passable", false);
            if (!entry.validOn.isEmpty()) o.put("validOn", new JSONArray(entry.validOn));
            if (!entry.label.isEmpty()) o.put("name", entry.label);
            if (!entry.note.isEmpty()) o.put("description", entry.note);
            tiles.put(o);
        }

        JSONObject root = new JSONObject();
        root.put("sheet", sheetPath);
        root.put("slice", slice);
        root.put("tiles", tiles);
        return root;
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
            if (entry.ballisticHalfHeight != null) {
                o.put("ballisticHalfHeight", entry.ballisticHalfHeight.doubleValue());
            }
            if (!entry.preferredWallSide.isEmpty()) {
                o.put("preferredWallSide", entry.preferredWallSide);
            }
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
            if (spec.isPool()) {
                List<Entry> pooled = new ArrayList<>();
                for (Entry entry : entries) {
                    if (entry.included && spec.id.equals(entry.blockId)) pooled.add(entry);
                }
                pooled.sort(Comparator.comparingInt((Entry e) -> e.row).thenComparingInt(e -> e.col));
                JSONArray cellList = new JSONArray();
                for (Entry entry : pooled) {
                    cellList.put(new JSONArray().put(entry.col).put(entry.row));
                }
                o.put("cells", cellList);
            } else {
                o.put("origin", new JSONArray().put(origin[0]).put(origin[1]));
                o.put("layout", jsonLayout(spec.layout));
                if (spec.fillRgb != null) o.put("fillRgb", String.format("0x%06X", spec.fillRgb));
            }
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

    /**
     * How a block's shape is spelled in JSON: a {@link GridLayout}'s own name,
     * or {@link #VARIANT_POOL} for a pool, which has no layout.
     */
    public static String jsonLayout(GridLayout layout) {
        if (layout == null) return VARIANT_POOL;
        return layout.name().toLowerCase(Locale.ROOT).replace("_3x3", "-3x3");
    }

    /**
     * The {@code layout} a pool declares in an authoring document.
     *
     * <p>It is deliberately not a {@link GridLayout}: a pool resolves by
     * hashing a coordinate, not by a neighbour mask, so giving it a member of
     * the geometry enum would put it where every autotile resolver would then
     * have to special-case it out again.
     */
    public static final String VARIANT_POOL = "variants";

    /** The shape named by {@code spelling}, or {@code null} for a pool. */
    public static GridLayout layoutFromJson(String spelling) {
        return VARIANT_POOL.equalsIgnoreCase(spelling.trim()) ? null
                : GridLayout.fromJson(spelling);
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
