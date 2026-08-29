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

/**
 * One piece's cut, magnified enough to fix it.
 *
 * <p>The slicer keys on alpha and the grid cutter divides by a stated pitch.
 * Both are right most of the time and wrong for a particular piece — a prop
 * whose contact shadow was keyed away with it, a cell whose seam sits a pixel
 * off the line through its neighbours. Re-slicing to correct one of those moves
 * every other piece as well, which is a bad trade for two pixels.
 *
 * <p>The sheet view this sits beside draws the plate at 1:1, which is the right
 * scale for picking a piece out of a hundred and no use at all for seeing
 * whether a boundary is one pixel out. So this magnifies the region around the
 * cut and draws the rectangle over it: the picture shows what is being included
 * and, just as important, the sliver of neighbouring art that is not.
 */
public final class CutAdjusterView extends JPanel {

    /** How much the region around the cut is blown up. */
    private static final int ZOOM = 6;
    /** Plate pixels kept visible outside the cut, so its edges have context. */
    private static final int MARGIN_PX = 10;

    private static final Color CUT = new Color(0x3F, 0xC1, 0xFF);
    private static final Color OUTSIDE = new Color(0x00, 0x00, 0x00, 0x88);

    private final JSpinner left = spinner();
    private final JSpinner top = spinner();
    private final JSpinner width = spinner();
    private final JSpinner height = spinner();
    private final JLabel caption = new JLabel(" ");
    private final Magnified magnified = new Magnified();
    private final JButton save = new JButton("Save and re-export");

    private BufferedImage sheet;
    private TilesetExport.Entry entry;
    private boolean loading;

    /**
     * @param onChanged   told the proposed rectangle whenever it moves, so the
     *                    caller can preview without committing
     * @param onSave      told to write the document
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
        controls.add(new JLabel("   width "));
        controls.add(size(width));
        controls.add(new JLabel("   height "));
        controls.add(size(height));
        controls.add(Box.createHorizontalStrut(16));
        controls.add(save);
        controls.add(Box.createHorizontalGlue());

        for (JSpinner field : new JSpinner[]{left, top, width, height}) {
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
                "The cut, magnified — the dimmed edge is plate this piece leaves out"));
        picture.add(magnified, BorderLayout.CENTER);

        add(controls, BorderLayout.NORTH);
        add(picture, BorderLayout.CENTER);
        add(caption, BorderLayout.SOUTH);
    }

    /** Show one piece of one sheet. */
    public void show(BufferedImage sheet, TilesetExport.Entry entry) {
        this.sheet = sheet;
        this.entry = entry;
        loading = true;
        try {
            SheetSlicer.Piece piece = entry == null ? null : entry.piece;
            left.setValue(piece == null ? 0 : piece.x());
            top.setValue(piece == null ? 0 : piece.y());
            width.setValue(piece == null ? 1 : piece.width());
            height.setValue(piece == null ? 1 : piece.height());
        } finally {
            loading = false;
        }
        save.setEnabled(entry != null && sheet != null);
        describe();
        magnified.repaint();
    }

    /** The piece being adjusted, or null. */
    public TilesetExport.Entry entry() {
        return entry;
    }

    /** The rectangle the controls currently propose. */
    public SheetSlicer.Piece proposed() {
        return new SheetSlicer.Piece(value(left), value(top),
                Math.max(1, value(width)), Math.max(1, value(height)));
    }

    /** Whether the proposal differs from the piece as it stands. */
    public boolean isChanged() {
        return entry != null && !proposed().equals(entry.piece);
    }

    private void describe() {
        if (entry == null) {
            caption.setText("Pick a piece to adjust.");
            return;
        }
        SheetSlicer.Piece was = entry.piece;
        SheetSlicer.Piece now = proposed();
        if (was.equals(now)) {
            caption.setText(entry.id + " — cut at " + was.x() + "," + was.y()
                    + " of " + was.width() + "x" + was.height());
            return;
        }
        caption.setText(entry.id + " — " + was.x() + "," + was.y() + " of "
                + was.width() + "x" + was.height() + "  →  " + now.x() + "," + now.y()
                + " of " + now.width() + "x" + now.height() + "   (not saved)");
    }

    private static int value(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static JSpinner spinner() {
        return new JSpinner(new SpinnerNumberModel(0, 0, 100_000, 1));
    }

    private static JComponent size(JSpinner spinner) {
        spinner.setMaximumSize(new Dimension(78, 26));
        spinner.setPreferredSize(new Dimension(78, 26));
        return spinner;
    }

    /** The plate around the cut, blown up, with the cut drawn over it. */
    private final class Magnified extends JComponent {

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(0x1B, 0x20, 0x27));
                g.fillRect(0, 0, getWidth(), getHeight());
                if (sheet == null || entry == null) return;

                SheetSlicer.Piece cut = proposed();
                int fromX = Math.max(0, cut.x() - MARGIN_PX);
                int fromY = Math.max(0, cut.y() - MARGIN_PX);
                int toX = Math.min(sheet.getWidth(), cut.x() + cut.width() + MARGIN_PX);
                int toY = Math.min(sheet.getHeight(), cut.y() + cut.height() + MARGIN_PX);
                if (toX <= fromX || toY <= fromY) return;

                // Nearest neighbour: this is pixel art being inspected a pixel
                // at a time, and a smoothed edge is the thing being looked for.
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int drawnWidth = (toX - fromX) * ZOOM;
                int drawnHeight = (toY - fromY) * ZOOM;
                int originX = Math.max(0, (getWidth() - drawnWidth) / 2);
                int originY = Math.max(0, (getHeight() - drawnHeight) / 2);
                g.drawImage(sheet.getSubimage(fromX, fromY, toX - fromX, toY - fromY),
                        originX, originY, drawnWidth, drawnHeight, null);

                int cutX = originX + (cut.x() - fromX) * ZOOM;
                int cutY = originY + (cut.y() - fromY) * ZOOM;
                int cutW = cut.width() * ZOOM;
                int cutH = cut.height() * ZOOM;

                // Dim what the cut leaves out, so the boundary reads as a
                // decision about which pixels belong rather than as a frame.
                g.setColor(OUTSIDE);
                g.fillRect(originX, originY, drawnWidth, cutY - originY);
                g.fillRect(originX, cutY + cutH, drawnWidth,
                        originY + drawnHeight - (cutY + cutH));
                g.fillRect(originX, cutY, cutX - originX, cutH);
                g.fillRect(cutX + cutW, cutY, originX + drawnWidth - (cutX + cutW), cutH);

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
