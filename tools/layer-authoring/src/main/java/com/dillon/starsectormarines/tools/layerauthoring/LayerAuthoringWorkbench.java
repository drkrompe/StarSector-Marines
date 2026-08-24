package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.FrameDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.LayerDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.UnitComposition;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Standalone Swing application for authoring modular marine and mech layouts. */
public final class LayerAuthoringWorkbench {

    private LayerAuthoringWorkbench() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        if (args.length > 1 && "--export-all".equals(args[1])) {
            Path output = args.length > 2 ? Path.of(args[2])
                    : projectRoot.resolve("build/layer-authoring");
            exportAll(projectRoot, output);
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Layer authoring UI requires a desktop display");
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> {
            try {
                new WorkbenchFrame(projectRoot).setVisible(true);
            } catch (Exception failure) {
                JOptionPane.showMessageDialog(null, failure.getMessage(),
                        "Layer authoring failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    static void exportAll(Path projectRoot, Path outputDirectory) throws Exception {
        AuthoringDocument document = AuthoringDocument.load(projectRoot);
        CompositionRenderer renderer = new CompositionRenderer(projectRoot);
        Files.createDirectories(outputDirectory);
        for (UnitComposition unit : document.units()) {
            Path output = outputDirectory.resolve(unit.id() + "-sheet.png");
            ImageIO.write(renderer.renderSheet(unit, 420, 420), "PNG", output.toFile());
            System.out.println("Wrote " + output.toAbsolutePath());
        }
    }

    private static final class WorkbenchFrame extends JFrame {
        private final Path projectRoot;
        private AuthoringDocument document;
        private CompositionRenderer renderer;
        private final JComboBox<UnitComposition> unitBox = new JComboBox<>();
        private final JComboBox<FrameDefinition> frameBox = new JComboBox<>();
        private final JComboBox<LayerDefinition> layerBox = new JComboBox<>();
        private final JToggleButton play = new JToggleButton("▶ Play");
        private final JLabel status = new JLabel(" ");
        private final JSpinner offsetX = number(0.0, -5.0, 5.0, 0.005);
        private final JSpinner offsetY = number(0.0, -5.0, 5.0, 0.005);
        private final JSpinner scaleX = number(1.0, 0.01, 10.0, 0.01);
        private final JSpinner scaleY = number(1.0, 0.01, 10.0, 0.01);
        private final JSpinner angle = number(0.0, -360.0, 360.0, 0.5);
        private final JSpinner pivotX = number(0.5, 0.0, 1.0, 0.01);
        private final JSpinner pivotY = number(0.5, 0.0, 1.0, 0.01);
        private final JSpinner z = integer(0, -1000, 1000, 1);
        private final JSpinner duration = integer(400, 1, 10000, 10);
        private final JCheckBox visible = new JCheckBox("Visible");
        private final JTextField sprite = new JTextField();
        private CompositionCanvas canvas;
        private SheetPanel sheet;
        private boolean refreshing;
        private boolean dirty;
        private long frameElapsedMs;
        private final Timer timer;

        WorkbenchFrame(Path projectRoot) throws Exception {
            super("Marine / Mech Layer Authoring");
            this.projectRoot = projectRoot.toAbsolutePath().normalize();
            reloadDocument();
            timer = new Timer(40, this::animate);
            timer.start();
            buildUi();
            bind();
            populateUnits();
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) {
                    if (confirmDiscard()) dispose();
                }
            });
            setMinimumSize(new Dimension(1050, 680));
            setSize(1320, 840);
            setLocationByPlatform(true);
        }

        private void buildUi() {
            canvas = new CompositionCanvas(renderer);
            sheet = new SheetPanel();
            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
            top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            top.add(new JLabel("Unit  "));
            top.add(unitBox);
            top.add(Box.createHorizontalStrut(12));
            top.add(new JLabel("Frame  "));
            top.add(frameBox);
            top.add(Box.createHorizontalStrut(12));
            top.add(new JLabel("Layer  "));
            top.add(layerBox);
            top.add(Box.createHorizontalStrut(10));
            top.add(play);
            top.add(Box.createHorizontalStrut(6));
            top.add(button("+ Frame", event -> duplicateFrame()));
            top.add(Box.createHorizontalStrut(4));
            top.add(button("− Frame", event -> deleteFrame()));
            top.add(Box.createHorizontalGlue());
            top.add(button("Reload", event -> reload()));
            top.add(Box.createHorizontalStrut(6));
            top.add(button("Export sheet", event -> exportSheet()));
            top.add(Box.createHorizontalStrut(6));
            JButton save = button("Save JSON", event -> save());
            save.setFont(save.getFont().deriveFont(Font.BOLD));
            top.add(save);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Frame", canvas);
            tabs.addTab("Combined sheet", new JScrollPane(sheet));

            JPanel inspector = inspector();
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    tabs, new JScrollPane(inspector));
            split.setResizeWeight(1.0);
            split.setDividerLocation(980);

            status.setBorder(BorderFactory.createEmptyBorder(5, 10, 7, 10));
            setLayout(new BorderLayout());
            add(top, BorderLayout.NORTH);
            add(split, BorderLayout.CENTER);
            add(status, BorderLayout.SOUTH);
        }

