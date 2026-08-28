package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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

    private BufferedImage source;
    private Path sourcePath;
    private Path documentPath;
    private boolean dirty;

    public TilesetAuthoringPage(AuthoringPageContext context) {
        this.context = context;

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new AbstractAction("Open sheet…") {
            @Override public void actionPerformed(ActionEvent e) {
                openSheet();
            }
        });
        bar.add(new AbstractAction("Open document…") {
            @Override public void actionPerformed(ActionEvent e) {
                openDocument();
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
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(view), rightSide);
        split.setResizeWeight(0.5);

        summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
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
            model.setEntries(new ArrayList<>());
            slice();
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(root, "Could not read " + file + ":\n" + failure,
                    "Open sheet", JOptionPane.ERROR_MESSAGE);
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
        Path path = chooser.getSelectedFile().toPath();
        try {
            TilesetDocument document = TilesetDocument.read(path);
            loadSheet(resolve(document.sheet));
            documentPath = path;
            idPrefix.setText(document.idPrefix);
            sheetName.setText(document.sheetName);
            cellPx.setValue(document.cellPx);
            alphaMin.setValue(document.alphaMin);
            gridCell.setValue(document.gridCell);
            model.setEntries(document.entries);
            view.setEntries(document.entries);
            dirty = false;
            context.stateChanged();
            context.reportStatus("Opened " + path);
            report();
            refreshPreview();
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(root, "Could not open " + path + ":\n" + failure,
                    "Open document", JOptionPane.ERROR_MESSAGE);
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
        document.entries = model.entries;
        Path path = documentPath != null ? documentPath
                : TilesetDocument.pathFor(context.projectRoot(), document.sheetName);
        try {
            document.write(path);
            documentPath = path;
            dirty = false;
            context.stateChanged();
            context.reportStatus("Wrote " + path);
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(root, "Could not save " + path + ":\n" + failure,
                    "Save document", JOptionPane.ERROR_MESSAGE);
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
            JOptionPane.showMessageDialog(root,
                    "Select the fused plates in the table first.",
                    "Split on grid", JOptionPane.INFORMATION_MESSAGE);
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
            BufferedImage atlas = TilesetExport.atlas(source, model.entries, cell);
            BufferedImage image = TilesetPreview.render(atlas,
                    TilesetExport.tileset("graphics/doodads/preview.png", cell, model.entries),
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
        try {
            BufferedImage atlas = TilesetExport.atlas(source, model.entries, cell);
            TilesetExport.write(atlas,
                    TilesetExport.tileset(sheetRelative, cell, model.entries),
                    atlasPath, tilesetPath);
            context.reportStatus("Wrote " + atlasPath + " and " + tilesetPath);
            report();
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(root, "Export failed:\n" + failure,
                    "Export tileset", JOptionPane.ERROR_MESSAGE);
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
            cells += entry.footprintX * entry.footprintY;
        }
        int[] size = TilesetExport.pack(model.entries);
        summary.setText(String.format(
                "%s   %d pieces, %d included, %d cells   atlas %dx%d cells (%dx%d px)",
                sourcePath == null ? "no sheet" : sourcePath.getFileName().toString(),
                model.entries.size(), included, cells,
                size[0], size[1],
                size[0] * (Integer) cellPx.getValue(), size[1] * (Integer) cellPx.getValue()));
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

    /** Editable view of the sliced pieces: id, footprint, cover, and whether it ships. */
    private final class EntryTableModel extends AbstractTableModel {

        private final String[] columns = { "#", "id", "cells X", "cells Y", "cover", "px", "in" };
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
                case 0, 2, 3 -> Integer.class;
                case 6 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column != 0 && column != 5;
        }

        @Override
        public Object getValueAt(int row, int column) {
            TilesetExport.Entry e = entries.get(row);
            return switch (column) {
                case 0 -> row;
                case 1 -> e.id;
                case 2 -> e.footprintX;
                case 3 -> e.footprintY;
                case 4 -> e.cover;
                case 5 -> e.piece.width() + "x" + e.piece.height();
                default -> e.included;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            TilesetExport.Entry e = entries.get(row);
            switch (column) {
                case 1 -> e.id = String.valueOf(value).trim();
                case 2 -> e.footprintX = Math.max(1, ((Number) value).intValue());
                case 3 -> e.footprintY = Math.max(1, ((Number) value).intValue());
                case 4 -> e.cover = String.valueOf(value).trim().toLowerCase();
                case 6 -> e.included = Boolean.TRUE.equals(value);
                default -> { }
            }
            markDirty();
            report();
            refreshPreview();
        }
    }
}
