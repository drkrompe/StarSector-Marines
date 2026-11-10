package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Box;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Drop;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Rise;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Settlement;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Site;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
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
import javax.swing.JCheckBox;
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
 * The Maps page: the editor for owned mission maps ({@code owned-maps.md}). It opens an owned map, never an empty grid,
 * built for the chosen world by the mission's own recipe and drawn by the battle renderer as it will play, and edits
 * it on its own picture: natural drops with their depth along them and their crossings, banks, roads, the mission's
 * sites and origin, and settlements by program and role (a town that terraces into a drop is a crevasse town where
 * the world takes one). The world switch rebuilds the same document for each world, and the all-worlds sheet shows
 * them side by side with each world's drop style and how each settlement adapted. Every edit is one undo step; the
 * checks run with every redraw, and a save is refused while one fails on any world.
 */
public final class MapsAuthoringPage implements AuthoringPage {

    /** What a click on the map does. */
    enum Tool {
        SELECT("Select / move"), DROP("Drop"), CROSSING("Crossing"), RISE("Bank"), ROAD("Road"), SITE("Site"),
        SETTLEMENT("Settlement"), ORIGIN("Origin");
        final String label;
        Tool(String label) { this.label = label; }
    }

    private final AuthoringPageContext context;
    private final MapsSession session;
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final Canvas canvas = new Canvas();
    private final JComboBox<String> mapBox = new JComboBox<>();
    private final JComboBox<MapWorld> worldBox = new JComboBox<>(MapWorld.values());
    private final ButtonGroup toolGroup = new ButtonGroup();
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(5d, 2d, 40d, 1d));
    private final JSpinner share = new JSpinner(new SpinnerNumberModel(50d, 0d, 100d, 5d));
    private final JComboBox<OwnedMap.DropRole> dropRole = new JComboBox<>(OwnedMap.DropRole.values());
    private final JComboBox<RoadPlan.Material> material = new JComboBox<>(RoadPlan.Material.values());
    private final JComboBox<TacticalNode.Kind> siteKind = new JComboBox<>(TacticalNode.Kind.values());
    private final JComboBox<OwnedMap.SettlementRole> settlementRole = new JComboBox<>(OwnedMap.SettlementRole.values());
    private final JCheckBox terrace = new JCheckBox("Terraces into the nearest drop");
    private final JTextField label = new JTextField("New site");
    private final DefaultListModel<String> editsModel = new DefaultListModel<>();
    private final JList<String> edits = new JList<>(editsModel);
    private final DefaultListModel<String> checksModel = new DefaultListModel<>();
    private final JLabel state = new JLabel(" ");
    private final ArrayDeque<MapDocument> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Path saveRoot;
    private MapDocument doc, saved;
    private Tool tool = Tool.SELECT;
    private String selected;
    private MapsRender.Frame frame;
    private MapsSession.Built built;
    private int revision, displayed = -1;
    private boolean busy, closed, binding;

    public MapsAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context = context;
        saveRoot = context.projectRoot();
        session = new MapsSession(context.projectRoot());
        for (String id : MapsSession.maps()) mapBox.addItem(id);
        doc = saved = session.open(MapsSession.maps().get(0));
        worldBox.setSelectedItem(MapWorld.ARID);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.add(new JLabel("Owned map")); toolbar.add(mapBox);
        toolbar.add(new JLabel("  World")); toolbar.add(worldBox);
        button(toolbar, "All worlds sheet…", this::allWorlds);
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
            toolGroup.add(b);
            tools.add(b);
        }
        tools.setAlignmentX(Component.LEFT_ALIGNMENT);
        tools.setMaximumSize(new Dimension(280, 140));
        side.add(tools);
        side.add(gap(10));
        field(side, "Width · cells", width);
        field(side, "Depth or bank height · % of band", share);
        field(side, "Drop role", dropRole);
        field(side, "Road surface", material);
        field(side, "Site kind", siteKind);
        field(side, "Site name, or a settlement's program", label);
        field(side, "Settlement role", settlementRole);
        terrace.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(terrace);
        side.add(gap(8));
        JLabel editsTitle = new JLabel("Edits");
        editsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(editsTitle);
        JScrollPane editsScroll = new JScrollPane(edits);
        editsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        editsScroll.setMaximumSize(new Dimension(280, 160));
        side.add(editsScroll);
        JPanel editButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        editButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        button(editButtons, "Delete", this::deleteSelected);
        side.add(editButtons);
        JLabel help = new JLabel("<html>Click a path's points; double-click<br>or Enter ends it, Esc drops it.<br>"
                + "Wheel over a drop's point sets its depth.<br>Drag a point, site, bank or lot to move it.<br>"
                + "A settlement is where it stands and<br>what it is for, never its style:<br>each world builds it its own way.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(help);
        side.add(new Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, Short.MAX_VALUE)));
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
        mapBox.addActionListener(e -> { if (!binding) openMap((String) mapBox.getSelectedItem()); });
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
    @Override public boolean hasUnsavedChanges() { return !doc.equals(saved); }
    @Override public void close() { closed = true; session.close(); }

    MapDocument document() { return doc; }
    MapWorld world() { return (MapWorld) worldBox.getSelectedItem(); }
    List<String> problems() { return built == null ? List.of() : built.problems(); }
    boolean previewReady() { return !busy && displayed == revision; }
    Canvas canvas() { return canvas; }
    String status() { return state.getText(); }
    void saveInto(Path root) { saveRoot = root; }
    void selectEdit(String id) { selected = id; canvas.repaint(); }
    void setWidth(double v) { width.setValue(v); }
    void setShare(double percent) { share.setValue(percent); }
    void setDropRole(OwnedMap.DropRole r) { dropRole.setSelectedItem(r); }
    void setSite(TacticalNode.Kind k, String name) { siteKind.setSelectedItem(k); label.setText(name); }
    void setSettlement(OwnedMap.SettlementRole r, String program, boolean terraces) {
        settlementRole.setSelectedItem(r); label.setText(program); terrace.setSelected(terraces);
    }
    void selectWorld(MapWorld w) { worldBox.setSelectedItem(w); }

    void selectTool(Tool t) {
        for (var e = toolGroup.getElements(); e.hasMoreElements(); ) {
            var b = e.nextElement();
            if (("tool-" + t.name().toLowerCase(Locale.ROOT)).equals(b.getName())) b.setSelected(true);
        }
        tool = t;
        canvas.cancel();
        describe();
    }

    void commit(MapDocument next) {
        if (next.equals(doc)) return;
        undo.push(doc);
        while (undo.size() > 100) undo.removeLast();
        redo.clear();
        doc = next;
        bindEdits();
        requestRender();
    }

    void undo() { if (!undo.isEmpty()) { redo.push(doc); doc = undo.pop(); bindEdits(); requestRender(); } }
    void redo() { if (!redo.isEmpty()) { undo.push(doc); doc = redo.pop(); bindEdits(); requestRender(); } }

    private void openMap(String id) {
        if (hasUnsavedChanges() && JOptionPane.showConfirmDialog(root, "Discard the edits on " + doc.map().id() + "?", "Maps",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            binding = true; mapBox.setSelectedItem(doc.map().id()); binding = false;
            return;
        }
        doc = saved = session.open(id);
        undo.clear(); redo.clear();
        selected = null;
        bindEdits();
        requestRender();
    }

    void deleteSelected() {
        if (selected == null || "origin".equals(selected)) return;
        commit(doc.without(selected));
        selected = null;
    }

    /** Saves the map and its roads once every check passes on every world; the checks run off the event thread. */
    void save() {
        if (busy) { state.setText("Wait for the redraw to finish."); return; }
        busy = true;
        MapDocument d = doc;
        Path into = saveRoot;
        state.setText("Checking " + d.map().id() + " on every world before saving…");
        new SwingWorker<List<Path>, Void>() {
            protected List<Path> doInBackground() throws Exception { return session.save(d, into); }
            protected void done() {
                busy = false;
                try {
                    List<Path> written = get();
                    saved = d;
                    context.reportStatus("Saved " + written);
                    state.setText("Saved " + written.get(0).getFileName() + (written.size() > 1 ? " and " + written.get(1).getFileName() : "")
                            + " · every check passes on every world");
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
        state.setText("Building " + doc.map().id() + " for every world…");
        MapDocument d = doc;
        new SwingWorker<Path, Void>() {
            protected Path doInBackground() throws Exception {
                BufferedImage sheet = session.contactSheet(d);
                Path dir = context.projectRoot().resolve("build/map-authoring");
                Files.createDirectories(dir);
                Path out = dir.resolve(d.map().id() + "-all-worlds.png");
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

    private void requestRender() {
        revision++;
        context.stateChanged();
        canvas.repaint();
        render();
    }

    private void render() {
        if (closed || busy) return;
        busy = true;
        int version = revision;
        MapDocument d = doc;
        MapWorld world = world();
        state.setText("Building " + d.map().id() + " for " + world.label() + "…");
        new SwingWorker<Object[], Void>() {
            protected Object[] doInBackground() throws Exception {
                MapsSession.Built b = session.build(d, world);
                return new Object[]{b, b.map() == null ? null : session.frame(b)};
            }
            protected void done() {
                busy = false;
                if (closed) return;
                try {
                    Object[] r = get();
                    if (version == revision) {
                        built = (MapsSession.Built) r[0];
                        if (r[1] != null) frame = (MapsRender.Frame) r[1];
                        displayed = version;
                        checksModel.clear();
                        if (built.problems().isEmpty()) checksModel.addElement("✓ Every check passes: every place reachable from the origin, every crossing crosses, nothing built on a way.");
                        for (String p : built.problems()) checksModel.addElement("✗ " + p);
                        String drop = d.map().drops().isEmpty() ? "no drop" : world.dropStyle(d.map().drops().get(0).role());
                        state.setText(d.map().id() + " as " + world.label() + " · drop " + drop + " · "
                                + (hasUnsavedChanges() ? "unsaved" : "saved") + " · 1920 × 1080 / 100%");
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
        OwnedMap m = doc.map();
        editsModel.addElement("origin · where the attacker lands");
        for (Drop d : m.drops()) editsModel.addElement(d.name() + " · drop, " + d.role().name().toLowerCase(Locale.ROOT) + ", " + d.crossings().size() + " crossing(s)");
        for (Rise r : m.rises()) editsModel.addElement(r.name() + " · bank");
        for (Settlement s : m.settlements()) editsModel.addElement(s.name() + " · " + s.program() + ", " + s.role().name().toLowerCase(Locale.ROOT));
        for (Site s : m.sites()) editsModel.addElement(s.name() + " · site, " + s.kind().name().toLowerCase(Locale.ROOT));
        if (doc.roads() != null) for (RoadPlan.Route r : doc.roads().routes()) editsModel.addElement(r.id() + " · road");
        binding = false;
    }

    private static String idOf(String row) { return row.substring(0, row.indexOf(" · ")); }

    private void describe() {
        state.setText(switch (tool) {
            case SELECT -> "Select: click an edit; drag its points; wheel a drop's point to set its depth.";
            case DROP -> "Drop: click along its course; each point takes the depth set at the left.";
            case CROSSING -> "Crossing: click on a drop where it is crossed; it is decked as wide as set at the left.";
            case RISE -> "Bank: drag out the high ground; its height is the share set at the left.";
            case ROAD -> "Road: click along its course; it joins the map's road document.";
            case SITE -> "Site: click where the mission's named place stands.";
            case SETTLEMENT -> "Settlement: drag out its lot; the name field is its program.";
            case ORIGIN -> "Origin: click where the attacker arrives; every route is proved from here.";
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

    private int w() { return (int) Math.round(((Number) width.getValue()).doubleValue()); }
    private float s() { return ((Number) share.getValue()).floatValue() / 100f; }

    private static Box shifted(Box b, int dx, int dy) {
        return b == null ? null : new Box(b.x0() + dx, b.y0() + dy, b.x1() + dx, b.y1() + dy);
    }

    private static Cell shifted(Cell c, int dx, int dy) {
        return c == null ? null : new Cell(c.x() + dx, c.y() + dy);
    }

    /** A settlement moved as a whole: its lot, gates, buildings and works. */
    private static Settlement shifted(Settlement s, int dx, int dy) {
        List<Cell> gates = s.gates().stream().map(c -> shifted(c, dx, dy)).toList();
        List<OwnedMap.Building> buildings = s.buildings().stream().map(b -> new OwnedMap.Building(b.name(), b.form(), b.plan(),
                shifted(b.at(), dx, dy), b.turns(), shifted(b.box(), dx, dy), b.frontage(), b.opposed())).toList();
        List<OwnedMap.Work> works = s.works().stream().map(k -> new OwnedMap.Work(k.op(), shifted(k.box(), dx, dy),
                shifted(k.at(), dx, dy), k.id(), k.turns())).toList();
        return new Settlement(s.name(), s.program(), s.role(), shifted(s.lot(), dx, dy), gates, buildings, works, s.terraceInto());
    }

    // ------------------------------------------------------------------ canvas

    /** The map as it plays, fitted to the panel, with the marks over it; clicks become cells through the frame. */
    final class Canvas extends JPanel {
        private final List<Cell> drawing = new ArrayList<>();
        private final List<Float> sharesDrawn = new ArrayList<>();
        private Cell dragFrom, hover;
        private int dragPoint = -1;
        private String dragId;
        private MapDocument preview;

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

        /** Where cell {@code c} lies on the canvas. */
        Point at(Cell c) {
            return new Point((int) Math.round(ox() + frame.sx(c.x() + .5f) * scale()), (int) Math.round(oy() + frame.sy(c.y() + .5f) * scale()));
        }

        Cell cell(MouseEvent e) {
            if (frame == null) return null;
            float fx = (float) ((e.getX() - ox()) / scale()), fy = (float) ((e.getY() - oy()) / scale());
            int x = (int) Math.floor(frame.cellX(fx)), y = (int) Math.floor(frame.cellY(fy));
            if (x < 0 || y < 0 || x >= frame.mapW() || y >= frame.mapH()) return null;
            return new Cell(x, y);
        }

        void press(MouseEvent e) {
            requestFocusInWindow();
            Cell c = cell(e);
            if (c == null) return;
            if (SwingUtilities.isRightMouseButton(e)) { cancel(); return; }
            switch (tool) {
                case DROP, ROAD -> {
                    if (e.getClickCount() >= 2) { finish(); return; }
                    if (drawing.isEmpty() || !drawing.get(drawing.size() - 1).equals(c)) { drawing.add(c); sharesDrawn.add(s()); }
                }
                case CROSSING -> crossingAt(c);
                case SITE -> {
                    String name = label.getText().isBlank() ? doc.freshName("site") : label.getText().trim();
                    if (doc.map().site(name) != null) name = doc.freshName(name);
                    String n = name;
                    try {
                        commit(doc.withSites(l -> { l.add(new Site(n, (TacticalNode.Kind) siteKind.getSelectedItem(), null, c,
                                new Box(c.x() - 4, c.y() - 4, c.x() + 4, c.y() + 4), Faction.DEFENDER, 40, 4)); return l; }));
                        selected = n;
                    } catch (IllegalArgumentException ex) { state.setText("Not added: " + ex.getMessage()); }
                }
                case ORIGIN -> { commit(doc.withOrigin(c)); selected = "origin"; }
                case SETTLEMENT, RISE -> dragFrom = c;
                case SELECT -> grab(c);
            }
            repaint();
        }

        void drag(MouseEvent e) {
            Cell c = cell(e);
            hover = c;
            if (c != null && tool == Tool.SELECT && dragId != null) preview = moved(c);
            repaint();
        }

        void release(MouseEvent e) {
            Cell c = cell(e);
            if ((tool == Tool.SETTLEMENT || tool == Tool.RISE) && dragFrom != null && c != null) {
                Box box = new Box(dragFrom.x(), dragFrom.y(), c.x(), c.y());
                if (box.x1() - box.x0() >= 3 && box.y1() - box.y0() >= 3) {
                    try {
                        if (tool == Tool.RISE) {
                            String name = doc.freshName("bank");
                            commit(doc.withRises(l -> { l.add(new Rise(name, OwnedMap.RiseRole.BANK, box, Math.max(.05f, s()))); return l; }));
                            selected = name;
                        } else {
                            String program = label.getText().isBlank() || label.getText().equals("New site") ? "settlement" : label.getText().trim();
                            String name = doc.freshName(program);
                            Drop into = terrace.isSelected() ? doc.dropAt((box.x0() + box.x1()) / 2f, (box.y0() + box.y1()) / 2f) : null;
                            if (terrace.isSelected() && into == null) into = nearestDrop(box);
                            String terraceInto = into == null ? null : into.name();
                            commit(doc.withSettlements(l -> { l.add(new Settlement(name, program, (OwnedMap.SettlementRole) settlementRole.getSelectedItem(),
                                    box, List.of(), List.of(), List.of(), terraceInto)); return l; }));
                            selected = name;
                        }
                    } catch (IllegalArgumentException ex) { state.setText("Not added: " + ex.getMessage()); }
                }
                dragFrom = null;
            }
            if (tool == Tool.SELECT && dragId != null && c != null) {
                MapDocument next = moved(c);
                preview = null;
                commit(next);
            }
            dragId = null;
            dragPoint = -1;
            dragFrom = null;
            repaint();
        }

        private Drop nearestDrop(Box box) {
            Drop best = null;
            float bestDistance = Float.MAX_VALUE;
            for (Drop d : doc.map().drops()) {
                float distance = MapDocument.distance(d.path(), (box.x0() + box.x1()) / 2f, (box.y0() + box.y1()) / 2f);
                if (distance < bestDistance) { bestDistance = distance; best = d; }
            }
            return best;
        }

        /** Ends the path being drawn and commits it as a drop or a road. */
        void finish() {
            if (drawing.size() < 2) { cancel(); return; }
            List<Cell> path = List.copyOf(drawing);
            try {
                if (tool == Tool.DROP) {
                    String name = doc.freshName("drop");
                    List<OwnedMap.Depth> depths = new ArrayList<>();
                    for (int k = 0; k < path.size(); k++) depths.add(new OwnedMap.Depth(path.get(k), Math.max(0f, Math.min(1f, sharesDrawn.get(k)))));
                    Drop drop = new Drop(name, (OwnedMap.DropRole) dropRole.getSelectedItem(), path, Math.max(2, w()), List.of(), 0, depths);
                    commit(doc.withDrops(l -> { l.add(drop); return l; }));
                    selected = name;
                } else if (tool == Tool.ROAD) {
                    if (doc.roads() == null) { state.setText("This map names no road document to add a road to."); cancel(); return; }
                    String name = doc.freshName("road");
                    List<RoadPlan.Point> points = path.stream().map(c -> new RoadPlan.Point(c.x(), c.y())).toList();
                    List<RoadPlan.Route> routes = new ArrayList<>(doc.roads().routes());
                    routes.add(new RoadPlan.Route(name, points, Math.max(2, Math.min(32, w())), 1, .35, name.hashCode(),
                            (RoadPlan.Material) material.getSelectedItem()));
                    commit(doc.withRoads(new RoadPlan(doc.roads().width(), doc.roads().height(), routes)));
                    selected = name;
                }
            } catch (IllegalArgumentException ex) {
                state.setText("Not added: " + ex.getMessage());
            }
            cancel();
        }

        void cancel() { drawing.clear(); sharesDrawn.clear(); dragFrom = null; repaint(); }

        private void crossingAt(Cell c) {
            Drop d = doc.dropAt(c.x() + .5f, c.y() + .5f);
            if (d == null) { state.setText("Click on a drop to cross it."); return; }
            commit(doc.withDrops(l -> { l.replaceAll(x -> x == d ? MapDocument.withCrossing(d, c, Math.max(2, w())) : x); return l; }));
            selected = d.name();
        }

        /** Picks the drop point, site, origin, bank or settlement under {@code c}, ready to drag. */
        private void grab(Cell c) {
            dragId = null;
            dragPoint = -1;
            float best = 3f;
            OwnedMap m = doc.map();
            for (Drop d : m.drops()) for (int k = 0; k < d.path().size(); k++) {
                float dist = (float) Math.hypot(d.path().get(k).x() - c.x(), d.path().get(k).y() - c.y());
                if (dist < best) { best = dist; dragId = d.name(); dragPoint = k; }
            }
            for (Site s : m.sites()) if (s.at() != null) {
                float dist = (float) Math.hypot(s.at().x() - c.x(), s.at().y() - c.y());
                if (dist < best) { best = dist; dragId = s.name(); dragPoint = -1; }
            }
            if ((float) Math.hypot(m.origin().x() - c.x(), m.origin().y() - c.y()) < best) { dragId = "origin"; dragPoint = -1; }
            if (dragId == null) for (Rise r : m.rises()) if (r.box().contains(c.x(), c.y())) dragId = r.name();
            if (dragId == null) for (Settlement s : m.settlements()) if (s.lot().contains(c.x(), c.y())) dragId = s.name();
            if (dragId == null) { Drop d = doc.dropAt(c.x() + .5f, c.y() + .5f); if (d != null) selected = d.name(); }
            else selected = dragId;
            dragFrom = c;
        }

        /** What was grabbed, moved to {@code c}. */
        private MapDocument moved(Cell c) {
            String id = dragId;
            int k = dragPoint, dx = c.x() - dragFrom.x(), dy = c.y() - dragFrom.y();
            if ("origin".equals(id)) return doc.withOrigin(c);
            return doc.withDrops(l -> { l.replaceAll(d -> d.name().equals(id) && k >= 0 ? MapDocument.withPoint(d, k, c) : d); return l; })
                    .withSites(l -> { l.replaceAll(s -> s.name().equals(id) && s.at() != null ? new Site(s.name(), s.kind(), s.building(), c,
                            shifted(s.area(), c.x() - s.at().x(), c.y() - s.at().y()), s.guard(), s.value(), s.garrison()) : s); return l; })
                    .withRises(l -> { l.replaceAll(r -> r.name().equals(id) ? new Rise(r.name(), r.role(), shifted(r.box(), dx, dy), r.share()) : r); return l; })
                    .withSettlements(l -> { l.replaceAll(s -> s.name().equals(id) ? shifted(s, dx, dy) : s); return l; });
        }

        private void wheel(MouseWheelEvent e) {
            Cell c = cell(e);
            if (c == null) return;
            float step = (float) -e.getPreciseWheelRotation() * .05f;
            for (Drop d : doc.map().drops()) for (int k = 0; k < d.path().size(); k++) {
                if (Math.hypot(d.path().get(k).x() - c.x(), d.path().get(k).y() - c.y()) >= 3) continue;
                float was = MapDocument.depthAt(d, k);
                int at = k;
                commit(doc.withDrops(l -> { l.replaceAll(x -> x == d ? MapDocument.withDepth(d, at, (was < 0 ? .5f : was) + step) : x); return l; }));
                selected = d.name();
                return;
            }
            share.setValue(Math.max(0d, Math.min(100d, ((Number) share.getValue()).doubleValue() + step * 100)));
        }

        @Override
        protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g = (Graphics2D) raw.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform before = g.getTransform();
            g.translate(ox(), oy());
            g.scale(scale(), scale());
            if (frame != null && built != null) {
                g.drawImage(frame.image(), 0, 0, null);
                MapsRender.mark(g, frame, preview != null && dragId != null ? preview : doc, built, selected);
                g.setStroke(new BasicStroke(2f));
                g.setColor(new Color(255, 255, 255, 220));
                for (int k = 1; k < drawing.size(); k++) {
                    g.drawLine(Math.round(frame.sx(drawing.get(k - 1).x() + .5f)), Math.round(frame.sy(drawing.get(k - 1).y() + .5f)),
                            Math.round(frame.sx(drawing.get(k).x() + .5f)), Math.round(frame.sy(drawing.get(k).y() + .5f)));
                }
                if (!drawing.isEmpty() && hover != null) {
                    Cell last = drawing.get(drawing.size() - 1);
                    g.setColor(new Color(255, 255, 255, 120));
                    g.drawLine(Math.round(frame.sx(last.x() + .5f)), Math.round(frame.sy(last.y() + .5f)),
                            Math.round(frame.sx(hover.x() + .5f)), Math.round(frame.sy(hover.y() + .5f)));
                }
                if ((tool == Tool.SETTLEMENT || tool == Tool.RISE) && dragFrom != null && hover != null) {
                    g.setColor(new Color(255, 230, 150));
                    Box b = new Box(dragFrom.x(), dragFrom.y(), hover.x(), hover.y());
                    g.drawRect(Math.round(frame.sx(b.x0())), Math.round(frame.sy(b.y1() + 1)),
                            Math.round((b.x1() - b.x0() + 1) * frame.cellPx()), Math.round((b.y1() - b.y0() + 1) * frame.cellPx()));
                }
            }
            g.setTransform(before);
            if (hover != null) {
                g.setColor(new Color(20, 23, 23, 220));
                g.fillRect(getWidth() - 150, 8, 140, 26);
                g.setColor(Color.WHITE);
                g.drawString("cell " + hover.x() + ", " + hover.y(), getWidth() - 140, 26);
            }
            if (displayed != revision) {
                g.setColor(new Color(20, 23, 23, 235));
                g.fillRect(12, 12, 300, 34);
                g.setColor(Color.WHITE);
                g.drawString("Building the map as the battle plays it…", 24, 34);
            }
            g.dispose();
        }
    }
}
