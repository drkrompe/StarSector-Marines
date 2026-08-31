package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

/** Owns world-click placement for an infantry squad or Mech lance's defend area. */
public final class SquadDefendTargetingPanel implements HudPanel {

    private final BattleUiContext ctx;
    private int targetingSquadId = Selection.NONE;
    private int mouseX;
    private int mouseY;

    public SquadDefendTargetingPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    public int targetingSquadId() {
        return targetingSquadId;
    }

    /** Arms the selected formation, or cancels when its command is clicked again. */
    public void toggle(int squadId) {
        if (squadId == Selection.NONE) return;
        if (targetingSquadId == squadId) {
            cancel();
            return;
        }
        BattleSimulation sim = ctx.getSim();
        if (validPlayerDefendSquad(sim, squadId)) targetingSquadId = squadId;
    }

    public void cancel() {
        targetingSquadId = Selection.NONE;
    }

    @Override public boolean isVisible() { return targetingSquadId != Selection.NONE; }

    @Override
    public void update(float dt) {
        if (targetingSquadId == Selection.NONE) return;
        if (ctx.getSelection().getSelectedSquadId() != targetingSquadId
                || !validPlayerDefendSquad(ctx.getSim(), targetingSquadId)) {
            cancel();
        }
    }

    @Override
    public void render(float alphaMult) {
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (!validPlayerDefendSquad(sim, targetingSquadId) || camera == null
                || !camera.containsScreen(mouseX, mouseY)) return;
        int cellX = (int) Math.floor(camera.screenToCellX(mouseX));
        int cellY = (int) Math.floor(camera.screenToCellY(mouseY));
        if (!sim.getGrid().inBounds(cellX, cellY)) return;
        float centerX = camera.cellToScreenX(cellX + 0.5f);
        float centerY = camera.cellToScreenY(cellY + 0.5f);
        var marker = BattlefieldMarkerPresentation.target("DEFEND AREA", true,
                SquadMoveOrderService.DEFEND_AREA_RADIUS_CELLS);
        CommandPowerTargetingPanel.paintTargetingMarker(camera, marker,
                centerX, centerY, alphaMult);
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null || targetingSquadId == Selection.NONE) return;
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (sim == null || camera == null) return;
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (event.isMouseMoveEvent()) {
                mouseX = event.getX();
                mouseY = event.getY();
                continue;
            }
            if (event.isRMBDownEvent()
                    || event.isKeyDownEvent()
                    && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                cancel();
                event.consume();
                continue;
            }
            if (!event.isLMBDownEvent()
                    || !camera.containsScreen(event.getX(), event.getY())) continue;
            int cellX = (int) Math.floor(camera.screenToCellX(event.getX()));
            int cellY = (int) Math.floor(camera.screenToCellY(event.getY()));
            if (sim.getGrid().inBounds(cellX, cellY)) {
                sim.getSquadMoveOrderService().requestDefendArea(
                        targetingSquadId, cellX, cellY);
                cancel();
            }
            event.consume();
        }
    }

    private static boolean validPlayerDefendSquad(BattleSimulation sim, int squadId) {
        if (sim == null || squadId == Selection.NONE) return false;
        Squad squad = sim.getSquad(squadId);
        if (squad == null || squad.aliveMembers <= 0
                || squad.faction != Faction.MARINE || squad.isDroneSquad()
                || squad.rescuePickupGuard || squad.rescueShelterGuard
                || squad.rescuePickupMech) return false;
        long member = sim.resolveUnit(squad.leaderId);
        if (member == 0L) {
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                member = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
                if (member != 0L) break;
            }
        }
        if (member == 0L || !sim.identity().has(member)) return false;
        var type = sim.identity().type(member);
        return type.usesInfantryTraining()
                || (squad.isMechSquad() && type.isMech());
    }
}
