package com.dillon.starsectormarines.battle.ui;

import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

/**
 * One drawable + interactive HUD element rendered alongside the battle view —
 * squad status pane, mini-map, objectives bar, etc. Concrete panels decide
 * their own anchor + size; {@link BattleHud} just orchestrates the list.
 *
 * <p>Visibility is panel-owned: {@link #isVisible()} is consulted on every
 * frame so panels can show/hide themselves based on {@link com.dillon.starsectormarines.battle.ui.picking.Selection}
 * (the squad-detail panel only renders while a squad is selected, the
 * overview only renders while one isn't, etc.) without {@link BattleHud}
 * needing to know about specific panel classes.
 */
public interface HudPanel {

    /** Per-frame tick — wall time, in real (not sim) seconds. */
    void update(float dt);

    /** Draws the panel. Caller guarantees the dialog scissor is active so panels can draw anywhere inside the dialog rect. */
    void render(float alphaMult);

    /**
     * Consumes any events the panel claims (clicks inside its bounds, etc.).
     * Implementations should call {@code event.consume()} on claimed events so
     * later handlers in the input chain (other panels, the camera) ignore them.
     */
    void handleInput(List<InputEventAPI> events);

    /** When false, the panel skips rendering and input; cached state still updates. */
    boolean isVisible();

    /** Releases transient captures when a visible surface is hidden by a mode change. */
    default void deactivateInput() {}

    /** Only immediate unit-control cues remain visible while the player inhabits a body. */
    default boolean visibleDuringDirectControl() { return false; }

    /** Opaque chrome blocks held world controls even without a fresh click. */
    default boolean blocksWorldPointer(float x, float y) { return false; }
}
