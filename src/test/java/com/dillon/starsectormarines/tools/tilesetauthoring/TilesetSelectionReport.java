package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * What a selection on the sheet looks like when it has to leave the window.
 *
 * <p>Annotating a sheet is a conversation about art, and the two participants
 * cannot see the same thing. A person is looking at a picture; a model is
 * reading a table of ids. The table was unusable for the purpose because a cut
 * cell's id says nothing about where the cell is, so no row could be pointed at
 * on the sheet and no sentence about "that crate" survived the trip.
 *
 * <p>So a selection leaves as two halves that share one vocabulary — the cell's
 * {@code col,row}. {@link #contactSheet} draws the selected cells with that
 * coordinate burned into each one, which is the half that carries what the art
 * actually looks like. {@link #markdown} lists the same cells under the same
 * coordinates with whatever is already authored on them, which is the half that
 * says what must not be trampled. Neither is useful alone: an unlabelled crop
 * can only be discussed in prose, and a table of ids cannot be seen.
 *
 * <p>Deliberately not a tool call. Handing over a ready-made
 * {@code tileset_write_document} body would mean handing over the whole
 * document — every entry, including the ones the selection is not about — and
 * getting it back transcribed. The small call is cheap to construct and the
 * transcription is where annotation would quietly go missing.
 */
public final class TilesetSelectionReport {

    private TilesetSelectionReport() {}

    /** Edge of one cell in the contact sheet, in pixels. Big enough to judge art by. */
    private static final int TILE = 168;
    private static final int GAP = 8;
    private static final int CAPTION = 30;
    private static final int PAD = 14;
    private static final int TITLE = 26;

    private static final Color BACKDROP = new Color(0x18, 0x1a, 0x1e);
    private static final Color OUTLINE = new Color(0x5a, 0x5f, 0x69);
    private static final Color TEXT = new Color(0xd8, 0xdc, 0xe4);
    private static final Color DIM = new Color(0xa4, 0xac, 0xb8);

    /**
     * Where a cut cell sits on the sheet.
     *
     * <p>{@code col} and {@code row} are {@code -1} when the entries are not a
     * grid cut — an alpha-keyed prop sheet has pieces wherever the art is, and
     * inventing a coordinate for one would be a coordinate that means nothing.
     */
    public record Cell(int index, int col, int row, TilesetExport.Entry entry) {

        /** How this cell is named in conversation: its coordinate, or failing that its index. */
        public String label() {
            return col < 0 ? "#" + index : col + "," + row;
        }
    }

    /**
     * Resolve the selected indices to cells.
     *
     * <p>The grid is trusted only when it accounts for every entry exactly. A
     * document whose stated grid disagrees with what it holds has been cut and
     * then edited, and a coordinate derived from the stale grid would point at
     * the wrong cell — worse than having no coordinate at all.
     */
    public static List<Cell> cells(List<TilesetExport.Entry> entries, int[] selected,
                                   int gridCols, int gridRows) {
        boolean isGrid = gridCols > 0 && gridRows > 0
                && gridCols * gridRows == entries.size();
        List<Cell> resolved = new ArrayList<>();
        for (int index : selected) {
            if (index < 0 || index >= entries.size()) continue;
            resolved.add(isGrid
                    ? new Cell(index, index % gridCols, index / gridCols, entries.get(index))
                    : new Cell(index, -1, -1, entries.get(index)));
        }
        return resolved;
    }

    /**
     * Draw the selected cells, keeping their relative arrangement on the sheet.
     *
     * <p>Arrangement is the point rather than tidiness: a wall's nine cells read
     * as a building when they are laid out as one, and a mirrored assignment is
     * visible there and nowhere else. A selection with no grid falls back to a
     * strip in reading order, which is all that can honestly be said about it.
     */
    public static BufferedImage contactSheet(BufferedImage sheet, String title,
                                             List<Cell> cells) {
        if (cells.isEmpty()) throw new IllegalArgumentException("nothing is selected");
        boolean grid = cells.get(0).col() >= 0;

        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            int col = grid ? cell.col() : i % STRIP_ACROSS;
            int row = grid ? cell.row() : i / STRIP_ACROSS;
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
        }
        int across = maxCol - minCol + 1;
        int down = maxRow - minRow + 1;

        int width = PAD * 2 + across * TILE + (across - 1) * GAP;
        int height = TITLE + PAD * 2 + down * (TILE + CAPTION) + (down - 1) * GAP;
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(BACKDROP);
        g.fillRect(0, 0, width, height);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(title, PAD, 19);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            int col = (grid ? cell.col() : i % STRIP_ACROSS) - minCol;
            int row = (grid ? cell.row() : i / STRIP_ACROSS) - minRow;
            int x = PAD + col * (TILE + GAP);
            int y = TITLE + PAD + row * (TILE + CAPTION + GAP);

            SheetSlicer.Piece piece = cell.entry().piece;
            g.drawImage(sheet,
                    x, y, x + TILE, y + TILE,
                    piece.x(), piece.y(), piece.x() + piece.width(), piece.y() + piece.height(),
                    null);
            g.setColor(OUTLINE);
            g.drawRect(x, y, TILE - 1, TILE - 1);

            g.setColor(TEXT);
            g.drawString(cell.label(), x + 2, y + TILE + 13);
            g.setColor(DIM);
            g.drawString(caption(cell.entry()), x + 2, y + TILE + 26);
        }
        g.dispose();
        return canvas;
    }

    /** Cells per row when a selection has no grid to preserve. */
    private static final int STRIP_ACROSS = 6;

    /** The one line under a tile: what it already is, or that it is not yet anything. */
    private static String caption(TilesetExport.Entry entry) {
        if (entry.isBlockMember()) {
            return entry.blockId + " / " + entry.slot;
        }
        return entry.id;
    }

    /**
     * The same cells as text, keyed by the same coordinates the image is.
     *
     * <p>Carries what is already authored, because the reader's first job is not
     * to trample it, and the sheet's own note, because that is where a reason
     * not to export at all would be written down.
     */
    public static String markdown(String sheetName, String sheetNote, int totalEntries,
                                  List<Cell> cells, String imagePath) {
        StringBuilder text = new StringBuilder();
        text.append("# ").append(sheetName).append(" — ").append(cells.size())
                .append(" of ").append(totalEntries).append(" cells selected\n");
        if (imagePath != null && !imagePath.isBlank()) {
            text.append("\nImage of this selection: ").append(imagePath).append('\n');
        }
        if (sheetNote != null && !sheetNote.isBlank()) {
            text.append("\nSheet note: ").append(sheetNote.replace('\n', ' ')).append('\n');
        }
        text.append("\n| cell | id | block / slot | cells | cover | tags | note |\n");
        text.append("|---|---|---|---|---|---|---|\n");
        for (Cell cell : cells) {
            TilesetExport.Entry entry = cell.entry();
            text.append("| ").append(cell.label())
                    .append(" | ").append(entry.id)
                    .append(" | ").append(entry.isBlockMember()
                            ? entry.blockId + " / " + entry.slot : "")
                    .append(" | ").append(entry.footprintX).append('x').append(entry.footprintY)
                    .append(" | ").append(entry.cover)
                    .append(" | ").append(String.join(" ", entry.tags))
                    .append(" | ").append(escape(entry.note))
                    .append(" |\n");
        }
        return text.toString();
    }

    /** A pipe or a newline in a note would end the row it is sitting in. */
    private static String escape(String note) {
        return note == null ? "" : note.replace("|", "\\|").replace('\n', ' ');
    }
}
