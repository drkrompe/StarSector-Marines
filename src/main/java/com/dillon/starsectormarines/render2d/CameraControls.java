package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Flying a {@link BattleCamera} by hand: drag to pan, wheel to zoom, WASD to
 * walk the view.
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

    private final boolean shiftRightReserved;

    private boolean panDragging;
    private boolean rightGestureActive;
    private float rightDownX;
    private float rightDownY;
    private float lastDragX;
    private float lastDragY;
    private boolean panKeyW;
    private boolean panKeyA;
    private boolean panKeyS;
    private boolean panKeyD;

    public CameraControls() {
        this(false);
    }

    /**
     * @param shiftRightReserved whether shift plus right-drag belongs to
     *     something else on this screen. The battle view spends it on the debug
     *     damage gesture; a view with nothing to click reserves nothing.
     */
    public CameraControls(boolean shiftRightReserved) {
        this.shiftRightReserved = shiftRightReserved;
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
        if (events == null || camera == null || space == null) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            // Zoom to the cursor, and only over the view itself, so scrolling
            // elsewhere does not fight whatever is under the pointer.
            if (event.isMouseScrollEvent()) {
                float wheelX = space.x(event.getX());
                float wheelY = space.y(event.getY());
                if (!camera.containsScreen(wheelX, wheelY)) continue;
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
                rightGestureActive = true;
                rightDownX = downX;
                rightDownY = downY;
                panDragging = rightClickHandler == null;
                lastDragX = downX;
                lastDragY = downY;
                event.consume();
                continue;
            }
            if (event.isRMBUpEvent()) {
                if (rightGestureActive) {
                    float upX = space.x(event.getX());
                    float upY = space.y(event.getY());
                    if (!panDragging && rightClickHandler != null
                            && camera.containsScreen(upX, upY)) {
                        rightClickHandler.onRightClick(upX, upY);
                    }
                    event.consume();
                }
                rightGestureActive = false;
                panDragging = false;
                continue;
            }
            if (rightGestureActive && event.isMouseMoveEvent()) {
                float x = space.x(event.getX());
                float y = space.y(event.getY());
                if (!panDragging) {
                    float dx = x - rightDownX;
                    float dy = y - rightDownY;
                    float thresholdSq = RIGHT_DRAG_THRESHOLD_PX
                            * RIGHT_DRAG_THRESHOLD_PX;
                    if (dx * dx + dy * dy >= thresholdSq) {
                        panDragging = true;
                    }
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
     * Move the camera for however long a key has been held.
     *
     * @param dt real seconds, not simulation seconds — the view keeps moving at
     *     the same rate whatever the sim is doing, including while it is paused
     */
    public void advance(float dt, BattleCamera camera) {
        if (camera == null || dt <= 0f) return;
        float dx = (panKeyD ? 1f : 0f) - (panKeyA ? 1f : 0f);
        float dy = (panKeyW ? 1f : 0f) - (panKeyS ? 1f : 0f);
        if (dx == 0f && dy == 0f) return;
        // Deliberately not normalised: diagonal is faster, which is what a
        // top-down map is expected to feel like.
        camera.panByCells(dx * KEY_PAN_CELLS_PER_SEC * dt,
                dy * KEY_PAN_CELLS_PER_SEC * dt);
    }

    /**
     * Forget what is held. A screen that loses input never sees the key-up, and
     * a camera left panning into a corner while the player is somewhere else is
     * the state this exists to avoid.
     */
    public void release() {
        rightGestureActive = false;
        panDragging = false;
        panKeyW = false;
        panKeyA = false;
        panKeyS = false;
        panKeyD = false;
    }

    /** Whether the view is being dragged right now. */
    public boolean dragging() {
        return panDragging;
    }
}
