package com.dillon.starsectormarines.tools.turretauthoring;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Bounded grid editor for data-authored defense-post layouts. */
final class EmplacementAuthoringPanel extends JPanel {

    private static final int CELL = 56;
    private static final Color EMPTY = new Color(0x16, 0x1D, 0x27);
    private static final Color BARRIER = new Color(0x7B, 0x68, 0x4F);
    private static final Color PAD = new Color(0x56, 0x62, 0x70);
    private static final Color TURRET = new Color(0xD0, 0x72, 0x4D);
    private static final Color HUB = new Color(0x55, 0xA8, 0xC6);

    private final TurretAuthoringPage page;
    private final JComboBox<LayoutItem> layouts = new JComboBox<>();
    private final JComboBox<Tool> tool = new JComboBox<>(Tool.values());
    private final JComboBox<String> appearance = new JComboBox<>(
            new String[]{"vent", "embankment", "bow-out"});
    private final JComboBox<String> structure = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final GridCanvas canvas = new GridCanvas();
    private Offset movingTurret;

    EmplacementAuthoringPanel(TurretAuthoringPage page) {
        super(new BorderLayout(8, 8));
        this.page = page;
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(new JLabel("Layout"));
        layouts.setPreferredSize(new Dimension(280, 28));
        controls.add(layouts);
        controls.add(button("Duplicate layout", this::duplicateLayout));
        controls.add(button("Expand bounds", this::expandBounds));
        controls.add(new JLabel("Tool"));
        controls.add(tool);
        controls.add(new JLabel("Barrier art"));
        controls.add(appearance);
        controls.add(new JLabel("Turret structure"));
        structure.setPreferredSize(new Dimension(230, 28));
        controls.add(structure);
        add(controls, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setBorder(BorderFactory.createTitledBorder(
                "Click to paint · Select/move: click a turret, then a destination pad"));
        add(scroll, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        layouts.addActionListener(event -> {
            movingTurret = null;
            canvas.repaint();
        });
        reloadLayouts(null);
    }

    void reloadLayouts(String preferredId) {
        layouts.removeAllItems();
        structure.removeAllItems();
        try {
            for (JSONObject object : page.document().layoutObjects()) {
                LayoutItem item = new LayoutItem(object);
                layouts.addItem(item);
                if (item.id().equals(preferredId)) layouts.setSelectedItem(item);
            }
            for (String id : page.document().structureIds()) structure.addItem(id);
        } catch (Exception failure) {
            status.setText("Could not load layouts: " + failure.getMessage());
        }
        canvas.repaint();
    }

    void repaintCanvas() { canvas.repaint(); }

    private void duplicateLayout() {
        LayoutItem current = (LayoutItem) layouts.getSelectedItem();
        if (current == null) return;
        String variant = JOptionPane.showInputDialog(this,
                "New LARGE variant id", current.json().optString("variant", "variant") + "-copy");
        if (variant == null) return;
        try {
            final JSONObject[] created = new JSONObject[1];
            page.emplacementChanged(() -> created[0] = page.document()
                    .duplicateLargeLayout(current.id(), variant));
            reloadLayouts(created[0].getString("id"));
            status.setText("Created " + created[0].getString("id"));
        } catch (Exception failure) {
            status.setText("Could not duplicate layout: " + failure.getMessage());
        }
    }

    private void expandBounds() {
        JSONObject current = selectedLayout();
        if (current == null) return;
        try {
            page.emplacementChanged(() -> {
                JSONObject bounds = current.getJSONObject("bounds");
                JSONArray min = bounds.getJSONArray("minOffset");
                JSONArray max = bounds.getJSONArray("maxOffset");
                int width = max.getInt(0) - min.getInt(0) + 1;
                int height = max.getInt(1) - min.getInt(1) + 1;
                if (width + 2 > 15 || height + 2 > 15) {
                    throw new JSONException("layout would exceed the 15-cell axis limit");
                }
                bounds.put("minOffset", pair(min.getInt(0) - 1, min.getInt(1) - 1));
                bounds.put("maxOffset", pair(max.getInt(0) + 1, max.getInt(1) + 1));
            });
            status.setText("Expanded layout bounds by one cell on every side");
        } catch (Exception failure) {
            status.setText("Could not expand bounds: " + failure.getMessage());
        }
    }

    private static JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JSONObject selectedLayout() {
        LayoutItem item = (LayoutItem) layouts.getSelectedItem();
        return item != null ? item.json() : null;
    }

    private void applyAt(int x, int y) {
        JSONObject layout = selectedLayout();
        if (layout == null) return;
        Tool selected = (Tool) tool.getSelectedItem();
        try {
            JSONObject existingCell = cellAt(layout, x, y);
            if (selected != Tool.SELECT_MOVE && existingCell != null
                    && "drone-hub".equals(existingCell.optString("occupant", ""))) {
                throw new JSONException("the required drone-hub occupant is protected");
            }
            switch (selected) {
                case SELECT_MOVE -> selectOrMove(layout, x, y);
                case BARRIER -> page.emplacementChanged(() -> paintBarrier(layout, x, y));
                case PAD -> page.emplacementChanged(() -> paintPad(layout, x, y));
                case TURRET -> page.emplacementChanged(() -> paintTurret(layout, x, y));
                case ERASE -> page.emplacementChanged(() -> erase(layout, x, y));
            }
            status.setText(selected + " at [" + x + ", " + y + "]");
        } catch (Exception failure) {
            status.setText("Edit rejected: " + failure.getMessage());
        }
    }

    private void selectOrMove(JSONObject layout, int x, int y) throws Exception {
        JSONObject placement = turretAt(layout, x, y);
        if (movingTurret == null) {
            if (placement != null) {
                movingTurret = new Offset(x, y);
                status.setText("Selected turret at " + movingTurret + "; click a pad to move it");
                canvas.repaint();
            } else {
                status.setText("Select/move needs a turret cell first");
            }
            return;
        }
        if (turretAt(layout, x, y) != null) throw new JSONException("destination already has a turret");
        JSONObject cell = cellAt(layout, x, y);
        if (cell == null || !"pad".equals(cell.getString("kind"))
                || "drone-hub".equals(cell.optString("occupant", ""))) {
            throw new JSONException("destination must be an ordinary pad");
        }
        Offset source = movingTurret;
        page.emplacementChanged(() -> {
            JSONObject moving = turretAt(layout, source.x(), source.y());
            if (moving == null) throw new JSONException("selected turret no longer exists");
            moving.put("offset", pair(x, y));
        });
        movingTurret = null;
    }

    private void paintBarrier(JSONObject layout, int x, int y) throws JSONException {
        removeTurret(layout, x, y);
        removeCell(layout, x, y);
        int fx = Integer.compare(x, 0);
        int fy = Integer.compare(y, 0);
        if (fx == 0 && fy == 0) fy = 1;
        layout.getJSONArray("cells").put(new JSONObject()
                .put("offset", pair(x, y)).put("kind", "barrier")
                .put("appearance", appearance.getSelectedItem())
                .put("facing", pair(fx, fy)));
    }

    private void paintPad(JSONObject layout, int x, int y) throws JSONException {
        removeTurret(layout, x, y);
        removeCell(layout, x, y);
        layout.getJSONArray("cells").put(new JSONObject()
                .put("offset", pair(x, y)).put("kind", "pad"));
    }

    private void paintTurret(JSONObject layout, int x, int y) throws JSONException {
        String structureId = (String) structure.getSelectedItem();
        if (structureId == null) throw new JSONException("select a turret structure");
        JSONObject cell = cellAt(layout, x, y);
        if (cell == null || !"pad".equals(cell.getString("kind"))
                || "drone-hub".equals(cell.optString("occupant", ""))) {
            paintPad(layout, x, y);
        }
        JSONObject existing = turretAt(layout, x, y);
        if (existing != null) {
            existing.put("structure", structureId);
        } else {
            layout.getJSONArray("turrets").put(new JSONObject()
                    .put("offset", pair(x, y)).put("structure", structureId));
        }
    }

    private static void erase(JSONObject layout, int x, int y) throws JSONException {
        JSONObject cell = cellAt(layout, x, y);
        if (cell != null && "drone-hub".equals(cell.optString("occupant", ""))) {
            throw new JSONException("the required drone-hub occupant cannot be erased");
        }
        removeTurret(layout, x, y);
        removeCell(layout, x, y);
    }

    private static JSONObject cellAt(JSONObject layout, int x, int y) throws JSONException {
        return at(layout.getJSONArray("cells"), x, y);
    }

    private static JSONObject turretAt(JSONObject layout, int x, int y) throws JSONException {
        return at(layout.getJSONArray("turrets"), x, y);
    }

    private static JSONObject at(JSONArray array, int x, int y) throws JSONException {
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            JSONArray offset = item.getJSONArray("offset");
            if (offset.getInt(0) == x && offset.getInt(1) == y) return item;
        }
        return null;
    }

    private static void removeCell(JSONObject layout, int x, int y) throws JSONException {
        removeAt(layout.getJSONArray("cells"), x, y);
    }

    private static void removeTurret(JSONObject layout, int x, int y) throws JSONException {
        removeAt(layout.getJSONArray("turrets"), x, y);
    }

    private static void removeAt(JSONArray array, int x, int y) throws JSONException {
        for (int index = array.length() - 1; index >= 0; index--) {
            JSONObject item = array.getJSONObject(index);
            JSONArray offset = item.getJSONArray("offset");
            if (offset.getInt(0) == x && offset.getInt(1) == y) array.remove(index);
        }
    }

    private static JSONArray pair(int x, int y) {
        return new JSONArray().put(x).put(y);
    }

    private final class GridCanvas extends JPanel {
        GridCanvas() {
            setPreferredSize(new Dimension(860, 680));
            setBackground(new Color(0x0D, 0x12, 0x19));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    JSONObject current = selectedLayout();
                    if (current == null) return;
                    try {
                        Bounds bounds = layoutBounds(current);
                        int originX = (getWidth() - bounds.width() * CELL) / 2;
                        int originY = (getHeight() - bounds.height() * CELL) / 2;
                        int column = (event.getX() - originX) / CELL;
                        int row = (event.getY() - originY) / CELL;
                        int x = bounds.minX() + column;
                        int y = bounds.maxY() - row;
                        if (x >= bounds.minX() && x <= bounds.maxX()
                                && y >= bounds.minY() && y <= bounds.maxY()) applyAt(x, y);
                    } catch (Exception failure) {
                        status.setText("Could not map grid click: " + failure.getMessage());
                    }
                }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            JSONObject current = selectedLayout();
            if (current == null) return;
            Graphics2D draw = (Graphics2D) graphics.create();
            draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            try {
                Bounds bounds = layoutBounds(current);
                int originX = (getWidth() - bounds.width() * CELL) / 2;
                int originY = (getHeight() - bounds.height() * CELL) / 2;
                for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
                    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                        int px = originX + (x - bounds.minX()) * CELL;
                        int py = originY + (bounds.maxY() - y) * CELL;
                        JSONObject cell = cellAt(current, x, y);
                        JSONObject turret = turretAt(current, x, y);
                        Color fill = EMPTY;
                        String label = "";
                        if (cell != null) {
                            if ("barrier".equals(cell.getString("kind"))) {
                                fill = BARRIER;
                                label = "B";
                            } else if ("drone-hub".equals(cell.optString("occupant", ""))) {
                                fill = HUB;
                                label = "HUB";
                            } else {
                                fill = PAD;
                                label = "P";
                            }
                        }
                        if (turret != null) {
                            fill = TURRET;
                            label = shortStructure(turret.getString("structure"));
                        }
                        draw.setColor(fill);
                        draw.fillRoundRect(px + 3, py + 3, CELL - 6, CELL - 6, 9, 9);
                        draw.setColor(new Color(0x61, 0x70, 0x82));
                        draw.drawRect(px, py, CELL, CELL);
                        if (x == 0 && y == 0) {
                            draw.setColor(Color.WHITE);
                            draw.setStroke(new BasicStroke(2f));
                            draw.drawRect(px + 2, py + 2, CELL - 4, CELL - 4);
                        }
                        if (movingTurret != null && movingTurret.x() == x && movingTurret.y() == y) {
                            draw.setColor(new Color(0xFF, 0xEB, 0x70));
                            draw.setStroke(new BasicStroke(3f));
                            draw.drawRect(px + 5, py + 5, CELL - 10, CELL - 10);
                        }
                        draw.setColor(Color.WHITE);
                        draw.setFont(draw.getFont().deriveFont(Font.BOLD, 11f));
                        draw.drawString(label, px + 8, py + 22);
                        draw.setFont(draw.getFont().deriveFont(Font.PLAIN, 9f));
                        draw.drawString(x + "," + y, px + 7, py + CELL - 8);
                    }
                }
            } catch (Exception failure) {
                draw.setColor(Color.RED);
                draw.drawString(failure.getMessage(), 20, 30);
            } finally {
                draw.dispose();
            }
        }
    }

    private static Bounds layoutBounds(JSONObject layout) throws JSONException {
        JSONObject bounds = layout.getJSONObject("bounds");
        JSONArray min = bounds.getJSONArray("minOffset");
        JSONArray max = bounds.getJSONArray("maxOffset");
        return new Bounds(min.getInt(0), min.getInt(1), max.getInt(0), max.getInt(1));
    }

    private static String shortStructure(String id) {
        int index = id.lastIndexOf('-');
        String value = index >= 0 ? id.substring(index + 1) : id;
        return value.length() <= 7 ? value.toUpperCase() : value.substring(0, 7).toUpperCase();
    }

    private enum Tool {
        SELECT_MOVE("Select / move turret"), BARRIER("Paint barrier"),
        PAD("Paint pad"), TURRET("Add / replace turret"), ERASE("Erase cell");
        private final String label;
        Tool(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private record Offset(int x, int y) {}

    private record Bounds(int minX, int minY, int maxX, int maxY) {
        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
    }

    private record LayoutItem(JSONObject json) {
        String id() { return json.optString("id", "layout"); }
        @Override public String toString() {
            return json.optString("tier", "?").toUpperCase() + " · "
                    + json.optString("variant", "default") + " · " + id();
        }
    }
}
