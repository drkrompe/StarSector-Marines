package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.decision.goap.action.LinePlacement;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/** Fans a world gesture out through the existing simulation-owned order mailboxes. */
public final class SelectionOrders {
    /**
     * The shortest right drag, in cells, read as a heading rather than as the click it nearly was.
     * Under it the angle is noise from the hand rather than intent.
     */
    public static final float MIN_FACING_DRAG_CELLS = 1f;

    private SelectionOrders() { }

    public static void requestMove(BattleSimulation sim, Selection selection, int x, int y) {
        requestMove(sim, selection, x, y, Float.NaN);
    }

    /**
     * Whether a right drag on this selection means "go to where the drag started and face the way it
     * went": a vehicle, or mechs with no infantry alongside them to draw a line. A selection with
     * infantry in it draws a frontage instead, and a warship has no use for a heading.
     */
    public static boolean takesFacing(BattleSimulation sim, Selection selection) {
        if (sim == null || selection == null || selection.hasWarshipSelection()) return false;
        if (selection.getSelectedVehicleId() != 0L) return true;
        if (!lineHolders(sim, selection).isEmpty()) return false;
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad != null && squad.faction == Faction.MARINE && squad.isMechSquad()
                    && !squad.rescuePickupMech) return true;
        }
        return false;
    }

    /** The heading of a drag by ({@code dx}, {@code dy}) cells, in the sprite convention every chassis uses. */
    public static float facingOf(float dx, float dy) {
        return LayeredAppearance.wrapDegrees(AirBody.facingToward(dx, dy));
    }

    /**
     * {@link #requestMove(BattleSimulation, Selection, int, int)} that finishes on
     * {@code facingDegrees}, or on whatever heading the unit arrives with when that is {@code NaN}.
     * Vehicles and mechs take the heading; ships and infantry, which have no order for one, take only
     * the destination.
     */
    public static void requestMove(BattleSimulation sim, Selection selection, int x, int y,
                                   float facingDegrees) {
        if (sim == null || selection == null || sim.directControl().active()
                || !sim.getGrid().inBounds(x, y)) return;
        if (selection.hasWarshipSelection()) {
            sim.shipCommands().requestMove(selection.getSelectedWarship(), x + .5f, y + .5f);
            return;
        }
        long vehicle = selection.getSelectedVehicleId();
        if (vehicle != 0L) {
            sim.getVehicleMoveOrderService().requestMove(vehicle, x, y, facingDegrees);
            return;
        }
        long primary = selection.getSelectedUnitEntityId();
        if (selection.squadCount() == 1 && primary != 0L
                && sim.world().hasMechLoadout(primary)) {
            sim.getMechMoveOrderService().requestMove(primary, x, y, facingDegrees);
            return;
        }
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad == null || squad.faction != Faction.MARINE || squad.rescuePickupMech) continue;
            if (squad.isMechSquad()) {
                for (int i = 0; i < sim.squadMemberCount(id); i++) {
                    long member = sim.squadMemberAt(id, i);
                    if (sim.world().isAlive(member) && sim.world().hasMechLoadout(member)) {
                        sim.getMechMoveOrderService().requestMove(member, x, y, facingDegrees);
                    }
                }
            } else {
                sim.getSquadMoveOrderService().requestMove(id, x, y);
            }
        }
    }

    /**
     * A right drag from ({@code x0},{@code y0}) to ({@code x1},{@code y1}). Selected infantry squads
     * share the line, each holding its own consecutive stretch of it in selection order; anything
     * that takes no line — a ship, a vehicle, a lance — is sent to where the drag ended. A drag
     * too short to be a line is the click it nearly was.
     */
    public static void requestLine(BattleSimulation sim, Selection selection, int x0, int y0, int x1, int y1) {
        if (sim == null || selection == null || sim.directControl().active()
                || !sim.getGrid().inBounds(x0, y0) || !sim.getGrid().inBounds(x1, y1)) return;
        List<Squad> holders = lineHolders(sim, selection);
        if (holders.isEmpty() || Math.hypot(x1 - x0, y1 - y0) < FrontageLine.MIN_LENGTH) {
            requestMove(sim, selection, x1, y1);
            return;
        }
        List<FrontageLine> lines = previewLines(sim, selection, x0, y0, x1, y1);
        for (int i = 0; i < holders.size(); i++) {
            FrontageLine line = lines.get(i);
            sim.getSquadMoveOrderService().requestLine(holders.get(i).id, line.x0(), line.y0(), line.x1(), line.y1());
        }
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad != null && squad.isMechSquad() && squad.faction == Faction.MARINE && !squad.rescuePickupMech) {
                for (int i = 0; i < sim.squadMemberCount(id); i++) {
                    long member = sim.squadMemberAt(id, i);
                    if (sim.world().isAlive(member) && sim.world().hasMechLoadout(member)) {
                        sim.getMechMoveOrderService().requestMove(member, x1, y1);
                    }
                }
            }
        }
    }

    /**
     * The lines a drag would give the selected infantry squads, one per squad in selection order,
     * each facing the way that squad's order will face. Empty when nothing selected holds lines.
     * The preview and the order both come from here, so the arrow drawn under the drag is the
     * facing the squad is given.
     */
    public static List<FrontageLine> previewLines(BattleSimulation sim, Selection selection,
                                                  int x0, int y0, int x1, int y1) {
        List<Squad> holders = lineHolders(sim, selection);
        List<FrontageLine> lines = new ArrayList<>(holders.size());
        int n = holders.size();
        for (int i = 0; i < n; i++) {
            Squad squad = holders.get(i);
            int ax = Math.round(x0 + (x1 - x0) * (float) i / n);
            int ay = Math.round(y0 + (y1 - y0) * (float) i / n);
            int bx = Math.round(x0 + (x1 - x0) * (float) (i + 1) / n);
            int by = Math.round(y0 + (y1 - y0) * (float) (i + 1) / n);
            SquadContactPicture picture = squad.contactPicture;
            boolean threat = picture != null && picture.hasContacts()
                    && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0;
            lines.add(FrontageLine.facing(ax, ay, bx, by, squad.centroidX, squad.centroidY,
                    threat ? picture.primaryCellX() : -1, threat ? picture.primaryCellY() : -1));
        }
        return lines;
    }

    /**
     * {@link #previewLines} with where every marine of each squad will stand on its line: the
     * claims' own dry run and the order's own overflow rule, so a ring drawn under the drag is
     * where the marine goes once it is released.
     */
    public static List<HighlightOverlay.LineMark> previewMarks(BattleSimulation sim, Selection selection,
                                                                int x0, int y0, int x1, int y1) {
        List<Squad> holders = lineHolders(sim, selection);
        List<FrontageLine> lines = previewLines(sim, selection, x0, y0, x1, y1);
        List<HighlightOverlay.LineMark> marks = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            List<HighlightOverlay.PlaceMark> places = new ArrayList<>();
            for (LinePlacement.Place place : LinePlacement.preview(sim, holders.get(i), lines.get(i))) {
                places.add(new HighlightOverlay.PlaceMark(place.member(), place.x(), place.y(), place.position()));
            }
            marks.add(new HighlightOverlay.LineMark(lines.get(i), places));
        }
        return marks;
    }

    private static List<Squad> lineHolders(BattleSimulation sim, Selection selection) {
        List<Squad> holders = new ArrayList<>();
        if (sim == null || selection == null || selection.hasWarshipSelection()
                || selection.getSelectedVehicleId() != 0L) return holders;
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad == null || squad.faction != Faction.MARINE || squad.isMechSquad()
                    || squad.isDroneSquad() || squad.aliveMembers <= 0) continue;
            holders.add(squad);
        }
        return holders;
    }
}
