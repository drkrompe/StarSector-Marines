package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Flying a {@link BattleCamera} by hand: middle-drag to pan, wheel to zoom,
 * WASD to walk the view.
 *
 * <p>The right button belongs to the screen when the screen wants it. A view
 * that gives orders takes a right click as an order and a right drag as a
 * drawn one (a line to hold), so panning lives on the middle button there. A
 * view with nothing to order keeps right-drag panning as well, since a right
 * button that does nothing is a worse answer than a second way to pan.
 *
 * <p>A screen may also turn on <b>edge scrolling</b>: resting the pointer against the edge of
 * the window walks the view that way, as an RTS does. It is a screen's choice, since only a
 * view the player commands from wants the edges of the window to mean anything.
 *
 * <p>Extracted rather than written twice. Every screen that shows a world the
 * player looks around — a battle, a deck — wants the same gestures, and a second
 * copy of them is how two views of the same game end up disagreeing about which
 * button pans and how fast. The camera itself already owns where it may sit and
 * how far it may zoom; this owns only the hand on it.
 *
 * <p><b>Held keys are integrated on advance, not on the event.</b> Key repeat
 * fires at whatever rate the platform decides, so panning from key events makes
 * the camera's speed a property of the operating system. The flags are set here
 * and the movement happens per delta time.
 */
public final class CameraControls {

    /** Cells per second of keyboard pan, in world cells. */
    private static final float KEY_PAN_CELLS_PER_SEC = 18f;
    /** Pointer travel required before a battle RMB gesture becomes a pan. */
    private static final float RIGHT_DRAG_THRESHOLD_PX = 4f;
    /** How close to the window's edge, in pixels, the pointer must rest to scroll the view. */
    static final float EDGE_SCROLL_PX = 6f;

    private final boolean shiftRightReserved;
    private final BooleanSupplier middleHeld;

    private boolean panDragging;
    private boolean orderDragging;
    private boolean middlePanning;
    private float middleLastX;
    private float middleLastY;
    private boolean rightGestureActive;
    private float rightDownX;
    private float rightDownY;
    private float lastDragX;
    private float lastDragY;
    private boolean panKeyW;
    private boolean panKeyA;
    private boolean panKeyS;
    private boolean panKeyD;
    private boolean edgeScroll;
    private float edgeX;
    private float edgeY;
    private float edgeW;
    private float edgeH;
    private boolean pointerSeen;
    private float pointerX;
    private float pointerY;

    public CameraControls() {
        this(false);
    }

    /**
     * @param shiftRightReserved whether shift plus right-drag belongs to
     *     something else on this screen. The battle view spends it on the debug
     *     damage gesture; a view with nothing to click reserves nothing.
     */
    public CameraControls(boolean shiftRightReserved) {
        this(shiftRightReserved, CameraControls::middleButtonDown);
    }

    /**
     * @param middleHeld whether the middle button is down now. The game's input
     *     events do not report the middle button, so it is read from the mouse
     *     itself; tests supply their own.
     */
    public CameraControls(boolean shiftRightReserved, BooleanSupplier middleHeld) {
        this.shiftRightReserved = shiftRightReserved;
        this.middleHeld = middleHeld;
    }

    /**
     * Turns edge scrolling on against the given window rectangle, in the game's pointer
     * coordinates, or off when {@code w} or {@code h} is not positive. Called whenever the
     * screen's own rectangle is laid out.
     */
    public void edgeScroll(float x, float y, float w, float h) {
        edgeScroll = w > 0f && h > 0f;
        edgeX = x;
        edgeY = y;
        edgeW = w;
        edgeH = h;
    }

    private static boolean middleButtonDown() {
        return Mouse.isCreated() && Mouse.isButtonDown(2);
    }

    /**
     * Where the camera's viewport lives, relative to the coordinates the game
     * reports pointer events in.
     *
     * <p>A battle camera's viewport is stated in screen pixels, so its answer
     * is the identity. A camera drawn into a canvas has a viewport in that
     * canvas's own surface pixels, and handing it screen coordinates would
     * have it compare two different spaces and decide the pointer was
     * somewhere it was not.
     */
    public interface PointerSpace {

        PointerSpace SCREEN = new PointerSpace() {
            @Override
            public float x(float screenX) {
                return screenX;
            }

            @Override
            public float y(float screenY) {
                return screenY;
            }
        };

        float x(float screenX);

        float y(float screenY);
    }

    /** Optional owner of an RMB click that did not cross the pan threshold. */
    @FunctionalInterface
    public interface RightClickHandler {
        void onRightClick(float pointerX, float pointerY);
    }

