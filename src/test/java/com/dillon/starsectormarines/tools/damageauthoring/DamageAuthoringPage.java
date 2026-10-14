package com.dillon.starsectormarines.tools.damageauthoring;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Layer;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Mode;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Point;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan.Stroke;
import com.dillon.starsectormarines.battle.world.gen.damage.DamageCatalog;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Damage edits the overlay document, never the pristine map. One drag is one undo step. */
public final class DamageAuthoringPage implements AuthoringPage {
    private final AuthoringPageContext context;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final Canvas canvas = new Canvas();
    private final JComboBox<String> layers = new JComboBox<>();
    private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
    private final JCheckBox enabled = new JCheckBox("Layer enabled"), intact = new JCheckBox("Compare intact base");
    private final JSpinner radius = new JSpinner(new SpinnerNumberModel(2d, .5d, 32d, .5d));
    private final JLabel state = new JLabel(" ");
    private final ArrayDeque<DamagePlan> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final DamagePreview preview;
    private final Timer debounce;
    private DamagePlan plan, saved;
    private Path file;
    private BufferedImage image;
    private int revision, displayed = -1;
    private boolean binding, busy, closed;

    public DamageAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context = context; DiskRegistries.installMapGeneration(context.projectRoot());
        preview = new DamagePreview(context.projectRoot());
        file = context.projectRoot().resolve("mod").resolve(DamageCatalog.RELIEF);
        plan = DamagePlan.read(new JSONObject(Files.readString(file))); saved = plan;
        debounce = new Timer(180, e -> render()); debounce.setRepeats(false);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(toolbar, "Open…", this::open); button(toolbar, "Save", () -> save(false)); button(toolbar, "Save as…", () -> save(true));
        button(toolbar, "Undo", this::undo); button(toolbar, "Redo", this::redo);
        button(toolbar, "Add layer", this::addLayer); button(toolbar, "Remove layer", this::removeLayer);
        toolbar.add(intact); root.add(toolbar, BorderLayout.NORTH);
        JPanel controls = new JPanel(); controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setPreferredSize(new Dimension(280, 600)); controls.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        field(controls, "Relief checkpoint · damage overlay", layers); controls.add(enabled);
        controls.add(Box.createVerticalStrut(20)); field(controls, "Modification brush", mode); field(controls, "Radius · cells", radius);
        JLabel help = new JLabel("<html>Paint damage onto the actual map.<br>Right-drag erases from this layer.<br>Wheel changes brush radius.<br><br>Scars leave cover intact.<br>Damage bends and breaks equipment.<br>Ruin opens walls, fences and roofs.<br><br>Hide or remove a layer to restore<br>what was underneath it.<br>Other enabled layers still apply.<br><br>Each drag is one undo step.<br>Intact comparison never changes<br>the saved layer visibility.<br><br>Preview: 1920 × 1080 / 100%.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT); controls.add(help); controls.add(Box.createVerticalGlue());
        root.add(controls, BorderLayout.WEST); root.add(canvas, BorderLayout.CENTER); root.add(state, BorderLayout.SOUTH);
        layers.setName("damage-layers"); enabled.setName("damage-layer-enabled"); mode.setName("damage-brush"); canvas.setName("damage-canvas");
        layers.addActionListener(e -> { if (!binding) bindEnabled(); });
        enabled.addActionListener(e -> { if (!binding && selected() >= 0) { Layer l = plan.layers().get(selected()); commit(plan.replace(selected(), new Layer(l.name(), enabled.isSelected(), l.strokes()))); } });
        intact.addActionListener(e -> requestRender());
        mode.setSelectedItem(Mode.RUIN); theme(root); bind(); requestRender();
    }
    private static void theme(Component c) {
        c.setBackground(new Color(31, 34, 34)); c.setForeground(new Color(222, 215, 193)); c.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        if (c instanceof Container p) for (Component child : p.getComponents()) theme(child);
    }
    private static void button(JPanel p, String title, Runnable run) { JButton b = new JButton(title); b.addActionListener(e -> run.run()); p.add(b); }
    private static void field(JPanel p, String title, JComponent value) { p.add(new JLabel(title)); value.setAlignmentX(Component.LEFT_ALIGNMENT); value.setMaximumSize(new Dimension(255, 34)); p.add(value); p.add(Box.createVerticalStrut(15)); }
    public JComponent component() { return root; }
    public boolean hasUnsavedChanges() { return !plan.equals(saved); }
    public void close() { closed = true; debounce.stop(); }
    DamagePlan document() { return plan; }
    boolean previewReady() { return !busy && displayed == revision && !debounce.isRunning(); }
    private int selected() { return layers.getSelectedIndex(); }
    private void bind() {
        binding = true; int old = selected(); layers.removeAllItems(); for (Layer layer : plan.layers()) layers.addItem(layer.name());
        if (!plan.layers().isEmpty()) layers.setSelectedIndex(Math.max(0, Math.min(old, plan.layers().size() - 1)));
        binding = false; bindEnabled();
    }
    private void bindEnabled() { binding = true; enabled.setEnabled(selected() >= 0); enabled.setSelected(selected() >= 0 && plan.layers().get(selected()).enabled()); binding = false; }
    void commit(DamagePlan next) { if (next.equals(plan)) return; undo.push(plan); while (undo.size() > 80) undo.removeLast(); redo.clear(); plan = next; bind(); requestRender(); }
    void undo() { if (!undo.isEmpty()) { redo.push(plan); plan = undo.pop(); bind(); requestRender(); } }
    void redo() { if (!redo.isEmpty()) { undo.push(plan); plan = redo.pop(); bind(); requestRender(); } }
    private void addLayer() {
        if (plan.layers().size() >= 16) return;
        String name = JOptionPane.showInputDialog(root, "Layer name", "New damage layer", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        try { List<Layer> next = new ArrayList<>(plan.layers()); next.add(new Layer(name.trim(), true, List.of())); commit(new DamagePlan(plan.width(), plan.height(), plan.seed(), next)); layers.setSelectedIndex(next.size() - 1); }
        catch (Exception ex) { problem(ex); }
    }
    private void removeLayer() { if (selected() < 0) return; List<Layer> next = new ArrayList<>(plan.layers()); next.remove(selected()); commit(new DamagePlan(plan.width(), plan.height(), plan.seed(), next)); }
    private void requestRender() { revision++; context.stateChanged(); canvas.repaint(); debounce.restart(); }
    private void render() {
        if (closed) return; if (busy) { debounce.restart(); return; }
        busy = true; int version = revision; DamagePlan document = plan; boolean base = intact.isSelected(); state.setText("Rebuilding Relief from its intact base…");
        new SwingWorker<BufferedImage, Void>() {
            protected BufferedImage doInBackground() { return preview.render(document, base); }
            protected void done() {
                busy = false; if (closed) return;
                try { BufferedImage result = get(); if (version == revision) { image = result; displayed = version; state.setText(file.getFileName() + (hasUnsavedChanges() ? " · unsaved" : " · saved") + " | " + (base ? "INTACT COMPARISON" : "Enabled damage layers") + " | 1920 × 1080 / 100%"); } }
                catch (Exception ex) { state.setText("Preview failed: " + ex.getMessage()); }
                canvas.repaint(); if (version != revision) debounce.restart();
            }
        }.execute();
    }
    void openDocument(Path source) throws Exception {
        DamagePlan loaded = DamagePlan.read(new JSONObject(Files.readString(source))); DamageCatalog.validate(loaded);
        plan = loaded; saved = loaded; file = source; undo.clear(); redo.clear(); bind(); requestRender();
    }
    void saveDocument(Path target) throws Exception {
        DamageCatalog.validate(plan);
        Path absolute = target.toAbsolutePath().normalize(), temp = Files.createTempFile(absolute.getParent(), "damage-", ".tmp");
        try { Files.writeString(temp, plan.json().toString(2) + "\n");
            try { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        file = absolute; saved = plan; context.reportStatus("Saved damage layers: " + file); requestRender();
    }
    private JFileChooser chooser() { return new JFileChooser(context.projectRoot().resolve("mod/data/world/damage").toFile()); }
    private void problem(Exception ex) { JOptionPane.showMessageDialog(root, ex.getMessage(), "Map damage", JOptionPane.ERROR_MESSAGE); }
    private void open() {
        if (hasUnsavedChanges() && JOptionPane.showConfirmDialog(root, "Discard unsaved damage edits?", "Map damage", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        var c = chooser(); if (c.showOpenDialog(root) == JFileChooser.APPROVE_OPTION) try { openDocument(c.getSelectedFile().toPath()); } catch (Exception ex) { problem(ex); }
    }
    private void save(boolean as) {
        Path target = file;
        if (as) { var c = chooser(); if (c.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return; target = c.getSelectedFile().toPath();
            if (!target.toString().endsWith(".json")) target = Path.of(target + ".damage.json");
            if (Files.exists(target) && JOptionPane.showConfirmDialog(root, "Replace " + target.getFileName() + "?", "Map damage", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        }
        try { saveDocument(target); } catch (Exception ex) { problem(ex); }
    }
    private final class Canvas extends JPanel {
        private List<Point> stroke;
        private Mode strokeMode;
        private double strokeRadius;
        private int strokeLayer, mx = -1, my;
        Canvas() {
            MouseAdapter mouse = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (!insideMap(e.getX(), e.getY()) || selected() < 0 || intact.isSelected() || !plan.layers().get(selected()).enabled()) return;
                    stroke = new ArrayList<>(); strokeLayer = selected(); strokeMode = SwingUtilities.isRightMouseButton(e) ? Mode.ERASE : (Mode)mode.getSelectedItem(); strokeRadius = ((Number)radius.getValue()).doubleValue(); add(e);
                }
                public void mouseDragged(MouseEvent e) { add(e); }
                public void mouseMoved(MouseEvent e) { mx = e.getX(); my = e.getY(); repaint(); }
                public void mouseExited(MouseEvent e) { mx = -1; repaint(); }
                public void mouseReleased(MouseEvent e) {
                    if (stroke == null) return; add(e);
                    try { commit(plan.replace(strokeLayer, plan.layers().get(strokeLayer).append(new Stroke(strokeMode, strokeRadius, stroke)))); } catch (Exception ex) { problem(ex); }
                    stroke = null; repaint();
                }
                public void mouseWheelMoved(MouseWheelEvent e) { radius.setValue(Math.max(.5, Math.min(32, ((Number)radius.getValue()).doubleValue() - e.getPreciseWheelRotation() * .5))); }
            }; addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
        }
        private void add(MouseEvent e) { mx = e.getX(); my = e.getY(); if (stroke == null || !insideMap(mx, my)) return; Point p = world(mx, my); if (stroke.size() < 4096 && (stroke.isEmpty() || Math.hypot(p.x() - stroke.get(stroke.size() - 1).x(), p.y() - stroke.get(stroke.size() - 1).y()) > .3)) stroke.add(p); repaint(); }
        double scale() { return Math.min(getWidth() / (double)plan.width(), getHeight() / (double)plan.height()); }
        double ox() { return (getWidth() - plan.width() * scale()) / 2; }
        double oy() { return (getHeight() - plan.height() * scale()) / 2; }
        boolean insideMap(int x, int y) { return x >= ox() && x < getWidth() - ox() && y >= oy() && y < getHeight() - oy(); }
        Point world(int x, int y) { return new Point(Math.max(0, Math.min(plan.width() - 1, (x - ox()) / scale() - .5)), Math.max(0, Math.min(plan.height() - 1, (getHeight() - y - oy()) / scale() - .5))); }
        int sx(double x) { return (int)Math.round(ox() + (x + .5) * scale()); }
        int sy(double y) { return (int)Math.round(getHeight() - oy() - (y + .5) * scale()); }
        protected void paintComponent(Graphics raw) {
            super.paintComponent(raw); Graphics2D g = (Graphics2D)raw.create();
            if (image != null) { double ratio = scale() / Math.min(1920d / plan.width(), 1080d / plan.height()); int w = (int)Math.round(1920 * ratio), h = (int)Math.round(1080 * ratio); g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null); }
            if (stroke != null) { g.setColor(new Color(235, 150, 80, 110)); g.setStroke(new BasicStroke((float)(strokeRadius * 2 * scale()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); for (int i = 0; i < stroke.size(); i++) { Point a = stroke.get(Math.max(0, i - 1)), b = stroke.get(i); g.drawLine(sx(a.x()), sy(a.y()), sx(b.x()), sy(b.y())); } }
            if (mx >= 0 && insideMap(mx, my)) { int r = (int)Math.round(((Number)radius.getValue()).doubleValue() * scale()); g.setStroke(new BasicStroke(1)); g.setColor(new Color(248, 203, 114)); g.drawOval(mx - r, my - r, r * 2, r * 2); }
            if (displayed != revision) { g.setColor(new Color(20, 23, 23, 235)); g.fillRect(12, 12, 240, 36); g.setColor(Color.WHITE); g.drawString("Updating damage preview…", 24, 35); }
            g.dispose();
        }
    }
}
