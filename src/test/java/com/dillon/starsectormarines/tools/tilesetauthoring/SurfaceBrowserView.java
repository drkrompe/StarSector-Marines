package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
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

    private final DefaultListModel<SurfaceCatalog.Purpose> purposeModel = new DefaultListModel<>();
    private final JList<SurfaceCatalog.Purpose> purposes = new JList<>(purposeModel);
    private final DefaultListModel<SurfaceCatalog.Candidate> candidateModel =
            new DefaultListModel<>();
    private final JList<SurfaceCatalog.Candidate> candidates = new JList<>(candidateModel);
    private final JLabel roomPreview = new JLabel("", SwingConstants.CENTER);
    private final JLabel provenance = new JLabel(" ");
    private final BlockPreview previews;

    private final JPanel purposeScreen = new JPanel(new BorderLayout(0, 4));
    private final JPanel setScreen = new JPanel(new BorderLayout(8, 4));
    private final JPanel setActions = new JPanel();

    /**
     * @param previews where the pictures come from
     * @param onOpen   given a candidate to open in the sheet editor
     */
    public SurfaceBrowserView(BlockPreview previews, Consumer<SurfaceCatalog.Candidate> onOpen) {
        this.previews = previews;

        purposes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        purposes.setCellRenderer(new PurposeCell());
        grid(purposes);
        purposes.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showCandidates();
        });

        candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidates.setCellRenderer(new CandidateCell());
        grid(candidates);
        candidates.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showPickedCandidate();
        });
        candidates.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && canOpen()) {
                    onOpen.accept(candidates.getSelectedValue());
                }
            }
        });

        JScrollPane purposeScroll = new JScrollPane(purposes);
        purposeScroll.getVerticalScrollBar().setUnitIncrement(24);
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

    /** Replace the listing, keeping the operator on the surface they were on. */
    public void setPurposes(List<SurfaceCatalog.Purpose> scanned) {
        SurfaceCatalog.Purpose keep = purposes.getSelectedValue();
        purposeModel.clear();
        int restore = -1;
        for (SurfaceCatalog.Purpose purpose : scanned) {
            if (keep != null && keep.name().equals(purpose.name())) restore = purposeModel.size();
            purposeModel.addElement(purpose);
        }
        if (restore >= 0) purposes.setSelectedIndex(restore);
        showCandidates();
    }

    /** Show one surface by name, as if it had been clicked. */
    public boolean select(String surfaceName) {
        for (int index = 0; index < purposeModel.size(); index++) {
            if (purposeModel.get(index).name().equalsIgnoreCase(surfaceName)) {
                purposes.setSelectedIndex(index);
                purposes.ensureIndexIsVisible(index);
                showCandidates();
                return true;
            }
        }
        return false;
    }

    public SurfaceCatalog.Purpose selectedPurpose() {
        return purposes.getSelectedValue();
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
        SurfaceCatalog.Purpose purpose = purposes.getSelectedValue();
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