    /**
     * Optional owner of an RMB gesture that did cross it. Called with the press point and the
     * pointer on every move, then once more on release; nothing pans while one is installed.
     */
    @FunctionalInterface
    public interface RightDragHandler {
        void onRightDrag(float startX, float startY, float pointerX, float pointerY, boolean released);
    }

    /** Read the frame's input and move the camera with it. */
    public void process(List<InputEventAPI> events, BattleCamera camera) {
        process(events, camera, PointerSpace.SCREEN);
    }

    /** @param space converts the game's pointer coordinates into the camera's */
    public void process(List<InputEventAPI> events, BattleCamera camera,
                        PointerSpace space) {
        process(events, camera, space, null);
    }

    /**
     * Processes camera input while reserving a stationary RMB gesture for the
     * supplied click owner. Passing {@code null} preserves immediate RMB-drag
     * behavior for camera-only views.
     */
    public void process(List<InputEventAPI> events, BattleCamera camera,
                        PointerSpace space, RightClickHandler rightClickHandler) {
        process(events, camera, space, rightClickHandler, (x, y) -> false);
    }

    /** Chrome refuses new gestures; a drag already begun in the world may finish over it. */
    public void process(List<InputEventAPI> events, BattleCamera camera,
                        PointerSpace space, RightClickHandler rightClickHandler,
                        BiPredicate<Float, Float> chrome) {
        process(events, camera, space, rightClickHandler, null, chrome);
    }

    /** As above, with right drags handed to {@code rightDragHandler} instead of panning. */
    public void process(List<InputEventAPI> events, BattleCamera camera,
                        PointerSpace space, RightClickHandler rightClickHandler,
                        RightDragHandler rightDragHandler,
                        BiPredicate<Float, Float> chrome) {
        if (events == null || camera == null || space == null) return;
        for (InputEventAPI event : events) {
            if (event.isMouseMoveEvent()) {
                // Recorded whoever owns the move: an edge is an edge over chrome too.
                pointerSeen = true;
                pointerX = event.getX();
                pointerY = event.getY();
            }
            if (event.isConsumed()) {
                if (event.isRMBUpEvent()) {
                    rightGestureActive = false;
                    panDragging = false;
                    orderDragging = false;
                }
                continue;
            }

            if (event.isMouseMoveEvent() && middleDrag(event, camera, space, chrome)) {
                event.consume();
                continue;
            }

            // Zoom to the cursor, and only over the view itself, so scrolling
            // elsewhere does not fight whatever is under the pointer.
            if (event.isMouseScrollEvent()) {
                float wheelX = space.x(event.getX());
                float wheelY = space.y(event.getY());
                if (!camera.containsScreen(wheelX, wheelY)) continue;
                if (chrome.test(wheelX, wheelY)) { event.consume(); continue; }
                // Wheel deltas arrive as raw LWJGL values — typically a
                // hundred and twenty per notch on Windows, one on some Linux
                // builds — so they are normalised to a notch either way.
                int raw = event.getEventValue();
                float notches = raw > 0 ? 1f : (raw < 0 ? -1f : 0f);
                camera.zoomAt(notches, wheelX, wheelY);
                event.consume();
                continue;
            }

            if (event.isRMBDownEvent() && !(shiftRightReserved && event.isShiftDown())) {
                float downX = space.x(event.getX());
                float downY = space.y(event.getY());
                if (!camera.containsScreen(downX, downY)) continue;
                if (chrome.test(downX, downY)) { event.consume(); continue; }
                rightGestureActive = true;
                rightDownX = downX;
                rightDownY = downY;
                panDragging = rightClickHandler == null && rightDragHandler == null;
                orderDragging = false;
                lastDragX = downX;
                lastDragY = downY;
                event.consume();
                continue;
            }
            if (event.isRMBUpEvent()) {
                if (rightGestureActive) {
                    float upX = space.x(event.getX());
                    float upY = space.y(event.getY());
                    if (orderDragging) {
                        rightDragHandler.onRightDrag(rightDownX, rightDownY, upX, upY, true);
                    } else if (!panDragging && rightClickHandler != null
                            && camera.containsScreen(upX, upY)) {
                        rightClickHandler.onRightClick(upX, upY);
                    }
                    event.consume();
                }
                rightGestureActive = false;
                panDragging = false;
                orderDragging = false;
                continue;
            }
            if (rightGestureActive && event.isMouseMoveEvent()) {
                float x = space.x(event.getX());
                float y = space.y(event.getY());
                if (!panDragging && !orderDragging) {
                    float dx = x - rightDownX;
                    float dy = y - rightDownY;
                    float thresholdSq = RIGHT_DRAG_THRESHOLD_PX
                            * RIGHT_DRAG_THRESHOLD_PX;
                    if (dx * dx + dy * dy >= thresholdSq) {
                        if (rightDragHandler != null) orderDragging = true;
                        else panDragging = true;
                    }
                }
                if (orderDragging) {
                    rightDragHandler.onRightDrag(rightDownX, rightDownY, x, y, false);
                    event.consume();
                    continue;
                }
                if (!panDragging) {
                    event.consume();
                    continue;
                }
                // Dragging right pulls the world right, so the camera goes
                // left over it. panByPixels negates internally; this passes the
                // raw mouse delta.
                camera.panByPixels(x - lastDragX, y - lastDragY);
                lastDragX = x;
                lastDragY = y;
                event.consume();
                continue;
            }

            if (event.isKeyDownEvent() || event.isKeyUpEvent()) {
                boolean down = event.isKeyDownEvent();
                int key = event.getEventValue();
                if (key == Keyboard.KEY_W || key == Keyboard.KEY_UP) panKeyW = down;
                else if (key == Keyboard.KEY_S || key == Keyboard.KEY_DOWN) panKeyS = down;
                else if (key == Keyboard.KEY_A || key == Keyboard.KEY_LEFT) panKeyA = down;
                else if (key == Keyboard.KEY_D || key == Keyboard.KEY_RIGHT) panKeyD = down;
            }
        }
    }

