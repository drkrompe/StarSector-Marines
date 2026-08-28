package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.json.JSONException;

/**
 * The annotation pass's mechanical half, with no window attached.
 *
 * <p>Everything the Tilesets page does other than presenting a table was already
 * headless — {@link SheetSlicer} finds pieces, {@link TilesetExport} packs and
 * writes, {@link TilesetMapPreview} renders a comparison. What was not headless
 * was the small amount of glue between them, which lived in the page's private
 * methods and read its widgets: which destination an export goes to, how the
 * packer's cell assignments turn a "stands in for" column into a substitution.
 *
 * <p>That glue is a property of the document rather than of the editor, so it
 * lives here and the page delegates. The alternative — a second copy for the
 * headless caller — would make the export path two things that have to agree,
 * and a tileset written by one of them to a path the other would not have
 * chosen is a startup crash rather than a visible difference.
 */
public final class TilesetOperations {

    private TilesetOperations() {}

    /** Where an export put things, so the caller can say what it wrote. */
    public record ExportResult(Path atlasPath, Path tilesetPath, Path cardPath,
                               String sheetPath, int columns, int rows,
                               int doodads, int blocks) {}

    /**
     * Read the raw sheet a document annotates.
     *
     * <p>Always converted to ARGB: a sheet with no alpha channel keys nothing,
     * and the whole slice threshold is meaningless against it until it has one.
     * The page does this on load for the same reason.
     */
    public static BufferedImage readSheet(Path projectRoot, TilesetDocument document)
            throws IOException {
        Path path = resolve(projectRoot, document.sheet);
        BufferedImage read = ImageIO.read(path.toFile());
        if (read == null) throw new IOException("not an image: " + path);
        return toArgb(read);
    }

    /** A document's sheet path, which is project-relative unless it lies outside. */
    public static Path resolve(Path projectRoot, String stored) {
        Path path = Path.of(stored);
        return path.isAbsolute() ? path : projectRoot.resolve(path);
    }

