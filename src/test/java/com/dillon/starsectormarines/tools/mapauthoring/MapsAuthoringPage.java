package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.layout.StructureKind;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box.Filler;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Maps page: a story map's one owned seed ({@code story-map-pools.md}), opened from its pool, generated once on its
 * home biome with its patch and drawn by the battle renderer as it will play. The world switch dresses that same map
 * as each planet type, and the all-worlds sheet shows them side by side. It edits the entry's small authored patch on
 * the picture: pins (a plan place moved before the map grows round it), ruin strokes (applied at battle assembly) and
 * layout structures placed by name. Every edit is one undo step and regenerates the map; the mission's own acceptance
 * runs with every redraw, and a save is refused while it fails. Save rewrites only that entry in the pool file.
 */
public final class MapsAuthoringPage implements AuthoringPage {

    /** What a click on the map does. */
    enum Tool {
        SELECT("Select / move"), PIN("Pin a place"), RUIN("Ruin stroke"), PLACE("Place a structure");
        final String label;
        Tool(String label) { this.label = label; }
    }

    private final AuthoringPageContext context;
    private final StoryMapsSession session;
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final Canvas canvas = new Canvas();
    private final JComboBox<String> mapBox = new JComboBox<>();
    private final JComboBox<MapWorld> worldBox = new JComboBox<>(MapWorld.values());
    private final ButtonGroup toolGroup = new ButtonGroup();
    private final JComboBox<String> placeBox = new JComboBox<>();
    private final JSpinner radius = new JSpinner(new SpinnerNumberModel(2d, .5d, 16d, .5d));
    private final JComboBox<DamagePlan.Mode> mode = new JComboBox<>(new DamagePlan.Mode[]{
            DamagePlan.Mode.RUIN, DamagePlan.Mode.BATTERED, DamagePlan.Mode.SCAR});
    private final JComboBox<String> structureBox = new JComboBox<>();
    private final JComboBox<StructureKind> kindBox = new JComboBox<>(StructureKind.values());
    private final JSpinner turns = new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));
    private final DefaultListModel<MapsRender.Edit> editsModel = new DefaultListModel<>();
    private final JList<MapsRender.Edit> edits = new JList<>(editsModel);
    private final DefaultListModel<String> checksModel = new DefaultListModel<>();
    private final JLabel entryLabel = new JLabel(" ");
    private final JLabel state = new JLabel(" ");
    private final ArrayDeque<MapPatch> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Path saveRoot;
    private StoryMapsSession.Opened opened;
    private MapPatch patch, saved;
    private Tool tool = Tool.SELECT;
    private String selected;
    private MapsRender.Frame frame;
    private StoryMapsSession.Stood stood;
    private List<String> problems = List.of();
    private int revision, displayed = -1;
    private boolean busy, closed, binding;

    public MapsAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context = context;
        saveRoot = context.projectRoot();
        session = new StoryMapsSession(context.projectRoot());
        for (String id : StoryMapsSession.maps()) mapBox.addItem(id);
        open(StoryMapsSession.maps().get(0));
        worldBox.setSelectedItem(MapWorld.ARID);
        structureBox.setEditable(true);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.add(new JLabel("Story map")); toolbar.add(mapBox);
        toolbar.add(new JLabel("  World")); toolbar.add(worldBox);
        button(toolbar, "All worlds sheet…", this::allWorlds);
        button(toolbar, "Undo", this::undo); button(toolbar, "Redo", this::redo);
        button(toolbar, "Save", this::save);
        toolbar.add(entryLabel);
        root.add(toolbar, BorderLayout.NORTH);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(300, 900));
        side.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JPanel tools = new JPanel(new GridLayout(0, 2, 4, 4));
        for (Tool t : Tool.values()) {
            JToggleButton b = new JToggleButton(t.label, t == tool);
            b.setName("tool-" + t.name().toLowerCase(Locale.ROOT));
            b.addActionListener(e -> { tool = t; canvas.cancel(); describe(); });
            b.addItemListener(e -> styleTool(b));
            toolGroup.add(b);
            tools.add(b);
        }
        tools.setAlignmentX(Component.LEFT_ALIGNMENT);
        tools.setMaximumSize(new Dimension(280, 70));
        side.add(tools);
        side.add(gap(10));
        field(side, "Pin · the plan place it moves", placeBox);
        field(side, "Ruin · radius in cells", radius);
        field(side, "Ruin · mode", mode);
        field(side, "Place · structure name (layout's)", structureBox);
        field(side, "Place · kind", kindBox);
        field(side, "Place · quarter turns", turns);
        JLabel editsTitle = new JLabel("Patch");
        editsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(editsTitle);
        JScrollPane editsScroll = new JScrollPane(edits);
        editsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        editsScroll.setMaximumSize(new Dimension(280, 180));
        side.add(editsScroll);
        JPanel editButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        editButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        button(editButtons, "Delete", this::deleteSelected);
        side.add(editButtons);
        JLabel help = new JLabel("<html>A pin moves a plan place before<br>the map grows round it. A ruin<br>"
                + "stroke breaches what it covers when<br>the battle stands up; it cannot make<br>a failing seed pass. "
                + "A placement<br>puts a layout structure by name<br>at its footprint corner.<br>"
                + "Drag a pin, stroke or placement<br>to move it.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(help);
        side.add(new Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, Short.MAX_VALUE)));
        root.add(side, BorderLayout.WEST);

        JPanel centre = new JPanel(new BorderLayout());
        centre.add(canvas, BorderLayout.CENTER);
        JList<String> checks = new JList<>(checksModel);
        checks.setName("maps-checks");
        JScrollPane checksScroll = new JScrollPane(checks);
        checksScroll.setPreferredSize(new Dimension(800, 72));
        centre.add(checksScroll, BorderLayout.SOUTH);
        root.add(centre, BorderLayout.CENTER);
        root.add(state, BorderLayout.SOUTH);

        canvas.setName("maps-canvas"); mapBox.setName("maps-map"); worldBox.setName("maps-world"); edits.setName("maps-edits");
        mapBox.addActionListener(e -> { if (!binding) openMap((String) mapBox.getSelectedItem()); });
        worldBox.addActionListener(e -> { if (!binding) requestRender(false); });
        edits.addListSelectionListener(e -> { if (!binding && edits.getSelectedValue() != null) { selected = edits.getSelectedValue().id(); canvas.repaint(); } });
        structureBox.addActionListener(e -> {
            String name = (String) structureBox.getSelectedItem();
            for (Structure s : patch.place()) if (s.name().equals(name)) { kindBox.setSelectedItem(s.kind()); turns.setValue(s.turns()); }
        });
        canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DELETE"), "delete");
        canvas.getActionMap().put("delete", action(this::deleteSelected));
        theme(root);
        for (var e = toolGroup.getElements(); e.hasMoreElements(); ) styleTool((JToggleButton) e.nextElement());
        bindEdits();
        describe();
        requestRender(true);
    }

    // ------------------------------------------------------------------ page

    @Override public JComponent component() { return root; }
    @Override public boolean hasUnsavedChanges() { return !patch.equals(saved); }
    @Override public void close() { closed = true; session.close(); }

    MapPatch patch() { return patch; }
    StoryMapsSession.Opened opened() { return opened; }
    StoryMapPool.Entry entry() { return StoryMapsSession.withPatch(opened.entry(), patch); }
    MapWorld world() { return (MapWorld) worldBox.getSelectedItem(); }
    List<String> problems() { return problems; }
    StoryMapsSession.Stood stood() { return stood; }
    boolean previewReady() { return !busy && displayed == revision; }
    Canvas canvas() { return canvas; }
    String status() { return state.getText(); }
    void saveInto(Path root) { saveRoot = root; }
    void selectEdit(String id) { selected = id; canvas.repaint(); }
    void selectWorld(MapWorld w) { worldBox.setSelectedItem(w); }
    void selectMap(String id) { mapBox.setSelectedItem(id); }
    void setPin(String place) { placeBox.setSelectedItem(place); }
    void setRuin(double r, DamagePlan.Mode m) { radius.setValue(r); mode.setSelectedItem(m); }
    void setStructure(String name, StructureKind kind, int quarterTurns) {
        structureBox.setSelectedItem(name); kindBox.setSelectedItem(kind); turns.setValue(quarterTurns);
    }

    void selectTool(Tool t) {
        for (var e = toolGroup.getElements(); e.hasMoreElements(); ) {
            var b = e.nextElement();
            if (("tool-" + t.name().toLowerCase(Locale.ROOT)).equals(b.getName())) b.setSelected(true);
        }
        tool = t;
        canvas.cancel();
        describe();
    }

    void commit(MapPatch next) {
        if (next.equals(patch)) return;
        undo.push(patch);
        while (undo.size() > 100) undo.removeLast();
        redo.clear();
        patch = next;
        bindEdits();
        requestRender(true);
    }

    void undo() { if (!undo.isEmpty()) { redo.push(patch); patch = undo.pop(); bindEdits(); requestRender(true); } }
    void redo() { if (!redo.isEmpty()) { undo.push(patch); patch = redo.pop(); bindEdits(); requestRender(true); } }

    private void open(String id) throws Exception {
        opened = session.open(id);
        patch = saved = opened.entry().patch();
        undo.clear(); redo.clear();
        selected = null;
        entryLabel.setText("   " + opened.map().id + " · seed " + opened.entry().seed() + " on "
                + StoryMapsSession.HOME.name().toLowerCase(Locale.ROOT) + (opened.entry().story() ? " (story map)" : "")
                + (opened.entry().note().isEmpty() ? "" : " · " + opened.entry().note()));
    }

    private void openMap(String id) {
        if (hasUnsavedChanges() && JOptionPane.showConfirmDialog(root, "Discard the patch edits on " + opened.map().id + "?", "Maps",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            binding = true; mapBox.setSelectedItem(opened.map().id); binding = false;
            return;
        }
        try {
            open(id);
        } catch (Exception e) {
            state.setText("Cannot open " + id + ": " + e.getMessage());
            return;
        }
        bindEdits();
        requestRender(true);
    }

    void deleteSelected() {
        if (selected == null) return;
        String id = selected;
        List<MapPatch.Pin> pins = new ArrayList<>(patch.pins());
        List<DamagePlan.Stroke> ruin = new ArrayList<>(patch.ruin());
        List<Structure> place = new ArrayList<>(patch.place());
        pins.removeIf(p -> MapsRender.Edit.pin(p).id().equals(id));
        if (id.startsWith("ruin:")) ruin.remove(Integer.parseInt(id.substring(5)));
        place.removeIf(s -> MapsRender.Edit.place(s).id().equals(id));
        selected = null;
        commit(with(pins, ruin, place));
    }

    private MapPatch with(List<MapPatch.Pin> pins, List<DamagePlan.Stroke> ruin, List<Structure> place) {
        return new MapPatch(pins, ruin, patch.lift(), patch.bores(), patch.holdouts(), place);
    }

    /** Saves the entry into its pool file once the mission's acceptance passes; off the event thread. */
    void save() {
        if (busy) { state.setText("Wait for the redraw to finish."); return; }
        busy = true;
        StoryMapsSession.Opened o = opened;
        StoryMapPool.Entry e = entry();
        MapPatch p = patch;
        Path into = saveRoot;
        state.setText("Checking " + o.map().id + " seed " + e.seed() + " before saving…");
        new SwingWorker<Path, Void>() {
            protected Path doInBackground() throws Exception { return session.save(o, e, into); }
            protected void done() {
                busy = false;
                try {
                    Path written = get();
                    saved = p;
                    context.reportStatus("Saved " + written);
                    state.setText("Saved " + written.getFileName() + " · the mission's acceptance passes");
                    context.stateChanged();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    state.setText(cause.getMessage());
                }
                if (displayed != revision) render();
            }
        }.execute();
    }

    void allWorlds() {
        if (busy) { state.setText("Wait for the redraw to finish."); return; }
        busy = true;
        state.setText("Dressing " + opened.map().id + " as every world…");
        StoryMapsSession.Opened o = opened;
        StoryMapPool.Entry e = entry();
        new SwingWorker<Path, Void>() {
            protected Path doInBackground() throws Exception {
                BufferedImage sheet = session.contactSheet(o.map(), e);
                Path dir = context.projectRoot().resolve("build/map-authoring");
                Files.createDirectories(dir);
                Path out = dir.resolve(o.map().id + "-all-worlds.png");
                ImageIO.write(sheet, "png", out.toFile());
                return out;
            }
            protected void done() {
                busy = false;
                try { state.setText("All-worlds sheet: " + get()); } catch (Exception ex) { state.setText("Sheet failed: " + ex.getMessage()); }
                if (displayed != revision) render();
            }
        }.execute();
    }

    private boolean recheck;

    /** Redraws; {@code entryChanged} also reruns the mission's acceptance (a world switch only re-dresses). */
    private void requestRender(boolean entryChanged) {
        revision++;
        recheck |= entryChanged;
        context.stateChanged();
        canvas.repaint();
        render();
    }

    private void render() {
        if (closed || busy) return;
        busy = true;
        int version = revision;
        boolean check = recheck;
        recheck = false;
        StoryMapsSession.Opened o = opened;
        StoryMapPool.Entry e = entry();
        MapWorld world = world();
        state.setText("Generating " + o.map().id + " seed " + e.seed() + " as " + world.label() + "…");
        new SwingWorker<Object[], Void>() {
            protected Object[] doInBackground() throws Exception {
                StoryMapsSession.Stood s = session.stand(o.map(), e);
                MapsRender.Frame f = session.frame(world, MapsRender.WIDTH, MapsRender.HEIGHT);
                List<String> found = check ? session.check(o.map(), e) : null;
                return new Object[]{s, f, found};
            }
            @SuppressWarnings("unchecked")
            protected void done() {
                busy = false;
                if (closed) return;
                try {
                    Object[] r = get();
                    stood = (StoryMapsSession.Stood) r[0];
                    frame = (MapsRender.Frame) r[1];
                    if (r[2] != null) problems = (List<String>) r[2];
                    if (version == revision) displayed = version;
                    bindChoices();
                    checksModel.clear();
                    if (problems.isEmpty()) checksModel.addElement("✓ The mission's acceptance passes on " + StoryMapsSession.HOME.name().toLowerCase(Locale.ROOT)
                            + " (the storyMapPools check): the setup seats, placed structures stand where put, nothing built on a way.");
                    for (String p : problems) checksModel.addElement("✗ " + p);
                    state.setText(o.map().id + " seed " + e.seed() + " as " + world.label() + " · "
                            + (hasUnsavedChanges() ? "unsaved" : "saved") + " · 1920 × 1080 / 100%");
                } catch (Exception ex) {
                    recheck |= check;
                    state.setText("Redraw failed: " + ex.getMessage());
                }
                canvas.repaint();
                if (displayed != revision) render();
            }
        }.execute();
    }

    /** The places and structures the standing map offers to pin and to place. */
    private void bindChoices() {
        if (stood == null) return;
        binding = true;
        Object place = placeBox.getSelectedItem(), structure = structureBox.getSelectedItem();
        placeBox.removeAllItems();
        for (StoryMapsSession.Place p : stood.places()) placeBox.addItem(p.name());
        if (place != null) placeBox.setSelectedItem(place);
        structureBox.removeAllItems();
        List<String> names = new ArrayList<>();
        for (Structure s : patch.place()) names.add(s.name());
        for (StoryMapsSession.LaidStructure s : stood.structures()) if (!names.contains(s.name())) names.add(s.name());
        for (String n : names) structureBox.addItem(n);
        if (structure != null) structureBox.setSelectedItem(structure);
        binding = false;
    }

    private void bindEdits() {
        binding = true;
        editsModel.clear();
        for (MapsRender.Edit e : MapsRender.Edit.of(patch)) editsModel.addElement(e);
        binding = false;
    }

    private void describe() {
        state.setText(switch (tool) {
            case SELECT -> "Select: click a pin, stroke or placement; drag it to move it.";
            case PIN -> "Pin: click where the place chosen at the left should grow from.";
            case RUIN -> "Ruin: drag along what should be breached; the stroke is as wide as set at the left.";
            case PLACE -> "Place: click the structure's footprint corner; its name is the layout's.";
        });
    }

    // ------------------------------------------------------------------ view helpers

    private static void theme(Component c) {
        c.setBackground(new Color(31, 34, 34));
        c.setForeground(new Color(222, 215, 193));
        c.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        if (c instanceof Container p) for (Component child : p.getComponents()) theme(child);
    }

    /** A chosen tool reads dark on light; the others as the page. */
    private static void styleTool(JToggleButton b) {
        b.setForeground(b.isSelected() ? new Color(24, 26, 26) : new Color(222, 215, 193));
        b.setFont(b.getFont().deriveFont(b.isSelected() ? Font.BOLD : Font.PLAIN));
    }

    private static Component gap(int h) {
        return new Filler(new Dimension(0, h), new Dimension(0, h), new Dimension(0, h));
    }

    private static void button(JPanel p, String title, Runnable run) {
        JButton b = new JButton(title);
        b.addActionListener(e -> run.run());
        p.add(b);
    }

    private static void field(JPanel p, String title, JComponent value) {
        JLabel l = new JLabel(title);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setMaximumSize(new Dimension(280, 30));
        p.add(value);
        p.add(gap(8));
    }

    private static AbstractAction action(Runnable run) {
        return new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { run.run(); } };
    }

    // ------------------------------------------------------------------ canvas

    /** The map as it plays, fitted to the panel, with the patch over it; clicks become cells through the frame. */
    final class Canvas extends JPanel {
        private final List<DamagePlan.Point> stroke = new ArrayList<>();
        private int[] hover, dragFrom;
        private String dragId;
        private MapPatch preview;

        Canvas() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { press(e); }
                @Override public void mouseDragged(MouseEvent e) { drag(e); }
                @Override public void mouseReleased(MouseEvent e) { release(e); }
                @Override public void mouseMoved(MouseEvent e) { hover = cell(e); repaint(); }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }

        /** The map's rectangle on the frame with a margin, in frame pixels: x, y, width, height. */
        private double[] view() {
            if (frame == null) return new double[]{0, 0, MapsRender.WIDTH, MapsRender.HEIGHT};
            double m = 3 * frame.cellPx();
            double x0 = frame.sx(0) - m, y0 = frame.sy(frame.mapH()) - m;
            return new double[]{x0, y0, frame.sx(frame.mapW()) + m - x0, frame.sy(0) + m - y0};
        }
        double scale() { double[] v = view(); return Math.min(getWidth() / v[2], getHeight() / v[3]); }
        double ox() { double[] v = view(); return (getWidth() - v[2] * scale()) / 2 - v[0] * scale(); }
        double oy() { double[] v = view(); return (getHeight() - v[3] * scale()) / 2 - v[1] * scale(); }

        /** Where cell {@code x, y} lies on the canvas. */
        Point at(int x, int y) {
            return new Point((int) Math.round(ox() + frame.sx(x + .5f) * scale()), (int) Math.round(oy() + frame.sy(y + .5f) * scale()));
        }

        int[] cell(MouseEvent e) {
            if (frame == null) return null;
            float fx = (float) ((e.getX() - ox()) / scale()), fy = (float) ((e.getY() - oy()) / scale());
            int x = (int) Math.floor(frame.cellX(fx)), y = (int) Math.floor(frame.cellY(fy));
            if (x < 0 || y < 0 || x >= frame.mapW() || y >= frame.mapH()) return null;
            return new int[]{x, y};
        }

        void press(MouseEvent e) {
            requestFocusInWindow();
            int[] c = cell(e);
            if (c == null) return;
            if (SwingUtilities.isRightMouseButton(e)) { cancel(); return; }
            switch (tool) {
                case PIN -> {
                    String place = (String) placeBox.getSelectedItem();
                    if (place == null) { state.setText("This map has no plan place to pin."); return; }
                    List<MapPatch.Pin> pins = new ArrayList<>(patch.pins());
                    pins.removeIf(p -> p.place().equals(place));
                    MapPatch.Pin pin = new MapPatch.Pin(place, c[0], c[1]);
                    pins.add(pin);
                    commit(with(pins, patch.ruin(), patch.place()));
                    selected = MapsRender.Edit.pin(pin).id();
                }
                case RUIN -> { stroke.clear(); stroke.add(new DamagePlan.Point(c[0], c[1])); }
                case PLACE -> {
                    String name = (String) structureBox.getSelectedItem();
                    if (name == null || name.isBlank()) { state.setText("Name the layout structure to place."); return; }
                    List<Structure> place = new ArrayList<>(patch.place());
                    place.removeIf(s -> s.name().equals(name.trim()));
                    Structure s = new Structure(name.trim(), (StructureKind) kindBox.getSelectedItem(), c[0], c[1],
                            ((Number) turns.getValue()).intValue(), true);
                    place.add(s);
                    commit(with(patch.pins(), patch.ruin(), place));
                    selected = MapsRender.Edit.place(s).id();
                }
                case SELECT -> grab(c);
            }
            repaint();
        }

        void drag(MouseEvent e) {
            int[] c = cell(e);
            hover = c;
            if (c != null && tool == Tool.RUIN && !stroke.isEmpty()) {
                DamagePlan.Point last = stroke.get(stroke.size() - 1);
                if (Math.hypot(last.x() - c[0], last.y() - c[1]) >= 2) stroke.add(new DamagePlan.Point(c[0], c[1]));
            }
            if (c != null && tool == Tool.SELECT && dragId != null) preview = moved(c);
            repaint();
        }

        void release(MouseEvent e) {
            int[] c = cell(e);
            if (tool == Tool.RUIN && !stroke.isEmpty()) {
                if (c != null) {
                    DamagePlan.Point last = stroke.get(stroke.size() - 1);
                    if (last.x() != c[0] || last.y() != c[1]) stroke.add(new DamagePlan.Point(c[0], c[1]));
                }
                List<DamagePlan.Stroke> ruin = new ArrayList<>(patch.ruin());
                ruin.add(new DamagePlan.Stroke((DamagePlan.Mode) mode.getSelectedItem(), ((Number) radius.getValue()).doubleValue(), stroke));
                stroke.clear();
                commit(with(patch.pins(), ruin, patch.place()));
                selected = "ruin:" + (ruin.size() - 1);
            }
            if (tool == Tool.SELECT && dragId != null && c != null && (c[0] != dragFrom[0] || c[1] != dragFrom[1])) {
                MapPatch next = moved(c);
                preview = null;
                commit(next);
            }
            preview = null;
            dragId = null;
            repaint();
        }

        void cancel() { stroke.clear(); dragId = null; preview = null; repaint(); }

        /** Picks the pin, placement or ruin stroke nearest {@code c}, ready to drag. */
        private void grab(int[] c) {
            dragId = null;
            double best = 4;
            for (MapPatch.Pin p : patch.pins()) {
                double d = Math.hypot(p.x() - c[0], p.y() - c[1]);
                if (d < best) { best = d; dragId = MapsRender.Edit.pin(p).id(); }
            }
            for (Structure s : patch.place()) {
                double d = Math.hypot(s.x() - c[0], s.y() - c[1]);
                if (d < best) { best = d; dragId = MapsRender.Edit.place(s).id(); }
            }
            for (int k = 0; k < patch.ruin().size(); k++) {
                for (DamagePlan.Point p : patch.ruin().get(k).points()) {
                    double d = Math.hypot(p.x() - c[0], p.y() - c[1]);
                    if (d < best) { best = d; dragId = "ruin:" + k; }
                }
            }
            if (dragId != null) selected = dragId;
            dragFrom = c;
        }

        /** What was grabbed, moved to {@code c}: a pin or placement to the cell, a stroke by the drag. */
        private MapPatch moved(int[] c) {
            String id = dragId;
            int dx = c[0] - dragFrom[0], dy = c[1] - dragFrom[1];
            List<MapPatch.Pin> pins = new ArrayList<>(patch.pins());
            pins.replaceAll(p -> MapsRender.Edit.pin(p).id().equals(id) ? new MapPatch.Pin(p.place(), c[0], c[1]) : p);
            List<Structure> place = new ArrayList<>(patch.place());
            place.replaceAll(s -> MapsRender.Edit.place(s).id().equals(id)
                    ? new Structure(s.name(), s.kind(), c[0], c[1], s.turns(), s.required()) : s);
            List<DamagePlan.Stroke> ruin = new ArrayList<>(patch.ruin());
            if (id.startsWith("ruin:")) {
                int k = Integer.parseInt(id.substring(5));
                DamagePlan.Stroke s = ruin.get(k);
                ruin.set(k, new DamagePlan.Stroke(s.mode(), s.radius(),
                        s.points().stream().map(p -> new DamagePlan.Point(p.x() + dx, p.y() + dy)).toList()));
            }
            return with(pins, ruin, place);
        }

        @Override
        protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g = (Graphics2D) raw.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform before = g.getTransform();
            g.translate(ox(), oy());
            g.scale(scale(), scale());
            if (frame != null) {
                g.drawImage(frame.image(), 0, 0, null);
                MapsRender.mark(g, frame, stood, preview != null ? preview : patch, selected);
                if (stroke.size() > 0) {
                    g.setColor(new Color(255, 96, 70, 110));
                    g.setStroke(new BasicStroke(Math.max(4f, (float) (((Number) radius.getValue()).doubleValue() * 2 * frame.cellPx())),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (int k = 1; k < stroke.size(); k++) {
                        g.drawLine(Math.round(frame.sx((float) stroke.get(k - 1).x() + .5f)), Math.round(frame.sy((float) stroke.get(k - 1).y() + .5f)),
                                Math.round(frame.sx((float) stroke.get(k).x() + .5f)), Math.round(frame.sy((float) stroke.get(k).y() + .5f)));
                    }
                }
            } else if (stood != null && stood.failure() != null) {
                g.setTransform(before);
                g.setColor(new Color(255, 120, 100));
                g.drawString("The map was refused: " + stood.failure(), 24, 60);
            }
            g.setTransform(before);
            if (hover != null) {
                g.setColor(new Color(20, 23, 23, 220));
                g.fillRect(getWidth() - 150, 8, 140, 26);
                g.setColor(Color.WHITE);
                g.drawString("cell " + hover[0] + ", " + hover[1], getWidth() - 140, 26);
            }
            if (displayed != revision) {
                g.setColor(new Color(20, 23, 23, 235));
                g.fillRect(12, 12, 360, 34);
                g.setColor(Color.WHITE);
                g.drawString("Generating the map as the battle plays it…", 24, 34);
            }
            g.dispose();
        }
    }
}
