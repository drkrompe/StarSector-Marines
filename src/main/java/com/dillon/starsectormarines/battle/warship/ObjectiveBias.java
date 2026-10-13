package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Where the mission's objective ground is, from the ship's side of it, read
 * from the seams the battle already has: the assignments friendly squads hold
 * (the same {@link ObjectiveAssignment} the commanders write and the player's
 * orders replace), the tactical map's nodes, and ground a mission declares on
 * the ship. It invents no objective of its own and never reads a hostile
 * commander's orders.
 *
 * <p>An attacking ship looks at what its squads were sent to take, falling back
 * to the hostile-held objective nodes when no squad has been sent anywhere. A
 * defending ship looks at the ground its squads hold and at the nodes that
 * must be held. The result is a short list of points; {@link #proximity}
 * turns it into a 0 to 1 weight for a candidate area.
 */
public final class ObjectiveBias {

    /** Distance, in cells, at which an objective stops pulling the director's choice. */
    static final float FOCUS_RADIUS_CELLS = 40f;
    /** How much a candidate sitting on the objective is worth beyond its hostile count: a multiple of it. */
    static final float GAIN = 2f;

    private static final Set<AssignmentKind> ASSAULT_KINDS = EnumSet.of(
            AssignmentKind.CLEAR_ZONE, AssignmentKind.SWEEP_SECTOR, AssignmentKind.ADVANCE_TRACK,
            AssignmentKind.ATTACK_MOVE, AssignmentKind.SECURE_COMPOUND, AssignmentKind.RUSH_OBJECTIVE);
    private static final Set<AssignmentKind> HOLD_KINDS = EnumSet.of(
            AssignmentKind.DEFEND_TRACK, AssignmentKind.DEFEND_SITE, AssignmentKind.DEFEND_AREA,
            AssignmentKind.HOLD_NODE, AssignmentKind.SECURE_COMPOUND);
    private static final Set<TacticalNode.Kind> OBJECTIVE_NODES = EnumSet.of(
            TacticalNode.Kind.COMMAND_POST, TacticalNode.Kind.OBJECTIVE);

    private ObjectiveBias() {}

    /** The ground the ship's stance cares about, in cells: declared points first, then the battle's own. */
    public static List<float[]> focus(BattleSimulation sim, Warship ship) {
        List<float[]> out = new ArrayList<>(ship.declaredFocus());
        MissionStance stance = ship.stance();
        if (stance == MissionStance.HOLD) return out;
        Set<AssignmentKind> kinds = stance == MissionStance.ATTACK ? ASSAULT_KINDS : HOLD_KINDS;
        int fromSquads = 0;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == null || !ship.faction.friendlyTo(squad.faction)) continue;
            ObjectiveAssignment a = squad.assignedObjective;
            if (a == null || !kinds.contains(a.kind())) continue;
            float[] point = pointOf(sim, a);
            if (point != null) {
                out.add(point);
                fromSquads++;
            }
        }
        TacticalMap map = sim.getTacticalMap();
        if (map == null || (stance == MissionStance.ATTACK && fromSquads > 0)) return out;
        for (TacticalNode node : map.all()) {
            boolean objective = node.mustHold || OBJECTIVE_NODES.contains(node.kind);
            if (!objective || node.defaultGuard == null) continue;
            boolean ours = ship.faction.friendlyTo(node.defaultGuard);
            if (stance == MissionStance.ATTACK ? ship.faction.hostileTo(node.defaultGuard) : ours) {
                out.add(new float[]{node.centerX() + 0.5f, node.centerY() + 0.5f});
            }
        }
        return out;
    }

    private static float[] pointOf(BattleSimulation sim, ObjectiveAssignment a) {
        if (a.targetNode() != null) return new float[]{a.targetNode().centerX() + 0.5f, a.targetNode().centerY() + 0.5f};
        if (a.targetCellX() >= 0 && a.targetCellY() >= 0) return new float[]{a.targetCellX() + 0.5f, a.targetCellY() + 0.5f};
        if (a.targetZoneId() >= 0) {
            NavigationZone zone = sim.getZoneGraph().zoneById(a.targetZoneId());
            if (zone == null || zone.getCellCount() == 0) return null;
            int width = sim.getGrid().getWidth();
            double sx = 0;
            double sy = 0;
            for (int cell : zone.getCellIndices()) {
                sx += cell % width;
                sy += cell / width;
            }
            return new float[]{(float) (sx / zone.getCellCount()) + 0.5f, (float) (sy / zone.getCellCount()) + 0.5f};
        }
        return null;
    }

    /** 0 to 1: 1 on an objective, fading to 0 at {@link #FOCUS_RADIUS_CELLS}; the nearest point counts. */
    static float proximity(float x, float y, List<float[]> focus) {
        float best = 0f;
        for (float[] p : focus) {
            float dx = p[0] - x;
            float dy = p[1] - y;
            float near = 1f - (float) Math.sqrt(dx * dx + dy * dy) / FOCUS_RADIUS_CELLS;
            if (near > best) best = near;
        }
        return best;
    }
}
