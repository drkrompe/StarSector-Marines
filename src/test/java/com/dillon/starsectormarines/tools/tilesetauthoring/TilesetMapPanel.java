package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringMessages;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The authored sheet's candidates, seen on a generated map beside the content
 * they would replace.
 *
 * <p>Rendering is explicit rather than live. Generating a city and drawing it
 * twice takes seconds, and a preview that re-ran on every keystroke in the
 * footprint column would make the table unusable — this is a thing you ask for
 * once you have a candidate worth asking about.
 */
public final class TilesetMapPanel extends JPanel {

    /** What the page has to offer: the packed sheet and what it is standing in for. */
    public interface Source {
        BufferedImage atlas();

        int cellPx();

        List<TilesetMapPreview.Substitution> substitutions();
    }

    private static final Color VOID = new Color(0x0d, 0x11, 0x17);
    private static final Color CAPTION = new Color(0xC8, 0xD2, 0xDC);
    private static final int GAP = 12;
    private static final int LABEL_PX = 20;

    private final Path projectRoot;
    private final Source source;
    private final Consumer<String> status;

    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(1, 0, 999_999, 1));
    private final JSpinner grid =
            new JSpinner(new SpinnerNumberModel(TilesetMapPreview.DEFAULT_GRID, 24, 160, 8));
    private final JSpinner cellPx =
            new JSpinner(new SpinnerNumberModel(TilesetMapPreview.DEFAULT_CELL_PX, 4, 32, 2));
    private final JLabel view = new JLabel("", JLabel.CENTER);
    private final JButton render = new JButton();

    public TilesetMapPanel(Path projectRoot, Source source, Consumer<String> status) {
        super(new BorderLayout());
        this.projectRoot = projectRoot;
        this.source = source;
        this.status = status;

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new JLabel(" seed "));
        bar.add(small(seed, 76));
        bar.add(new JLabel("  cells "));
        bar.add(small(grid, 64));
        bar.add(new JLabel("  px/cell "));
        bar.add(small(cellPx, 56));
        bar.addSeparator();
        render.setAction(new AbstractAction("Render comparison") {
            @Override public void actionPerformed(ActionEvent event) {
                renderComparison();
            }
        });
        bar.add(render);
        bar.add(Box.createHorizontalGlue());

        view.setVerticalAlignment(JLabel.TOP);
        view.setText("Bind a piece to a shipped id in the \"stands in for\" column, "
                + "then render.");
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createTitledBorder(
                "Generated map — as it ships, and with the candidates substituted"));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);

        add(bar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    private static JComponent small(JComponent field, int width) {
        field.setMaximumSize(new Dimension(width, 26));
        field.setPreferredSize(new Dimension(width, 26));
        return field;
    }

    private void renderComparison() {
        BufferedImage atlas = source.atlas();
        if (atlas == null) {
            status.accept("Open a sheet before rendering a map.");
            return;
        }
        List<TilesetMapPreview.Substitution> substitutions = source.substitutions();
        int authoredCellPx = source.cellPx();
        long chosenSeed = ((Number) seed.getValue()).longValue();
        int cells = (Integer) grid.getValue();
        int px = (Integer) cellPx.getValue();

        render.setEnabled(false);
        status.accept("Generating and rendering a " + cells + "x" + cells + " map…");
        new SwingWorker<TilesetMapPreview.Result, Void>() {
            @Override protected TilesetMapPreview.Result doInBackground() throws Exception {
                // Off the event thread: this generates a city and draws it twice.
                return TilesetMapPreview.render(projectRoot, atlas, authoredCellPx,
                        substitutions, chosenSeed, cells, px);
            }

            @Override protected void done() {
                render.setEnabled(true);
                try {
                    TilesetMapPreview.Result result = get();
                    view.setIcon(new ImageIcon(compose(result)));
                    view.setText(null);
                    status.accept(substitutions.isEmpty()
                            ? "Rendered the baseline. Bind a piece to compare against it."
                            : "Rendered seed " + chosenSeed + " with " + substitutions.size()
                                    + " substitution(s)."
                                    + (result.notes().isEmpty() ? "" : " " + String.join(" ",
                                            result.notes())));
                } catch (Exception failure) {
                    view.setIcon(null);
                    view.setText("map preview failed");
                    AuthoringMessages.error(TilesetMapPanel.this, "Map preview",
                            "Could not render the map preview.",
                            failure.getCause() == null ? failure : failure.getCause());
                }
            }
        }.execute();
    }

    /** Baseline and candidate side by side, captioned, so the difference is the subject. */
    static BufferedImage compose(TilesetMapPreview.Result result) {
        BufferedImage left = result.baseline();
        BufferedImage right = result.substituted();
        int width = left.getWidth() + GAP + right.getWidth();
        int height = Math.max(left.getHeight(), right.getHeight()) + LABEL_PX;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(VOID);
        g.fillRect(0, 0, width, height);
        g.drawImage(left, 0, LABEL_PX, null);
        g.drawImage(right, left.getWidth() + GAP, LABEL_PX, null);
        g.setColor(CAPTION);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
        g.drawString("as it ships", 4, LABEL_PX - 6);
        g.drawString("with candidates", left.getWidth() + GAP + 4, LABEL_PX - 6);
        g.dispose();
        return sheet;
    }
}
