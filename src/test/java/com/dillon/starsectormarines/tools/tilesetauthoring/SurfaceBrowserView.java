package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The two screens of the purpose-first walkthrough: what is needed, and what
 * the project has for it.
 *
 * <p>Kept in one class because they are one selection. Choosing a surface on
 * the first screen is what the second screen is a list of, and splitting them
 * into two components would mean wiring that selection between them for no gain.
 *
 * <p>Both are pictures rather than lists of ids. {@code urban.wall} and
 * {@code road.embankment} are both walls and are nothing alike — masonry and a
 * sandbag embankment — and only looking at them says which is which. Both are
 * alphabetical, because these are lists a name is looked up in.
 */
public final class SurfaceBrowserView {

    /** Edge of a tile's thumbnail, in pixels. */
    private static final int THUMB = 100;
    /** A grid tile: the picture, and two lines of caption under it. */
    private static final Dimension TILE = new Dimension(THUMB + 24, THUMB + 34);
    /** How large one deck cell is drawn in the big preview. */
    private static final int ROOM_CELL = 56;

    /** One grid per section, in reading order, sharing one selection between them. */
    private final List<JList<SurfaceCatalog.Purpose>> sections = new ArrayList<>();
    private final List<JList<SurfaceCatalog.Purpose>> gridsToReflow = new ArrayList<>();
    private final JPanel sectionStack = new WidthTrackingStack();
    private boolean clearingOtherSections;
    private final DefaultListModel<SurfaceCatalog.Candidate> candidateModel =
            new DefaultListModel<>();
    private final JList<SurfaceCatalog.Candidate> candidates = new JList<>(candidateModel);
    private final JLabel roomPreview = new JLabel("", SwingConstants.CENTER);
    private final JLabel provenance = new JLabel(" ");
    private final BlockPreview previews;
    private Runnable onSelectionChanged = () -> {};

    private final JPanel purposeScreen = new JPanel(new BorderLayout(0, 4));
    private final JPanel setScreen = new JPanel(new BorderLayout(8, 4));
    private final JPanel setActions = new JPanel();

