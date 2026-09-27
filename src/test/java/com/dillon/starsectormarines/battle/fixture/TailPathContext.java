package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.decision.goap.action.HoldZone;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Frozen referee context sampled after advance, never claimed as query-time state. */
record TailPathContext(int observedTick, long topologyRevision, long memberId, int squadId,
                       String goal, String action, String assignmentKind,
                       int assignmentZoneId, Zone actionZone, Node targetNode,
                       Position member, long currentTargetId, Position target) {
    record Bounds(int left, int top, int right, int bottom) {
        JSONObject json() throws JSONException {
            return new JSONObject().put("left", left).put("top", top)
                    .put("right", right).put("bottom", bottom);
        }
    }
    record Zone(int id, int cells, Bounds bounds) {
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("cellCount", cells)
                    .put("bounds", bounds == null ? JSONObject.NULL : bounds.json());
        }
    }
    record Node(String kind, int anchorX, int anchorY, Bounds footprint, Bounds compoundFootprint) {
        JSONObject json() throws JSONException {
            return new JSONObject().put("kind", kind).put("anchorX", anchorX).put("anchorY", anchorY)
                    .put("footprint", footprint.json()).put("compoundFootprint", compoundFootprint.json());
        }
    }
    record Position(float x, float y, int cellX, int cellY, int zoneId) {
        JSONObject json() throws JSONException {
            return new JSONObject().put("x", x).put("y", y).put("cellX", cellX)
                    .put("cellY", cellY).put("zoneId", zoneId);
        }
    }

    static List<TailPathContext> capture(List<TickInnerProfile.PathSearch> searches, BattleView sim) {
        List<TailPathContext> result = new ArrayList<>(searches.size());
        Map<Integer, Zone> zones = new HashMap<>();
        for (TickInnerProfile.PathSearch search : searches) {
            Squad squad = sim.getSquad(search.squadId());
            ObjectiveAssignment assignment = squad == null ? null : squad.assignmentForExecution();
            Action action = squad == null || squad.currentPlan == null || squad.currentPlan.isComplete()
                    ? null : squad.currentPlan.currentStep().action;
            int zoneId = actionZoneId(action);
            Zone zone = zoneId >= 0 ? zones.computeIfAbsent(zoneId, id -> snapshotZone(id, sim)) : null;
            Position member = position(search.memberId(), sim);
            long targetId = member == null ? 0L : sim.targetOf(search.memberId());
            result.add(new TailPathContext(sim.getSimTickIndex(), sim.getGrid().topologyRevision(),
                    search.memberId(), search.squadId(),
                    squad == null || squad.currentGoal == null ? "" : squad.currentGoal.name(),
                    action == null ? "" : action.name(), assignment == null ? "" : assignment.kind().name(),
                    assignment == null ? -1 : assignment.targetZoneId(), zone,
                    snapshotNode(assignment == null ? null : assignment.targetNode()),
                    member, targetId, position(targetId, sim)));
        }
        return List.copyOf(result);
    }

    private static int actionZoneId(Action action) {
        if (action instanceof ClearZone clear) return clear.targetZoneId();
        if (action instanceof EnterZone enter) return enter.targetZoneId();
        if (action instanceof HoldZone hold) return hold.targetZoneId();
        return -1;
    }

    private static Position position(long id, BattleView sim) {
        if (id == 0L || sim.liveUnitIndexOf(id) < 0) return null;
        int x = sim.world().cellX(id), y = sim.world().cellY(id);
        return new Position(sim.world().x(id), sim.world().y(id), x, y, sim.getZoneGraph().zoneIdAt(x, y));
    }

    private static Node snapshotNode(TacticalNode node) {
        return node == null ? null : new Node(node.kind.name(), node.anchorX, node.anchorY,
                new Bounds(node.left, node.top, node.right, node.bottom),
                new Bounds(node.compoundLeft(), node.compoundTop(), node.compoundRight(), node.compoundBottom()));
    }

    private static Zone snapshotZone(int id, BattleView sim) {
        NavigationZone zone = sim.getZoneGraph().zoneById(id);
        if (zone == null) return new Zone(id, 0, null);
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        int width = sim.getGrid().getWidth();
        for (int cell : zone.getCellIndices()) {
            int x = cell % width, y = cell / width;
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        return new Zone(id, zone.getCellCount(), zone.getCellCount() == 0 ? null
                : new Bounds(left, top, right, bottom));
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("observation", "POST_TICK_NOT_QUERY_TIME")
                .put("observedTick", observedTick).put("topologyRevision", topologyRevision)
                .put("memberId", memberId).put("squadId", squadId)
                .put("currentGoal", goal).put("currentAction", action)
                .put("assignmentKind", assignmentKind).put("assignmentTargetZoneId", assignmentZoneId)
                .put("actionTargetZone", actionZone == null ? JSONObject.NULL : actionZone.json())
                .put("assignmentTargetNode", targetNode == null ? JSONObject.NULL : targetNode.json())
                .put("member", member == null ? JSONObject.NULL : member.json())
                .put("currentTargetId", currentTargetId)
                .put("target", target == null ? JSONObject.NULL : target.json());
    }
}