    /**
     * Pans while the middle button is held, from the point it was first seen down. Returns
     * whether the move was the pan's. A pan begins only over the view and not over chrome; once
     * begun it follows the pointer anywhere until the button comes up.
     */
    private boolean middleDrag(InputEventAPI event, BattleCamera camera, PointerSpace space,
                               BiPredicate<Float, Float> chrome) {
        float x = space.x(event.getX());
        float y = space.y(event.getY());
        if (!middleHeld.getAsBoolean()) {
            middlePanning = false;
            return false;
        }
        if (!middlePanning) {
            if (!camera.containsScreen(x, y) || chrome.test(x, y)) return false;
            middlePanning = true;
        } else {
            camera.panByPixels(x - middleLastX, y - middleLastY);
        }
        middleLastX = x;
        middleLastY = y;
        return true;
    }

    /**
     * Move the camera for however long a key has been held.
     *
     * @param dt real seconds, not simulation seconds — the view keeps moving at
     *     the same rate whatever the sim is doing, including while it is paused
     */
    public void advance(float dt, BattleCamera camera) {
        if (camera == null || dt <= 0f) return;
        float dx = (panKeyD ? 1f : 0f) - (panKeyA ? 1f : 0f);
        float dy = (panKeyW ? 1f : 0f) - (panKeyS ? 1f : 0f);
        if (edgeScroll && pointerSeen && !middlePanning && pointerInWindow()) {
            // An axis the keys already drive is left to them, so a key and an edge together are
            // one speed rather than two.
            if (dx == 0f) dx = edgeDirection(pointerX, edgeX, edgeW);
            if (dy == 0f) dy = edgeDirection(pointerY, edgeY, edgeH);
        }
        if (dx == 0f && dy == 0f) return;
        // Deliberately not normalised: diagonal is faster, which is what a
        // top-down map is expected to feel like.
        camera.panByCells(dx * KEY_PAN_CELLS_PER_SEC * dt,
                dy * KEY_PAN_CELLS_PER_SEC * dt);
    }

    /** A windowed game whose pointer has left the window is not resting on its edge. */
    private static boolean pointerInWindow() {
        return !Mouse.isCreated() || Mouse.isInsideWindow();
    }

    /** -1 against the low edge, +1 against the high one, else 0. A pointer outside is neither. */
    private static float edgeDirection(float p, float lo, float extent) {
        if (p < lo || p > lo + extent) return 0f;
        if (p <= lo + EDGE_SCROLL_PX) return -1f;
        if (p >= lo + extent - EDGE_SCROLL_PX) return 1f;
        return 0f;
    }

    /**
     * Forget what is held. A screen that loses input never sees the key-up, and
     * a camera left panning into a corner while the player is somewhere else is
     * the state this exists to avoid.
     */
    public void release() {
        rightGestureActive = false;
        panDragging = false;
        orderDragging = false;
        middlePanning = false;
        panKeyW = false;
        panKeyA = false;
        panKeyS = false;
        panKeyD = false;
        // A pointer last seen at an edge before the screen lost input must not keep scrolling.
        pointerSeen = false;
    }

    /** Whether the view is being dragged right now. */
    public boolean dragging() {
        return panDragging || middlePanning;
    }
}
