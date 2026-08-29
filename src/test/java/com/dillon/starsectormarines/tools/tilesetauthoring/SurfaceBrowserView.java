package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * The other way into the workspace: pick what is needed, not which file to open.
 *
 * <p>The sheet library beside this one answers "which art do I have"; this
 * answers "what can be a wall". They are the same workspace reached from
 * opposite ends, which is why they sit as two tabs over one editor rather than
 * as two pages — picking a wall here and picking its sheet there both end with
 * that sheet open and its pieces selected.
 *
 * <p>Choosing a surface lists every block that could fill it, marked with the
 * one the mapping uses and with whether its slicing can be edited. Opening a
 * candidate is the handoff: the sheet it was cut from opens and its slots come
 * up selected, so the pieces behind the wall are the ones already picked out.
 */
public final class SurfaceBrowserView extends JPanel {

    private final DefaultListModel<SurfaceCatalog.Purpose> purposeModel = new DefaultListModel<>();
    private final JList<SurfaceCatalog.Purpose> purposes = new JList<>(purposeModel);
    private final DefaultListModel<SurfaceCatalog.Candidate> candidateModel =
            new DefaultListModel<>();
    private final JList<SurfaceCatalog.Candidate> candidates = new JList<>(candidateModel);
    private final JLabel advice = new JLabel(" ");
    private final JButton open = new JButton("Open its sheet");
    private final JPanel foot;

    /**
     * @param onOpen given the candidate to open — the page answers by loading its
     *               document and selecting the pieces its slots were cut from
     */
    public SurfaceBrowserView(Consumer<SurfaceCatalog.Candidate> onOpen) {
        super(new BorderLayout(0, 4));

        purposes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        purposes.setCellRenderer(new PurposeCell());
        purposes.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showCandidates();
        });

        candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidates.setCellRenderer(new CandidateCell());
        candidates.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateOpenAction();
        });
        candidates.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && canOpen()) {
                    onOpen.accept(candidates.getSelectedValue());
                }
            }
        });

        open.setEnabled(false);
        open.addActionListener(event -> {
            if (canOpen()) onOpen.accept(candidates.getSelectedValue());
        });

        JScrollPane purposeScroll = new JScrollPane(purposes);
        purposeScroll.setBorder(BorderFactory.createTitledBorder("What is needed"));
        purposeScroll.setPreferredSize(new Dimension(260, 240));

        JScrollPane candidateScroll = new JScrollPane(candidates);
        candidateScroll.setBorder(BorderFactory.createTitledBorder("What could fill it"));
        candidateScroll.setPreferredSize(new Dimension(260, 200));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                purposeScroll, candidateScroll);
        split.setResizeWeight(0.55);

        advice.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        advice.setFont(advice.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel foot = new JPanel(new BorderLayout(0, 2));
        foot.add(advice, BorderLayout.NORTH);
        foot.add(open, BorderLayout.SOUTH);

        this.foot = foot;
        add(split, BorderLayout.CENTER);
        add(foot, BorderLayout.SOUTH);
    }

    /**
     * Drop this panel's own advice line and Open button.
     *
     * <p>Inside a walkthrough the screen already has a footer saying what is
     * still needed and a button that leaves the step. Two of each, one greyed
     * out for a reason printed twice, reads as a bug rather than as guidance.
     */
    public void hideOwnActions() {
        remove(foot);
        revalidate();
    }

    /** Replace the listing, keeping the operator on the surface they were looking at. */
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

    /**
     * Show one surface by name, as if it had been clicked. Returns whether it
     * was there to show.
     */
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

    /** The candidate currently picked, or null. */
    public SurfaceCatalog.Candidate selectedCandidate() {
        return candidates.getSelectedValue();
    }

    /** How many blocks the picked surface is offering. */
    public int shownCandidateCount() {
        return candidateModel.size();
    }

    /**
     * List what could fill the chosen surface and pick the first one.
     *
     * <p>Candidates sort in-use first, so the default pick is the art actually
     * being drawn — which is what someone asking "what is the wall" wants to
     * look at. Leaving nothing picked would leave the one action on this panel
     * greyed out until a second click that has only one sensible target.
     */
    private void showCandidates() {
        candidateModel.clear();
        SurfaceCatalog.Purpose purpose = purposes.getSelectedValue();
        if (purpose != null) {
            for (SurfaceCatalog.Candidate candidate : purpose.candidates()) {
                candidateModel.addElement(candidate);
            }
        }
        if (!candidateModel.isEmpty()) candidates.setSelectedIndex(0);
        updateOpenAction();
    }

    private boolean canOpen() {
        SurfaceCatalog.Candidate candidate = candidates.getSelectedValue();
        return candidate != null && candidate.isEditable();
    }

    private void updateOpenAction() {
        open.setEnabled(canOpen());
        advice.setText(adviceFor(purposes.getSelectedValue(), candidates.getSelectedValue()));
    }

    /**
     * What to do next, in the operator's terms. A dead "Open" button with no
     * reason beside it reads as a broken tool rather than as art with no
     * authoring document behind it.
     */
    static String adviceFor(SurfaceCatalog.Purpose purpose, SurfaceCatalog.Candidate candidate) {
        if (purpose == null) return "Pick what you need.";
        if (purpose.isUnmapped()) {
            return purpose.name() + " has nothing mapped to it.";
        }
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

    /** The needs, with what fills each one, so the list is readable without opening anything. */
    private static final class PurposeCell extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            SurfaceCatalog.Purpose purpose = (SurfaceCatalog.Purpose) value;
            String filled = purpose.isUnmapped() ? "nothing mapped" : purpose.mappedId();
            setText("<html><b>" + purpose.name() + "</b> &nbsp;<font size=-2>"
                    + purpose.vocabulary() + "</font><br><font size=-2>" + filled
                    + "</font></html>");
            return this;
        }
    }

    /** The candidates, each saying where it came from and whether it is live. */
    private static final class CandidateCell extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            SurfaceCatalog.Candidate candidate = (SurfaceCatalog.Candidate) value;
            String provenance = candidate.isEditable()
                    ? candidate.sheetName() + ", " + candidate.slots().size() + " slots"
                    : candidate.sheetName() + ", shipped only";
            setText("<html>" + (candidate.inUse() ? "<b>" : "") + candidate.blockId()
                    + (candidate.inUse() ? "</b> &nbsp;<font size=-2>in use</font>" : "")
                    + "<br><font size=-2>" + provenance + "</font></html>");
            return this;
        }
    }

}
