package com.dillon.starsectormarines.battle.ui;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Shared accessors a {@link HudPanel} needs to read state and write selection.
 * Implemented by {@link com.dillon.starsectormarines.ops.BattleScreen} (or a
 * thin holder) so panels stay free of direct screen-internal coupling.
 *
 * <p>Selection is the one piece of shared mutable state: panels read it to
 * decide visibility / content, and click handlers write to it. The world
 * picker writes the same field for click and drag selection, so retained HUD
 * rows and battlefield gestures never diverge into separate selections.
 */
public interface BattleUiContext {

    BattleSimulation getSim();

    /** Tick-zero construction fixture when the current scenario supports capture. */
    BattleFixture getBattleFixture();

    BattleCamera getCamera();

    BattleLayout getLayout();

    /** Presentation pointer is currently over eligible battlefield space, including during pause. */
    default boolean directControlAimVisible() { return false; }

    /** Bottom of the visible subtitle; the center lane is free above this when no subtitle is shown. */
    default float topCenterContentBottom() { return getLayout().gridY + getLayout().gridH - BattleLayout.PAD; }
    /** Bottom of subtitles and the current comms notice together. */
    default float topCenterStackBottom() { return topCenterContentBottom(); }
    /**
     * Whether the developer HUD (tick profile, DEBUG tools, squad plan and ship diagnostics) may draw.
     * Contexts that never hide it keep the default; the battle screen starts with it hidden.
     */
    default boolean debugHudVisible() { return true; }
    default void setDebugHudVisible(boolean visible) {}
    /** Bottom of the visible centered developer cluster. */
    default float developerHudBottom() { return topCenterStackBottom(); }
    /** Bottom of the actual visible right-hand mission/time rail. */
    default float rightHudBottom() { return getLayout().gridY + getLayout().gridH - BattleLayout.PAD; }
    /** Highest occupied edge of the bottom command surfaces. */
    default float bottomHudTop() { return getLayout().gridY + 76f; }
    /** Highest bottom surface intersecting this horizontal lane; other columns do not reserve its space. */
    default float bottomHudTop(float x, float width) { return bottomHudTop(); }
    Selection getSelection();

    /** Shared overlay for debug cell highlights — plan-step cells, selected squad members, captain badge, etc. */
    HighlightOverlay getHighlights();
}
