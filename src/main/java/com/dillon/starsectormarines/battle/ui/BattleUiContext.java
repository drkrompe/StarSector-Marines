package com.dillon.starsectormarines.battle.ui;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ops.battleview.BattleCamera;

/**
 * Shared accessors a {@link HudPanel} needs to read state and write selection.
 * Implemented by {@link com.dillon.starsectormarines.ops.BattleScreen} (or a
 * thin holder) so panels stay free of direct screen-internal coupling — same
 * shape as the sim's {@code WeaponSimContext}.
 *
 * <p>Selection is the one piece of shared mutable state: panels read it to
 * decide visibility / content, and click handlers write to it. A future
 * world-picker will write the same field when the player clicks a squad's
 * centroid on the map.
 */
public interface BattleUiContext {

    BattleSimulation getSim();

    BattleCamera getCamera();

    BattleLayout getLayout();

    Selection getSelection();

    /** Shared overlay for debug cell highlights — plan-step cells, selected squad members, captain badge, etc. */
    HighlightOverlay getHighlights();
}
