package com.dillon.starsectormarines.tools.layerauthoring;

import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.FrameDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.LayerDefinition;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument.UnitComposition;
import com.dillon.starsectormarines.tools.layerauthoring.CompositionRenderer.RenderedLayer;

import javax.swing.JPanel;
import java.awt.Cursor;
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

/** Interactive frame canvas: click layers, drag pivots, and manipulate transforms. */
final class CompositionCanvas extends JPanel {

    static final int ROTATION_HANDLE_RADIUS = 38;
    static final int ROTATION_HANDLE_HIT_RADIUS = 10;

    private enum DragMode { NONE, MOVE, ROTATE }

    private final CompositionRenderer renderer;
    private UnitComposition unit;
    private FrameDefinition frame;
    private LayerDefinition selected;
    private boolean interactive = true;
    private List<RenderedLayer> rendered = new ArrayList<>();
    private Consumer<LayerDefinition> selectionListener = ignored -> { };
    private Runnable changeListener = () -> { };
    private Runnable changeStarted = () -> { };
    private Runnable changeFinished = () -> { };
    private Point dragStart;
    private double dragOffsetX;
    private double dragOffsetY;
    private DragMode dragMode = DragMode.NONE;
    private Point rotationPivot;
    private double rotationStartPointerAngle;
    private double rotationStartLayerAngle;

    CompositionCanvas(CompositionRenderer renderer) {
        this.renderer = renderer;
        setPreferredSize(new Dimension(720, 720));
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { beginDrag(event); }
            @Override public void mouseDragged(MouseEvent event) { drag(event); }
            @Override public void mouseReleased(MouseEvent event) { finishDrag(); }
            @Override public void mouseMoved(MouseEvent event) { updateCursor(event.getPoint()); }
            @Override public void mouseWheelMoved(MouseWheelEvent event) { wheel(event); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        setToolTipText("Drag layer: position · drag gold handle: rotate · "
                + "wheel: uniform scale · Shift-wheel: X scale · Alt-wheel: Y scale");
    }

    void selection(UnitComposition unit, FrameDefinition frame, LayerDefinition layer) {
        this.unit = unit;
        this.frame = frame;
        this.selected = layer;
        interactive = true;
        repaint();
    }

    void preview(UnitComposition unit, FrameDefinition frame) {
        this.unit = unit;
        this.frame = frame;
        interactive = false;
        repaint();
    }

    void onSelection(Consumer<LayerDefinition> listener) {
        selectionListener = listener;
    }

    void onChange(Runnable listener) {
        changeListener = listener;
    }

    void onChangeStarted(Runnable listener) {
        changeStarted = listener;
    }

    void onChangeFinished(Runnable listener) {
        changeFinished = listener;
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
        if (!interactive) return;
        requestFocusInWindow();
        if (overRotationHandle(event.getPoint())) {
            changeStarted.run();
            dragMode = DragMode.ROTATE;
            rotationPivot = pivotPoint(selected, getWidth(), getHeight());
            rotationStartPointerAngle = pointerAngle(rotationPivot, event.getPoint());
            rotationStartLayerAngle = selected.angleDegrees();
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            return;
        }
        LayerDefinition hit = hit(event.getPoint());
        if (hit != null) {
            selected = hit;
            selectionListener.accept(hit);
        }
        if (selected != null) {
            changeStarted.run();
            dragMode = DragMode.MOVE;
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
        if (selected == null || dragMode == DragMode.NONE) return;
        if (dragMode == DragMode.ROTATE) {
            selected.angleDegrees(round(draggedAngle(rotationStartLayerAngle,
                    rotationStartPointerAngle,
                    pointerAngle(rotationPivot, event.getPoint())), 3));
            changed();
            return;
        }
        if (dragStart == null) return;
        double pixelsPerUnit = CompositionRenderer.pixelsPerUnit(getWidth(), getHeight());
        double x = dragOffsetX + (event.getX() - dragStart.x) / pixelsPerUnit;
        double y = dragOffsetY - (event.getY() - dragStart.y) / pixelsPerUnit;
        selected.offset(round(x, 5), round(y, 5));
        changed();
    }

    private void wheel(MouseWheelEvent event) {
        if (!interactive || selected == null) return;
        changeStarted.run();
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
        changeFinished.run();
        event.consume();
    }

    private void finishDrag() {
        if (dragMode != DragMode.NONE) changeFinished.run();
        dragMode = DragMode.NONE;
        dragStart = null;
        rotationPivot = null;
        setCursor(Cursor.getDefaultCursor());
    }

    private void updateCursor(Point point) {
        if (!interactive || selected == null || dragMode != DragMode.NONE) return;
        setCursor(Cursor.getPredefinedCursor(overRotationHandle(point)
                ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private boolean overRotationHandle(Point point) {
        if (selected == null) return false;
        Point pivot = pivotPoint(selected, getWidth(), getHeight());
        Point handle = rotationHandlePoint(pivot, selected.angleDegrees());
        return handle.distance(point) <= ROTATION_HANDLE_HIT_RADIUS;
    }

    private void changed() {
        changeListener.run();
        repaint();
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10.0, places);
        return Math.round(value * factor) / factor;
    }

    static Point pivotPoint(LayerDefinition layer, int width, int height) {
        double pixelsPerUnit = CompositionRenderer.pixelsPerUnit(width, height);
        return new Point((int) Math.round(width * 0.5
                + layer.offsetX() * pixelsPerUnit),
                (int) Math.round(height * 0.54
                        - layer.offsetY() * pixelsPerUnit));
    }

    static Point rotationHandlePoint(Point pivot, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        return new Point((int) Math.round(pivot.x
                - Math.sin(radians) * ROTATION_HANDLE_RADIUS),
                (int) Math.round(pivot.y
                        - Math.cos(radians) * ROTATION_HANDLE_RADIUS));
    }

    static double pointerAngle(Point pivot, Point pointer) {
        return Math.toDegrees(Math.atan2(pivot.x - pointer.x,
                pivot.y - pointer.y));
    }

    static double draggedAngle(double startLayerAngle, double startPointerAngle,
                               double currentPointerAngle) {
        double delta = normalizeAngle(currentPointerAngle - startPointerAngle);
        return normalizeAngle(startLayerAngle + delta);
    }

    private static double normalizeAngle(double value) {
        double normalized = (value + 180.0) % 360.0;
        if (normalized < 0.0) normalized += 360.0;
        return normalized - 180.0;
    }
}
