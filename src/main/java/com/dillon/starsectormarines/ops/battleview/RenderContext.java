package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.RoomDoor;
import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import java.util.List;
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
    /** Optional absolute host clock for repeatable animation across multiple deck passes. */
    final float animationTimeSeconds;
    final boolean debugZonesVisible;
    final HighlightOverlay highlights;
    /** Shared selection state — read by renderSelectedVehicleDebug. */
    final Selection selection;
    final BattleRenderHostProfile hostProfile;
    final ShipDeckLighting deckLighting;
    /** The founding hull's Deficit dressing plan, or null for a deck that carries none. */
    final ShipDeckDressing deckDressing;
    /** Which cores' dressing is cleared; meaningful only with {@link #deckDressing}. */
    final CoreDressing coreDressing;
    /** A dressing change still playing out toward {@link #coreDressing}, or null. */
    final DressingWave dressingWave;
    /** Every compartment's sliding door with its current openness; empty off a ship deck. */
    final List<RoomDoor> roomDoors;

    public RenderContext(BattleSimulation sim, BattleCamera camera, BattleLayout layout,
                  float alphaMult, float realDt, boolean debugZonesVisible,
                  HighlightOverlay highlights, Selection selection) {
        this(sim, camera, layout, alphaMult, realDt, debugZonesVisible,
                highlights, selection, BattleRenderHostProfile.STANDALONE_BATTLE);
    }

    public RenderContext(BattleSimulation sim, BattleCamera camera, BattleLayout layout,
                  float alphaMult, float realDt, boolean debugZonesVisible,
                  HighlightOverlay highlights, Selection selection,
                  BattleRenderHostProfile hostProfile) {
        this(sim, camera, layout, alphaMult, realDt, debugZonesVisible, highlights, selection,
                hostProfile, Float.NaN);
    }

    public RenderContext(BattleSimulation sim, BattleCamera camera, BattleLayout layout,
                  float alphaMult, float realDt, boolean debugZonesVisible,
                  HighlightOverlay highlights, Selection selection,
                  BattleRenderHostProfile hostProfile, float animationTimeSeconds) {
        this(sim, camera, layout, alphaMult, realDt, debugZonesVisible, highlights, selection,
                hostProfile, animationTimeSeconds, null);
    }

    RenderContext(BattleSimulation sim, BattleCamera camera, BattleLayout layout,
                  float alphaMult, float realDt, boolean debugZonesVisible,
                  HighlightOverlay highlights, Selection selection,
                  BattleRenderHostProfile hostProfile, float animationTimeSeconds, ShipDeckLighting deckLighting) {
        this(sim, camera, layout, alphaMult, realDt, debugZonesVisible, highlights, selection,
                hostProfile, animationTimeSeconds, deckLighting, null, CoreDressing.RESTORED, null, List.of());
    }

    RenderContext(BattleSimulation sim, BattleCamera camera, BattleLayout layout,
                  float alphaMult, float realDt, boolean debugZonesVisible,
                  HighlightOverlay highlights, Selection selection,
                  BattleRenderHostProfile hostProfile, float animationTimeSeconds, ShipDeckLighting deckLighting,
                  ShipDeckDressing deckDressing, CoreDressing coreDressing, DressingWave dressingWave,
                  List<RoomDoor> roomDoors) {
        if (hostProfile == null) throw new IllegalArgumentException("host profile is required");
        this.sim = sim;
        this.camera = camera;
        this.layout = layout;
        this.alphaMult = alphaMult;
        this.realDt = realDt;
        this.animationTimeSeconds = animationTimeSeconds;
        this.debugZonesVisible = debugZonesVisible;
        this.highlights = highlights;
        this.selection = selection;
        this.hostProfile = hostProfile;
        this.deckLighting = deckLighting;
        this.deckDressing = deckDressing;
        this.coreDressing = coreDressing == null ? CoreDressing.RESTORED : coreDressing;
        this.dressingWave = dressingWave;
        this.roomDoors = roomDoors == null ? List.of() : roomDoors;
    }
}
