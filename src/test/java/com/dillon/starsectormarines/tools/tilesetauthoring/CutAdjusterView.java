package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A patch's cut, magnified enough to fix it.
 *
 * <p>The slicer keys on alpha and the grid cutter divides by a stated pitch.
 * Both are right most of the time and wrong for a particular plate — a prop
 * whose contact shadow was keyed away with it, a wall block whose grid starts
 * two pixels left of where the art does. Re-slicing to correct one of those
 * moves every other piece on the sheet as well, which is a bad trade for two
 * pixels.
 *
 * <p>The whole selection moves as one grid, because that is the shape the fault
 * has. Nine cells of a wall are not nine independent rectangles that happen to
 * be adjacent; they are one plate cut on one grid, and a correction applied to
 * them a cell at a time produces nine answers where the art has one. So the
 * controls are the grid's — where its first line falls, and how big one cell is
 * — and a single piece is simply the 1x1 case of the same thing.
 *
 * <p>The sheet view this sits beside draws the plate at 1:1, which is the right
 * scale for picking a piece out of a hundred and no use at all for seeing
 * whether a boundary is one pixel out. So this magnifies the region around the
 * cut and draws the grid over it: the picture shows what is being included and,
 * just as important, the sliver of neighbouring art that is not.
 */
public final class CutAdjusterView extends JPanel {

    /** The most the region around the cut is blown up; a big patch gets less. */
    private static final int MAX_ZOOM = 6;
    /** Plate pixels kept visible outside the cut, so its edges have context. */
    private static final int MARGIN_PX = 10;

    private static final Color CUT = new Color(0x3F, 0xC1, 0xFF);
    private static final Color OUTSIDE = new Color(0x00, 0x00, 0x00, 0x88);

    private final JSpinner left = spinner();
    private final JSpinner top = spinner();
    private final JSpinner cellWidth = spinner();
    private final JSpinner cellHeight = spinner();
    private final JLabel caption = new JLabel(" ");
    private final Magnified magnified = new Magnified();
    private final JButton save = new JButton("Save and re-export");

    private BufferedImage sheet;
    private GridPatch patch;
    private String refusal;
    private boolean loading;

