package com.dillon.starsectormarines.tools.turretauthoring;

import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.preview.HeadlessTurretCatalogPreviewRenderer;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Swing authoring page for the weapon → mount → structure turret chain. */
public final class TurretAuthoringPage implements AuthoringPage {

    private final AuthoringPageContext context;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JComboBox<TurretAuthoringDocument.TurretSelection> turretBox =
            new JComboBox<>();
    private final JComboBox<FxSlot> slotBox = new JComboBox<>(FxSlot.values());
    private final DefaultListModel<Integer> layerModel = new DefaultListModel<>();
    private final JList<Integer> layerList = new JList<>(layerModel);
    private final FxPropertyModel fxProperties = new FxPropertyModel();
    private final JTable fxTable = new JTable(fxProperties);
    private final JLabel preview = new JLabel("Preview loading…");
    private final TurretAuthoringHistory history = new TurretAuthoringHistory();
    private final List<FieldBinding> bindings = new ArrayList<>();
    private final HeadlessTurretCatalogPreviewRenderer previewRenderer;
    private EmplacementAuthoringPanel emplacementPanel;
    private TurretAuthoringDocument document;
    private boolean refreshing;

    TurretAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context = context;
        document = TurretAuthoringDocument.load(context.projectRoot());
        previewRenderer = new HeadlessTurretCatalogPreviewRenderer(
                context.projectRoot().resolve("mod"), context.starsectorCoreRoot());
        buildUi();
        reloadSelectors(null);
    }

    @Override public JComponent component() { return root; }

    @Override
    public boolean hasUnsavedChanges() {
        try {
            return fxTable.isEditing() || document.dirty();
        } catch (Exception failure) {
            return true;
        }
    }

    private void buildUi() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JLabel("Turret"));
        turretBox.setPreferredSize(new Dimension(250, 28));
        toolbar.add(turretBox);
        toolbar.add(button("Save", this::save));
        toolbar.add(button("Reload", this::reload));
        toolbar.add(button("Undo", this::undo));
        toolbar.add(button("Redo", this::redo));
        root.add(toolbar, BorderLayout.NORTH);

        JTabbedPane modes = new JTabbedPane();
        modes.addTab("Definition + FX", buildTurretMode());
        emplacementPanel = new EmplacementAuthoringPanel(this);
        modes.addTab("Emplacements", emplacementPanel);
        root.add(modes, BorderLayout.CENTER);

        turretBox.addActionListener(event -> refreshSelection());
        slotBox.addActionListener(event -> rebuildLayers());
        layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) fxProperties.refresh();
        });
    }

    private JComponent buildTurretMode() {
        JPanel inspector = new JPanel();
        inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
        inspector.add(section("Weapon payload",
                number("Range", 0.1, 500, 0.5, selection -> sim(selection).getDouble("range"),
                        (selection, value) -> sim(selection).put("range", value)),
                number("Damage", 0, 10000, 1, selection -> sim(selection).getDouble("damage"),
                        (selection, value) -> sim(selection).put("damage", value)),
                number("Penetration", 0, 1000, 1, selection -> sim(selection).getDouble("penetration"),
                        (selection, value) -> sim(selection).put("penetration", value)),
                number("Contact damage", 0, 10000, 1, this::contactDamage,
                        (selection, value) -> contact(selection).put("damage", value)),
                number("Contact penetration", 0, 1000, 1, this::contactPenetration,
                        (selection, value) -> contact(selection).put("penetration", value)),
                number("AoE radius", 0, 50, 0.05, selection -> sim(selection).optDouble("aoeRadius", 0),
                        (selection, value) -> sim(selection).put("aoeRadius", value)),
                number("Cooldown", 0.01, 60, 0.05, selection -> sim(selection).getDouble("cooldown"),
                        (selection, value) -> sim(selection).put("cooldown", value)),
                number("Accuracy", 0, 1, 0.01, selection -> sim(selection).getDouble("accuracy"),
                        (selection, value) -> sim(selection).put("accuracy", value))));

        inspector.add(section("Mount",
                number("Ammo", 0, 10000, 1, selection -> mountSim(selection).getDouble("ammoCapacity"),
                        (selection, value) -> mountSim(selection).put("ammoCapacity", (int) value)),
                number("Turn °/sec", 0.01, 1000, 1, selection -> mountSim(selection).getDouble("turnRateDegPerSec"),
                        (selection, value) -> mountSim(selection).put("turnRateDegPerSec", value)),
                number("Visual cells", 0, 20, 0.05, selection -> mountRender(selection).optDouble("visualCells", 0),
                        (selection, value) -> mountRender(selection).put("visualCells", value)),
                number("Muzzle offset", 0, 20, 0.01, selection -> mountRender(selection).optDouble("muzzleOffsetCells", 0),
                        (selection, value) -> mountRender(selection).put("muzzleOffsetCells", value)),
                text("Body sprite", selection -> mountRender(selection).optString("sprite", ""),
                        (selection, value) -> mountRender(selection).put("sprite", value)),
                text("Recoil sprite", selection -> mountRender(selection).optString("recoilSprite", ""),
                        (selection, value) -> mountRender(selection).put("recoilSprite", value))));

        inspector.add(section("Projectile / artillery",
                number("Burst rounds", 1, 100, 1,
                        selection -> sim(selection).optInt("burstCount", 1),
                        (selection, value) -> sim(selection).put("burstCount", (int) value)),
                number("Burst spacing", 0, 10, 0.01,
                        selection -> sim(selection).optDouble("burstSpacing", 0),
                        (selection, value) -> sim(selection).put("burstSpacing", value)),
                number("Round velocity", 0, 1000, 1,
                        selection -> sim(selection).optDouble("roundVelocity", 0),
                        (selection, value) -> sim(selection).put("roundVelocity", value)),
                number("Minimum range", 0, 500, 0.5,
                        selection -> sim(selection).optDouble("minRange", 0),
                        (selection, value) -> sim(selection).put("minRange", value)),
                number("Hit spread", 0, 100, 0.1,
                        selection -> sim(selection).optDouble("hitSpread", 0),
                        (selection, value) -> sim(selection).put("hitSpread", value)),
                number("Flight seconds", 0, 60, 0.05,
                        selection -> sim(selection).optDouble("flightSec", 0),
                        (selection, value) -> sim(selection).put("flightSec", value)),
                number("Arc height", 0, 50, 0.05,
                        selection -> sim(selection).optDouble("arcHeight", 0),
                        (selection, value) -> sim(selection).put("arcHeight", value)),
                number("No-LOS accuracy", 0, 1, 0.01,
                        selection -> sim(selection).optDouble("noLosAccuracyMult", 1),
                        (selection, value) -> sim(selection).put("noLosAccuracyMult", value)),
                toggle("Interceptable", selection -> sim(selection)
                                .optBoolean("interceptableProjectile", false),
                        (selection, value) -> sim(selection)
                                .put("interceptableProjectile", value)),
                toggle("Boost ramp", selection -> sim(selection).optBoolean("boostRamp", false),
                        (selection, value) -> sim(selection).put("boostRamp", value)),
                toggle("Indirect fire", selection -> sim(selection)
                                .optBoolean("indirectFire", false),
                        (selection, value) -> sim(selection).put("indirectFire", value)),
                text("Projectile sprite", selection -> weaponRender(selection)
                                .optString("projectileSprite", ""),
                        (selection, value) -> weaponRender(selection)
                                .put("projectileSprite", value)),
                number("Projectile cells", 0, 20, 0.05,
                        selection -> weaponRender(selection)
                                .optDouble("projectileVisualCells", 0),
                        (selection, value) -> weaponRender(selection)
                                .put("projectileVisualCells", value)),
                text("Contrail", selection -> weaponRender(selection)
                                .optString("contrail", ""),
                        (selection, value) -> weaponRender(selection).put("contrail", value)),
                text("Fire sound", selection -> audio(selection).optString("fireSound", ""),
                        (selection, value) -> audio(selection).put("fireSound", value)),
                text("Impact sound", selection -> audio(selection).optString("impactSound", ""),
                        (selection, value) -> audio(selection).put("impactSound", value))));

        inspector.add(section("Structure",
                number("Structure", 0.01, 10000, 1, selection -> durability(selection).getDouble("structure"),
                        (selection, value) -> durability(selection).put("structure", value)),
                number("Armor pool", 0.01, 10000, 1, selection -> durability(selection).getDouble("armorPool"),
                        (selection, value) -> durability(selection).put("armorPool", value)),
                number("Armor rating", 0.01, 1000, 1, selection -> durability(selection).getDouble("armorRating"),
                        (selection, value) -> durability(selection).put("armorRating", value)),
                number("Collision radius", 0.01, 10, 0.01, selection -> physics(selection).getDouble("radius"),
                        (selection, value) -> physics(selection).put("radius", value)),
                number("Hit half-height", 0.01, 10, 0.01, selection -> physics(selection).getDouble("hitHalfHeight"),
                        (selection, value) -> physics(selection).put("hitHalfHeight", value)),
                number("Force score", 0.01, 1000, 1, selection -> selection.structure().getDouble("forceScore"),
                        (selection, value) -> selection.structure().put("forceScore", value))));

        JPanel fx = new JPanel(new BorderLayout(6, 6));
        fx.setBorder(BorderFactory.createTitledBorder("Authored FX layers"));
        fx.add(slotBox, BorderLayout.NORTH);
        fx.add(new JScrollPane(layerList), BorderLayout.WEST);
        fxTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        fx.add(new JScrollPane(fxTable), BorderLayout.CENTER);
        fx.setPreferredSize(new Dimension(430, 240));
        inspector.add(fx);
        inspector.add(Box.createVerticalGlue());

        preview.setVerticalAlignment(JLabel.TOP);
        preview.setHorizontalAlignment(JLabel.LEFT);
        JScrollPane previewScroll = new JScrollPane(preview);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Deterministic six-state preview"));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(inspector), previewScroll);
        split.setResizeWeight(0.34);
        split.setDividerLocation(520);
        return split;
    }

    private void reloadSelectors(String preferredId) {
        refreshing = true;
        DefaultComboBoxModel<TurretAuthoringDocument.TurretSelection> model =
                new DefaultComboBoxModel<>();
        try {
            for (TurretAuthoringDocument.TurretSelection selection : document.selections()) {
                model.addElement(selection);
                if (selection.structureId().equals(preferredId)) model.setSelectedItem(selection);
            }
        } catch (Exception failure) {
            showFailure("Could not read turret catalogs", failure);
        }
        turretBox.setModel(model);
        refreshing = false;
        refreshSelection();
    }

    private void refreshSelection() {
        if (refreshing || selection() == null) return;
        refreshing = true;
        try {
            for (FieldBinding binding : bindings) binding.refresh(selection());
        } catch (Exception failure) {
            showFailure("Could not populate turret fields", failure);
        }
        refreshing = false;
        rebuildLayers();
        refreshPreview();
    }

    private void rebuildLayers() {
        layerModel.clear();
        JSONArray layers = selectedLayers();
        if (layers != null) {
            for (int index = 0; index < layers.length(); index++) layerModel.addElement(index);
        }
        if (!layerModel.isEmpty()) layerList.setSelectedIndex(0);
        fxProperties.refresh();
    }

    private JSONArray selectedLayers() {
        TurretAuthoringDocument.TurretSelection selection = selection();
        FxSlot slot = (FxSlot) slotBox.getSelectedItem();
        if (selection == null || slot == null) return null;
        JSONObject fx = selection.weapon().optJSONObject("fx");
        return fx != null ? fx.optJSONArray(slot.key) : null;
    }

    private JSONObject selectedLayer() {
        JSONArray layers = selectedLayers();
        int index = layerList.getSelectedIndex();
        return layers != null && index >= 0 && index < layers.length()
                ? layers.optJSONObject(index) : null;
    }

    private void mutate(Change change) {
        if (refreshing) return;
        try {
            String before = document.snapshot();
            change.run();
            String after = document.snapshot();
            history.record(before, after);
            context.stateChanged();
            refreshPreview();
        } catch (Exception failure) {
            showFailure("Could not apply edit", failure);
        }
    }

    private void refreshPreview() {
        TurretAuthoringDocument.TurretSelection selection = selection();
        if (selection == null) return;
        try {
            TurretMountDef mount = document.previewMount(selection.structureId());
            BufferedImage image = previewRenderer.render(mount).image();
            preview.setIcon(new ImageIcon(image));
            preview.setText(null);
            context.reportStatus("Previewing " + selection.label());
        } catch (Exception failure) {
            preview.setIcon(null);
            preview.setText("<html><b>Preview unavailable</b><br>" + html(failure.getMessage()) + "</html>");
            context.reportStatus("Turret definition is temporarily invalid");
        }
    }

    private void save() {
        if (!commitActiveEditor()) return;
        List<String> errors = document.validate();
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(root, String.join("\n", errors),
                    "Cannot save invalid catalogs", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(root,
                "Replace the turret weapon, mount/structure, and emplacement catalogs?",
                "Confirm catalog save", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            document.save();
            history.clear();
            context.stateChanged();
            context.reportStatus("Saved turret authoring catalogs");
        } catch (Exception failure) {
            showFailure("Save failed", failure);
        }
    }

    private void reload() {
        if (hasUnsavedChanges() && JOptionPane.showConfirmDialog(root,
                "Discard unsaved turret and emplacement changes?", "Unsaved changes",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        String id = selection() != null ? selection().structureId() : null;
        try {
            document = TurretAuthoringDocument.load(context.projectRoot());
            history.clear();
            reloadSelectors(id);
            emplacementPanel.reloadLayouts(null);
            context.stateChanged();
            context.reportStatus("Reloaded turret authoring catalogs");
        } catch (Exception failure) {
            showFailure("Reload failed", failure);
        }
    }

    private void undo() { restoreHistory(true); }

    private void redo() { restoreHistory(false); }

    private void restoreHistory(boolean undo) {
        if (undo ? !history.canUndo() : !history.canRedo()) return;
        String id = selection() != null ? selection().structureId() : null;
        try {
            if (undo) history.undo(document); else history.redo(document);
            reloadSelectors(id);
            emplacementPanel.reloadLayouts(null);
            context.stateChanged();
            context.reportStatus(undo ? "Undid turret edit" : "Redid turret edit");
        } catch (Exception failure) {
            showFailure("History restore failed", failure);
        }
    }

    TurretAuthoringDocument document() { return document; }

    void emplacementChanged(Change change) {
        mutate(change);
        emplacementPanel.repaintCanvas();
    }

    void refreshAfterEmplacementChange() { context.stateChanged(); }

    private FieldBinding number(String label, double min, double max, double step,
                                NumberRead read, NumberWrite write) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        spinner.setPreferredSize(new Dimension(115, 27));
        FieldBinding binding = new FieldBinding(label, spinner,
                selection -> spinner.setValue(read.get(selection)),
                selection -> write.set(selection, ((Number) spinner.getValue()).doubleValue()));
        spinner.addChangeListener(event -> mutate(() -> binding.write(selection())));
        bindings.add(binding);
        return binding;
    }

    private FieldBinding text(String label, TextRead read, TextWrite write) {
        JTextField field = new JTextField(28);
        FieldBinding binding = new FieldBinding(label, field,
                selection -> field.setText(read.get(selection)),
                selection -> write.set(selection, field.getText().trim()));
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { write(); }
            @Override public void removeUpdate(DocumentEvent event) { write(); }
            @Override public void changedUpdate(DocumentEvent event) { write(); }
            private void write() { mutate(() -> binding.write(selection())); }
        });
        bindings.add(binding);
        return binding;
    }

    private FieldBinding toggle(String label, BooleanRead read, BooleanWrite write) {
        JCheckBox checkbox = new JCheckBox();
        FieldBinding binding = new FieldBinding(label, checkbox,
                selection -> checkbox.setSelected(read.get(selection)),
                selection -> write.set(selection, checkbox.isSelected()));
        checkbox.addActionListener(event -> mutate(() -> binding.write(selection())));
        bindings.add(binding);
        return binding;
    }

    private boolean commitActiveEditor() {
        if (!fxTable.isEditing() || fxTable.getCellEditor().stopCellEditing()) return true;
        context.reportStatus("Finish the active FX value before saving");
        return false;
    }

    private static JPanel section(String title, FieldBinding... fields) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        for (FieldBinding field : fields) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            row.add(new JLabel(field.label()), BorderLayout.WEST);
            row.add(field.component(), BorderLayout.EAST);
            panel.add(row);
        }
        return panel;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private TurretAuthoringDocument.TurretSelection selection() {
        return (TurretAuthoringDocument.TurretSelection) turretBox.getSelectedItem();
    }

    private static JSONObject sim(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        return selection.weapon().getJSONObject("sim");
    }

    private JSONObject contact(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        JSONObject sim = sim(selection);
        JSONObject contact = sim.optJSONObject("contact");
        if (contact == null) {
            contact = new JSONObject().put("damage", 0.0).put("penetration", 0.0);
            sim.put("contact", contact);
        }
        return contact;
    }

    private double contactDamage(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        JSONObject value = sim(selection).optJSONObject("contact");
        return value != null ? value.optDouble("damage", 0.0) : 0.0;
    }

    private double contactPenetration(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        JSONObject value = sim(selection).optJSONObject("contact");
        return value != null ? value.optDouble("penetration", 0.0) : 0.0;
    }

    private static JSONObject mountSim(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        return selection.mount().getJSONObject("sim");
    }

    private static JSONObject mountRender(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        return selection.mount().getJSONObject("render");
    }

    private static JSONObject weaponRender(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        JSONObject render = selection.weapon().optJSONObject("render");
        if (render == null) {
            render = new JSONObject();
            selection.weapon().put("render", render);
        }
        return render;
    }

    private static JSONObject audio(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        JSONObject audio = selection.weapon().optJSONObject("audio");
        if (audio == null) {
            audio = new JSONObject();
            selection.weapon().put("audio", audio);
        }
        return audio;
    }

    private static JSONObject durability(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        return selection.structure().getJSONObject("durability");
    }

    private static JSONObject physics(TurretAuthoringDocument.TurretSelection selection)
            throws Exception {
        return selection.structure().getJSONObject("physics");
    }

    private void showFailure(String title, Exception failure) {
        JOptionPane.showMessageDialog(root,
                failure.getMessage() != null ? failure.getMessage() : failure.toString(),
                title, JOptionPane.ERROR_MESSAGE);
    }

    private static String html(String value) {
        if (value == null) return "Unknown error";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record FieldBinding(String label, JComponent component,
                                Refresh refreshAction, Refresh writeAction) {
        void refresh(TurretAuthoringDocument.TurretSelection selection) throws Exception {
            refreshAction.run(selection);
        }
        void write(TurretAuthoringDocument.TurretSelection selection) throws Exception {
            writeAction.run(selection);
        }
    }

    private final class FxPropertyModel extends AbstractTableModel {
        private List<String> keys = List.of();

        void refresh() {
            JSONObject layer = selectedLayer();
            List<String> next = new ArrayList<>();
            if (layer != null) {
                Iterator<?> iterator = layer.keys();
                while (iterator.hasNext()) next.add(String.valueOf(iterator.next()));
                Collections.sort(next);
            }
            keys = List.copyOf(next);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return keys.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int column) { return column == 0 ? "Property" : "Value"; }
        @Override public boolean isCellEditable(int row, int column) { return column == 1 && !"kind".equals(keys.get(row)); }
        @Override public Object getValueAt(int row, int column) {
            String key = keys.get(row);
            if (column == 0) return key;
            Object value = selectedLayer().opt(key);
            return value != null ? value.toString() : "";
        }
        @Override public void setValueAt(Object value, int row, int column) {
            JSONObject layer = selectedLayer();
            if (layer == null) return;
            String key = keys.get(row);
            Object prior = layer.opt(key);
            mutate(() -> {
                if (prior instanceof String) {
                    layer.put(key, String.valueOf(value).trim());
                } else {
                    Object parsed = new JSONObject("{\"value\":" + value + "}").get("value");
                    layer.put(key, parsed);
                }
            });
            fireTableRowsUpdated(row, row);
        }
    }

    @FunctionalInterface interface Change { void run() throws Exception; }
    @FunctionalInterface private interface Refresh { void run(TurretAuthoringDocument.TurretSelection selection) throws Exception; }
    @FunctionalInterface private interface NumberRead { double get(TurretAuthoringDocument.TurretSelection selection) throws Exception; }
    @FunctionalInterface private interface NumberWrite { void set(TurretAuthoringDocument.TurretSelection selection, double value) throws Exception; }
    @FunctionalInterface private interface TextRead { String get(TurretAuthoringDocument.TurretSelection selection) throws Exception; }
    @FunctionalInterface private interface TextWrite { void set(TurretAuthoringDocument.TurretSelection selection, String value) throws Exception; }
    @FunctionalInterface private interface BooleanRead { boolean get(TurretAuthoringDocument.TurretSelection selection) throws Exception; }
    @FunctionalInterface private interface BooleanWrite { void set(TurretAuthoringDocument.TurretSelection selection, boolean value) throws Exception; }
}
