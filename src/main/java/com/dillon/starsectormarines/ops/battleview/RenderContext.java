package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Per-frame carrier passed from {@link com.dillon.starsectormarines.ops.BattleScreen} into
 * {@link BattleRenderer#renderWorld}. All fields are final — constructed once per render call
 * and discarded; the renderer stashes a reference only for the duration of the call.
 *
 * <p>Fields here are things the screen owns (because they're also read by input handlers,
 * rebuild(), or advance()) but the renderer needs to read per-frame. The renderer owns the
 * rest of the world-pass state (batches, overlays, etc.) as instance fields.
 */
public final class RenderContext {
    final BattleSimulation sim;
    final BattleCamera camera;
    final BattleLayout layout;
    final float alphaMult;
    /** Real-time dt from the most recent advance() call. Used to age contrails during pause. */
    final float realDt;
    final boolean debugZonesVisible;
    final HighlightOverlay highlights;
    /** Shared selection state — read by renderSelectedVehicleDebug. */
    final Selection selection;

    public RenderContext(BattleSimulation sim, BattleCamera camera, BattleLayout layout,
                  float alphaMult, float realDt, boolean debugZonesVisible,
                  HighlightOverlay highlights, Selection selection) {
        this.sim = sim;
        this.camera = camera;
        this.layout = layout;
        this.alphaMult = alphaMult;
        this.realDt = realDt;
        this.debugZonesVisible = debugZonesVisible;
        this.highlights = highlights;
        this.selection = selection;
    }
}
