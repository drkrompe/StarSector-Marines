package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * S0b probe — <b>verified facts 8 + 9 + 12: free camera, input override, and the
 * "own the top visible layer by starving the HUD" result.</b>
 *
 * <p>Drives a detached free camera over the spectator battle:
 * <ul>
 *   <li><b>Camera (fact 8):</b> {@code viewport.setExternalControl(true)} each frame,
 *       then {@code setCenter} / {@code setViewMult} from our own state. WASD pans
 *       (polled via {@link Keyboard}), right-mouse drag pans, scroll zooms.</li>
 *   <li><b>Input override (fact 9):</b> WASD / RMB / scroll are consumed in
 *       {@code processInputPreCoreControls} so nothing leaks to a ship or command
 *       action, and player-ship control is disabled defensively each frame.</li>
 *   <li><b>Overlay (fact 12):</b> a screen-space marker drawn in
 *       {@code renderInUICoords}. Because the battle runs spectator (no player ship,
 *       zero CP) the vanilla HUD has nothing to draw, so this marker is effectively
 *       the topmost visible layer — the "starve, don't cover" workaround in action.</li>
 * </ul>
 *
 * <p>Completion control (suppress auto-end + F10 to end) is handled separately by
 * {@link S0CompletionPlugin}, added alongside this plugin.
 */
@DebugOnly
public class SpectatorCanvasPlugin extends BaseEveryFrameCombatPlugin {

    /** World units/sec the camera pans at full WASD. */
    private static final float PAN_UNITS_PER_SEC = 3500f;
    private static final float ZOOM_STEP = 0.1f;
    private static final float MIN_ZOOM = 0.05f;
    private static final float MAX_ZOOM = 4f;

    private CombatEngineAPI engine;
    private final Vector2f center = new Vector2f(0f, 0f);
    private float zoom = 0.4f; // < 1 = zoomed out, to take in the whole plate
    private boolean initialized;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        ViewportAPI vp = engine.getViewport();
        if (vp != null) {
            vp.setExternalControl(true);
            zoom = vp.getViewMult();
            initialized = true;
        }
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        ViewportAPI vp = engine != null ? engine.getViewport() : null;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;

            if (e.isMouseScrollEvent()) {
                float dir = Math.signum(e.getEventValue());
                zoom = clamp(zoom * (1f + dir * ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
                e.consume();
            } else if (e.isRMBDownEvent() || (e.isMouseEvent() && Mouse_isRMBHeld())) {
                // RMB-drag pan: move the world opposite the cursor delta.
                if (vp != null) {
                    center.x -= vp.convertScreenWidthToWorldWidth(e.getDX());
                    center.y += vp.convertScreenHeightToWorldHeight(e.getDY());
                }
                e.consume();
            } else if (isPanKey(e)) {
                e.consume(); // movement itself is polled in advance(); just stop the leak
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) return;
        ViewportAPI vp = engine.getViewport();
        if (vp == null) return;

        if (!initialized) {
            vp.setExternalControl(true);
            zoom = vp.getViewMult();
            initialized = true;
        }

        // Spectator: never let a player ship take WASD; we own the camera.
        if (engine.getCombatUI() != null) {
            engine.getCombatUI().setDisablePlayerShipControlOneFrame(true);
        }

        float pan = PAN_UNITS_PER_SEC * amount;
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) center.y += pan;
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) center.y -= pan;
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) center.x -= pan;
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) center.x += pan;

        vp.setExternalControl(true);
        vp.setCenter(center);
        vp.setViewMult(zoom);
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        // Top-left marker proving our overlay is the top visible layer.
        float w = Display.getWidth();
        float h = Display.getHeight();
        float x0 = 12f, y1 = h - 12f, x1 = x0 + 240f, y0 = y1 - 34f;

        SolidQuadBatch batch = new SolidQuadBatch(2);
        batch.appendRect(x0 - 2f, y0 - 2f, x1 + 2f, y1 + 2f, 0.10f, 0.55f, 0.75f, 0.9f); // border
        batch.appendRect(x0, y0, x1, y1, 0.03f, 0.10f, 0.14f, 0.85f);                    // fill
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            batch.flush();
        }
    }

    private static boolean isPanKey(InputEventAPI e) {
        if (!e.isKeyboardEvent()) return false;
        int v = e.getEventValue();
        return v == Keyboard.KEY_W || v == Keyboard.KEY_A
                || v == Keyboard.KEY_S || v == Keyboard.KEY_D;
    }

    private static boolean Mouse_isRMBHeld() {
        return org.lwjgl.input.Mouse.isButtonDown(1);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
