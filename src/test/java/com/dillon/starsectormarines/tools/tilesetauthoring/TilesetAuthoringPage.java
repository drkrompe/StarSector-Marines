package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.DoodadDef.WallSide;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.tools.authoring.AuthoringMessages;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a raw art sheet into a tileset the game can load.
 *
 * <p>The mechanical half — finding the pieces — is automatic and reliable. The
 * half that is not is <b>how much deck each piece covers</b>, and that is a
 * judgement about what the object is rather than a measurement: a console drawn
 * slightly wider than a cell may be a one-cell console or a two-cell bank, and
 * nothing in the pixels says which. So the slicing runs unattended and the
 * footprints are edited here, which is the division of labour a tool for this
 * should have.
 *
 * <p>Pieces are stretched into the footprint they are given rather than placed
 * at their drawn size, so setting a footprint is the authoring act: it says how
 * much floor the thing occupies, and the art follows.
 *
 * <p>Those judgements are saved to a {@link TilesetDocument} beside the raw
 * sheet, so annotating a sheet is a task that can be put down and picked up
 * rather than one long sitting. Re-slicing carries the existing annotations
 * across, which is what makes tuning the threshold safe to do late.
 *
 * <p>The sheets of the project are found rather than browsed for: the toolbar
 * lists what {@link TilesetLibrary} discovers, with the state of each, so
 * picking up an unfinished sheet does not depend on remembering which one it
 * was. A document that names a sheet and its slice settings but no pieces is a
 * valid starting point — it is what a person or a model writes to set a sheet
 * up — and opening one slices immediately rather than showing an empty table.
 *
 * <p>A piece is either a doodad or one cell of a named <b>block</b>. A block is
 * how a wall gets its facing: the game picks the cell from the four-neighbour
 * mask through the block's {@code GridLayout}, so the authoring act is assigning
 * pieces to that layout's slots, not labelling each piece with a direction. The
 * slot names read as <b>"the exterior is on this side"</b>, and the preview
 * draws each block as a room so a mirrored assignment is visible rather than
 * merely wrong.
 */
public final class TilesetAuthoringPage implements AuthoringPage {

    private final AuthoringPageContext context;
    private final JPanel root = new JPanel(new BorderLayout());
    private final EntryTableModel model = new EntryTableModel();
    private final SheetView view = new SheetView();
    private final JTextField idPrefix = new JTextField("doodad.ship", 14);
    private final JTextField sheetName = new JTextField("ship", 10);
    private final JSpinner cellPx = new JSpinner(new SpinnerNumberModel(64, 8, 256, 8));
    private final JSpinner alphaMin =
            new JSpinner(new SpinnerNumberModel(SheetSlicer.DEFAULT_ALPHA_MIN, 1, 254, 1));
    private final JSpinner gridCols = new JSpinner(new SpinnerNumberModel(1, 1, 128, 1));
    private final JSpinner gridRows = new JSpinner(new SpinnerNumberModel(1, 1, 128, 1));
    private final JSpinner screenCellPx = new JSpinner(new SpinnerNumberModel(40, 8, 160, 4));
    private final JLabel preview = new JLabel("", JLabel.CENTER);
    private final JLabel summary = new JLabel(" ");

    private final List<TilesetExport.BlockSpec> blocks = new ArrayList<>();
    private final TilesetLibraryView library = new TilesetLibraryView(this::openFromLibrary);
    private final BlockPreview blockPreviews;
    private final SurfaceBrowserView surfaces;
    private final CutAdjusterView cutAdjuster =
            new CutAdjusterView(this::refreshStep, this::saveAdjustedCut);
    private JTable table;
    private JScrollPane tableScroll;
    private JScrollPane sheetPicture;
    private JTabbedPane previews;

    /** The two screens the page alternates between: the chooser and a walkthrough. */
    private static final String CHOOSER = "chooser";
    private static final String WIZARD = "wizard";
    private final JPanel screens = new JPanel(new CardLayout());
    private TilesetWizard wizard;
    private TilesetWorkflow workflow;
    private TilesetMapPanel mapPanel;

    private BufferedImage source;
    private Path sourcePath;
    private Path documentPath;
    private String sheetNote = "";
    /**
     * Where the stated grid was measured to sit, or null while it is still the
     * canvas division.
     *
     * <p>Carries its own counts, because a placement measured for one layout says
     * nothing about another: restating the grid in the spinners retires it rather
     * than reinterpreting it.
     */
    private GridCut placement;
    /** Explicit atlas destination from the document; empty derives it from content. */
    private String outputSheet = "";
    /**
     * The strip settings from the document, or null for a cell-grid sheet.
     *
     * <p>Held rather than edited: which shape a sheet is, and the scale it is
     * drawn at, are decided when it is set up, and there is nothing on this page
     * that could change them. What matters is that they survive a sitting —
     * dropping them turns a sliced sheet into a grid one on the next save, which
     * renames every id the map selects by and reads as no change at all.
     */
    private TilesetExport.StripSpec strip;
    /**
     * The pictures for the open document's material-backed frames.
     *
     * <p>Held beside the plate for the same reason the plate is: a frame whose
     * picture is a tileable material is not sized off the sheet at all, so
     * anything that measures the strip needs the materials in hand.
     */
    private TilesetExport.Materials materials = TilesetExport.Materials.NONE;
    private boolean dirty;
    /** True while the table is being set from the canvas, so it does not answer back. */
    private boolean syncingSelection;