    /** A sheet without an alpha channel keys nothing; convert so the threshold means something. */
    public static BufferedImage toArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) return image;
        BufferedImage copy = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    /**
     * Re-slice a sheet and carry its existing annotations onto the result.
     *
     * <p>Does not mutate the document: tuning a threshold is something a caller
     * should be able to try and look at before keeping.
     */
    public static TilesetDocument.Reconciliation slice(BufferedImage sheet,
                                                       TilesetDocument document,
                                                       int alphaMin) {
        List<SheetSlicer.Piece> pieces =
                SheetSlicer.slice(sheet, alphaMin, SheetSlicer.DEFAULT_MIN_AREA);
        return TilesetDocument.reconcile(pieces, document.entries, document.idPrefix,
                document.cellPxX(sheet.getWidth()), document.cellPxY(sheet.getHeight()));
    }

    /**
     * A lost entry that was decided rather than measured, and what made it one.
     *
     * <p>The reason is carried because a refusal that only counts entries tells
     * an operator nothing about whether losing them matters.
     */
    public record AtRisk(String id, String reason) {}

    /** How many at-risk entries a refusal names before summarizing the rest. */
    private static final int NAMED_IN_REFUSAL = 6;

    /**
     * Which of a re-slice's lost entries carry authored work.
     *
     * <p>Re-slicing derives pieces from pixels, so it can only ever recover what
     * a threshold can see. Everything else an entry holds was decided by someone
     * looking at the art, and a piece that reconciles to nothing takes all of it
     * with it. Two shapes qualify:
     *
     * <ul>
     *   <li>an entry carrying judgement — block membership, a note, tags, a
     *       non-default cover, a footprint other than one cell, a stand-in
     *       binding, or an id that is no longer the generated one;
     *   <li>a cut cell, recognizable from the positional id
     *       {@link #gridId} gives it. It may carry no annotation yet, but the cut
     *       itself is a stated decision that a re-slice silently reverses: a
     *       fused plate has no alpha gutters, so slicing finds it whole again and
     *       every cell reconciles to nothing.
     * </ul>
     *
     * <p>Exclusion is deliberately not judgement. Marking specks as not shipping
     * and then raising the threshold until they vanish is a threshold sweep
     * working, not work being lost.
     */
    public static List<AtRisk> atRisk(List<TilesetExport.Entry> lost, String idPrefix) {
        List<AtRisk> found = new ArrayList<>();
        for (TilesetExport.Entry entry : lost) {
            String reason = authoredReason(entry, idPrefix);
            if (reason != null) found.add(new AtRisk(entry.id, reason));
        }
        return found;
    }

    private static String authoredReason(TilesetExport.Entry entry, String idPrefix) {
        List<String> reasons = new ArrayList<>();
        if (entry.isBlockMember()) {
            reasons.add("slot " + entry.slot + " of block " + entry.blockId);
        }
        if (!entry.note.isEmpty()) reasons.add("a note");
        if (!entry.tags.isEmpty()) reasons.add("tags");
        if (!"none".equals(entry.cover)) reasons.add("cover " + entry.cover);
        if (entry.footprintX != 1 || entry.footprintY != 1) {
            reasons.add("footprint " + entry.footprintX + "x" + entry.footprintY);
        }
        if (!entry.standsInFor.isEmpty()) reasons.add("stands in for " + entry.standsInFor);
        if (isCutCell(entry.id, idPrefix)) {
            reasons.add("a cut cell");
        } else if (!isGeneratedSerialId(entry.id, idPrefix)) {
            reasons.add("a chosen id");
        }
        return reasons.isEmpty() ? null : String.join(", ", reasons);
    }

    /** A cell of a plate that was cut on the grid: {@code <idPrefix>.c<col>r<row>}. */
    private static boolean isCutCell(String id, String idPrefix) {
        return id.matches(Pattern.quote(idPrefix) + "\\.c\\d+r\\d+");
    }

    /** The reading-order name a slice hands a piece nobody has named yet. */
    private static boolean isGeneratedSerialId(String id, String idPrefix) {
        return id.matches(Pattern.quote(idPrefix) + "\\.piece-\\d+");
    }

    /**
     * Why keeping a re-slice is refused, and what it would have cost.
     *
     * <p>Shared so the window and the headless caller refuse the same thing for
     * the same stated reason. Neither says how to override here: a confirmation
     * button and a {@code force} argument are not the same escape, and a message
     * naming the wrong one is worse than one naming none.
     */
    public static String discardWarning(List<AtRisk> atRisk) {
        StringBuilder message = new StringBuilder("keeping this re-slice would drop ")
                .append(atRisk.size())
                .append(atRisk.size() == 1 ? " entry that was" : " entries that were")
                .append(" decided rather than found: ");
        for (int i = 0; i < Math.min(NAMED_IN_REFUSAL, atRisk.size()); i++) {
            if (i > 0) message.append("; ");
            message.append(atRisk.get(i).id()).append(" (").append(atRisk.get(i).reason()).append(')');
        }
        if (atRisk.size() > NAMED_IN_REFUSAL) {
            message.append("; and ").append(atRisk.size() - NAMED_IN_REFUSAL).append(" more");
        }
        return message.append(". Slicing reads pixels, so none of that comes back: a fused "
                + "plate has no gutters and is found whole again, and every cell cut from it "
                + "reconciles to nothing.").toString();
    }

    /**
     * What a caller is told when it asks for a {@code 1 x 1} split.
     *
     * <p>Shared so the window and the headless caller refuse the same thing for
     * the same stated reason. {@code 1 x 1} is the default that means "this sheet
     * is not a plate", not a layout anyone chose, so a split at it is a caller
     * that has not stated the layout yet rather than one asking for one part.
     */
    public static final String DEGENERATE_GRID_MESSAGE =
            "The grid is 1 x 1, so splitting would change nothing. Set it to the "
                    + "layout the sheet was generated to — a 20-frame strip is 20 x 1 "
                    + "— and the cells need not be square.";

    /**
     * Cut the selected entries into the stated grid, leaving the rest in place.
     *
     * <p>A tileable plate arrives fused into one piece because its cells are drawn
     * edge to edge, so cutting it is an authoring decision rather than something
     * {@link SheetSlicer#slice} could have found. Each part is one cell of the
     * plate by construction, which is why the footprints come out {@code 1x1}
     * instead of being guessed from pixels, and the parts land in row-major
     * reading order so a 3x3 plate fills a block's slots without further
     * correction.
     *
     * <p>Annotations other than the cover level are deliberately not carried onto
     * the parts: a plate's id, note and footprint describe the plate, and every
     * cell inheriting one description would read as many answers where there is
     * one.
     *
     * <p>Each part is named for where it sits — see {@link #gridId} — so the
     * plate's own id is not part of its cells' names and the {@code idPrefix} the
     * document states is.
     *
     * @throws IllegalArgumentException if a part's positional id is one the sheet
     *                                  already uses
     */
    public static List<TilesetExport.Entry> splitOnGrid(List<TilesetExport.Entry> entries,
                                                        Predicate<TilesetExport.Entry> selected,
                                                        String idPrefix, int cols, int rows) {
        Set<String> taken = new LinkedHashSet<>();
        for (TilesetExport.Entry entry : entries) {
            if (!selected.test(entry)) taken.add(entry.id);
        }
        List<String> collisions = new ArrayList<>();
        List<TilesetExport.Entry> replaced = new ArrayList<>();
        for (TilesetExport.Entry entry : entries) {
            if (!selected.test(entry)) {
                replaced.add(entry);
                continue;
            }
            int part = 0;
            for (SheetSlicer.Piece piece : SheetSlicer.splitOnGrid(entry.piece, cols, rows)) {
                String id = gridId(idPrefix, part % cols, part / cols);
                part++;
                if (!taken.add(id)) collisions.add(id);
                TilesetExport.Entry split = new TilesetExport.Entry(piece, id);
                split.cover = entry.cover;
                split.footprintX = 1;
                split.footprintY = 1;
                replaced.add(split);
            }
        }
        if (!collisions.isEmpty()) throw new IllegalArgumentException(collisionMessage(collisions));
        return replaced;
    }

    /**
     * A cut cell's name: {@code <idPrefix>.c<col>r<row>}, zero-based, column
     * first.
     *
     * <p>The id has to say where on the plate the cell is. A table row and a cell
     * in the picture are otherwise impossible to line up — a 10x10 plate cut into
     * a hundred serial names gives a person and a model no way to point at the
     * same cell out loud — and column-then-row is the order the grid is stated in
     * everywhere else here.
     */
    public static String gridId(String idPrefix, int col, int row) {
        return idPrefix + ".c" + col + "r" + row;
    }

    /**
     * Why a split that would rename a piece out of existence is refused.
     *
     * <p>A positional id cannot be stepped past to make room the way
     * {@link TilesetDocument#reconcile} steps its serial ones: the number in it
     * is the cell's address, so a part renamed to dodge a clash would name the
     * wrong cell. The cut is refused whole instead of half-applied.
     */
    private static String collisionMessage(List<String> collisions) {
        return "the sheet already holds " + collisions.size()
                + " of the ids this cut would create: " + String.join(", ", collisions)
                + ". A cut cell's id says which cell it is, so it cannot be renumbered to "
                + "make room. Rename or remove the pieces holding those ids, or cut the "
                + "plate on a sheet of its own.";
    }

    /**
     * Pack the document's pieces, write the atlas and its tileset, and generate
     * the catalog card beside them.
     *
     * <p>The three files are the whole export. The card is not decoration: an id
     * and a pair of atlas coordinates say nothing about what a piece is, and the
     * reader choosing content from this sheet is usually a model that cannot
     * open the atlas at all.
     */
    public static ExportResult export(Path projectRoot, TilesetDocument document,
                                      BufferedImage sheet) throws IOException, JSONException {
        String name = document.sheetName.isBlank() ? "sheet" : document.sheetName;
        String sheetPath = document.resolvedOutputSheet(!document.blocks.isEmpty());

        Path atlasPath = projectRoot.resolve("mod").resolve(sheetPath);
        Path tilesetPath = projectRoot.resolve(TilesetLibrary.EXPORT_DIR)
                .resolve(name + ".tileset.json");
        Path cardPath = tilesetPath.resolveSibling(name + ".tileset.md");

        BufferedImage atlas =
                TilesetExport.atlas(sheet, document.entries, document.blocks, document.cellPx);
        TilesetExport.write(atlas,
                TilesetExport.tileset(sheetPath, document.cellPx, document.entries, document.blocks),
                atlasPath, tilesetPath);
        Files.writeString(cardPath, TilesetCatalogCard.render(
                name, sheetPath, document.cellPx, document.entries, document.blocks));

        TilesetExport.Packing packing = TilesetExport.pack(document.entries, document.blocks);
        int doodads = 0;
        for (TilesetExport.Entry entry : document.entries) {
            if (entry.included && !entry.isBlockMember()) doodads++;
        }
        return new ExportResult(atlasPath, tilesetPath, cardPath, sheetPath,
                packing.columns(), packing.rows(), doodads, packing.blockOrigins().size());
    }

    /**
     * Put pieces into the named slots of a block, declaring or redeclaring it.
     *
     * <p>The spec is replaced outright; the membership is merged, so a sheet can
     * be grouped a few slots at a time rather than all at once. A slot holds one
     * piece, so assigning over an occupied slot displaces whatever was there —
     * returned rather than dropped quietly, because that is the case the caller
     * has to look at.
     *
     * <p>Assignment forces the piece to ship, which is the one piece of
     * doodad-side state a grouping settles: an excluded block cell would leave a
     * hole the layout has no fill for. A footprint and a cover level are left
     * alone because a block cell is one cell by definition and neither is read
     * for it.
     *
     * @return the members this assignment pushed out of the block
     */
    public static List<TilesetExport.Entry> setBlock(List<TilesetExport.Entry> entries,
                                                     List<TilesetExport.BlockSpec> blocks,
                                                     String blockId, GridLayout layout,
                                                     Integer fillRgb,
                                                     Map<String, TilesetExport.Entry> bySlot) {
        List<TilesetExport.Entry> displaced = new ArrayList<>();
        for (TilesetExport.Entry entry : entries) {
            if (!blockId.equals(entry.blockId) || bySlot.containsValue(entry)) continue;
            if (bySlot.containsKey(entry.slot)) {
                entry.blockId = "";
                entry.slot = "";
                displaced.add(entry);
            }
        }
        for (Map.Entry<String, TilesetExport.Entry> assignment : bySlot.entrySet()) {
            TilesetExport.Entry entry = assignment.getValue();
            entry.blockId = blockId;
            entry.slot = assignment.getKey();
            entry.included = true;
        }
        blocks.removeIf(spec -> spec.id.equals(blockId));
        blocks.add(new TilesetExport.BlockSpec(blockId, layout, fillRgb));
        return displaced;
    }

    /**
     * Dissolve a block, handing its cells back as doodads.
     *
     * <p>A released member keeps its id, footprint and annotation: it was always
     * a piece of the sheet, and only its membership is being withdrawn.
     *
     * @return the members released, in document order
     */
    public static List<TilesetExport.Entry> removeBlock(List<TilesetExport.Entry> entries,
                                                        List<TilesetExport.BlockSpec> blocks,
                                                        String blockId) {
        List<TilesetExport.Entry> released = new ArrayList<>();
        for (TilesetExport.Entry entry : entries) {
            if (!blockId.equals(entry.blockId)) continue;
            entry.blockId = "";
            entry.slot = "";
            released.add(entry);
        }
        blocks.removeIf(spec -> spec.id.equals(blockId));
        return released;
    }

    /**
     * The candidates bound to shipped ids, addressed by where the packer put them.
     *
     * <p>Packing assigns each included piece its atlas cell, so running it here
     * is what turns "this row stands in for urban.wall" into a rectangle the
     * preview can paint from.
     */
    public static List<TilesetMapPreview.Substitution> bindings(List<TilesetExport.Entry> entries,
                                                                List<TilesetExport.BlockSpec> blocks) {
        TilesetExport.pack(entries, blocks);
        List<TilesetMapPreview.Substitution> bound = new ArrayList<>();
        for (TilesetExport.Entry entry : entries) {
            if (!entry.included || entry.standsInFor.isEmpty()) continue;
            int cellsX = entry.isBlockMember() ? 1 : entry.footprintX;
            int cellsY = entry.isBlockMember() ? 1 : entry.footprintY;
            bound.add(new TilesetMapPreview.Substitution(
                    entry.standsInFor, entry.col, entry.row, cellsX, cellsY));
        }
        // A block stands in as a whole patch: bind the block, not nine cells.
        for (TilesetExport.BlockSpec spec : blocks) {
            TilesetExport.Entry origin = firstBoundMember(entries, spec.id);
            if (origin == null) continue;
            bound.removeIf(binding -> binding.shippedId().equals(origin.standsInFor));
            int span = spec.layout.span();
            bound.add(new TilesetMapPreview.Substitution(origin.standsInFor,
                    origin.col - BlockSlots.offset(origin.slot)[0],
                    origin.row - BlockSlots.offset(origin.slot)[1], span, span));
        }
        return bound;
    }

    private static TilesetExport.Entry firstBoundMember(List<TilesetExport.Entry> entries,
                                                        String blockId) {
        for (TilesetExport.Entry entry : entries) {
            if (entry.included && blockId.equals(entry.blockId)
                    && !entry.standsInFor.isEmpty()) {
                return entry;
            }
        }
        return null;
    }
}
