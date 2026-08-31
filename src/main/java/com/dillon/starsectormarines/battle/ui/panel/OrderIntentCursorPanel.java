package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.picking.TacticalOrderIntent;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

/**
 * Says what a right-click would do, under the pointer, before it is clicked.
 *
 * <p>Contextual orders are a good interaction and an invisible one: the same
 * click walks a squad, puts it in a transport, or empties one, and nothing on
 * screen says so until after the fact. This paints the answer at the cell the
 * pointer is over — "GET IN" on a transport with room, "UNLOAD" on a loaded one
 * that is selected, "MOVE" on open ground — so the verb is discoverable rather
 * than folklore.
 *
 * <p>It never resolves anything itself. The intent comes from
 * {@link TacticalOrderIntent}, which asks the same services the order systems
 * ask, because a cursor offering a ride that the order then refuses is worse
 * than a cursor that says nothing.
 */
public final class OrderIntentCursorPanel implements HudPanel {

    private final BattleUiContext ctx;
    private int mouseX;
    private int mouseY;
    private TacticalOrderIntent intent = TacticalOrderIntent.NONE;

    public OrderIntentCursorPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    /** The verb the pointer is currently over. Read by tests and the debug dumper. */
    public TacticalOrderIntent intent() {
        return intent;
    }

    @Override
    public boolean isVisible() {
        // MOVE is the default meaning of a right-click and captioning every
        // patch of ground with it would be noise. The marker earns its place
        // only where the click means something else.
        return intent != TacticalOrderIntent.NONE
                && intent != TacticalOrderIntent.MOVE;
    }

    @Override
    public void update(float dt) {
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (sim == null || camera == null || !camera.containsScreen(mouseX, mouseY)) {
            intent = TacticalOrderIntent.NONE;
            return;
        }
        int cellX = (int) Math.floor(camera.screenToCellX(mouseX));
        int cellY = (int) Math.floor(camera.screenToCellY(mouseY));
        intent = TacticalOrderIntent.resolve(sim, ctx.getSelection(), cellX, cellY);
    }

    @Override
    public void render(float alphaMult) {
        if (!isVisible()) return;
        BattleCamera camera = ctx.getCamera();
        if (camera == null) return;
        int cellX = (int) Math.floor(camera.screenToCellX(mouseX));
        int cellY = (int) Math.floor(camera.screenToCellY(mouseY));
        var marker = BattlefieldMarkerPresentation.target(
                intent.label(), intent.actionable(), 0f);
        CommandPowerTargetingPanel.paintTargetingMarker(
                camera, marker, cellX, cellY, alphaMult);
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI event : events) {
            // Deliberately not consuming and not skipping consumed events: this
            // only watches where the pointer is. Swallowing a move would break
            // every other panel's hover, and ignoring a consumed one would
            // freeze the marker whenever some other surface took the event.
            if (event.isMouseMoveEvent()) {
                mouseX = event.getX();
                mouseY = event.getY();
            }
        }
    }
}
