package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageCatalog;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.AnimationDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.AppearanceVariant;
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
import javax.swing.JTextArea;
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
import java.util.ArrayList;
import java.util.List;

/** Standalone Swing application for authoring modular marine and mech layouts. */
public final class LayerAuthoringWorkbench {

    private LayerAuthoringWorkbench() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
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

    private static final class WorkbenchFrame extends JFrame {
        private final Path projectRoot;
        private AuthoringDocument document;
        private CompositionRenderer renderer;
        private final JComboBox<UnitComposition> unitBox = new JComboBox<>();
        private final JComboBox<AppearanceVariant> variantBox = new JComboBox<>();
        private final JComboBox<AnimationDefinition> animationBox = new JComboBox<>();
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
        private final JCheckBox loop = new JCheckBox("Loop animation");
        private final JTextField sprite = new JTextField();
        private CompositionCanvas canvas;
        private SheetPanel sheet;
        private final DocumentHistory history = new DocumentHistory();
        private String savedSnapshot;
        private boolean refreshing;
        private boolean dirty;
        private long frameElapsedMs;
        private int playbackFrameIndex;
        private final Timer timer;
        private final List<MountedAuthoringPage> contributedPages = new ArrayList<>();
        private boolean contributedPagesClosed;

        WorkbenchFrame(Path projectRoot) throws Exception {
            super("Starsector Marines Authoring");
            this.projectRoot = projectRoot.toAbsolutePath().normalize();
            reloadDocument();
            savedSnapshot = document.snapshot();
            timer = new Timer(40, this::animate);
            buildUi();
            bind();
            populateUnits();
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) {
                    if (confirmClose()) dispose();
                }
            });
            setMinimumSize(new Dimension(1050, 680));
            setSize(1320, 840);
            setLocationByPlatform(true);
            timer.start();
        }

        @Override
        public void dispose() {
            timer.stop();
            closeContributedPages();
            super.dispose();
        }

        private void buildUi() throws Exception {
            canvas = new CompositionCanvas(renderer);
            sheet = new SheetPanel();
            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JPanel selectors = new JPanel();
            selectors.setLayout(new BoxLayout(selectors, BoxLayout.X_AXIS));
            selectors.add(new JLabel("Unit  "));
            selectors.add(unitBox);
            selectors.add(Box.createHorizontalStrut(10));
            selectors.add(new JLabel("Variant  "));
            selectors.add(variantBox);
            selectors.add(Box.createHorizontalStrut(10));
            selectors.add(new JLabel("Animation  "));
            selectors.add(animationBox);
            selectors.add(Box.createHorizontalStrut(10));
            selectors.add(new JLabel("Keyframe  "));
            selectors.add(frameBox);
            selectors.add(Box.createHorizontalStrut(10));
            selectors.add(new JLabel("Layer  "));
            selectors.add(layerBox);
            top.add(selectors);

            JPanel actions = new JPanel();
            actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
            actions.add(play);
            actions.add(Box.createHorizontalStrut(6));
            actions.add(button("+ Animation", event -> duplicateAnimation()));
            actions.add(Box.createHorizontalStrut(4));
            actions.add(button("− Animation", event -> deleteAnimation()));
            actions.add(Box.createHorizontalStrut(10));
            actions.add(button("+ Keyframe", event -> duplicateFrame()));
            actions.add(Box.createHorizontalStrut(4));
            actions.add(button("− Keyframe", event -> deleteFrame()));
            actions.add(Box.createHorizontalGlue());
            actions.add(button("Reload", event -> reload()));
            actions.add(Box.createHorizontalStrut(6));
            actions.add(button("Export sheet", event -> exportSheet()));
            actions.add(Box.createHorizontalStrut(6));
            JButton save = button("Save JSON…", event -> save());
            save.setFont(save.getFont().deriveFont(Font.BOLD));
            actions.add(save);
            top.add(Box.createVerticalStrut(5));
            top.add(actions);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Animation", canvas);
            tabs.addTab("Combined sheet", new JScrollPane(sheet));
            tabs.addTab("Snapshots", new SnapshotPanel(projectRoot,
                    starsectorCoreRoot(), () -> !dirty));

            JPanel inspector = inspector();
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    tabs, new JScrollPane(inspector));
            split.setResizeWeight(1.0);
            split.setDividerLocation(980);

            JPanel layersPage = new JPanel(new BorderLayout());
            layersPage.add(top, BorderLayout.NORTH);
            layersPage.add(split, BorderLayout.CENTER);

            JTabbedPane authoringTabs = new JTabbedPane();
            authoringTabs.addTab("Layers", layersPage);
            mountContributedPages(authoringTabs);

            status.setBorder(BorderFactory.createEmptyBorder(5, 10, 7, 10));
            setLayout(new BorderLayout());
            add(authoringTabs, BorderLayout.CENTER);
            add(status, BorderLayout.SOUTH);
        }

        private Path starsectorCoreRoot() {
            String configured = System.getProperty("starsectorDir", "").trim();
            return configured.isEmpty()
                    ? projectRoot.resolve("starsector-core")
                    : Path.of(configured).resolve("starsector-core");
        }

        private void mountContributedPages(JTabbedPane tabs) throws Exception {
            AuthoringPageContext context = new AuthoringPageContext(projectRoot,
                    starsectorCoreRoot(), status::setText, this::updateTitle);
            for (AuthoringPageProvider provider : AuthoringPageCatalog.discover().providers()) {
                try {
                    AuthoringPage page = provider.create(context);
                    if (page == null) {
                        throw new IllegalStateException("Authoring page provider '"
                                + provider.id() + "' returned no page");
                    }
                    if (page.component() == null) {
                        page.close();
                        throw new IllegalStateException("Authoring page provider '"
                                + provider.id() + "' returned no component");
                    }
                    contributedPages.add(new MountedAuthoringPage(provider.label(), page));
                    tabs.addTab(provider.label(), page.component());
                } catch (Exception failure) {
                    tabs.addTab(provider.label() + " (error)",
                            providerFailurePage(provider, failure));
                    status.setText("Could not load " + provider.label() + ": "
                            + failureMessage(failure));
                }
            }
        }

        private static JPanel providerFailurePage(AuthoringPageProvider provider,
                                                  Exception failure) {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
            JLabel heading = new JLabel("Could not load " + provider.label());
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
            panel.add(heading, BorderLayout.NORTH);
            JTextArea detail = new JTextArea(failureMessage(failure));
            detail.setEditable(false);
            detail.setLineWrap(true);
            detail.setWrapStyleWord(true);
            detail.setOpaque(false);
            panel.add(detail, BorderLayout.CENTER);
            return panel;
        }

        private static String failureMessage(Exception failure) {
            return failure.getMessage() != null ? failure.getMessage() : failure.toString();
        }

        private void closeContributedPages() {
            if (contributedPagesClosed) return;
            contributedPagesClosed = true;
            for (MountedAuthoringPage mounted : contributedPages) {
                try {
                    mounted.page().close();
                } catch (RuntimeException failure) {
                    status.setText("Could not close " + mounted.label() + ": "
                            + failure.getMessage());
                }
            }
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
            panel.add(row("Transition ms", duration));
            panel.add(visible);
            panel.add(loop);
            panel.add(Box.createVerticalStrut(8));
            panel.add(new JLabel("Sprite path"));
            sprite.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            panel.add(sprite);
            panel.add(Box.createVerticalStrut(18));
            JLabel help = new JLabel("<html><b>Playback</b><br>Play samples only the selected "
                    + "animation and blends matching layers between keyframes.<br><br>"
                    + "<b>Canvas</b><br>Click to select<br>Drag layer to position<br>"
                    + "Drag gold handle to rotate<br>"
                    + "Wheel: scale<br>Shift-wheel: X only<br>Alt-wheel: Y only<br>"
                    + "Ctrl-wheel: rotate<br><br><b>History</b><br>Ctrl+Z: undo<br>"
                    + "Ctrl+Shift+Z: redo<br><br><b>Save</b><br>Ctrl+S opens a confirmation "
                    + "before replacing the mod JSON.</html>");
            help.setForeground(new Color(0x55, 0x55, 0x55));
            panel.add(help);
            panel.add(Box.createVerticalGlue());
            return panel;
        }

        private void bind() {
            unitBox.addActionListener(event -> populateVariants(null));
            variantBox.addActionListener(event -> populateAnimations(null));
            animationBox.addActionListener(event -> populateFrames(null));
            frameBox.addActionListener(event -> {
                if (!refreshing) stopPlayback();
                populateLayers(selectedLayerId());
            });
            layerBox.addActionListener(event -> refreshSelection());
            play.addActionListener(event -> togglePlayback());
            canvas.onSelection(layer -> layerBox.setSelectedItem(layer));
            canvas.onChangeStarted(this::beginHistoryChange);
            canvas.onChangeFinished(this::finishHistoryChange);
            canvas.onChange(() -> {
                refreshFields();
                sheet.repaint();
            });
            bindSpinner(offsetX); bindSpinner(offsetY); bindSpinner(scaleX);
            bindSpinner(scaleY); bindSpinner(angle); bindSpinner(pivotX);
            bindSpinner(pivotY); bindSpinner(z); bindSpinner(duration);
            visible.addActionListener(event -> updateFromFields());
            loop.addActionListener(event -> updateLoop());
            sprite.addActionListener(event -> updateFromFields());
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save");
            getRootPane().getActionMap().put("save", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { save(); }
            });
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo");
            getRootPane().getActionMap().put("undo", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { undo(); }
            });
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                            KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "redo");
            getRootPane().getActionMap().put("redo", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { redo(); }
            });
        }

        private void bindSpinner(JSpinner spinner) {
            spinner.addChangeListener(event -> updateFromFields());
        }

        private void populateUnits() {
            populateSelection(null, null, null, null, null);
        }

        private void populateSelection(String unitId, String variantId, String animationId,
                                       String frameId, String layerId) {
            refreshing = true;
            unitBox.removeAllItems();
            UnitComposition selectedUnit = null;
            for (UnitComposition candidate : document.units()) {
                unitBox.addItem(candidate);
                if (candidate.id().equals(unitId)) selectedUnit = candidate;
            }
            if (selectedUnit != null) unitBox.setSelectedItem(selectedUnit);
            else if (unitBox.getItemCount() > 0) unitBox.setSelectedIndex(0);

            variantBox.removeAllItems();
            AppearanceVariant selectedVariant = null;
            UnitComposition currentUnit = unit();
            if (currentUnit != null) {
                for (AppearanceVariant candidate : currentUnit.variants()) {
                    variantBox.addItem(candidate);
                    if (candidate.id().equals(variantId)) selectedVariant = candidate;
                }
            }
            if (selectedVariant != null) variantBox.setSelectedItem(selectedVariant);
            else if (variantBox.getItemCount() > 0) variantBox.setSelectedIndex(0);

            animationBox.removeAllItems();
            AnimationDefinition selectedAnimation = null;
            AppearanceVariant currentVariant = variant();
            if (currentVariant != null) {
                for (AnimationDefinition candidate : currentVariant.animations()) {
                    animationBox.addItem(candidate);
                    if (candidate.id().equals(animationId)) selectedAnimation = candidate;
                }
            }
            if (selectedAnimation != null) animationBox.setSelectedItem(selectedAnimation);
            else if (animationBox.getItemCount() > 0) animationBox.setSelectedIndex(0);

            frameBox.removeAllItems();
            FrameDefinition selectedFrame = null;
            AnimationDefinition currentAnimation = animation();
            if (currentAnimation != null) {
                for (FrameDefinition candidate : currentAnimation.frames()) {
                    frameBox.addItem(candidate);
                    if (candidate.id().equals(frameId)) selectedFrame = candidate;
                }
            }
            if (selectedFrame != null) frameBox.setSelectedItem(selectedFrame);
            else if (frameBox.getItemCount() > 0) frameBox.setSelectedIndex(0);

            layerBox.removeAllItems();
            LayerDefinition selectedLayer = null;
            FrameDefinition currentFrame = frame();
            if (currentFrame != null) {
                for (LayerDefinition candidate : currentFrame.layers()) {
                    layerBox.addItem(candidate);
                    if (candidate.id().equals(layerId)) selectedLayer = candidate;
                }
            }
            if (selectedLayer != null) layerBox.setSelectedItem(selectedLayer);
            else if (layerBox.getItemCount() > 0) layerBox.setSelectedIndex(0);
            refreshing = false;
            refreshSelection();
            sheet.repaint();
        }

        private void populateVariants(String preferredVariant) {
            if (refreshing) return;
            stopPlayback();
            UnitComposition unit = unit();
            refreshing = true;
            variantBox.removeAllItems();
            AppearanceVariant preferred = null;
            if (unit != null) {
                for (AppearanceVariant variant : unit.variants()) {
                    variantBox.addItem(variant);
                    if (variant.id().equals(preferredVariant)) preferred = variant;
                }
            }
            if (preferred != null) variantBox.setSelectedItem(preferred);
            else if (variantBox.getItemCount() > 0) variantBox.setSelectedIndex(0);
            refreshing = false;
            populateAnimations(null);
        }

        private void populateAnimations(String preferredAnimation) {
            if (refreshing) return;
            stopPlayback();
            AppearanceVariant variant = variant();
            refreshing = true;
            animationBox.removeAllItems();
            AnimationDefinition preferred = null;
            if (variant != null) {
                for (AnimationDefinition animation : variant.animations()) {
                    animationBox.addItem(animation);
                    if (animation.id().equals(preferredAnimation)) preferred = animation;
                }
            }
            if (preferred != null) animationBox.setSelectedItem(preferred);
            else if (animationBox.getItemCount() > 0) animationBox.setSelectedIndex(0);
            refreshing = false;
            populateFrames(null);
        }

        private void populateFrames(String preferredLayer) {
            if (refreshing) return;
            stopPlayback();
            AnimationDefinition animation = animation();
            refreshing = true;
            frameBox.removeAllItems();
            if (animation != null) {
                for (FrameDefinition frame : animation.frames()) frameBox.addItem(frame);
            }
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
            if (animation() != null) loop.setSelected(animation().loop());
            refreshing = false;
        }

        private void updateFromFields() {
            if (refreshing) return;
            LayerDefinition layer = layer();
            FrameDefinition frame = frame();
            if (layer == null || frame == null) return;
            beginHistoryChange();
            layer.offset(value(offsetX), value(offsetY));
            layer.scale(value(scaleX), value(scaleY));
            layer.angleDegrees(value(angle));
            layer.pivot(value(pivotX), value(pivotY));
            layer.z(((Number) z.getValue()).intValue());
            layer.visible(visible.isSelected());
            layer.spritePath(sprite.getText().trim());
            frame.durationMs(((Number) duration.getValue()).intValue());
            finishHistoryChange();
            canvas.repaint();
            sheet.repaint();
        }

        private void updateLoop() {
            if (refreshing || animation() == null) return;
            beginHistoryChange();
            animation().loop(loop.isSelected());
            finishHistoryChange();
            sheet.repaint();
        }

        private void animate(ActionEvent event) {
            AnimationDefinition animation = animation();
            if (!play.isSelected() || animation == null || animation.frames().isEmpty()) return;
            frameElapsedMs += timer.getDelay();
            FrameDefinition current = animation.frames().get(playbackFrameIndex);
            while (frameElapsedMs >= current.durationMs()) {
                frameElapsedMs -= current.durationMs();
                if (playbackFrameIndex + 1 < animation.frames().size()) {
                    playbackFrameIndex++;
                } else if (animation.loop()) {
                    playbackFrameIndex = 0;
                } else {
                    play.setSelected(false);
                    stopPlayback();
                    status.setText("Completed " + animation.label());
                    return;
                }
                current = animation.frames().get(playbackFrameIndex);
            }
            double progress = (double) frameElapsedMs / current.durationMs();
            canvas.preview(unit(), renderer.sample(animation, playbackFrameIndex, progress));
            status.setText("Previewing " + animation.label() + " · keyframe "
                    + (playbackFrameIndex + 1) + "/" + animation.frames().size());
        }

        private void togglePlayback() {
            if (!play.isSelected()) {
                stopPlayback();
                return;
            }
            AnimationDefinition animation = animation();
            if (animation == null || animation.frames().isEmpty()) {
                play.setSelected(false);
                return;
            }
            play.setText("■ Stop");
            playbackFrameIndex = 0;
            frameElapsedMs = 0L;
            canvas.preview(unit(), renderer.sample(animation, 0, 0.0));
            status.setText("Previewing " + animation.label());
        }

        private void stopPlayback() {
            play.setSelected(false);
            play.setText("▶ Play");
            frameElapsedMs = 0L;
            if (canvas != null) canvas.selection(unit(), frame(), layer());
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
                int choice = JOptionPane.showConfirmDialog(this,
                        "<html><b>Replace the unit-layer layout JSON?</b><br><br>"
                                + document.sourcePath().toAbsolutePath()
                                + "<br><br>This writes the current editor state to disk.</html>",
                        "Confirm JSON overwrite", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    status.setText("Save cancelled");
                    return;
                }
                document.save();
                savedSnapshot = document.snapshot();
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
                stopPlayback();
                reloadDocument();
                history.clear();
                savedSnapshot = document.snapshot();
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

        private boolean confirmClose() {
            List<String> unsaved = new ArrayList<>();
            if (dirty) unsaved.add("Layers");
            for (MountedAuthoringPage mounted : contributedPages) {
                if (mounted.page().hasUnsavedChanges()) unsaved.add(mounted.label());
            }
            if (unsaved.isEmpty()) return true;
            String changes = String.join("<br>", unsaved.stream()
                    .map(label -> "• " + label)
                    .toList());
            return JOptionPane.showConfirmDialog(this,
                    "<html>Discard unsaved changes in:<br><br>" + changes + "?</html>",
                    "Unsaved changes", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
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
                if (Files.exists(output)) {
                    int choice = JOptionPane.showConfirmDialog(this,
                            "<html><b>Replace the existing PNG?</b><br><br>"
                                    + output.toAbsolutePath() + "</html>",
                            "Confirm PNG overwrite", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (choice != JOptionPane.YES_OPTION) {
                        status.setText("Export cancelled");
                        return;
                    }
                }
                if (output.getParent() != null) Files.createDirectories(output.getParent());
                ImageIO.write(renderer.renderSheet(unit, 420, 420), "PNG", output.toFile());
                status.setText("Exported " + output.toAbsolutePath());
            } catch (IOException failure) {
                JOptionPane.showMessageDialog(this, failure.getMessage(),
                        "Export failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void beginHistoryChange() {
            try {
                history.begin(document);
            } catch (Exception failure) {
                history.cancel();
                showHistoryFailure(failure);
            }
        }

        private void finishHistoryChange() {
            try {
                history.commit(document);
                updateDirtyFromDocument();
            } catch (Exception failure) {
                history.cancel();
                dirty = true;
                updateTitle();
                showHistoryFailure(failure);
            }
        }

        private void updateDirtyFromDocument() {
            try {
                dirty = !document.snapshot().equals(savedSnapshot);
            } catch (Exception failure) {
                dirty = true;
            }
            updateTitle();
        }

        private void undo() {
            restoreHistory(true);
        }

        private void redo() {
            restoreHistory(false);
        }

        private void restoreHistory(boolean undo) {
            boolean available = undo ? history.canUndo() : history.canRedo();
            if (!available) {
                status.setText(undo ? "Nothing to undo" : "Nothing to redo");
                return;
            }
            stopPlayback();
            String unitId = unit() != null ? unit().id() : null;
            String variantId = variant() != null ? variant().id() : null;
            String animationId = animation() != null ? animation().id() : null;
            String frameId = frame() != null ? frame().id() : null;
            String layerId = selectedLayerId();
            try {
                document = undo ? history.undo(document) : history.redo(document);
                populateSelection(unitId, variantId, animationId, frameId, layerId);
                updateDirtyFromDocument();
                status.setText(undo ? "Undid last change" : "Redid last change");
            } catch (Exception failure) {
                showHistoryFailure(failure);
            }
        }

        private void showHistoryFailure(Exception failure) {
            JOptionPane.showMessageDialog(this, failure.getMessage(),
                    "Edit history failed", JOptionPane.ERROR_MESSAGE);
        }

        private void duplicateAnimation() {
            stopPlayback();
            AppearanceVariant variant = variant();
            AnimationDefinition animation = animation();
            if (variant == null || animation == null) return;
            String id = JOptionPane.showInputDialog(this, "New animation id",
                    animation.id() + "-copy");
            if (id == null) return;
            id = id.trim();
            boolean duplicate = false;
            for (AnimationDefinition item : variant.animations()) {
                if (item.id().equals(id)) duplicate = true;
            }
            if (id.isEmpty() || duplicate) {
                JOptionPane.showMessageDialog(this,
                        "Animation id must be non-empty and unique within the variant",
                        "Cannot duplicate animation", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String label = JOptionPane.showInputDialog(this, "Animation label",
                    animation.label());
            if (label == null) return;
            AnimationDefinition copy = animation.copy(id,
                    label.trim().isEmpty() ? id : label.trim());
            beginHistoryChange();
            variant.animations().add(copy);
            rebuildAnimations(copy);
            finishHistoryChange();
            sheet.repaint();
        }

        private void deleteAnimation() {
            stopPlayback();
            AppearanceVariant variant = variant();
            AnimationDefinition animation = animation();
            if (variant == null || animation == null) return;
            if (variant.animations().size() <= 1) {
                JOptionPane.showMessageDialog(this,
                        "A variant must retain at least one animation",
                        "Cannot delete animation", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (JOptionPane.showConfirmDialog(this,
                    "Delete animation '" + animation.label() + "' and all its keyframes?",
                    "Delete animation", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            beginHistoryChange();
            variant.animations().remove(animation);
            rebuildAnimations(variant.animations().get(0));
            finishHistoryChange();
            sheet.repaint();
        }

        private void rebuildAnimations(AnimationDefinition selection) {
            refreshing = true;
            animationBox.removeAllItems();
            for (AnimationDefinition item : variant().animations()) animationBox.addItem(item);
            animationBox.setSelectedItem(selection);
            refreshing = false;
            populateFrames(null);
        }

        private void duplicateFrame() {
            stopPlayback();
            AnimationDefinition animation = animation();
            FrameDefinition frame = frame();
            if (animation == null || frame == null) return;
            String id = JOptionPane.showInputDialog(this, "New keyframe id",
                    frame.id() + "-copy");
            if (id == null) return;
            id = id.trim();
            boolean duplicate = false;
            for (FrameDefinition item : animation.frames()) {
                if (item.id().equals(id)) duplicate = true;
            }
            if (id.isEmpty() || duplicate) {
                JOptionPane.showMessageDialog(this, "Keyframe id must be non-empty and unique",
                        "Cannot duplicate keyframe", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String label = JOptionPane.showInputDialog(this, "Keyframe label", frame.label());
            if (label == null) return;
            FrameDefinition copy = frame.copy(id, label.trim().isEmpty() ? id : label.trim());
            beginHistoryChange();
            animation.frames().add(copy);
            rebuildFrames(copy, selectedLayerId());
            finishHistoryChange();
            sheet.repaint();
        }

        private void deleteFrame() {
            stopPlayback();
            AnimationDefinition animation = animation();
            FrameDefinition frame = frame();
            if (animation == null || frame == null) return;
            if (animation.frames().size() <= 1) {
                JOptionPane.showMessageDialog(this,
                        "An animation must retain at least one keyframe",
                        "Cannot delete keyframe", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (JOptionPane.showConfirmDialog(this,
                    "Delete keyframe '" + frame.label() + "'?",
                    "Delete keyframe", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            beginHistoryChange();
            animation.frames().remove(frame);
            rebuildFrames(animation.frames().get(0), null);
            finishHistoryChange();
            sheet.repaint();
        }

        private void rebuildFrames(FrameDefinition selection, String preferredLayer) {
            refreshing = true;
            frameBox.removeAllItems();
            for (FrameDefinition item : animation().frames()) frameBox.addItem(item);
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
            boolean anyDirty = dirty || contributedPages.stream()
                    .anyMatch(mounted -> mounted.page().hasUnsavedChanges());
            setTitle((anyDirty ? "* " : "") + "Starsector Marines Authoring — "
                    + document.sourcePath().getFileName());
        }

        private record MountedAuthoringPage(String label, AuthoringPage page) {
        }

        private UnitComposition unit() {
            return (UnitComposition) unitBox.getSelectedItem();
        }

        private AppearanceVariant variant() {
            return (AppearanceVariant) variantBox.getSelectedItem();
        }

        private AnimationDefinition animation() {
            return (AnimationDefinition) animationBox.getSelectedItem();
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
