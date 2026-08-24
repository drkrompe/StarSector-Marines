package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.FrameDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.LayerDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.UnitComposition;
import com.dillon.starsectormarines.tools.layerauthoring.CompositionRenderer.RenderedLayer;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Interactive frame canvas: click layers, drag pivots, wheel scale, Ctrl-wheel rotate. */
final class CompositionCanvas extends JPanel {

    private final CompositionRenderer renderer;
    private UnitComposition unit;
    private FrameDefinition frame;
    private LayerDefinition selected;
    private List<RenderedLayer> rendered = new ArrayList<>();
    private Consumer<LayerDefinition> selectionListener = ignored -> { };
    private Runnable changeListener = () -> { };
    private Point dragStart;
    private double dragOffsetX;
    private double dragOffsetY;

    CompositionCanvas(CompositionRenderer renderer) {
        this.renderer = renderer;
        setPreferredSize(new Dimension(720, 720));
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { beginDrag(event); }
            @Override public void mouseDragged(MouseEvent event) { drag(event); }
            @Override public void mouseReleased(MouseEvent event) { dragStart = null; }
            @Override public void mouseWheelMoved(MouseWheelEvent event) { wheel(event); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        setToolTipText("Drag: position · wheel: uniform scale · Shift-wheel: X scale · "
                + "Alt-wheel: Y scale · Ctrl-wheel: rotate");
    }

    void selection(UnitComposition unit, FrameDefinition frame, LayerDefinition layer) {
        this.unit = unit;
        this.frame = frame;
        this.selected = layer;
        repaint();
    }

    void onSelection(Consumer<LayerDefinition> listener) {
        selectionListener = listener;
    }

    void onChange(Runnable listener) {
        changeListener = listener;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (unit == null || frame == null) return;
        Graphics2D copy = (Graphics2D) graphics.create();
        rendered = renderer.renderFrame(copy, unit, frame, getWidth(), getHeight(),
                selected != null ? selected.id() : null, true);
        copy.dispose();
    }

    private void beginDrag(MouseEvent event) {
        requestFocusInWindow();
        LayerDefinition hit = hit(event.getPoint());
        if (hit != null) {
            selected = hit;
            selectionListener.accept(hit);
        }
        if (selected != null) {
            dragStart = event.getPoint();
            dragOffsetX = selected.offsetX();
            dragOffsetY = selected.offsetY();
        }
        repaint();
    }

    private LayerDefinition hit(Point point) {
        for (int index = rendered.size() - 1; index >= 0; index--) {
            RenderedLayer candidate = rendered.get(index);
            if (candidate.contains(point)) return candidate.layer();
        }
        return null;
    }

    private void drag(MouseEvent event) {
        if (selected == null || dragStart == null) return;
        double pixelsPerUnit = CompositionRenderer.pixelsPerUnit(getWidth(), getHeight());
        double x = dragOffsetX + (event.getX() - dragStart.x) / pixelsPerUnit;
        double y = dragOffsetY - (event.getY() - dragStart.y) / pixelsPerUnit;
        selected.offset(round(x, 5), round(y, 5));
        changed();
    }

    private void wheel(MouseWheelEvent event) {
        if (selected == null) return;
        double turns = event.getPreciseWheelRotation();
        if (event.isControlDown()) {
            selected.angleDegrees(round(selected.angleDegrees() - turns * 2.0, 3));
        } else {
            double factor = Math.pow(1.04, -turns);
            double x = selected.scaleX();
            double y = selected.scaleY();
            if (!event.isAltDown()) x = Math.max(0.01, x * factor);
            if (!event.isShiftDown()) y = Math.max(0.01, y * factor);
            selected.scale(round(x, 5), round(y, 5));
        }
        changed();
        event.consume();
    }

    private void changed() {
        changeListener.run();
        repaint();
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10.0, places);
        return Math.round(value * factor) / factor;
    }
}