    /**
     * @param onChanged told whenever the proposed grid moves, so the caller can
     *                  preview without committing
     * @param onSave    told to write the document
     */
    public CutAdjusterView(Runnable onChanged, Runnable onSave) {
        super(new BorderLayout(0, 6));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        controls.add(new JLabel("left "));
        controls.add(size(left));
        controls.add(new JLabel("   top "));
        controls.add(size(top));
        controls.add(new JLabel("   cell width "));
        controls.add(size(cellWidth));
        controls.add(new JLabel("   cell height "));
        controls.add(size(cellHeight));
        controls.add(Box.createHorizontalStrut(16));
        controls.add(save);
        controls.add(Box.createHorizontalGlue());

        for (JSpinner field : new JSpinner[]{left, top, cellWidth, cellHeight}) {
            field.addChangeListener(event -> {
                if (loading) return;
                magnified.repaint();
                describe();
                onChanged.run();
            });
        }
        save.addActionListener(event -> onSave.run());

        caption.setFont(caption.getFont().deriveFont(Font.PLAIN, 11f));
        caption.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));

        JPanel picture = new JPanel(new BorderLayout());
        picture.setBorder(BorderFactory.createTitledBorder(
                "The cut, magnified — the dimmed edge is plate the selection leaves out"));
        picture.add(magnified, BorderLayout.CENTER);

        add(controls, BorderLayout.NORTH);
        add(picture, BorderLayout.CENTER);
        add(caption, BorderLayout.SOUTH);
    }

    /**
     * Show the grid {@code selected} forms on {@code sheet}.
     *
     * <p>A selection that is not a filled rectangle of cells leaves the controls
     * dead and says why, rather than adjusting some subset of it.
     */
    public void show(BufferedImage sheet, List<TilesetExport.Entry> selected) {
        this.sheet = sheet;
        GridPatch.Derived derived = GridPatch.of(selected);
        this.patch = derived.patch();
        this.refusal = derived.refusal();
        loading = true;
        try {
            GridCut cut = patch == null ? null : patch.cut();
            left.setValue(cut == null ? 0.0 : cut.originX());
            top.setValue(cut == null ? 0.0 : cut.originY());
            cellWidth.setValue(cut == null ? 1.0 : cut.pitchX());
            cellHeight.setValue(cut == null ? 1.0 : cut.pitchY());
        } finally {
            loading = false;
        }
        boolean editable = patch != null && sheet != null;
        for (JSpinner field : new JSpinner[]{left, top, cellWidth, cellHeight}) {
            field.setEnabled(editable);
        }
        save.setEnabled(editable);
        describe();
        magnified.repaint();
    }

    /** The patch being adjusted, or null when the selection forms no grid. */
    public GridPatch patch() {
        return patch;
    }

    /** The grid the controls currently propose, or null. */
    public GridCut proposed() {
        if (patch == null) return null;
        return new GridCut(patch.cut().cols(), patch.cut().rows(),
                value(left), Math.max(0.01, value(cellWidth)),
                value(top), Math.max(0.01, value(cellHeight)));
    }

    /** The patch as the controls propose it, or null. */
    public GridPatch proposedPatch() {
        return patch == null ? null : patch.withCut(proposed());
    }

    /** Whether the proposal would move anything. */
    public boolean isChanged() {
        GridPatch now = proposedPatch();
        return now != null && now.drift() > 0;
    }

    private void describe() {
        if (patch == null) {
            caption.setText(refusal == null ? "Pick a piece to adjust." : refusal);
            return;
        }
        GridPatch now = proposedPatch();
        String what = patch.cells().size() == 1
                ? patch.cells().get(0).entry().id
                : patch.cut().cols() + "x" + patch.cut().rows() + " cells";
        int drift = now.drift();
        if (drift == 0) {
            caption.setText(what + " — " + describe(patch.bounds())
                    + ", cut on a " + round(patch.cut().pitchX()) + "x"
                    + round(patch.cut().pitchY()) + " grid");
            return;
        }
        caption.setText(what + " — " + describe(patch.bounds()) + "  →  "
                + describe(now.bounds()) + ", worst cell edge moves " + drift
                + " px   (not saved)");
    }

    private static String describe(SheetSlicer.Piece piece) {
        return piece.x() + "," + piece.y() + " of " + piece.width() + "x" + piece.height();
    }

    /** A pitch reads as a whole number when it is one, and to two places when not. */
    private static String round(double pitch) {
        return pitch == Math.rint(pitch)
                ? String.valueOf((long) pitch)
                : String.format("%.2f", pitch);
    }

    private static double value(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    /**
     * A grid line can fall on a fraction of a pixel, so the controls carry one.
     *
     * <p>A ten-column plate whose pitch is 123.25 has to keep the quarter: dropped,
     * it is three pixels of drift by the last column, which is the fault being
     * corrected here rather than a rounding detail.
     */
    private static JSpinner spinner() {
        JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(0.0d, -100_000.0d, 100_000.0d, 1.0d));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.##"));
        return spinner;
    }

    private static JComponent size(JSpinner spinner) {
        spinner.setMaximumSize(new Dimension(84, 26));
        spinner.setPreferredSize(new Dimension(84, 26));
        return spinner;
    }

    /** The plate around the cut, blown up, with the grid drawn over it. */
    private final class Magnified extends JComponent {

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(0x1B, 0x20, 0x27));
                g.fillRect(0, 0, getWidth(), getHeight());
                if (sheet == null || patch == null) return;

                GridPatch proposed = proposedPatch();
                SheetSlicer.Piece bounds = proposed.bounds();
                int fromX = Math.max(0, bounds.x() - MARGIN_PX);
                int fromY = Math.max(0, bounds.y() - MARGIN_PX);
                int toX = Math.min(sheet.getWidth(), bounds.x() + bounds.width() + MARGIN_PX);
                int toY = Math.min(sheet.getHeight(), bounds.y() + bounds.height() + MARGIN_PX);
                if (toX <= fromX || toY <= fromY) return;

                // A whole plate does not fit at inspection magnification, and
                // shrinking the picture is better than cropping it: the fault
                // being looked for is usually at an edge.
                int zoom = Math.max(1, Math.min(MAX_ZOOM, Math.min(
                        getWidth() / (toX - fromX), getHeight() / (toY - fromY))));

                // Nearest neighbour: this is pixel art being inspected a pixel
                // at a time, and a smoothed edge is the thing being looked for.
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int drawnWidth = (toX - fromX) * zoom;
                int drawnHeight = (toY - fromY) * zoom;
                int originX = Math.max(0, (getWidth() - drawnWidth) / 2);
                int originY = Math.max(0, (getHeight() - drawnHeight) / 2);
                g.drawImage(sheet.getSubimage(fromX, fromY, toX - fromX, toY - fromY),
                        originX, originY, drawnWidth, drawnHeight, null);

                int cutX = originX + (bounds.x() - fromX) * zoom;
                int cutY = originY + (bounds.y() - fromY) * zoom;
                int cutW = bounds.width() * zoom;
                int cutH = bounds.height() * zoom;

                // Dim what the cut leaves out, so the boundary reads as a
                // decision about which pixels belong rather than as a frame.
                g.setColor(OUTSIDE);
                g.fillRect(originX, originY, drawnWidth, cutY - originY);
                g.fillRect(originX, cutY + cutH, drawnWidth,
                        originY + drawnHeight - (cutY + cutH));
                g.fillRect(originX, cutY, cutX - originX, cutH);
                g.fillRect(cutX + cutW, cutY, originX + drawnWidth - (cutX + cutW), cutH);

                // The seams inside the patch, so a pitch that is a fraction out
                // is visible where it shows first: against the art, cell by cell.
                g.setColor(CUT);
                g.setStroke(new BasicStroke(1f));
                GridCut cut = proposed.cut();
                for (GridPatch.Placed placed : proposed.cells()) {
                    SheetSlicer.Piece cell = cut.cell(placed.col(), placed.row());
                    g.drawRect(originX + (cell.x() - fromX) * zoom,
                            originY + (cell.y() - fromY) * zoom,
                            cell.width() * zoom, cell.height() * zoom);
                }

                g.setColor(CUT);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(cutX, cutY, cutW, cutH);
            } finally {
                g.dispose();
            }
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(520, 420);
        }
    }

}
