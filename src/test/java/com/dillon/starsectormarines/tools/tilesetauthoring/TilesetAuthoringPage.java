package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private final JSpinner gridCell = new JSpinner(new SpinnerNumberModel(104, 8, 512, 1));
    private final JSpinner screenCellPx = new JSpinner(new SpinnerNumberModel(40, 8, 160, 4));
    private final JLabel preview = new JLabel("", JLabel.CENTER);
    private final JLabel summary = new JLabel(" ");

    private final List<TilesetExport.BlockSpec> blocks = new ArrayList<>();
    private final TilesetLibraryView library = new TilesetLibraryView(this::openFromLibrary);

    private BufferedImage source;
    private Path sourcePath;
    private Path documentPath;
    private String sheetNote = "";
    private boolean dirty;

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
        bar.add(small(gridCell, 70));
        bar.add(new AbstractAction("Split selected on grid") {
            @Override public void actionPerformed(ActionEvent e) {
                splitSelected();
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
            view.highlight = table.getSelectedRow();
            view.repaint();
            describeSelectedSlot();
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(520, 260));
        preview.setVerticalAlignment(JLabel.TOP);
        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setPreferredSize(new Dimension(520, 320));
        previewScroll.setBorder(BorderFactory.createTitledBorder(
                "Compartment preview — the tileset as loaded, at deck scale"));

        JSplitPane rightSide = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tableScroll, previewScroll);
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
            gridCell.setValue(document.gridCell);
            blocks.clear();
            blocks.addAll(document.blocks);
            sheetNote = document.note;
            model.setEntries(document.entries);
            view.setEntries(document.entries);
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

    private void saveDocument() {
        if (source == null) return;
        TilesetDocument document = new TilesetDocument();
        document.sheet = relative(sourcePath);
        document.sheetName = sheetNameOrDefault();
        document.idPrefix = idPrefix.getText().trim();
        document.cellPx = (Integer) cellPx.getValue();
        document.alphaMin = (Integer) alphaMin.getValue();
        document.gridCell = (Integer) gridCell.getValue();
        document.note = sheetNote;
        document.entries = model.entries;
        document.blocks = new ArrayList<>(blocks);
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
        List<SheetSlicer.Piece> pieces = SheetSlicer.slice(
                source, (Integer) alphaMin.getValue(), SheetSlicer.DEFAULT_MIN_AREA);
        TilesetDocument.Reconciliation reconciled = TilesetDocument.reconcile(
                pieces, model.entries, idPrefix.getText().trim(), (Integer) gridCell.getValue());
        model.setEntries(reconciled.entries());
        view.setEntries(reconciled.entries());
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
        int cell = (Integer) gridCell.getValue();
        List<TilesetExport.Entry> replaced = new ArrayList<>();
        for (TilesetExport.Entry entry : model.entries) {
            if (!model.isSelected(entry)) {
                replaced.add(entry);
                continue;
            }
            int part = 0;
            for (SheetSlicer.Piece piece : SheetSlicer.splitOnGrid(entry.piece, cell)) {
                TilesetExport.Entry split = new TilesetExport.Entry(
                        piece, entry.id + "-" + (char) ('a' + part++));
                split.cover = entry.cover;
                split.footprintX = TilesetDocument.guessFootprint(piece.width(), cell);
                split.footprintY = TilesetDocument.guessFootprint(piece.height(), cell);
                replaced.add(split);
            }
        }
        model.setEntries(replaced);
        view.setEntries(replaced);
        markDirty();
        report();
        refreshPreview();
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
        for (int i = 0; i < selected.size(); i++) {
            TilesetExport.Entry entry = selected.get(i);
            entry.blockId = blockId;
            entry.slot = slots.get(i);
            entry.included = true;
        }
        blocks.removeIf(spec -> spec.id.equals(blockId));
        blocks.add(new TilesetExport.BlockSpec(blockId, chosen, fillRgb));
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
        int cell = (Integer) cellPx.getValue();
        String name = sheetNameOrDefault();
        String sheetRelative = "graphics/doodads/" + name + ".png";
        Path atlasPath = context.projectRoot().resolve("mod").resolve(sheetRelative);
        Path tilesetPath = context.projectRoot()
                .resolve("mod/data/tilesets").resolve(name + ".tileset.json");
        Path cardPath = tilesetPath.resolveSibling(name + ".tileset.md");
        try {
            BufferedImage atlas = TilesetExport.atlas(source, model.entries, blocks, cell);
            TilesetExport.write(atlas,
                    TilesetExport.tileset(sheetRelative, cell, model.entries, blocks),
                    atlasPath, tilesetPath);
            Files.writeString(cardPath, TilesetCatalogCard.render(
                    name, sheetRelative, cell, model.entries, blocks));
            rescanLibrary();
            context.reportStatus("Wrote " + atlasPath + ", " + tilesetPath + " and " + cardPath);
            report();
        } catch (Exception failure) {
            AuthoringMessages.error(root, "Export tileset", "Export failed.", failure);
        }
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
    private static final class SheetView extends JComponent {

        private BufferedImage sheet;
        private List<TilesetExport.Entry> entries = List.of();
        private int highlight = -1;

        void setSheet(BufferedImage sheet) {
            this.sheet = sheet;
            setPreferredSize(new Dimension(sheet.getWidth(), sheet.getHeight()));
            revalidate();
            repaint();
        }

        void setEntries(List<TilesetExport.Entry> entries) {
            this.entries = entries;
            this.highlight = -1;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x10, 0x14, 0x1a));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (sheet != null) g.drawImage(sheet, 0, 0, null);
            for (int i = 0; i < entries.size(); i++) {
                TilesetExport.Entry entry = entries.get(i);
                SheetSlicer.Piece p = entry.piece;
                g.setColor(!entry.included ? new Color(0x55, 0x5a, 0x62)
                        : i == highlight ? new Color(0x6b, 0xe0, 0xff)
                        : new Color(0xff, 0x5c, 0x5c));
                g.drawRect(p.x() - 1, p.y() - 1, p.width() + 1, p.height() + 1);
                if (i == highlight) {
                    g.drawRect(p.x() - 2, p.y() - 2, p.width() + 3, p.height() + 3);
                }
            }
            g.dispose();
        }
    }

    /** Editable view of the sliced pieces: role, id, footprint, cover, and whether it ships. */
    private final class EntryTableModel extends AbstractTableModel {

        private final String[] columns = { "#", "id", "block", "slot", "cells X", "cells Y",
                "cover", "tags", "note", "px", "in" };
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
                case 10 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column != 0 && column != 9;
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
                case 7 -> String.join(", ", e.tags);
                case 8 -> e.note;
                case 9 -> e.piece.width() + "x" + e.piece.height();
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
                case 7 -> e.tags = parseTags(String.valueOf(value));
                case 8 -> e.note = String.valueOf(value).trim();
                case 10 -> e.included = Boolean.TRUE.equals(value);
                default -> { }
            }
            fireTableRowsUpdated(row, row);
            markDirty();
            report();
            refreshPreview();
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