    public TilesetAuthoringPage(AuthoringPageContext context) {
        this.context = context;
        this.blockPreviews = new BlockPreview(context.projectRoot());
        this.surfaces = new SurfaceBrowserView(blockPreviews, this::openCandidate);
        this.wizard = new TilesetWizard(this::showChooser, context::reportStatus);
        // Every screen whose Next depends on a selection has to tell the wizard
        // when that selection moves; nothing else can see it. Without these the
        // step is answered and the button stays dead, which reads as a bug in
        // the tool rather than as a missing answer.
        surfaces.onSelectionChanged(this::refreshStep);
        library.addSelectionListener(this::refreshStep);
        sheetName.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshStep(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshStep(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshStep(); }
        });

        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getSelectionModel().addListSelectionListener(e -> {
            model.selectedRows = table.getSelectedRows();
            if (!syncingSelection) view.showSelection(model.selectedRows);
            describeSelectedSlot();
            showSelectedCut();
        });
        // The picture is the surface the work happens on, so a selection made
        // there drives the table rather than the other way round. The guard is
        // what keeps the two from answering each other forever.
        view.onSelectionChanged = () -> {
            syncingSelection = true;
            try {
                table.clearSelection();
                int[] rows = view.selection();
                for (int row : rows) table.addRowSelectionInterval(row, row);
                if (rows.length > 0) {
                    table.scrollRectToVisible(table.getCellRect(rows[0], 0, true));
                }
            } finally {
                syncingSelection = false;
            }
        };

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(520, 260));
        preview.setVerticalAlignment(JLabel.TOP);
        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setPreferredSize(new Dimension(520, 320));
        previewScroll.setBorder(BorderFactory.createTitledBorder(
                "Compartment preview — the tileset as loaded, at deck scale"));

        mapPanel = new TilesetMapPanel(context.projectRoot(), new TilesetMapPanel.Source() {
            @Override public BufferedImage atlas() {
                if (source == null || model.entries.isEmpty()) return null;
                return TilesetExport.atlas(source, model.entries, blocks,
                        (Integer) cellPx.getValue(), materials);
            }

            @Override public int cellPx() {
                return (Integer) TilesetAuthoringPage.this.cellPx.getValue();
            }

            @Override public List<TilesetMapPreview.Substitution> substitutions() {
                return bindings();
            }
        }, context::reportStatus);

        JTabbedPane previews = new JTabbedPane();
        previews.addTab("Compartment", previewScroll);
        previews.addTab("Map", mapPanel);

        this.tableScroll = tableScroll;
        this.previews = previews;
        this.sheetPicture = new JScrollPane(view);

        summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        rescanLibrary();

        screens.add(new WorkflowChooser(this::enterWorkflow), CHOOSER);
        screens.add(wizard, WIZARD);
        root.add(screens, BorderLayout.CENTER);
        root.add(summary, BorderLayout.SOUTH);
        showChooser();
    }

    /**
     * Re-ask the screen on show whether it is finished.
     *
     * <p>Called from anything a step's precondition reads: a selection, a text
     * field, the pieces a slice found.
     */
    private void refreshStep() {
        if (wizard != null) wizard.refresh();
    }

    /** Back to the first screen: what are you doing? */
    private void showChooser() {
        ((CardLayout) screens.getLayout()).show(screens, CHOOSER);
        context.reportStatus("Pick what you are here to do");
    }

    /** Begin the chosen walkthrough at its first screen. */
    private void enterWorkflow(TilesetWorkflow workflow) {
        this.workflow = workflow;
        wizard.start(switch (workflow) {
            case SURFACE -> surfaceWalkthrough();
            case SHEET -> sheetWalkthrough();
            case LOOK -> lookWalkthrough();
        });
        ((CardLayout) screens.getLayout()).show(screens, WIZARD);
    }

    // ---- walkthroughs --------------------------------------------------------
    //
    // Each screen holds only the controls its own step needs, which is the whole
    // difference from the toolbar this replaced: a command belonging to the cut
    // no longer sits beside one belonging to the export, and neither is reachable
    // before the sheet it would act on is open.

    /** Start from a need: a wall is wanted, and the sheet is the answer. */
    private List<WizardStep> surfaceWalkthrough() {
        return List.of(
                new LambdaStep("What do you need?",
                        "Every surface the generator can ask for, with a picture of whatever is "
                                + "drawn for it today.",
                        surfaces::purposeScreen)
                        .onEnter(this::rescanSurfaces)
                        .blockedWhen(() -> surfaces.selectedPurpose() == null
                                ? "Pick a surface" : null)
                        .nextLabel("See what fills it"),

                new LambdaStep("The set",
                        "Every block in the project that could fill this surface, whichever "
                                + "sheet it is on. Choose which one is drawn, or add another.",
                        this::surfaceSetBody)
                        .onEnter(this::showSurfaceSet)
                        .blockedWhen(() -> {
                            SurfaceCatalog.Candidate picked = surfaces.selectedCandidate();
                            if (picked == null) return "Pick one of them";
                            if (!picked.isEditable()) {
                                return picked.sheetName() + " has no authoring document, so its "
                                        + "cut cannot be adjusted — it can still be drawn with";
                            }
                            return null;
                        })
                        .onLeave(() -> openCandidate(surfaces.selectedCandidate()))
                        .nextLabel("Open its sheet"),

                adjustCutStep(true));
    }

    /**
     * The screen a piece's cut is corrected on.
     *
     * <p>Reached from either walkthrough, because a bad cut is found either way
     * round: from the surface, when the wall being drawn has a sliver of its
     * neighbour on one edge; or from the sheet, while annotating it.
     */
    private WizardStep adjustCutStep(boolean last) {
        LambdaStep step = new LambdaStep("Adjust the cut",
                "Pick a piece on the left and move its rectangle. Only that piece moves — "
                        + "re-slicing to fix one of them moves every other piece too.",
                this::adjustCutBody)
                .onEnter(this::showSelectedCut);
        return last ? step.last() : step;
    }

    private JPanel adjustCutScreen;

    private JPanel adjustCutBody() {
        if (adjustCutScreen == null) {
            adjustCutScreen = new JPanel(new BorderLayout(0, 6));
        }
        adjustCutScreen.removeAll();
        adjustCutScreen.add(splitOf(tableScroll, cutAdjuster), BorderLayout.CENTER);
        return adjustCutScreen;
    }

    /** Show whichever piece the entry table has selected. */
    private void showSelectedCut() {
        int[] rows = table == null ? new int[0] : table.getSelectedRows();
        TilesetExport.Entry picked = rows.length > 0 && rows[0] < model.entries.size()
                ? model.entries.get(rows[0]) : null;
        cutAdjuster.show(source, picked);
        refreshStep();
    }

    /**
     * Apply the adjusted rectangle, save the document, and re-export.
     *
     * <p>All three, because a cut is only fixed once the atlas is packed from
     * it. Saving the document alone leaves the sheet the game loads with the
     * old rectangle and nothing saying they disagree.
     */
    private void saveAdjustedCut() {
        TilesetExport.Entry entry = cutAdjuster.entry();
        if (entry == null || source == null) return;
        SheetSlicer.Piece proposed = cutAdjuster.proposed();
        try {
            SheetSlicer.Piece was = TilesetOperations.setCut(model.entries, entry.id,
                    proposed.x(), proposed.y(), proposed.width(), proposed.height(),
                    source.getWidth(), source.getHeight());
            model.setEntries(model.entries);
            view.setEntries(model.entries);
            markDirty();
            saveDocument();
            export();
            cutAdjuster.show(source, entry);
            context.reportStatus(entry.id + " cut moved from " + was.x() + "," + was.y()
                    + " of " + was.width() + "x" + was.height() + " and re-exported");
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Save and re-export",
                    "Could not move " + entry.id + "'s cut", failure);
        }
    }

    /**
     * Screen two of the purpose-first walkthrough: the set, and what can be done
     * to it.
     *
     * <p>This is where the workflow ends for somebody working on walls. Opening
     * a sheet is one thing that can be done here — the one that adds a wall —
     * rather than the road every path leads down.
     */
    private JPanel surfaceSetBody() {
        surfaces.setSetActions(List.of(
                button("Draw this one", this::useSelectedCandidate),
                button("Add one from a sheet…", () -> enterWorkflow(TilesetWorkflow.SHEET)),
                button("Remove from the set", this::removeSelectedCandidate)));
        return surfaces.setScreen();
    }

    /**
     * Re-read the set on the way in, then say what it holds.
     *
     * <p>A screen shows what is true when it is arrived at. Coming back here
     * from the cut screen means an export may have happened in between, and the
     * set is exactly what that changes.
     */
    private void showSurfaceSet() {
        SurfaceCatalog.Purpose was = surfaces.selectedPurpose();
        rescanSurfaces();
        if (was != null) surfaces.select(was.name());
        describeSurfaceSet();
    }

    /** Say what the chosen set holds, so the status line is not stale from the screen before. */
    private void describeSurfaceSet() {
        SurfaceCatalog.Purpose purpose = surfaces.selectedPurpose();
        if (purpose == null) return;
        int count = surfaces.shownCandidateCount();
        context.reportStatus(purpose.name() + " — " + count
                + (count == 1 ? " block could fill it" : " blocks could fill it")
                + (purpose.isUnmapped() ? ", none of them mapped" : ", drawn as " + purpose.mappedId()));
    }

    /**
     * Point the chosen surface at the chosen block and save the mapping.
     *
     * <p>The other half of showing the alternatives. Seeing them is worth little
     * if choosing one means finding the mapping file and retyping an id the
     * listing already knows.
     */
    /**
     * Dissolve the chosen block, so it stops being one of the things that could
     * fill this surface.
     *
     * <p>Its pieces are not deleted. A released member keeps its id, footprint
     * and annotation — it was always a piece of the sheet, and only the
     * membership is withdrawn — so this is undone by grouping them again.
     *
     * <p>Refused while the mapping still points here. A surface whose block no
     * longer exists is a startup crash rather than a wrong-looking map, and the
     * order to do it in is: draw something else first, then remove this.
     */
    private void removeSelectedCandidate() {
        SurfaceCatalog.Purpose purpose = surfaces.selectedPurpose();
        SurfaceCatalog.Candidate candidate = surfaces.selectedCandidate();
        if (purpose == null || candidate == null) return;
        if (candidate.inUse()) {
            AuthoringMessages.info(root, "Remove from the set",
                    candidate.blockId() + " is what " + purpose.name() + " is drawn with, so "
                            + "removing it would leave the surface pointing at nothing. Draw "
                            + "another one first.");
            return;
        }
        if (!candidate.isEditable()) {
            AuthoringMessages.info(root, "Remove from the set",
                    candidate.blockId() + " is on " + candidate.sheetName() + ", which has no "
                            + "authoring document. There is nothing here that declares it, so "
                            + "there is nothing here to withdraw.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(root,
                "Dissolve " + candidate.blockId() + " on " + candidate.sheetName() + "?\n\n"
                        + "Its " + candidate.slots().size() + " pieces go back to being doodads, "
                        + "keeping their ids and annotation. The sheet is saved and re-exported, "
                        + "so its atlas is repacked.",
                "Remove from the set", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;

        try {
            openDocumentAt(candidate.document());
            if (!candidate.document().equals(documentPath)) return;
            TilesetOperations.removeBlock(model.entries, blocks, candidate.blockId());
            model.setEntries(model.entries);
            view.setEntries(model.entries);
            markDirty();
            saveDocument();
            export();
            rescanSurfaces();
            surfaces.select(purpose.name());
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Remove from the set",
                    "Could not dissolve " + candidate.blockId(), failure);
        }
    }

    private void useSelectedCandidate() {
        SurfaceCatalog.Purpose purpose = surfaces.selectedPurpose();
        SurfaceCatalog.Candidate candidate = surfaces.selectedCandidate();
        if (purpose == null || candidate == null) return;
        if (candidate.inUse()) {
            AuthoringMessages.info(root, "Draw this one",
                    candidate.blockId() + " is already what " + purpose.name() + " is drawn with.");
            return;
        }
        try {
            SurfaceMapping.use(context.projectRoot(), purpose.name(), purpose.vocabulary(),
                    candidate.blockId());
            rescanSurfaces();
            surfaces.select(purpose.name());
            context.reportStatus(purpose.name() + " is now drawn with " + candidate.blockId());
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Draw this one",
                    "Could not point " + purpose.name() + " at " + candidate.blockId(), failure);
        }
    }

    /** Start from art: cut it, say what it is, group it, export it. */
    private List<WizardStep> sheetWalkthrough() {
        return List.of(
                pickSheetStep("Pick a sheet",
                        "Raw art nobody has annotated, a sheet part-way through, or one already "
                                + "exported. Each row says which it is."),

                new LambdaStep("Find the pieces",
                        "Slicing keys on alpha. A plate drawn edge to edge has no gaps to find, "
                                + "so it is cut on its stated grid instead.",
                        this::cutBody)
                        .onEnter(() -> cutBody().revalidate())
                        .blockedWhen(() -> model.entries.isEmpty()
                                ? "Slice or split the sheet so it has pieces to annotate" : null),

                adjustCutStep(false),

                new LambdaStep("Say what each piece is",
                        "Pick pieces on the picture and edit the row: an id, how much deck it "
                                + "covers, what it hides you from. None of that is measurable.",
                        this::annotateBody)
                        .onEnter(() -> annotateBody().revalidate()),

                groupingStep("Group pieces into blocks",
                        "A wall or a corner set is a block whose slots the pieces fill. Skip this "
                                + "for a sheet that is only props."),

                new LambdaStep("Name and size the output",
                        "What the exported tileset is called, what its ids are prefixed with, and "
                                + "how many pixels a cell is packed at.",
                        this::outputBody)
                        .blockedWhen(() -> sheetName.getText().isBlank()
                                ? "Give the sheet a name — it names the tileset and its card" : null),

                exportStep());
    }

    /** Look at it. Nothing on these screens writes. */
    private List<WizardStep> lookWalkthrough() {
        return List.of(
                pickSheetStep("Pick a sheet to look at",
                        "Nothing on the next screen writes anything."),
                new LambdaStep("Look at it",
                        "The tileset as the game loads it, at deck scale; and a generated map "
                                + "drawn with it beside the one that ships.",
                        this::lookBody)
                        .onEnter(() -> {
                            lookBody().revalidate();
                            refreshPreview();
                        })
                        .last());
    }

    // ---- screens shared between walkthroughs ---------------------------------

    private WizardStep pickSheetStep(String title, String blurb) {
        return new LambdaStep(title, blurb, () -> library)
                .onEnter(this::rescanLibrary)
                .blockedWhen(() -> library.selected() == null ? "Pick a sheet from the list" : null)
                .onLeave(this::openSelectedFromLibrary)
                .nextLabel("Open it");
    }

    private WizardStep groupingStep(String title, String blurb) {
        return new LambdaStep(title, blurb, this::groupBody)
                .onEnter(() -> groupBody().revalidate());
    }

    private WizardStep exportStep() {
        return new LambdaStep("Save and export",
                "Saving keeps the annotation. Exporting writes the atlas, the tileset the game "
                        + "loads, and the catalog card that says what each id is.",
                this::exportBody)
                .onEnter(() -> exportBody().revalidate())
                .last();
    }

    // ---- screen bodies -------------------------------------------------------
    //
    // The picture of the sheet and the annotation table are wanted by several
    // screens. Swing moves a component when it is added somewhere else, so each
    // body re-parents what it needs on the way in rather than every screen
    // owning a copy of it.

    private JPanel cutScreen;
    private JPanel annotateScreen;
    private JPanel groupScreen;
    private JPanel outputScreen;
    private JPanel exportScreen;
    private JPanel lookScreen;

    private JPanel cutBody() {
        if (cutScreen == null) {
            JPanel actions = actionRow();
            actions.add(new JLabel("alpha ≥ "));
            actions.add(small(alphaMin, 60));
            actions.add(button("Re-slice", this::slice));
            actions.add(new JLabel("   grid "));
            actions.add(small(gridCols, 50));
            actions.add(new JLabel(" x "));
            actions.add(small(gridRows, 50));
            actions.add(button("Split selected on grid", this::splitSelected));
            actions.add(button("Fit grid to art…", this::fitGrid));
            cutScreen = withActions(actions);
        }
        cutScreen.add(sheetPicture, BorderLayout.CENTER);
        return cutScreen;
    }

    private JPanel annotateBody() {
        if (annotateScreen == null) {
            JPanel actions = actionRow();
            actions.add(button("Copy selection for LLM", this::copySelectionForModel));
            annotateScreen = withActions(actions);
        }
        annotateScreen.add(splitOf(sheetPicture, tableScroll), BorderLayout.CENTER);
        return annotateScreen;
    }

    private JPanel groupBody() {
        if (groupScreen == null) {
            JPanel actions = actionRow();
            actions.add(button("Group selected as block…", this::groupSelected));
            groupScreen = withActions(actions);
        }
        groupScreen.add(splitOf(sheetPicture, tableScroll), BorderLayout.CENTER);
        return groupScreen;
    }

    private JPanel outputBody() {
        if (outputScreen == null) {
            JPanel actions = actionRow();
            actions.add(new JLabel(" sheet "));
            actions.add(small(sheetName, 140));
            actions.add(new JLabel("   id prefix "));
            actions.add(small(idPrefix, 160));
            actions.add(new JLabel("   cellPx "));
            actions.add(small(cellPx, 70));
            outputScreen = withActions(actions);
        }
        outputScreen.add(previews, BorderLayout.CENTER);
        return outputScreen;
    }

    private JPanel exportBody() {
        if (exportScreen == null) {
            JPanel actions = actionRow();
            actions.add(button("Save document", this::saveDocument));
            actions.add(button("Export tileset", this::export));
            exportScreen = withActions(actions);
        }
        exportScreen.add(previews, BorderLayout.CENTER);
        return exportScreen;
    }

    private JPanel lookBody() {
        if (lookScreen == null) {
            JPanel actions = actionRow();
            actions.add(new JLabel(" cell on screen "));
            actions.add(small(screenCellPx, 66));
            actions.add(button("Refresh preview", this::refreshPreview));
            lookScreen = withActions(actions);
        }
        lookScreen.add(previews, BorderLayout.CENTER);
        return lookScreen;
    }

    /** A screen: its own controls across the top, its working area beneath. */
    private static JPanel withActions(JPanel actions) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(actions, BorderLayout.NORTH);
        return panel;
    }

    private static JPanel actionRow() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    }

    private static JButton button(String label, Runnable action) {
        return new JButton(new AbstractAction(label) {
            @Override public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private static JSplitPane splitOf(JComponent left, JComponent right) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.55);
        return split;
    }

    private static JComponent small(JComponent field, int width) {
        field.setMaximumSize(new Dimension(width, 26));
        field.setPreferredSize(new Dimension(width, 26));
        return field;
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return dirty;
    }

    private void openSheet() {
        JFileChooser chooser = new JFileChooser(rawSheetDir().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("PNG art sheet", "png"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            loadSheet(file.toPath());
            documentPath = null;
            blocks.clear();
            sheetNote = "";
            outputSheet = "";
            strip = null;
            materials = TilesetExport.Materials.NONE;
            model.setEntries(new ArrayList<>());
            slice();
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Open sheet", "Could not read " + file, failure);
        }
    }

    private void rescanLibrary() {
        List<TilesetLibrary.Sheet> sheets = TilesetLibrary.scan(context.projectRoot());
        library.setSheets(sheets);
        rescanSurfaces();
        context.reportStatus(sheets.size() + " sheets in " + TilesetLibrary.SOURCE_DIR);
    }

    /**
     * Re-derive what can fill each surface.
     *
     * <p>A failure here is reported and dropped rather than raised. The surface
     * listing is a second way to find a sheet, so a mapping that will not parse
     * should cost the operator that convenience and not the editor.
     */
    private void rescanSurfaces() {
        try {
            surfaces.setPurposes(SurfaceCatalog.scan(context.projectRoot()));
        } catch (Exception failure) {
            surfaces.setPurposes(List.of());
            context.reportStatus("Could not read the surface mapping: " + failure.getMessage());
        }
    }

    /**
     * Open the sheet a candidate was cut from with its slots already selected.
     *
     * <p>This is the handoff the surface listing exists for. Landing on the
     * right sheet is only half of it: a wall is nine pieces among a hundred on
     * the plate, and finding them again by eye is the work the listing just
     * did.
     */
    private void openCandidate(SurfaceCatalog.Candidate candidate) {
        if (candidate == null || candidate.document() == null) return;
        openDocumentAt(candidate.document());
        // openDocumentAt reports its own failure and leaves the previous sheet
        // loaded. Selecting rows then would pick pieces out of whatever was
        // already open, which looks like the handoff worked.
        if (!candidate.document().equals(documentPath)) return;
        int[] rows = rowsFor(model.entries,
                candidate.slots().stream().map(SurfaceCatalog.Slot::pieceId).toList());
        selectRows(rows);
        context.reportStatus(candidate.blockId() + " — " + rows.length + " of "
                + candidate.slots().size() + " slots selected on " + candidate.sheetName());
    }

    /**
     * The rows holding {@code pieceIds}, in table order.
     *
     * <p>A slot names a piece that the document said was there when the block
     * was declared. Re-slicing can rename pieces, so a slot may name one that no
     * longer exists; those are dropped rather than reported as selected, which
     * is why the caller says how many of how many it found.
     */
    static int[] rowsFor(List<TilesetExport.Entry> entries, List<String> pieceIds) {
        if (entries == null || pieceIds == null || pieceIds.isEmpty()) return new int[0];
        Set<String> wanted = new HashSet<>(pieceIds);
        List<Integer> rows = new ArrayList<>();
        for (int row = 0; row < entries.size(); row++) {
            if (wanted.contains(entries.get(row).id)) rows.add(row);
        }
        int[] indices = new int[rows.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = rows.get(i);
        return indices;
    }

    /** Select {@code rows} in the entry table and scroll the first into view. */
    private void selectRows(int[] rows) {
        if (table == null) return;
        table.clearSelection();
        for (int row : rows) table.addRowSelectionInterval(row, row);
        if (rows.length > 0) table.scrollRectToVisible(table.getCellRect(rows[0], 0, true));
    }

    /**
     * Open whichever sheet the project list has selected.
     *
     * <p>Three cases, and the operator should not have to know which they are
     * in: an annotated sheet reopens its document, a raw sheet is loaded and
     * sliced, and a sheet that only exists as an exported tileset cannot be
     * opened at all because its raw art is not in the project.
     */
    private void openSelectedFromLibrary() {
        openFromLibrary(library.selected());
    }

    private void openFromLibrary(TilesetLibrary.Sheet sheet) {
        if (sheet == null) return;
        if (sheet.isAnnotated()) {
            openDocumentAt(sheet.document());
            return;
        }
        if (sheet.rawSheet() == null) {
            AuthoringMessages.info(root, "Open", sheet.name() + " ships as a tileset but has no raw art in "
                            + TilesetLibrary.SOURCE_DIR + ", so there is nothing to annotate.");
            return;
        }
        try {
            loadSheet(sheet.rawSheet());
            documentPath = null;
            blocks.clear();
            sheetNote = "";
            outputSheet = "";
            strip = null;
            materials = TilesetExport.Materials.NONE;
            model.setEntries(new ArrayList<>());
            sheetName.setText(sheet.name());
            idPrefix.setText("doodad." + sheet.name());
            slice();
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Open", "Could not read " + sheet.rawSheet(), failure);
        }
    }

    /**
     * Reopen a saved annotation pass exactly as it was left.
     *
     * <p>Deliberately does not re-slice. The document's pieces are the ones its
     * annotations describe, and re-deriving them on open would let the sheet's
     * current threshold, rather than the saved one, decide what those
     * annotations are attached to.
     */
    private void openDocument() {
        JFileChooser chooser = new JFileChooser(documentDir().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Tileset authoring document", "json"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        openDocumentAt(chooser.getSelectedFile().toPath());
    }

    private void openDocumentAt(Path path) {
        try {
            TilesetDocument document = TilesetDocument.read(path);
            loadSheet(resolve(document.sheet));
            documentPath = path;
            idPrefix.setText(document.idPrefix);
            sheetName.setText(document.sheetName);
            cellPx.setValue(document.cellPx);
            alphaMin.setValue(document.alphaMin);
            gridCols.setValue(document.gridCols);
            gridRows.setValue(document.gridRows);
            GridCut stored = document.cut(source.getWidth(), source.getHeight());
            placement = stored.isDivisionOf(source.getWidth(), source.getHeight())
                    ? null : stored;
            blocks.clear();
            blocks.addAll(document.blocks);
            sheetNote = document.note;
            outputSheet = document.outputSheet;
            strip = document.strip;
            materials = TilesetOperations.readMaterials(context.projectRoot(), document);
            model.setEntries(document.entries);
            view.setEntries(document.entries);
            syncGrid();
            dirty = false;
            context.stateChanged();
            if (!sheetNote.isEmpty()) {
                // Whatever the seed knows about this sheet is worth reading
                // before the first slice, not after it goes wrong.
                AuthoringMessages.info(root, document.sheetName, sheetNote);
            }
            if (document.entries.isEmpty()) {
                // A seeded document: it chose the sheet and the slice settings
                // and left the pieces to be found. Finding them is this tool's
                // mechanical half, so do it rather than presenting an empty table.
                slice();
                context.reportStatus("Opened " + path + " and sliced it for the first time");
                return;
            }
            context.reportStatus("Opened " + path);
            report();
            refreshPreview();
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Open document", "Could not open " + path, failure);
        }
    }

    /**
     * The open annotation pass as a document.
     *
     * <p>Saving and exporting are the same state seen two ways, and the headless
     * operations take a document, so the widgets are read into one here rather
     * than by each caller in its own order.
     */
    private TilesetDocument currentDocument() {
        TilesetDocument document = new TilesetDocument();
        document.sheet = relative(sourcePath);
        document.sheetName = sheetNameOrDefault();
        document.idPrefix = idPrefix.getText().trim();
        document.cellPx = (Integer) cellPx.getValue();
        document.alphaMin = (Integer) alphaMin.getValue();
        document.gridCols = (Integer) gridCols.getValue();
        document.gridRows = (Integer) gridRows.getValue();
        GridCut placed = placementForStatedGrid();
        if (placed != null) document.setCut(placed);
        document.outputSheet = outputSheet;
        document.note = sheetNote;
        document.strip = strip;
        document.entries = model.entries;
        document.blocks = new ArrayList<>(blocks);
        return document;
    }

    private void saveDocument() {
        if (source == null) return;
        TilesetDocument document = currentDocument();
        Path path = documentPath != null ? documentPath
                : TilesetDocument.pathFor(context.projectRoot(), document.sheetName);
        try {
            document.write(path);
            documentPath = path;
            dirty = false;
            context.stateChanged();
            rescanLibrary();
            context.reportStatus("Wrote " + path);
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Save document", "Could not save " + path, failure);
        }
    }

    private void loadSheet(Path path) throws Exception {
        BufferedImage read = ImageIO.read(path.toFile());
        if (read == null) throw new IllegalStateException("not an image: " + path);
        source = toArgb(read);
        sourcePath = path;
        view.setSheet(source);
    }

    /** Raw sheets live outside the shipped mod folder; start the chooser where they are. */
    private Path rawSheetDir() {
        Path raw = context.projectRoot().resolve("art-source/tilesets");
        return Files.isDirectory(raw) ? raw : context.projectRoot();
    }

    private Path documentDir() {
        return documentPath != null ? documentPath.getParent() : rawSheetDir();
    }

    private Path resolve(String stored) {
        Path path = Path.of(stored);
        return path.isAbsolute() ? path : context.projectRoot().resolve(path);
    }

    /** Project-relative where possible, so a document survives the project moving. */
    private String relative(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path projectRoot = context.projectRoot();
        if (!absolute.startsWith(projectRoot)) return absolute.toString().replace(BACKSLASH, '/');
        return projectRoot.relativize(absolute).toString().replace(BACKSLASH, '/');
    }

    /** Documents are read on whichever platform wrote them, so their paths use one separator. */
    private static final char BACKSLASH = '\\';

    /** One cell's width in source pixels, from the stated layout and this sheet. */
    private int cellPxX() {
        if (source == null) return 1;
        return Math.max(1, Math.round(source.getWidth() / (float) (Integer) gridCols.getValue()));
    }

    private int cellPxY() {
        if (source == null) return 1;
        return Math.max(1, Math.round(source.getHeight() / (float) (Integer) gridRows.getValue()));
    }

    private String sheetNameOrDefault() {
        String name = sheetName.getText().trim();
        return name.isEmpty() ? "sheet" : name;
    }

    /** A sheet without an alpha channel keys nothing; convert so the threshold means something. */
    private static BufferedImage toArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) return image;
        BufferedImage copy = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    private void slice() {
        if (source == null) return;
        String prefix = idPrefix.getText().trim();
        List<SheetSlicer.Piece> bounds = new ArrayList<>();
        for (TilesetExport.Entry entry : model.entries) bounds.add(entry.piece);

        List<SheetSlicer.Piece> pieces = SheetSlicer.slice(
                source, (Integer) alphaMin.getValue(), SheetSlicer.DEFAULT_MIN_AREA);
        TilesetDocument.Reconciliation reconciled = TilesetDocument.reconcile(
                pieces, model.entries, prefix, cellPxX(), cellPxY());
        List<TilesetOperations.AtRisk> atRisk =
                TilesetOperations.atRisk(reconciled.lost(), prefix);
        if (!atRisk.isEmpty() && !AuthoringMessages.confirm(root, "Re-slice",
                "Re-slicing " + sheetNameOrDefault() + ": "
                        + TilesetOperations.discardWarning(atRisk),
                "Discard and re-slice")) {
            // Reconciling already moved each carried annotation onto its newly
            // found bounds; a declined re-slice leaves the table as it was.
            for (int i = 0; i < bounds.size(); i++) model.entries.get(i).piece = bounds.get(i);
            context.reportStatus("Re-slice declined; nothing changed");
            return;
        }
        model.setEntries(reconciled.entries());
        view.setEntries(reconciled.entries());
        syncGrid();
        markDirty();
        context.reportStatus("Re-sliced: " + reconciled.summary());
        report();
        refreshPreview();
    }

    private void splitSelected() {
        int[] rows = model.selectedRows;
        if (source == null || rows == null || rows.length == 0) {
            AuthoringMessages.info(root, "Split on grid", "Select the fused plates in the table first.");
            return;
        }
        int cols = (Integer) gridCols.getValue();
        int gridDown = (Integer) gridRows.getValue();
        if (cols == 1 && gridDown == 1) {
            AuthoringMessages.info(root, "Split on grid", TilesetOperations.DEGENERATE_GRID_MESSAGE);
            return;
        }
        GridCut placed = placementForStatedGrid();
        List<TilesetExport.Entry> replaced;
        try {
            replaced = placed != null
                    ? TilesetOperations.splitOnGrid(
                            model.entries, model::isSelected, idPrefix.getText().trim(), placed)
                    : TilesetOperations.splitOnGrid(
                            model.entries, model::isSelected, idPrefix.getText().trim(),
                            cols, gridDown);
        } catch (IllegalArgumentException refused) {
            AuthoringMessages.info(root, "Split on grid", refused.getMessage());
            return;
        }
        model.setEntries(replaced);
        view.setEntries(replaced);
        syncGrid();
        markDirty();
        report();
        refreshPreview();
    }

    /**
     * The measured placement, if it is still about the grid the spinners state.
     *
     * <p>An origin and a pitch were fitted for a particular number of cells;
     * restating that number makes them a measurement of something else, so they
     * are dropped rather than reused.
     */
    private GridCut placementForStatedGrid() {
        if (placement == null) return null;
        return placement.cols() == (Integer) gridCols.getValue()
                && placement.rows() == (Integer) gridRows.getValue() ? placement : null;
    }

    /**
     * Measure where the stated grid really sits, show the evidence, and let it be
     * corrected before it is kept.
     *
     * <p>The dialog shows both the numbers and the reason to doubt them, and its
     * fields start on the fitted values rather than replacing the current cut
     * behind the operator. An axis the fit disowns starts on the cut that is
     * already in force, so keeping the dialog unchanged accepts only what
     * measured well — but the fitted numbers are in the message, so overriding
     * that judgement is a matter of typing them in rather than of arguing with
     * the tool.
     */
    private void fitGrid() {
        if (source == null) {
            AuthoringMessages.info(root, "Fit grid", "Open a sheet first.");
            return;
        }
        int cols = (Integer) gridCols.getValue();
        int rows = (Integer) gridRows.getValue();
        if (cols == 1 && rows == 1) {
            AuthoringMessages.info(root, "Fit grid", TilesetOperations.DEGENERATE_GRID_MESSAGE);
            return;
        }
        GridCut stated = current(cols, rows);
        GridFit.Measured measured = GridFit.measure(source, stated);
        GridCut suggested = measured.appliedTo(stated);

        JTextField originX = new JTextField(String.format("%.2f", suggested.originX()), 10);
        JTextField pitchX = new JTextField(String.format("%.4f", suggested.pitchX()), 10);
        JTextField originY = new JTextField(String.format("%.2f", suggested.originY()), 10);
        JTextField pitchY = new JTextField(String.format("%.4f", suggested.pitchY()), 10);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        for (String line : measured.describe().split("\n")) form.add(new JLabel(line));
        form.add(new JLabel(" "));
        form.add(new JLabel("The cell counts are yours; only the placement is measured."));
        form.add(labelled("columns start at", originX));
        form.add(labelled("columns every", pitchX));
        form.add(labelled("rows start at", originY));
        form.add(labelled("rows every", pitchY));
        if (JOptionPane.showConfirmDialog(root, form, "Fit grid to art",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }

        GridCut chosen;
        try {
            chosen = new GridCut(cols, rows,
                    Double.parseDouble(originX.getText().trim()),
                    Double.parseDouble(pitchX.getText().trim()),
                    Double.parseDouble(originY.getText().trim()),
                    Double.parseDouble(pitchY.getText().trim()));
        } catch (IllegalArgumentException bad) {
            AuthoringMessages.error(root, "Fit grid", "That is not a usable cut: "
                    + bad.getMessage());
            return;
        }

        placement = chosen;
        TilesetOperations.Recut recut =
                TilesetOperations.recut(model.entries, idPrefix.getText().trim(), chosen);
        model.fireTableDataChanged();
        view.setEntries(model.entries);
        syncGrid();
        markDirty();
        context.reportStatus(chosen.describe() + " — " + recut.summary());
        report();
        refreshPreview();
    }

    /** The cut in force: what has been measured, or the canvas divided by the stated grid. */
    private GridCut current(int cols, int rows) {
        GridCut divided = GridCut.dividing(source.getWidth(), source.getHeight(), cols, rows);
        GridCut placed = placementForStatedGrid();
        return placed == null ? divided : placed;
    }

    /**
     * Turn the selected pieces into the cells of one named block.
     *
     * <p>The selection fills the layout's slots in slice reading order, which is
     * the order {@link SheetSlicer#splitOnGrid} produces and the order a plate is
     * drawn in, so the common case — split one fused 3x3 plate, group its nine
     * pieces — needs no further correction. Where a sheet disagrees, the slot
     * column is editable per row.
     */
    private void groupSelected() {
        List<TilesetExport.Entry> selected = selectedEntries();
        if (selected.isEmpty()) {
            AuthoringMessages.info(root, "Group as block", "Select the block's pieces in the table first.");
            return;
        }
        JTextField id = new JTextField(defaultBlockId(), 18);
        JComboBox<GridLayout> layout = new JComboBox<>(GridLayout.values());
        layout.setSelectedItem(GridLayout.WALL_3X3);
        JTextField fill = new JTextField("", 10);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(labelled("block id", id));
        form.add(labelled("layout", layout));
        form.add(labelled("fill 0xRRGGBB (optional)", fill));
        form.add(new JLabel("slots read as \"exterior on this side\""));
        if (JOptionPane.showConfirmDialog(root, form, "Group as block",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }

        String blockId = id.getText().trim();
        if (blockId.isEmpty()) return;
        GridLayout chosen = (GridLayout) layout.getSelectedItem();
        Integer fillRgb;
        try {
            String text = fill.getText().trim();
            fillRgb = text.isEmpty() ? null : Integer.decode(text);
        } catch (NumberFormatException bad) {
            AuthoringMessages.error(root, "Group as block", "Fill must look like 0x060A10.");
            return;
        }

        List<String> slots = BlockSlots.of(chosen);
        if (selected.size() > slots.size()) {
            AuthoringMessages.error(root, "Group as block", selected.size() + " pieces selected but " + jsonName(chosen)
                            + " has only " + slots.size() + " slots.");
            return;
        }
        Map<String, TilesetExport.Entry> bySlot = new LinkedHashMap<>();
        for (int i = 0; i < selected.size(); i++) bySlot.put(slots.get(i), selected.get(i));
        TilesetOperations.setBlock(model.entries, blocks, blockId, chosen, fillRgb, bySlot);
        pruneEmptyBlocks();
        model.fireTableDataChanged();
        markDirty();
        context.reportStatus(blockId + ": " + selected.size() + " of " + slots.size()
                + " slots filled" + (selected.size() < slots.size()
                ? " — the rest fall to the block's fill" : ""));
        report();
        refreshPreview();
    }

    private static JPanel labelled(String text, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.add(new JLabel(text));
        row.add(field);
        return row;
    }

    /** Tell the canvas the stated cut, which is what makes a cell's coordinate mean something. */
    private void syncGrid() {
        view.setGrid((Integer) gridCols.getValue(), (Integer) gridRows.getValue());
    }

    /**
     * Put the selection somewhere a model can look at it.
     *
     * <p>Two artifacts because the reader needs two things and neither
     * substitutes for the other: an image, because the question is about art and
     * nothing else conveys it, and a table under the same coordinates, because
     * the reader's first duty is not to trample annotation that is already
     * there. The image goes to a file and the text to the clipboard with that
     * file's path in it, so one paste carries both — the path is readable
     * directly by a session on this machine, and the file is there to attach
     * for one that is not.
     */
    private void copySelectionForModel() {
        if (source == null || model.entries.isEmpty()) {
            AuthoringMessages.info(root, "Copy selection", "Open a sheet first.");
            return;
        }
        List<TilesetSelectionReport.Cell> cells = TilesetSelectionReport.cells(
                model.entries, model.selectedRows,
                (Integer) gridCols.getValue(), (Integer) gridRows.getValue());
        if (cells.isEmpty()) {
            AuthoringMessages.info(root, "Copy selection",
                    "Nothing is selected. Click a cell on the sheet, or drag a box across "
                            + "several; ctrl-click adds one and shift-click extends the run.");
            return;
        }
        String name = sheetNameOrDefault();
        try {
            Path directory = context.projectRoot().resolve("build").resolve("tileset-authoring");
            Files.createDirectories(directory);
            Path image = directory.resolve(name + "-selection.png");
            ImageIO.write(TilesetSelectionReport.contactSheet(source,
                    name + " — " + cells.size() + " selected", cells), "png", image.toFile());

            String text = TilesetSelectionReport.markdown(name, sheetNote,
                    model.entries.size(), cells, image.toString());
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            context.reportStatus(cells.size() + " cells copied; image at " + image);
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Copy selection failed",
                    "Could not write the selection image or reach the clipboard.", failure);
        }
    }

    private List<TilesetExport.Entry> selectedEntries() {
        List<TilesetExport.Entry> selected = new ArrayList<>();
        for (TilesetExport.Entry entry : model.entries) {
            if (model.isSelected(entry)) selected.add(entry);
        }
        return selected;
    }

    private String defaultBlockId() {
        String prefix = idPrefix.getText().trim();
        int dot = prefix.indexOf('.');
        return (dot < 0 ? prefix : prefix.substring(dot + 1)) + ".wall";
    }

    private static String jsonName(GridLayout layout) {
        return TilesetExport.jsonLayout(layout);
    }

    /** A block nobody is a member of any more is not a block. */
    private void pruneEmptyBlocks() {
        blocks.removeIf(spec -> model.entries.stream()
                .noneMatch(entry -> spec.id.equals(entry.blockId)));
    }

    TilesetExport.BlockSpec specFor(String blockId) {
        for (TilesetExport.BlockSpec spec : blocks) {
            if (spec.id.equals(blockId)) return spec;
        }
        return null;
    }

    /**
     * Say in words what the highlighted slot means, because the one mistake this
     * tool cannot catch is assigning the pieces to the mirrored slots.
     */
    private void describeSelectedSlot() {
        if (model.selectedRows.length != 1) return;
        TilesetExport.Entry entry = model.entries.get(model.selectedRows[0]);
        if (!entry.isBlockMember()) return;
        context.reportStatus(entry.blockId + " / " + entry.slot + " — "
                + BlockSlots.describe(entry.slot));
    }

    /**
     * Pack, load and draw the tileset as it currently stands, without writing
     * anything. Checking art should not require exporting it and starting the
     * game — and ingesting the document here means one that would not load
     * fails in front of the person editing it rather than at startup.
     */
    private void refreshPreview() {
        if (source == null || model.entries.isEmpty()) {
            preview.setIcon(null);
            preview.setText("open a sheet");
            return;
        }
        try {
            int cell = (Integer) cellPx.getValue();
            BufferedImage atlas =
                    TilesetExport.atlas(source, model.entries, blocks, cell, materials);
            BufferedImage image = TilesetPreview.render(atlas,
                    TilesetExport.tileset(
                            "graphics/doodads/preview.png", cell, model.entries, blocks),
                    cell, (Integer) screenCellPx.getValue());
            preview.setIcon(new ImageIcon(image));
            preview.setText(null);
        } catch (Exception failure) {
            preview.setIcon(null);
            preview.setText("preview failed: " + failure.getMessage());
        }
    }

    private void export() {
        if (source == null || model.entries.isEmpty()) return;
        try {
            // Delegated so the window and the headless tools cannot drift into
            // two export paths. A tileset written to a destination the other
            // would not have chosen is a startup crash, not a visible difference.
            TilesetOperations.ExportResult exported =
                    TilesetOperations.export(context.projectRoot(), currentDocument(), source);
            // The atlas on disk is a different file now, and the catalog about to
            // be re-read describes the new packing. A picture held from the old
            // one would be drawn at the new coordinates.
            blockPreviews.forget();
            rescanLibrary();
            context.reportStatus("Wrote " + exported.atlasPath() + ", "
                    + exported.tilesetPath() + " and " + exported.cardPath());
            report();
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Export tileset", "Export failed.", failure);
        }
    }

    /** The bound candidates; see {@link TilesetOperations#bindings}. */
    private List<TilesetMapPreview.Substitution> bindings() {
        return TilesetOperations.bindings(model.entries, blocks);
    }

    private void markDirty() {
        dirty = true;
        context.stateChanged();
    }

    private void report() {
        refreshStep();
        int included = 0;
        int cells = 0;
        for (TilesetExport.Entry entry : model.entries) {
            if (!entry.included) continue;
            included++;
            cells += entry.isBlockMember() ? 1 : entry.footprintX * entry.footprintY;
        }
        if (strip != null) {
            // A strip has no cells to count and no blocks to count them into.
            TilesetExport.StripPacking packed =
                    TilesetExport.packStrip(model.entries, strip, materials);
            summary.setText(String.format(
                    "%s   %d pieces, %d frames   strip %dx%d px at 1/%.4g",
                    sourcePath == null ? "no sheet" : sourcePath.getFileName().toString(),
                    model.entries.size(), packed.frames().size(),
                    packed.width(), packed.height(), strip.scale()));
            return;
        }
        TilesetExport.Packing packing = TilesetExport.pack(model.entries, blocks);
        summary.setText(String.format(
                "%s   %d pieces, %d included, %d blocks, %d cells   atlas %dx%d cells (%dx%d px)",
                sourcePath == null ? "no sheet" : sourcePath.getFileName().toString(),
                model.entries.size(), included, blocks.size(), cells,
                packing.columns(), packing.rows(),
                packing.columns() * (Integer) cellPx.getValue(),
                packing.rows() * (Integer) cellPx.getValue()));
    }

    /** The sheet with every detected piece outlined, so the slicing can be checked by eye. */
    /**
     * The sheet, with the cut drawn on it and selectable.
     *
     * <p>This is the surface the work actually happens on. The table beside it
     * lists the same pieces, but a row there cannot be recognised — the id of a
     * cut cell is a placeholder, and 100 of them in a scroll pane say nothing
     * about which is the crate you were looking at. So selection starts here,
     * on the picture, and the table follows.
     *
     * <p>Each cell is labelled with its {@code col,row}, which is the whole
     * point of the label: it gives a person and a model the same name for the
     * same cell. Without it the only way to point at a piece is prose about
     * where it sits, and prose about position is exactly what stops being true
     * the moment either party miscounts.
     */
    private static final class SheetView extends JComponent {

        private BufferedImage sheet;
        private List<TilesetExport.Entry> entries = List.of();
        private final SheetSelection selection = new SheetSelection();
        private Runnable onSelectionChanged = () -> {};
        private int gridCols;
        private int gridRows;
        private Point pressedAt;
        private Rectangle band;

        SheetView() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    pressedAt = e.getPoint();
                    band = null;
                    requestFocusInWindow();
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (pressedAt == null) return;
                    band = new Rectangle(pressedAt);
                    band.add(e.getPoint());
                    repaint();
                }

                @Override public void mouseReleased(MouseEvent e) {
                    if (pressedAt == null) return;
                    // A drag of a couple of pixels is a click with a shaky hand,
                    // not a band select over one cell.
                    boolean dragged = band != null
                            && (band.width > DRAG_SLOP || band.height > DRAG_SLOP);
                    if (dragged) {
                        selectWithin(band, e);
                    } else {
                        clickAt(e);
                    }
                    pressedAt = null;
                    band = null;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            setFocusable(true);
        }

        void setSheet(BufferedImage sheet) {
            this.sheet = sheet;
            setPreferredSize(new Dimension(sheet.getWidth(), sheet.getHeight()));
            revalidate();
            repaint();
        }

        void setEntries(List<TilesetExport.Entry> entries) {
            this.entries = entries;
            selection.clear();
            repaint();
        }

        /** The stated cut, which is what makes a cell's coordinate meaningful. */
        void setGrid(int cols, int rows) {
            this.gridCols = cols;
            this.gridRows = rows;
            repaint();
        }

        /** Set from outside — the table — without calling back and starting a loop. */
        void showSelection(int[] rows) {
            selection.set(rows);
            repaint();
        }

        int[] selection() {
            return selection.toArray();
        }

        private void clickAt(MouseEvent e) {
            selection.click(entryAt(e.getX(), e.getY()), isToggle(e), e.isShiftDown());
            onSelectionChanged.run();
        }

        private void selectWithin(Rectangle area, MouseEvent e) {
            selection.band(entries, area, isAdditive(e));
            onSelectionChanged.run();
        }

        private static boolean isToggle(MouseEvent e) {
            return e.isControlDown() || e.isMetaDown();
        }

        private static boolean isAdditive(MouseEvent e) {
            return isToggle(e) || e.isShiftDown();
        }

        /** Topmost piece under the point, or -1. Later entries win, matching what is drawn. */
        private int entryAt(int x, int y) {
            for (int i = entries.size() - 1; i >= 0; i--) {
                SheetSlicer.Piece p = entries.get(i).piece;
                if (x >= p.x() && y >= p.y()
                        && x < p.x() + p.width() && y < p.y() + p.height()) {
                    return i;
                }
            }
            return -1;
        }

        /** How this cell is named out loud: its grid coordinate, or failing that its index. */
        private String labelFor(int index) {
            boolean isGrid = gridCols > 0 && gridRows > 0
                    && gridCols * gridRows == entries.size();
            return isGrid ? (index % gridCols) + "," + (index / gridCols) : "#" + index;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x10, 0x14, 0x1a));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (sheet != null) g.drawImage(sheet, 0, 0, null);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            for (int i = 0; i < entries.size(); i++) {
                TilesetExport.Entry entry = entries.get(i);
                SheetSlicer.Piece p = entry.piece;
                boolean picked = selection.contains(i);
                if (picked) {
                    g.setColor(SELECTED_WASH);
                    g.fillRect(p.x(), p.y(), p.width(), p.height());
                }
                g.setColor(!entry.included ? new Color(0x55, 0x5a, 0x62)
                        : picked ? new Color(0x6b, 0xe0, 0xff)
                        : new Color(0xff, 0x5c, 0x5c));
                g.drawRect(p.x() - 1, p.y() - 1, p.width() + 1, p.height() + 1);
                if (picked) g.drawRect(p.x() - 2, p.y() - 2, p.width() + 3, p.height() + 3);
                // A label larger than the cell it names is worse than no label.
                if (p.width() >= LABEL_MIN_PX && p.height() >= LABEL_MIN_PX) {
                    String label = labelFor(i);
                    int width = g.getFontMetrics().stringWidth(label) + 6;
                    g.setColor(LABEL_BACKDROP);
                    g.fillRect(p.x() + 1, p.y() + 1, width, 14);
                    g.setColor(picked ? new Color(0x9f, 0xef, 0xff) : new Color(0xff, 0xe6, 0x78));
                    g.drawString(label, p.x() + 4, p.y() + 12);
                }
            }
            if (band != null) {
                g.setColor(new Color(0x6b, 0xe0, 0xff));
                g.drawRect(band.x, band.y, band.width, band.height);
            }
            g.dispose();
        }
    }

    /** Pixels of movement below which a press-and-release is a click. */
    private static final int DRAG_SLOP = 4;
    /** Cell edge below which a coordinate label would cover the art it names. */
    private static final int LABEL_MIN_PX = 26;
    private static final Color SELECTED_WASH = new Color(0x6b, 0xe0, 0xff, 48);
    private static final Color LABEL_BACKDROP = new Color(0x00, 0x00, 0x00, 170);

    /** Editable view of the sliced pieces: role, id, footprint, cover, and whether it ships. */
    private final class EntryTableModel extends AbstractTableModel {

        private final String[] columns = { "#", "id", "block", "slot", "cells X", "cells Y",
                "cover", "half height", "wall side", "tags", "note", "stands in for",
                "px", "in" };
        private List<TilesetExport.Entry> entries = new ArrayList<>();
        private int[] selectedRows = new int[0];

        void setEntries(List<TilesetExport.Entry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        boolean isSelected(TilesetExport.Entry entry) {
            int index = entries.indexOf(entry);
            for (int row : selectedRows) {
                if (row == index) return true;
            }
            return false;
        }

        @Override public int getRowCount() { return entries.size(); }

        @Override public int getColumnCount() { return columns.length; }

        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0, 4, 5 -> Integer.class;
                case 13 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column != 0 && column != 12;
        }

        @Override
        public Object getValueAt(int row, int column) {
            TilesetExport.Entry e = entries.get(row);
            return switch (column) {
                case 0 -> row;
                case 1 -> e.isBlockMember() ? "" : e.id;
                case 2 -> e.blockId;
                case 3 -> e.slot;
                case 4 -> e.isBlockMember() ? 1 : e.footprintX;
                case 5 -> e.isBlockMember() ? 1 : e.footprintY;
                case 6 -> e.isBlockMember() ? "" : e.cover;
                case 7 -> e.isBlockMember() || e.ballisticHalfHeight == null
                        ? "" : String.valueOf(e.ballisticHalfHeight);
                case 8 -> e.isBlockMember() ? "" : e.preferredWallSide;
                case 9 -> String.join(", ", e.tags);
                case 10 -> e.note;
                case 11 -> e.standsInFor;
                case 12 -> e.piece.width() + "x" + e.piece.height();
                default -> e.included;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            TilesetExport.Entry e = entries.get(row);
            switch (column) {
                case 1 -> e.id = String.valueOf(value).trim();
                case 2 -> setBlock(e, String.valueOf(value).trim());
                case 3 -> setSlot(e, String.valueOf(value).trim().toLowerCase());
                case 4 -> e.footprintX = Math.max(1, ((Number) value).intValue());
                case 5 -> e.footprintY = Math.max(1, ((Number) value).intValue());
                case 6 -> e.cover = String.valueOf(value).trim().toLowerCase();
                case 7 -> setBallisticHalfHeight(e, String.valueOf(value).trim());
                case 8 -> setPreferredWallSide(e, String.valueOf(value).trim());
                case 9 -> e.tags = parseTags(String.valueOf(value));
                case 10 -> e.note = String.valueOf(value).trim();
                case 11 -> setStandsInFor(e, String.valueOf(value).trim());
                case 13 -> e.included = Boolean.TRUE.equals(value);
                default -> { }
            }
            fireTableRowsUpdated(row, row);
            markDirty();
            report();
            refreshPreview();
        }

        /**
         * How high the piece stops a shot. Blank clears it back to the height
         * the cover level implies, which is a different statement from writing
         * that height down: one says nobody has judged it, the other says
         * somebody did and this is the answer.
         */
        private void setBallisticHalfHeight(TilesetExport.Entry e, String text) {
            if (text.isEmpty()) {
                e.ballisticHalfHeight = null;
                return;
            }
            try {
                double height = Double.parseDouble(text);
                if (!Double.isFinite(height) || height < 0) throw new NumberFormatException(text);
                e.ballisticHalfHeight = height;
            } catch (NumberFormatException notANumber) {
                AuthoringMessages.error(root, "Half height",
                        "'" + text + "' is not a height. Give cells above the deck as a "
                                + "positive number, or leave it blank to take the cover "
                                + "level's default.");
            }
        }

        /** The edge that backs onto a wall, or blank for a piece with no such edge. */
        private void setPreferredWallSide(TilesetExport.Entry e, String text) {
            if (text.isEmpty()) {
                e.preferredWallSide = "";
                return;
            }
            try {
                e.preferredWallSide = WallSide.fromJson(text).name();
            } catch (IllegalArgumentException unknown) {
                AuthoringMessages.error(root, "Wall side",
                        "'" + text + "' is not a side. Use N, S, E or W, or leave it blank.");
            }
        }

        /** Clearing the block column turns the piece back into a doodad. */
        private void setBlock(TilesetExport.Entry e, String blockId) {
            if (blockId.isEmpty()) {
                e.blockId = "";
                e.slot = "";
                pruneEmptyBlocks();
                return;
            }
            if (specFor(blockId) == null) {
                AuthoringMessages.error(root, "Block", "No block named '" + blockId + "'. Use \"Group selected as block\" "
                                + "to declare one with its layout.");
                return;
            }
            e.blockId = blockId;
            if (e.slot.isEmpty()) e.slot = BlockSlots.of(specFor(blockId).layout).get(0);
            pruneEmptyBlocks();
        }

        /** Comma-separated, lowercase, de-duplicated, order preserved. */
        private List<String> parseTags(String text) {
            List<String> tags = new ArrayList<>();
            for (String part : text.split(",")) {
                String tag = part.trim().toLowerCase();
                if (!tag.isEmpty() && !tags.contains(tag)) tags.add(tag);
            }
            return tags;
        }

        /**
         * Bind this piece as a candidate for a shipped id.
         *
         * <p>Checked against the installed catalog when there is one, because a
         * typo here fails silently later: the map renders, and simply does not
         * contain the thing you were trying to look at.
         */
        private void setStandsInFor(TilesetExport.Entry e, String shippedId) {
            if (!shippedId.isEmpty()) {
                TileRegistry registry = TileRegistry.installed();
                if (registry != null && registry.doodad(shippedId) == null
                        && registry.block(shippedId) == null) {
                    AuthoringMessages.error(root, "Stands in for",
                            "'" + shippedId + "' is not a doodad or block in the shipped "
                                    + "catalog, so a map preview would silently leave it "
                                    + "alone. Use an id from mod/data/tilesets, for example "
                                    + "urban.wall or doodad.crate.");
                    return;
                }
            }
            e.standsInFor = shippedId;
        }

        private void setSlot(TilesetExport.Entry e, String slot) {
            TilesetExport.BlockSpec spec = specFor(e.blockId);
            if (spec == null) return;
            if (!BlockSlots.fits(spec.layout, slot)) {
                AuthoringMessages.error(root, "Slot", "'" + slot + "' is not a slot of " + jsonName(spec.layout) + ". Use one of "
                                + String.join(", ", BlockSlots.of(spec.layout)) + ".");
                return;
            }
            e.slot = slot;
            context.reportStatus(e.blockId + " / " + slot + " — " + BlockSlots.describe(slot));
        }
    }
}
