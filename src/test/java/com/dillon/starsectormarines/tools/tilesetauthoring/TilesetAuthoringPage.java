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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private boolean dirty;
    /** True while the table is being set from the canvas, so it does not answer back. */
    private boolean syncingSelection;

    public TilesetAuthoringPage(AuthoringPageContext context) {
        this.context = context;

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new AbstractAction("Open selected") {
            @Override public void actionPerformed(ActionEvent e) {
                openSelectedFromLibrary();
            }
        });
        bar.add(new AbstractAction("Rescan") {
            @Override public void actionPerformed(ActionEvent e) {
                rescanLibrary();
            }
        });
        bar.addSeparator();
        bar.add(new AbstractAction("Browse…") {
            @Override public void actionPerformed(ActionEvent e) {
                openSheet();
            }
        });
        bar.add(new AbstractAction("Save document") {
            @Override public void actionPerformed(ActionEvent e) {
                saveDocument();
            }
        });
        bar.add(new AbstractAction("Re-slice") {
            @Override public void actionPerformed(ActionEvent e) {
                slice();
            }
        });
        bar.addSeparator();
        bar.add(new JLabel(" alpha ≥ "));
        bar.add(small(alphaMin, 60));
        bar.add(new JLabel("  grid "));
        bar.add(small(gridCols, 50));
        bar.add(new JLabel(" x "));
        bar.add(small(gridRows, 50));
        bar.add(new AbstractAction("Split selected on grid") {
            @Override public void actionPerformed(ActionEvent e) {
                splitSelected();
            }
        });
        bar.add(new AbstractAction("Fit grid to art…") {
            @Override public void actionPerformed(ActionEvent e) {
                fitGrid();
            }
        });
        bar.add(new AbstractAction("Group selected as block…") {
            @Override public void actionPerformed(ActionEvent e) {
                groupSelected();
            }
        });
        bar.addSeparator();
        bar.add(new JLabel(" id prefix "));
        bar.add(small(idPrefix, 140));
        bar.add(new JLabel("  sheet "));
        bar.add(small(sheetName, 110));
        bar.add(new JLabel("  cellPx "));
        bar.add(small(cellPx, 70));
        bar.addSeparator();
        bar.add(new JLabel(" cell on screen "));
        bar.add(small(screenCellPx, 66));
        bar.add(new AbstractAction("Refresh preview") {
            @Override public void actionPerformed(ActionEvent e) {
                refreshPreview();
            }
        });
        bar.addSeparator();
        bar.add(new AbstractAction("Copy selection for LLM") {
            @Override public void actionPerformed(ActionEvent e) {
                copySelectionForModel();
            }
        });
        JButton export = new JButton(new AbstractAction("Export tileset") {
            @Override public void actionPerformed(ActionEvent e) {
                export();
            }
        });
        bar.add(export);

        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getSelectionModel().addListSelectionListener(e -> {
            model.selectedRows = table.getSelectedRows();
            if (!syncingSelection) view.showSelection(model.selectedRows);
            describeSelectedSlot();
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
                        (Integer) cellPx.getValue());
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

        JSplitPane rightSide = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tableScroll, previews);
        rightSide.setResizeWeight(0.45);
        JSplitPane sheetSide = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                library, new JScrollPane(view));
        sheetSide.setResizeWeight(0.0);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sheetSide, rightSide);
        split.setResizeWeight(0.5);

        summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        rescanLibrary();
        root.add(bar, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(summary, BorderLayout.SOUTH);
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
            model.setEntries(new ArrayList<>());
            slice();
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Open sheet", "Could not read " + file, failure);
        }
    }

    private void rescanLibrary() {
        List<TilesetLibrary.Sheet> sheets = TilesetLibrary.scan(context.projectRoot());
        library.setSheets(sheets);
        context.reportStatus(sheets.size() + " sheets in " + TilesetLibrary.SOURCE_DIR);
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
            BufferedImage atlas = TilesetExport.atlas(source, model.entries, blocks, cell);
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
        int included = 0;
        int cells = 0;
        for (TilesetExport.Entry entry : model.entries) {
            if (!entry.included) continue;
            included++;
            cells += entry.isBlockMember() ? 1 : entry.footprintX * entry.footprintY;
        }
        if (strip != null) {
            // A strip has no cells to count and no blocks to count them into.
            TilesetExport.StripPacking packed = TilesetExport.packStrip(model.entries, strip);
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