    /**
     * @param previews where the pictures come from
     * @param onOpen   given a candidate to open in the sheet editor
     */
    public SurfaceBrowserView(BlockPreview previews, Consumer<SurfaceCatalog.Candidate> onOpen) {
        this.previews = previews;

        sectionStack.setLayout(new BoxLayout(sectionStack, BoxLayout.Y_AXIS));

        candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidates.setCellRenderer(new CandidateCell());
        grid(candidates);
        candidates.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showPickedCandidate();
                onSelectionChanged.run();
            }
        });
        candidates.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && canOpen()) {
                    onOpen.accept(candidates.getSelectedValue());
                }
            }
        });

        JScrollPane purposeScroll = new JScrollPane(sectionStack);
        purposeScroll.getVerticalScrollBar().setUnitIncrement(24);
        purposeScroll.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                reflow(purposeScroll.getViewport().getWidth());
            }
        });
        purposeScreen.add(purposeScroll, BorderLayout.CENTER);

        JScrollPane candidateScroll = new JScrollPane(candidates);
        candidateScroll.getVerticalScrollBar().setUnitIncrement(24);
        candidateScroll.setPreferredSize(new Dimension(3 * TILE.width + 30, 320));

        roomPreview.setBorder(BorderFactory.createTitledBorder(
                "Drawn as a room — every cell of a 3×3 has a different mask"));
        roomPreview.setPreferredSize(new Dimension(320, 260));
        provenance.setFont(provenance.getFont().deriveFont(Font.PLAIN, 11f));
        provenance.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.add(roomPreview, BorderLayout.CENTER);
        right.add(provenance, BorderLayout.SOUTH);

        setScreen.add(candidateScroll, BorderLayout.WEST);
        setScreen.add(right, BorderLayout.CENTER);
        setScreen.add(setActions, BorderLayout.SOUTH);
    }

    /**
     * Lay a list out as a wrapping grid of tiles rather than a column of rows.
     *
     * <p>These are pictures being compared. A column gives each one a whole line
     * of the window and shows six of nineteen; a grid shows all of them at once,
     * which is what makes it a choice rather than a scroll.
     */
    private static void grid(JList<?> list) {
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(0);
        list.setFixedCellWidth(TILE.width);
        list.setFixedCellHeight(TILE.height);
    }

    /**
     * Say who to tell when the selection moves.
     *
     * <p>A screen's precondition is usually this selection, and nothing else can
     * see it change.
     */
    public void onSelectionChanged(Runnable listener) {
        this.onSelectionChanged = listener;
    }

    /** Screen one: every surface the generator can ask for. */
    public JPanel purposeScreen() {
        return purposeScreen;
    }

    /** Screen two: what the project has for the chosen surface. */
    public JPanel setScreen() {
        return setScreen;
    }

    /** Put the actions that manage the set along the bottom of screen two. */
    public void setSetActions(List<JButton> buttons) {
        setActions.removeAll();
        for (JButton button : buttons) setActions.add(button);
        setActions.revalidate();
    }

    /**
     * Replace the listing, keeping the operator on the surface they were on.
     *
     * <p>Rebuilt rather than refilled: which sections exist depends on what was
     * scanned, and a heading over an empty grid is worse than no heading.
     */
    public void setPurposes(List<SurfaceCatalog.Purpose> scanned) {
        SurfaceCatalog.Purpose keep = selectedPurpose();
        sections.clear();
        gridsToReflow.clear();
        sectionStack.removeAll();

        SurfaceCategory.Setting heading = null;
        for (SurfaceCategory.Section section : SurfaceCategory.sectionsOf(scanned)) {
            if (section.setting() != heading) {
                heading = section.setting();
                sectionStack.add(header(heading.label(), 15f, 12));
            }
            sectionStack.add(header(section.kind().label(), 12f, 4));

            DefaultListModel<SurfaceCatalog.Purpose> model = new DefaultListModel<>();
            for (SurfaceCatalog.Purpose purpose : scanned) {
                if (SurfaceCategory.of(purpose.name()).equals(section)) model.addElement(purpose);
            }
            sectionStack.add(sectionGrid(model));
        }
        reflow(purposeScreen.getWidth() > 0 ? purposeScreen.getWidth() : 960);
        sectionStack.revalidate();
        sectionStack.repaint();

        if (keep != null) select(keep.name());
        showCandidates();
    }

    /** One section's grid, sharing the single selection with every other. */
    private JComponent sectionGrid(DefaultListModel<SurfaceCatalog.Purpose> model) {
        JList<SurfaceCatalog.Purpose> grid = new JList<>(model);
        grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grid.setCellRenderer(new PurposeCell());
        grid(grid);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        // A wrapping list decides its columns from its own width, and a vertical
        // BoxLayout offers it only its preferred one - so it comes out a single
        // tile wide however much room the window has. Telling it how many
        // columns to use, from the width the pane actually has, is what makes it
        // a grid rather than a column.
        grid.setVisibleRowCount(0);
        gridsToReflow.add(grid);
        grid.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || clearingOtherSections) return;
            if (grid.getSelectedValue() == null) return;
            // One selection across every section: the grids are a single list
            // that happens to be drawn under headings.
            clearingOtherSections = true;
            try {
                for (JList<SurfaceCatalog.Purpose> other : sections) {
                    if (other != grid) other.clearSelection();
                }
            } finally {
                clearingOtherSections = false;
            }
            showCandidates();
            onSelectionChanged.run();
        });
        sections.add(grid);
        return grid;
    }

    /**
     * Re-column every section for the width the pane now has.
     *
     * <p>Called on resize and after a rebuild. A wrapping list will not do this
     * for itself inside a vertical stack: it is asked for a preferred size
     * before it has a width, answers as though it had one column, and is then
     * given exactly that.
     */
    void reflow(int available) {
        // A width of nothing is a resize that arrived before the pane had one,
        // and acting on it collapses every section to a single column - which is
        // then what a later paint draws, because nothing re-columns it. Ignore
        // it and wait for a width worth laying out to.
        if (available < TILE.width) return;

        int columns = Math.max(1, (available - 12) / TILE.width);
        for (JList<SurfaceCatalog.Purpose> grid : gridsToReflow) {
            int rows = (grid.getModel().getSize() + columns - 1) / columns;
            grid.setVisibleRowCount(Math.max(1, rows));
            int height = Math.max(1, rows) * TILE.height + 4;
            grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            grid.setPreferredSize(new Dimension(columns * TILE.width, height));
        }
        sectionStack.revalidate();
    }

    /** How many rows the nth section is laid out in — for a test of the columning. */
    int rowsInSection(int index) {
        return gridsToReflow.get(index).getVisibleRowCount();
    }

    /** How many sections the listing built. */
    int sectionCount() {
        return gridsToReflow.size();
    }

    /**
     * A vertical stack that is always exactly as wide as the scroll pane
     * showing it, so its sections can fill the window rather than the width
     * their contents happened to prefer.
     */
    private static final class WidthTrackingStack extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return 120;
        }
        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JComponent header(String text, float size, int topGap) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, size));
        label.setForeground(new Color(0x3A, 0x42, 0x4E));
        label.setBorder(BorderFactory.createEmptyBorder(topGap, 6, 2, 6));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    /** Show one surface by name, as if it had been clicked. */
    public boolean select(String surfaceName) {
        for (JList<SurfaceCatalog.Purpose> grid : sections) {
            for (int index = 0; index < grid.getModel().getSize(); index++) {
                if (grid.getModel().getElementAt(index).name().equalsIgnoreCase(surfaceName)) {
                    grid.setSelectedIndex(index);
                    grid.ensureIndexIsVisible(index);
                    return true;
                }
            }
        }
        return false;
    }

    public SurfaceCatalog.Purpose selectedPurpose() {
        for (JList<SurfaceCatalog.Purpose> grid : sections) {
            SurfaceCatalog.Purpose picked = grid.getSelectedValue();
            if (picked != null) return picked;
        }
        return null;
    }

    public SurfaceCatalog.Candidate selectedCandidate() {
        return candidates.getSelectedValue();
    }

    public int shownCandidateCount() {
        return candidateModel.size();
    }

    /**
     * List what could fill the chosen surface, and start on the one being drawn.
     *
     * <p>The list is alphabetical, so the one in use is not first. It is still
     * what somebody asking "what is the wall" came to look at, so it is what
     * comes up selected.
     */
    private void showCandidates() {
        candidateModel.clear();
        SurfaceCatalog.Purpose purpose = selectedPurpose();
        int inUse = -1;
        if (purpose != null) {
            for (SurfaceCatalog.Candidate candidate : purpose.candidates()) {
                if (candidate.inUse()) inUse = candidateModel.size();
                candidateModel.addElement(candidate);
            }
        }
        if (!candidateModel.isEmpty()) {
            candidates.setSelectedIndex(Math.max(0, inUse));
            candidates.ensureIndexIsVisible(Math.max(0, inUse));
        }
        showPickedCandidate();
    }

    private void showPickedCandidate() {
        SurfaceCatalog.Candidate picked = candidates.getSelectedValue();
        BufferedImage room = picked == null ? null : previews.room(picked.block(), ROOM_CELL);
        roomPreview.setIcon(room == null ? null : new ImageIcon(room));
        roomPreview.setText(room == null ? "no picture for this block" : "");
        provenance.setText(picked == null ? " " : provenanceOf(picked));
    }

    private boolean canOpen() {
        SurfaceCatalog.Candidate candidate = candidates.getSelectedValue();
        return candidate != null && candidate.isEditable();
    }

    /** Where a candidate came from, and whether its slicing can still be changed. */
    static String provenanceOf(SurfaceCatalog.Candidate candidate) {
        if (candidate == null) return " ";
        String where = candidate.isEditable()
                ? "cut from " + candidate.sheetName() + ", " + candidate.slots().size() + " slots"
                : "on " + candidate.sheetName() + ", which has no authoring document — it can be "
                        + "used but not re-cut";
        return candidate.blockId() + " · " + candidate.shape() + " · " + where;
    }

    /**
     * What to do next, in the operator's terms. A dead button with no reason
     * beside it reads as a broken tool rather than as art with no document
     * behind it.
     */
    static String adviceFor(SurfaceCatalog.Purpose purpose, SurfaceCatalog.Candidate candidate) {
        if (purpose == null) return "Pick what you need.";
        if (purpose.isUnmapped()) return purpose.name() + " has nothing mapped to it.";
        if (candidate == null) {
            if (purpose.candidates().isEmpty()) {
                return purpose.name() + " is filled by a sliced tile, not a block.";
            }
            return purpose.candidates().size() + " could fill " + purpose.name() + ".";
        }
        if (!candidate.isEditable()) {
            return candidate.sheetName() + " ships as a tileset with no authoring document, "
                    + "so its slicing cannot be edited here.";
        }
        return "Opens " + candidate.sheetName() + " with its " + candidate.slots().size()
                + " slots selected.";
    }

    /** A surface, with a picture of whatever is drawn for it today. */
    private final class PurposeCell extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            SurfaceCatalog.Purpose purpose = (SurfaceCatalog.Purpose) value;
            SurfaceCatalog.Candidate live = purpose.inUse();
            tile(this, live == null ? null : previews.patch(live.block(), THUMB),
                    purpose.name(),
                    purpose.isUnmapped() ? "nothing mapped" : purpose.mappedId());
            return this;
        }
    }

    /** A block that could fill the chosen surface, with a picture of it. */
    private final class CandidateCell extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            SurfaceCatalog.Candidate candidate = (SurfaceCatalog.Candidate) value;
            tile(this, previews.patch(candidate.block(), THUMB),
                    candidate.blockId(),
                    candidate.inUse() ? "in use" : candidate.sheetName());
            return this;
        }
    }

    /** One tile of the grid: the picture above, two short lines under it. */
    private static void tile(DefaultListCellRenderer cell, BufferedImage picture,
                             String name, String under) {
        cell.setIcon(picture == null ? null : new ImageIcon(picture));
        cell.setHorizontalAlignment(SwingConstants.CENTER);
        cell.setHorizontalTextPosition(SwingConstants.CENTER);
        cell.setVerticalTextPosition(SwingConstants.BOTTOM);
        cell.setIconTextGap(4);
        cell.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        // Centred, and small enough that a long id still fits the tile it names.
        cell.setText("<html><center><b>" + name + "</b><br><font size=-2>" + under
                + "</font></center></html>");
    }
}
