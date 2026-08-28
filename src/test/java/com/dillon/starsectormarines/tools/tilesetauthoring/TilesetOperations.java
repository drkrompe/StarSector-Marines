package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
     */
    public static List<TilesetExport.Entry> splitOnGrid(List<TilesetExport.Entry> entries,
                                                        Predicate<TilesetExport.Entry> selected,
                                                        int cols, int rows) {
        List<TilesetExport.Entry> replaced = new ArrayList<>();
        for (TilesetExport.Entry entry : entries) {
            if (!selected.test(entry)) {
                replaced.add(entry);
                continue;
            }
            int part = 0;
            for (SheetSlicer.Piece piece : SheetSlicer.splitOnGrid(entry.piece, cols, rows)) {
                TilesetExport.Entry split = new TilesetExport.Entry(
                        piece, entry.id + "-" + partSuffix(part++));
                split.cover = entry.cover;
                split.footprintX = 1;
                split.footprintY = 1;
                replaced.add(split);
            }
        }
        return replaced;
    }

    /**
     * A part's name suffix: {@code a}…{@code z}, then {@code aa}, {@code ab}, and
     * on. Real plates run well past 26 cells — a 25x26 floor sheet is 650 — and
     * stepping one character further off {@code 'a'} walks out of the alphabet
     * into punctuation.
     */
    static String partSuffix(int index) {
        StringBuilder suffix = new StringBuilder();
        for (int n = index; ; n = n / 26 - 1) {
            suffix.insert(0, (char) ('a' + n % 26));
            if (n < 26) return suffix.toString();
        }
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