        private JPanel inspector() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            panel.setPreferredSize(new Dimension(280, 620));
            JLabel heading = new JLabel("Selected layer");
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
            panel.add(heading);
            panel.add(Box.createVerticalStrut(12));
            panel.add(row("Offset X", offsetX));
            panel.add(row("Offset Y", offsetY));
            panel.add(row("Scale X", scaleX));
            panel.add(row("Scale Y", scaleY));
            panel.add(row("Angle", angle));
            panel.add(row("Pivot X", pivotX));
            panel.add(row("Pivot Y", pivotY));
            panel.add(row("Z order", z));
            panel.add(row("Frame ms", duration));
            panel.add(visible);
            panel.add(Box.createVerticalStrut(8));
            panel.add(new JLabel("Sprite path"));
            sprite.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            panel.add(sprite);
            panel.add(Box.createVerticalStrut(18));
            JLabel help = new JLabel("<html><b>Canvas</b><br>Click to select<br>Drag to position<br>"
                    + "Wheel: scale<br>Shift-wheel: X only<br>Alt-wheel: Y only<br>"
                    + "Ctrl-wheel: rotate<br><br><b>Save</b><br>Ctrl+S writes the mod JSON atomically.</html>");
            help.setForeground(new Color(0x55, 0x55, 0x55));
            panel.add(help);
            panel.add(Box.createVerticalGlue());
            return panel;
        }

        private void bind() {
            unitBox.addActionListener(event -> populateFrames(null));
            frameBox.addActionListener(event -> populateLayers(selectedLayerId()));
            layerBox.addActionListener(event -> refreshSelection());
            play.addActionListener(event -> {
                play.setText(play.isSelected() ? "■ Stop" : "▶ Play");
                frameElapsedMs = 0L;
            });
            canvas.onSelection(layer -> layerBox.setSelectedItem(layer));
            canvas.onChange(() -> {
                markDirty();
                refreshFields();
                sheet.repaint();
            });
            bindSpinner(offsetX); bindSpinner(offsetY); bindSpinner(scaleX);
            bindSpinner(scaleY); bindSpinner(angle); bindSpinner(pivotX);
            bindSpinner(pivotY); bindSpinner(z); bindSpinner(duration);
            visible.addActionListener(event -> updateFromFields());
            sprite.addActionListener(event -> updateFromFields());
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save");
            getRootPane().getActionMap().put("save", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { save(); }
            });
        }

        private void bindSpinner(JSpinner spinner) {
            spinner.addChangeListener(event -> updateFromFields());
        }

        private void populateUnits() {
            refreshing = true;
            unitBox.removeAllItems();
            for (UnitComposition unit : document.units()) unitBox.addItem(unit);
            refreshing = false;
            if (unitBox.getItemCount() > 0) unitBox.setSelectedIndex(0);
            populateFrames(null);
        }

        private void populateFrames(String preferredLayer) {
            if (refreshing) return;
            UnitComposition unit = unit();
            refreshing = true;
            frameBox.removeAllItems();
            if (unit != null) for (FrameDefinition frame : unit.frames()) frameBox.addItem(frame);
            refreshing = false;
            if (frameBox.getItemCount() > 0) frameBox.setSelectedIndex(0);
            populateLayers(preferredLayer);
            sheet.repaint();
        }

        private void populateLayers(String preferredId) {
            if (refreshing) return;
            FrameDefinition frame = frame();
            refreshing = true;
            layerBox.removeAllItems();
            LayerDefinition preferred = null;
            if (frame != null) {
                for (LayerDefinition layer : frame.layers()) {
                    layerBox.addItem(layer);
                    if (layer.id().equals(preferredId)) preferred = layer;
                }
            }
            refreshing = false;
            if (preferred != null) layerBox.setSelectedItem(preferred);
            else if (layerBox.getItemCount() > 0) layerBox.setSelectedIndex(0);
            refreshSelection();
        }

        private void refreshSelection() {
            if (refreshing) return;
            canvas.selection(unit(), frame(), layer());
            refreshFields();
        }

        private void refreshFields() {
            LayerDefinition layer = layer();
            FrameDefinition frame = frame();
            refreshing = true;
            if (layer != null) {
                offsetX.setValue(layer.offsetX()); offsetY.setValue(layer.offsetY());
                scaleX.setValue(layer.scaleX()); scaleY.setValue(layer.scaleY());
                angle.setValue(layer.angleDegrees()); pivotX.setValue(layer.pivotX());
                pivotY.setValue(layer.pivotY()); z.setValue(layer.z());
                visible.setSelected(layer.visible()); sprite.setText(layer.spritePath());
            }
            if (frame != null) duration.setValue(frame.durationMs());
            refreshing = false;
        }

        private void updateFromFields() {
            if (refreshing) return;
            LayerDefinition layer = layer();
            FrameDefinition frame = frame();
            if (layer == null || frame == null) return;
            layer.offset(value(offsetX), value(offsetY));
            layer.scale(value(scaleX), value(scaleY));
            layer.angleDegrees(value(angle));
            layer.pivot(value(pivotX), value(pivotY));
            layer.z(((Number) z.getValue()).intValue());
            layer.visible(visible.isSelected());
            layer.spritePath(sprite.getText().trim());
            frame.durationMs(((Number) duration.getValue()).intValue());
            markDirty();
            canvas.repaint();
            sheet.repaint();
        }

        private void animate(ActionEvent event) {
            if (!play.isSelected() || frame() == null || frameBox.getItemCount() < 2) return;
            frameElapsedMs += 40L;
            if (frameElapsedMs < frame().durationMs()) return;
            frameElapsedMs = 0L;
            frameBox.setSelectedIndex((frameBox.getSelectedIndex() + 1)
                    % frameBox.getItemCount());
        }

        private void save() {
            try {
                commitEditors();
                updateFromFields();
                List<String> errors = document.validate();
                if (!errors.isEmpty()) {
                    JOptionPane.showMessageDialog(this, String.join("\n", errors),
                            "Cannot save invalid layout", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                document.save();
                dirty = false;
                updateTitle();
                status.setText("Saved " + document.sourcePath());
            } catch (Exception failure) {
                JOptionPane.showMessageDialog(this, failure.getMessage(),
                        "Save failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void reload() {
            if (!confirmDiscard()) return;
            try {
                reloadDocument();
                dirty = false;
                populateUnits();
                updateTitle();
                status.setText("Reloaded " + document.sourcePath());
            } catch (Exception failure) {
                JOptionPane.showMessageDialog(this, failure.getMessage(),
                        "Reload failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void reloadDocument() throws Exception {
            document = AuthoringDocument.load(projectRoot);
            if (renderer == null) renderer = new CompositionRenderer(projectRoot);
        }

        private boolean confirmDiscard() {
            if (!dirty) return true;
            return JOptionPane.showConfirmDialog(this,
                    "Discard unsaved layer changes?", "Unsaved changes",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }

        private void exportSheet() {
            UnitComposition unit = unit();
            if (unit == null) return;
            try {
                commitEditors();
                updateFromFields();
            } catch (java.text.ParseException failure) {
                JOptionPane.showMessageDialog(this, failure.getMessage(),
                        "Invalid numeric value", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JFileChooser chooser = new JFileChooser(projectRoot.resolve("build").toFile());
            chooser.setSelectedFile(projectRoot.resolve("build/layer-authoring/"
                    + unit.id() + "-sheet.png").toFile());
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try {
                Path output = chooser.getSelectedFile().toPath();
                if (output.getParent() != null) Files.createDirectories(output.getParent());
                ImageIO.write(renderer.renderSheet(unit, 420, 420), "PNG", output.toFile());
                status.setText("Exported " + output.toAbsolutePath());
            } catch (IOException failure) {
                JOptionPane.showMessageDialog(this, failure.getMessage(),
                        "Export failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void markDirty() {
            if (!dirty) {
                dirty = true;
                updateTitle();
            }
        }

        private void duplicateFrame() {
            UnitComposition unit = unit();
            FrameDefinition frame = frame();
            if (unit == null || frame == null) return;
            String id = JOptionPane.showInputDialog(this, "New frame id",
                    frame.id() + "-copy");
            if (id == null) return;
            id = id.trim();
            boolean duplicate = false;
            for (FrameDefinition item : unit.frames()) {
                if (item.id().equals(id)) duplicate = true;
            }
            if (id.isEmpty() || duplicate) {
                JOptionPane.showMessageDialog(this, "Frame id must be non-empty and unique",
                        "Cannot duplicate frame", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String label = JOptionPane.showInputDialog(this, "Frame label", frame.label());
            if (label == null) return;
            FrameDefinition copy = frame.copy(id, label.trim().isEmpty() ? id : label.trim());
            unit.frames().add(copy);
            rebuildFrames(copy, selectedLayerId());
            markDirty();
            sheet.repaint();
        }

        private void deleteFrame() {
            UnitComposition unit = unit();
            FrameDefinition frame = frame();
            if (unit == null || frame == null) return;
            if (unit.frames().size() <= 1) {
                JOptionPane.showMessageDialog(this, "A unit must retain at least one frame",
                        "Cannot delete frame", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "Delete frame '" + frame.label() + "'?",
                    "Delete frame", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            unit.frames().remove(frame);
            rebuildFrames(unit.frames().get(0), null);
            markDirty();
            sheet.repaint();
        }

        private void rebuildFrames(FrameDefinition selection, String preferredLayer) {
            refreshing = true;
            frameBox.removeAllItems();
            for (FrameDefinition item : unit().frames()) frameBox.addItem(item);
            frameBox.setSelectedItem(selection);
            refreshing = false;
            populateLayers(preferredLayer);
        }

        private void commitEditors() throws java.text.ParseException {
            for (JSpinner spinner : List.of(offsetX, offsetY, scaleX, scaleY, angle,
                    pivotX, pivotY, z, duration)) {
                spinner.commitEdit();
            }
        }

        private void updateTitle() {
            setTitle((dirty ? "* " : "") + "Marine / Mech Layer Authoring — "
                    + document.sourcePath().getFileName());
        }

        private UnitComposition unit() {
            return (UnitComposition) unitBox.getSelectedItem();
        }

        private FrameDefinition frame() {
            return (FrameDefinition) frameBox.getSelectedItem();
        }

        private LayerDefinition layer() {
            return (LayerDefinition) layerBox.getSelectedItem();
        }

        private String selectedLayerId() {
            LayerDefinition layer = layer();
            return layer != null ? layer.id() : null;
        }

        private static JButton button(String label,
                                      java.awt.event.ActionListener listener) {
            JButton button = new JButton(label);
            button.addActionListener(listener);
            return button;
        }

        private static JPanel row(String label, JComponent field) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            row.add(new JLabel(label), BorderLayout.WEST);
            row.add(field, BorderLayout.EAST);
            return row;
        }

        private static JSpinner number(double value, double min, double max, double step) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            spinner.setPreferredSize(new Dimension(105, 27));
            return spinner;
        }

        private static JSpinner integer(int value, int min, int max, int step) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            spinner.setPreferredSize(new Dimension(105, 27));
            return spinner;
        }

        private static double value(JSpinner spinner) {
            return ((Number) spinner.getValue()).doubleValue();
        }

        private final class SheetPanel extends JPanel {
            SheetPanel() {
                setPreferredSize(new Dimension(900, 900));
            }

            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                UnitComposition unit = unit();
                if (unit == null) return;
                BufferedImage image = renderer.renderSheet(unit, 420, 420);
                double scale = Math.min((double) getWidth() / image.getWidth(),
                        (double) getHeight() / image.getHeight());
                scale = Math.min(1.0, scale);
                int width = (int) Math.round(image.getWidth() * scale);
                int height = (int) Math.round(image.getHeight() * scale);
                int x = (getWidth() - width) / 2;
                int y = Math.max(0, (getHeight() - height) / 2);
                Graphics2D copy = (Graphics2D) graphics.create();
                copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                copy.drawImage(image, x, y, width, height, null);
                copy.dispose();
            }
        }
    }
}
