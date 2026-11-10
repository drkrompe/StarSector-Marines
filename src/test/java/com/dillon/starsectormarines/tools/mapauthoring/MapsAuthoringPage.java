package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Crossing;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.CrossingKind;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Drop;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Place;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Program;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Pt;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Rise;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Road;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Role;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Settlement;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.AbstractAction;
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
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Maps page: the editor for owned mission maps. It opens an existing map, never an empty grid, drawn by the battle
 * renderer as it will play, and edits it on its own picture: natural drops with their depth, ledges and crossings,
 * rises with their height and terraces, roads, named places and settlements by program. A world switch redraws the same
 * layout as every world, and the all-worlds sheet shows them side by side with each world's drop and town adaptation.
 * Every edit is one undo step; the checks run with every redraw and a save is refused while one fails.
 */
public final class MapsAuthoringPage implements AuthoringPage {

    /** What a click on the map does. */
    enum Tool {
        SELECT("Select / move"), DROP("Drop"), CROSSING("Crossing"), RISE("Rise"), ROAD("Road"), PLACE("Place"),
        SETTLEMENT("Settlement");
        final String label;
        Tool(String label) { this.label = label; }
    }

    private final AuthoringPageContext context;
    private final MapsSession session;
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final Canvas canvas = new Canvas();
    private final JComboBox<String> mapBox = new JComboBox<>();
    private final JComboBox<BiomeSkin> worldBox = new JComboBox<>(BiomeSkin.values());
    private final ButtonGroup toolGroup = new ButtonGroup();
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(10d, 2d, 60d, 1d));
    private final JSpinner metres = new JSpinner(new SpinnerNumberModel(40d, 1d, 200d, 1d));
    private final JSpinner terraces = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
    private final JComboBox<CrossingKind> crossingKind = new JComboBox<>(CrossingKind.values());
    private final JComboBox<RoadPlan.Material> material = new JComboBox<>(RoadPlan.Material.values());
    private final JComboBox<Role> role = new JComboBox<>(Role.values());
    private final JComboBox<Program> program = new JComboBox<>(Program.values());
    private final JTextField label = new JTextField("New site");
    private final DefaultListModel<String> editsModel = new DefaultListModel<>();
    private final JList<String> edits = new JList<>(editsModel);
    private final DefaultListModel<String> checksModel = new DefaultListModel<>();
    private final JLabel state = new JLabel(" ");
    private final ArrayDeque<MapSketch> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final List<MapBases.Base> bases = MapBases.all();
    private MapBases.Base base;
    private MapSketch sketch, saved;
    private Tool tool = Tool.SELECT;
    private String selected;
    private MapsRender.Frame frame;
    private List<String> problems = List.of();
    private int revision, displayed = -1;
    private boolean busy, closed, binding;

    public MapsAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context = context;
        session = new MapsSession(context.projectRoot());
        base = bases.get(bases.size() > 2 ? 2 : 0);
        sketch = base.sketch();
        saved = sketch;
        for (MapBases.Base b : bases) mapBox.addItem(b.label());
        mapBox.setSelectedIndex(bases.indexOf(base));
        worldBox.setSelectedItem(BiomeSkin.of(base.builtAs()));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.add(new JLabel("Map")); toolbar.add(mapBox);
        toolbar.add(new JLabel("  World")); toolbar.add(worldBox);
        button(toolbar, "All worlds sheet…", this::allWorlds);
        button(toolbar, "Bridge the roads it cuts", this::bridgeRoads);
        button(toolbar, "Undo", this::undo); button(toolbar, "Redo", this::redo);
        button(toolbar, "Save", this::save);
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
            toolGroup.add(b); tools.add(b);
        }
        tools.setAlignmentX(Component.LEFT_ALIGNMENT);
        tools.setMaximumSize(new Dimension(280, 140));
        side.add(tools);
        side.add(Box.createVerticalStrut(10));
        field(side, "Width · cells", width);
        field(side, "Depth / height at next point · m", metres);
        field(side, "Ledges or terraces", terraces);
        field(side, "Crossing", crossingKind);
        field(side, "Road surface", material);
        field(side, "Place role", role);
        field(side, "Place name or town role", label);
        field(side, "Settlement program", program);
        JLabel editsTitle = new JLabel("Edits");
        editsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(editsTitle);
        JScrollPane editsScroll = new JScrollPane(edits);
        editsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        editsScroll.setMaximumSize(new Dimension(280, 170));
        side.add(editsScroll);
        JPanel editButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        editButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        button(editButtons, "Delete", this::deleteSelected);
        side.add(editButtons);
        JLabel help = new JLabel("<html>Click to add a path's points;<br>double-click or Enter ends it, Esc drops it.<br>"
                + "Wheel over a point sets its depth or height.<br>Drag a point, place or settlement to move it.<br>"
                + "A settlement is dragged out as a box:<br>where it stands and what it is, never its style.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(help);
        side.add(Box.createVerticalGlue());
        root.add(side, BorderLayout.WEST);

        JPanel centre = new JPanel(new BorderLayout());
        centre.add(canvas, BorderLayout.CENTER);
        JList<String> checks = new JList<>(checksModel);
        checks.setName("maps-checks");
        JScrollPane checksScroll = new JScrollPane(checks);
        checksScroll.setPreferredSize(new Dimension(800, 92));
        centre.add(checksScroll, BorderLayout.SOUTH);
        root.add(centre, BorderLayout.CENTER);
        root.add(state, BorderLayout.SOUTH);

        canvas.setName("maps-canvas"); mapBox.setName("maps-map"); worldBox.setName("maps-world"); edits.setName("maps-edits");
        mapBox.addActionListener(e -> { if (!binding) openBase(bases.get(mapBox.getSelectedIndex())); });
        worldBox.addActionListener(e -> { if (!binding) requestRender(); });
        edits.addListSelectionListener(e -> { if (!binding && edits.getSelectedValue() != null) { selected = idOf(edits.getSelectedValue()); canvas.repaint(); } });
        canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"), "finish");
        canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
        canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DELETE"), "delete");
        canvas.getActionMap().put("finish", action(canvas::finish));
        canvas.getActionMap().put("cancel", action(canvas::cancel));
        canvas.getActionMap().put("delete", action(this::deleteSelected));
        theme(root);
        for (var e = toolGroup.getElements(); e.hasMoreElements(); ) styleTool((JToggleButton) e.nextElement());
        bindEdits();
        describe();
        requestRender();
    }

    // ------------------------------------------------------------------ page

    @Override public JComponent component() { return root; }
    @Override public boolean hasUnsavedChanges() { return !sketch.equals(saved); }
    @Override public void close() { closed = true; session.close(); }

    MapSketch document() { return sketch; }
    BiomeSkin world() { return (BiomeSkin) worldBox.getSelectedItem(); }
    List<String> problems() { return problems; }
    boolean previewReady() { return !busy && displayed == revision; }
    Canvas canvas() { return canvas; }
    void selectEdit(String id) { selected = id; canvas.repaint(); }
    void setWidth(double v) { width.setValue(v); }
    void setMetres(double v) { metres.setValue(v); }
    void setTerraces(int v) { terraces.setValue(v); }
    void setProgram(Program p, String why) { program.setSelectedItem(p); label.setText(why); }
    void setPlace(Role r, String name) { role.setSelectedItem(r); label.setText(name); }
    void selectTool(Tool t) {
        for (var e = toolGroup.getElements(); e.hasMoreElements(); ) {
            var b = e.nextElement();
            if (("tool-" + t.name().toLowerCase(Locale.ROOT)).equals(b.getName())) b.setSelected(true);
        }
        tool = t;
        canvas.cancel();
        describe();
    }
    void setCrossing(CrossingKind kind) { crossingKind.setSelectedItem(kind); }
    void selectWorld(BiomeSkin skin) { worldBox.setSelectedItem(skin); }

    void commit(MapSketch next) {
        if (next.equals(sketch)) return;
        undo.push(sketch);
        while (undo.size() > 100) undo.removeLast();
        redo.clear();
        sketch = next;
        bindEdits();
        requestRender();
    }

    void undo() { if (!undo.isEmpty()) { redo.push(sketch); sketch = undo.pop(); bindEdits(); requestRender(); } }
    void redo() { if (!redo.isEmpty()) { undo.push(sketch); sketch = redo.pop(); bindEdits(); requestRender(); } }

    private void openBase(MapBases.Base next) {
        if (hasUnsavedChanges()) {
            int answer = JOptionPane.showConfirmDialog(root, "Discard the edits on " + base.label() + "?", "Maps",
                    JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                binding = true; mapBox.setSelectedIndex(bases.indexOf(base)); binding = false;
                return;
            }
        }
        base = next;
        sketch = saved = next.sketch();
        undo.clear(); redo.clear();
        selected = null;
        binding = true; worldBox.setSelectedItem(BiomeSkin.of(next.builtAs())); binding = false;
        bindEdits();
        requestRender();
    }

    void bridgeRoads() {
        if (sketch.drops().isEmpty()) { state.setText("Draw a drop first: bridges go where a drop cuts a road."); return; }
        commit(session.bridgeRoads(base, sketch));
    }

    void deleteSelected() {
        if (selected == null) return;
        commit(sketch.without(selected));
        selected = null;
    }

    /**
     * Saves only a map whose checks all pass. The owned-map document is written through the owned-map format
     * ({@code owned-maps}); until it lands this keeps the edits as a draft under {@code build/map-authoring/}.
     */
    void save() {
        if (!previewReady()) { state.setText("Wait for the redraw: the checks run with it."); return; }
        if (!problems.isEmpty()) {
            state.setText("Not saved: " + problems.size() + " check(s) fail. " + problems.get(0));
            return;
        }
        try {
            Path target = MapsDrafts.write(context.projectRoot(), sketch);
            saved = sketch;
            context.reportStatus("Saved map edits: " + target);
            state.setText("Saved " + target.getFileName() + " · every check passes");
            context.stateChanged();
        } catch (Exception ex) {
            state.setText("Save failed: " + ex.getMessage());
        }
    }

    void allWorlds() {
        if (busy) { state.setText("Wait for the redraw to finish."); return; }
        busy = true;
        state.setText("Drawing " + base.label() + " as every world…");
        MapBases.Base b = base;
        MapSketch s = sketch;
        new SwingWorker<Path, Void>() {
            protected Path doInBackground() throws Exception {
                BufferedImage sheet = session.contactSheet(b, s);
                Path dir = context.projectRoot().resolve("build/map-authoring").resolve(b.id().replace('/', '-'));
                Files.createDirectories(dir);
                Path out = dir.resolve("all-worlds.png");
                ImageIO.write(sheet, "png", out.toFile());
                return out;
            }
            protected void done() {
                busy = false;
                try { state.setText("All-worlds sheet: " + get()); } catch (Exception ex) { state.setText("Sheet failed: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void requestRender() {
        revision++;
        context.stateChanged();
        canvas.repaint();
        render();
    }

    private void render() {
        if (closed) return;
        if (busy) return;
        busy = true;
        int version = revision;
        MapSketch s = sketch;
        BiomeSkin skin = world();
        MapBases.Base b = base;
        state.setText("Laying the edits on " + b.label() + " as " + skin.label() + "…");
        new SwingWorker<Object[], Void>() {
            protected Object[] doInBackground() throws Exception {
                MapsSession.Built built = session.build(b, s, skin);
                return new Object[]{session.frame(built), built.problems()};
            }
            @SuppressWarnings("unchecked")
            protected void done() {
                busy = false;
                if (closed) return;
                try {
                    Object[] r = get();
                    if (version == revision) {
                        frame = (MapsRender.Frame) r[0];
                        problems = (List<String>) r[1];
                        displayed = version;
                        checksModel.clear();
                        if (problems.isEmpty()) checksModel.addElement("✓ Every check passes: every place reachable from the landing zone, every crossing crosses, nothing built on a way.");
                        for (String p : problems) checksModel.addElement("✗ " + p);
                        state.setText(b.label() + " as " + skin.label() + " · drop " + skin.drop().style().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                                + " · towns " + skin.adaptation(Program.TOWN) + (hasUnsavedChanges() ? " · unsaved" : " · saved") + " · 1920 × 1080 / 100%");
                    }
                } catch (Exception ex) {
                    state.setText("Redraw failed: " + ex.getMessage());
                }
                canvas.repaint();
                if (version != revision) render();
            }
        }.execute();
    }

    private void bindEdits() {
        binding = true;
        editsModel.clear();
        for (Drop d : sketch.drops()) editsModel.addElement(d.id() + " · drop, " + d.crossings().size() + " crossing(s)");
        for (Rise r : sketch.rises()) editsModel.addElement(r.id() + " · rise");
        for (Road r : sketch.roads()) editsModel.addElement(r.id() + " · road");
        for (Settlement s : sketch.settlements()) editsModel.addElement(s.id() + " · " + MapsRender.program(s.program()).toLowerCase(Locale.ROOT));
        for (Place p : sketch.places()) editsModel.addElement(p.id() + " · " + p.role().name().toLowerCase(Locale.ROOT) + " " + p.label());
        binding = false;
    }

    private static String idOf(String row) { return row.substring(0, row.indexOf(" · ")); }

    private void describe() {
        state.setText(switch (tool) {
            case SELECT -> "Select: click an edit; drag its points; wheel a point to change its depth or height.";
            case DROP -> "Drop: click along its course; each point takes the depth set at the left.";
            case CROSSING -> "Crossing: click on a drop where it can be crossed.";
            case RISE -> "Rise: click along its crest; each point takes the height set at the left.";
            case ROAD -> "Road: click along its course.";
            case PLACE -> "Place: click where it stands.";
            case SETTLEMENT -> "Settlement: drag out where it stands.";
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
        p.add(Box.createVerticalStrut(8));
    }

    private static AbstractAction action(Runnable run) {
        return new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { run.run(); } };
    }

    private float w() { return ((Number) width.getValue()).floatValue(); }
    private float m() { return ((Number) metres.getValue()).floatValue(); }
    private int t() { return ((Number) terraces.getValue()).intValue(); }

    // ------------------------------------------------------------------ canvas

    /** The map as it plays, scaled to fit, with the marks over it; clicks become cells through the frame. */
    final class Canvas extends JPanel {
        private final List<Pt> drawing = new ArrayList<>();
        private Pt dragFrom, hover;
        private int dragPoint = -1;
        private String dragId;

        Canvas() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { press(e); }
                @Override public void mouseDragged(MouseEvent e) { drag(e); }
                @Override public void mouseReleased(MouseEvent e) { release(e); }
                @Override public void mouseMoved(MouseEvent e) { hover = cell(e); repaint(); }
                @Override public void mouseWheelMoved(MouseWheelEvent e) { wheel(e); }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
        }

        /** The map's own rectangle on the frame, a little margin round it, in frame pixels: x, y, width, height. */
        private double[] view() {
            if (frame == null) return new double[]{0, 0, MapsRender.WIDTH, MapsRender.HEIGHT};
            double m = 3 * frame.cellPx();
            double x0 = frame.sx(0) - m, y0 = frame.sy(frame.mapH()) - m;
            return new double[]{x0, y0, frame.sx(frame.mapW()) + m - x0, frame.sy(0) + m - y0};
        }
        double scale() { double[] v = view(); return Math.min(getWidth() / v[2], getHeight() / v[3]); }
        double ox() { double[] v = view(); return (getWidth() - v[2] * scale()) / 2 - v[0] * scale(); }
        double oy() { double[] v = view(); return (getHeight() - v[3] * scale()) / 2 - v[1] * scale(); }

        /** The cell under a point on the canvas, or null off the picture. */
        Pt cell(MouseEvent e) { return cellAt(e.getX(), e.getY()); }

        /** Where cell {@code c} lies on the canvas. */
        Point at(Pt c) {
            return new Point((int) Math.round(ox() + frame.sx(c.x()) * scale()), (int) Math.round(oy() + frame.sy(c.y()) * scale()));
        }

        Pt cellAt(double px, double py) {
            if (frame == null) return null;
            float fx = (float) ((px - ox()) / scale()), fy = (float) ((py - oy()) / scale());
            float x = frame.cellX(fx), y = frame.cellY(fy);
            if (x < 0 || y < 0 || x > frame.mapW() || y > frame.mapH()) return null;
            return new Pt(Math.round(x * 2f) / 2f, Math.round(y * 2f) / 2f);
        }

        void press(MouseEvent e) {
            requestFocusInWindow();
            Pt c = cell(e);
            if (c == null) return;
            if (SwingUtilities.isRightMouseButton(e)) { cancel(); return; }
            switch (tool) {
                case DROP, RISE, ROAD -> {
                    if (e.getClickCount() >= 2) { finish(); return; }
                    if (drawing.isEmpty() || drawing.get(drawing.size() - 1).distance(c) >= 1f) drawing.add(c);
                    depthsDrawn.add(m());
                }
                case CROSSING -> crossingAt(c);
                case PLACE -> {
                    String id = sketch.freshId("place");
                    String name = label.getText().isBlank() ? id : label.getText().trim();
                    commit(sketch.withPlaces(l -> { l.add(new Place(id, name, (int) c.x(), (int) c.y(), (Role) role.getSelectedItem())); return l; }));
                    selected = id;
                }
                case SETTLEMENT -> dragFrom = c;
                case SELECT -> grab(c);
            }
            repaint();
        }

        private final List<Float> depthsDrawn = new ArrayList<>();

        void drag(MouseEvent e) {
            Pt c = cell(e);
            hover = c;
            if (c == null) return;
            if (tool == Tool.SELECT && dragId != null) moveGrabbed(c, false);
            repaint();
        }

        void release(MouseEvent e) {
            Pt c = cell(e);
            if (tool == Tool.SETTLEMENT && dragFrom != null && c != null) {
                int x0 = (int) Math.min(dragFrom.x(), c.x()), y0 = (int) Math.min(dragFrom.y(), c.y());
                int x1 = (int) Math.ceil(Math.max(dragFrom.x(), c.x())), y1 = (int) Math.ceil(Math.max(dragFrom.y(), c.y()));
                if (x1 - x0 >= 4 && y1 - y0 >= 4) {
                    String id = sketch.freshId(((Program) program.getSelectedItem()).name().toLowerCase(Locale.ROOT).replace('_', '-'));
                    String why = label.getText().isBlank() || label.getText().equals("New site") ? "" : label.getText().trim();
                    commit(sketch.withSettlements(l -> { l.add(new Settlement(id, x0, y0, x1 - x0, y1 - y0, (Program) program.getSelectedItem(), why)); return l; }));
                    selected = id;
                }
                dragFrom = null;
            }
            if (tool == Tool.SELECT && dragId != null && c != null) moveGrabbed(c, true);
            dragId = null;
            dragPoint = -1;
            repaint();
        }

        /** Ends the path being drawn and commits it as a drop, rise or road. */
        void finish() {
            if (drawing.size() < 2) { cancel(); return; }
            List<Pt> path = List.copyOf(drawing);
            List<Float> values = List.copyOf(depthsDrawn.subList(0, path.size()));
            try {
                switch (tool) {
                    case DROP -> { String id = sketch.freshId("drop"); commit(sketch.withDrops(l -> { l.add(new Drop(id, path, w(), values, Math.min(8, t()), List.of())); return l; })); selected = id; }
                    case RISE -> { String id = sketch.freshId("rise"); commit(sketch.withRises(l -> { l.add(new Rise(id, path, w(), values.stream().map(v -> Math.min(100f, v)).toList(), Math.max(2, t()))); return l; })); selected = id; }
                    case ROAD -> { String id = sketch.freshId("road"); commit(sketch.withRoads(l -> { l.add(new Road(id, path, Math.min(32f, w()), (RoadPlan.Material) material.getSelectedItem())); return l; })); selected = id; }
                    default -> { }
                }
            } catch (IllegalArgumentException ex) {
                state.setText("Not added: " + ex.getMessage());
            }
            cancel();
        }

        void cancel() { drawing.clear(); depthsDrawn.clear(); dragFrom = null; repaint(); }

        private void crossingAt(Pt c) {
            Drop best = null;
            float bestDistance = Float.MAX_VALUE, at = 0f;
            for (Drop d : sketch.drops()) {
                float[] n = SketchRaster.nearest(d.path(), c.x(), c.y());
                if (n[1] < bestDistance) { bestDistance = n[1]; best = d; at = n[0]; }
            }
            if (best == null || bestDistance > best.width()) { state.setText("Click on a drop to cross it."); return; }
            Drop d = best;
            Crossing crossing = new Crossing(at, Math.max(2, Math.min(24, Math.round(w()))), (CrossingKind) crossingKind.getSelectedItem());
            commit(sketch.withDrops(l -> {
                l.replaceAll(x -> x == d ? d.withCrossings(append(d.crossings(), crossing)) : x);
                return l;
            }));
            selected = d.id();
        }

        /** Picks the nearest edit's point, place or settlement under {@code c}, ready to drag. */
        private void grab(Pt c) {
            dragId = null;
            float best = 3f;
            for (Drop d : sketch.drops()) for (int k = 0; k < d.path().size(); k++) if (d.path().get(k).distance(c) < best) { best = d.path().get(k).distance(c); dragId = d.id(); dragPoint = k; }
            for (Rise r : sketch.rises()) for (int k = 0; k < r.path().size(); k++) if (r.path().get(k).distance(c) < best) { best = r.path().get(k).distance(c); dragId = r.id(); dragPoint = k; }
            for (Road r : sketch.roads()) for (int k = 0; k < r.path().size(); k++) if (r.path().get(k).distance(c) < best) { best = r.path().get(k).distance(c); dragId = r.id(); dragPoint = k; }
            for (Place p : sketch.places()) { float d = new Pt(p.x() + .5f, p.y() + .5f).distance(c); if (d < best) { best = d; dragId = p.id(); dragPoint = -1; } }
            if (dragId == null) for (Settlement s : sketch.settlements()) if (s.contains((int) c.x(), (int) c.y())) { dragId = s.id(); dragPoint = -1; dragFrom = c; }
            if (dragId == null) for (Drop d : sketch.drops()) if (SketchRaster.nearest(d.path(), c.x(), c.y())[1] <= d.width() / 2f) dragId = d.id();
            selected = dragId;
            if (dragId != null && dragPoint < 0 && dragFrom == null) dragFrom = c;
        }

        /** Moves what was grabbed to {@code c}: shown while dragging, committed as one undo step on release. */
        private void moveGrabbed(Pt c, boolean done) {
            String id = dragId;
            int k = dragPoint;
            Pt from = dragFrom;
            MapSketch next = sketch.withDrops(l -> { l.replaceAll(d -> d.id().equals(id) && k >= 0 ? d.withPath(set(d.path(), k, c), d.depths()) : d); return l; })
                    .withRises(l -> { l.replaceAll(r -> r.id().equals(id) && k >= 0 ? new Rise(r.id(), set(r.path(), k, c), r.width(), r.heights(), r.terraces()) : r); return l; })
                    .withRoads(l -> { l.replaceAll(r -> r.id().equals(id) && k >= 0 ? new Road(r.id(), set(r.path(), k, c), r.width(), r.material()) : r); return l; })
                    .withPlaces(l -> { l.replaceAll(p -> p.id().equals(id) ? new Place(p.id(), p.label(), (int) c.x(), (int) c.y(), p.role()) : p); return l; })
                    .withSettlements(l -> {
                        l.replaceAll(s -> s.id().equals(id) && from != null ? new Settlement(s.id(), s.x() + Math.round(c.x() - from.x()),
                                s.y() + Math.round(c.y() - from.y()), s.w(), s.h(), s.program(), s.role()) : s);
                        return l;
                    });
            if (done) {
                preview = null;
                dragFrom = null;
                commit(next);
            } else {
                preview = next;
            }
        }

        private MapSketch preview;

        private void wheel(MouseWheelEvent e) {
            Pt c = cell(e);
            if (c == null) return;
            float step = (float) -e.getPreciseWheelRotation();
            for (Drop d : sketch.drops()) for (int k = 0; k < d.path().size(); k++) if (d.path().get(k).distance(c) < 3f) {
                int at = k;
                List<Float> depths = new ArrayList<>(d.depths());
                depths.set(at, Math.max(1f, Math.min(200f, depths.get(at) + step)));
                commit(sketch.withDrops(l -> { l.replaceAll(x -> x == d ? x.withPath(x.path(), depths) : x); return l; }));
                selected = d.id();
                return;
            }
            for (Rise r : sketch.rises()) for (int k = 0; k < r.path().size(); k++) if (r.path().get(k).distance(c) < 3f) {
                int at = k;
                List<Float> heights = new ArrayList<>(r.heights());
                heights.set(at, Math.max(1f, Math.min(100f, heights.get(at) + step)));
                commit(sketch.withRises(l -> { l.replaceAll(x -> x == r ? new Rise(r.id(), r.path(), r.width(), heights, r.terraces()) : x); return l; }));
                selected = r.id();
                return;
            }
            metres.setValue(Math.max(1d, Math.min(200d, ((Number) metres.getValue()).doubleValue() + step)));
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
                MapsRender.mark(g, frame, preview != null && dragId != null ? preview : sketch, world(), selected);
                g.setStroke(new BasicStroke(2f));
                g.setColor(new Color(255, 255, 255, 220));
                for (int k = 1; k < drawing.size(); k++) {
                    g.drawLine(Math.round(frame.sx(drawing.get(k - 1).x())), Math.round(frame.sy(drawing.get(k - 1).y())),
                            Math.round(frame.sx(drawing.get(k).x())), Math.round(frame.sy(drawing.get(k).y())));
                }
                if (!drawing.isEmpty() && hover != null) {
                    Pt last = drawing.get(drawing.size() - 1);
                    g.setColor(new Color(255, 255, 255, 120));
                    g.drawLine(Math.round(frame.sx(last.x())), Math.round(frame.sy(last.y())), Math.round(frame.sx(hover.x())), Math.round(frame.sy(hover.y())));
                }
                if (tool == Tool.SETTLEMENT && dragFrom != null && hover != null) {
                    g.setColor(new Color(255, 230, 150));
                    int x0 = Math.round(frame.sx(Math.min(dragFrom.x(), hover.x()))), y0 = Math.round(frame.sy(Math.max(dragFrom.y(), hover.y())));
                    g.drawRect(x0, y0, Math.round(Math.abs(hover.x() - dragFrom.x()) * frame.cellPx()), Math.round(Math.abs(hover.y() - dragFrom.y()) * frame.cellPx()));
                }
            }
            g.setTransform(before);
            if (hover != null) {
                g.setColor(new Color(20, 23, 23, 220));
                g.fillRect(getWidth() - 150, 8, 140, 26);
                g.setColor(Color.WHITE);
                g.drawString(String.format(Locale.ROOT, "cell %.0f, %.0f", Math.floor(hover.x()), Math.floor(hover.y())), getWidth() - 140, 26);
            }
            if (displayed != revision) {
                g.setColor(new Color(20, 23, 23, 235));
                g.fillRect(12, 12, 270, 34);
                g.setColor(Color.WHITE);
                g.drawString("Redrawing as the battle draws it…", 24, 34);
            }
            g.dispose();
        }
    }

    private static List<Pt> set(List<Pt> path, int k, Pt p) {
        List<Pt> out = new ArrayList<>(path);
        out.set(k, p);
        return out;
    }

    private static <T> List<T> append(List<T> list, T item) {
        List<T> out = new ArrayList<>(list);
        out.add(item);
        return out;
    }
}
