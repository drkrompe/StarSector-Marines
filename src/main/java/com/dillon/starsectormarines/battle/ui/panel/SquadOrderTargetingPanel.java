package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattlefieldMarkerPresentation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

/** Owns the next world click for a selected formation's tactical order. */
public final class SquadOrderTargetingPanel implements HudPanel {

    public enum Mode { NONE, ATTACK_MOVE, DEFEND_AREA }

    private final BattleUiContext ctx;
    private Mode mode = Mode.NONE;
    private long targetingMember;
    private int targetingSquadId = Selection.NONE;
    private List<Integer> targetingSquads = List.of();
    private int mouseX;
    private int mouseY;

    public SquadOrderTargetingPanel(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    public int targetingSquadId() {
        return targetingSquadId;
    }

    public Mode mode() { return mode; }

    /** Arms the selected formation, or cancels when its command is clicked again. */
    public void toggle(int squadId) { toggle(squadId, Mode.DEFEND_AREA); }

    public void toggle(int squadId, Mode requested) {
        if (squadId == Selection.NONE || requested == Mode.NONE) return;
        if (targetingSquadId == squadId && mode == requested) {
            cancel();
            return;
        }
        BattleSimulation sim = ctx.getSim();
        if (canOrderSquad(sim, squadId)) {
            mode = requested;
            targetingMember = ctx.getSelection().getSelectedUnitEntityId();
            targetingSquadId = squadId;
            targetingSquads = ctx.getSelection().getSelectedSquadIds();
        }
    }

    public void cancel() {
        mode = Mode.NONE;
        targetingMember = 0L;
        targetingSquadId = Selection.NONE;
        targetingSquads = List.of();
    }

    @Override public boolean isVisible() { return targetingSquadId != Selection.NONE; }

    @Override
    public void update(float dt) {
        if (targetingSquadId == Selection.NONE) return;
        if (!ctx.getSelection().getSelectedSquadIds().equals(targetingSquads)
                || ctx.getSelection().getSelectedSquadId() != targetingSquadId
                || mode == Mode.ATTACK_MOVE
                && targetingMember != ctx.getSelection().getSelectedUnitEntityId()
                || !canOrderSquad(ctx.getSim(), targetingSquadId)) {
            cancel();
        }
    }

    @Override
    public void render(float alphaMult) {
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (!canOrderSquad(sim, targetingSquadId) || camera == null
                || !camera.containsScreen(mouseX, mouseY)) return;
        int cellX = (int) Math.floor(camera.screenToCellX(mouseX));
        int cellY = (int) Math.floor(camera.screenToCellY(mouseY));
        if (!sim.getGrid().inBounds(cellX, cellY)) return;
        float centerX = camera.cellToScreenX(cellX + 0.5f);
        float centerY = camera.cellToScreenY(cellY + 0.5f);
        var marker = BattlefieldMarkerPresentation.target(mode == Mode.DEFEND_AREA ? "DEFEND AREA" : "ATTACK MOVE", true,
                mode == Mode.DEFEND_AREA ? SquadMoveOrderService.DEFEND_AREA_RADIUS_CELLS : 1f);
        CommandPowerTargetingPanel.paintTargetingMarker(camera, marker,
                centerX, centerY, alphaMult);
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null || targetingSquadId == Selection.NONE) return;
        update(0f);
        if (targetingSquadId == Selection.NONE) return;
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
                return;
            }
            if (!event.isLMBDownEvent()
                    || !camera.containsScreen(event.getX(), event.getY())) continue;
            int cellX = (int) Math.floor(camera.screenToCellX(event.getX()));
            int cellY = (int) Math.floor(camera.screenToCellY(event.getY()));
            if (sim.getGrid().inBounds(cellX, cellY)) {
                if (mode == Mode.ATTACK_MOVE) {
                    SelectionOrders.requestMove(sim, ctx.getSelection(), cellX, cellY);
                } else for (int squadId : targetingSquads) {
                    if (canOrderSquad(sim, squadId)) {
                        sim.getSquadMoveOrderService().requestDefendArea(squadId, cellX, cellY);
                    }
                }
                cancel();
            }
            event.consume();
            if (targetingSquadId == Selection.NONE) return;
        }
    }

    public static boolean canOrderSquad(BattleSimulation sim, int squadId) {
        if (sim == null || sim.isComplete() || sim.directControl().active()
                || squadId == Selection.NONE) return false;
        Squad squad = sim.getSquad(squadId);
        if (squad == null || squad.aliveMembers <= 0
                || squad.faction != Faction.MARINE || squad.isDroneSquad()
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
