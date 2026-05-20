package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * One-shot JSON dump of a squad's GOAP state — blackboard predicates +
 * current goal + current plan steps with assignments + commander assignment
 * + per-member status. Written to
 * {@code saves/common/starsector_marines/debug/squad_<id>.json} via the
 * SettingsAPI common-folder write path (the only file I/O available to mod
 * code; {@link java.nio.file} and {@link java.io.File} are blocked by the
 * Starsector script sandbox — see {@code TilesetCatalog} for the prior
 * art).
 *
 * <p>Triggered manually from the {@link
 * com.dillon.starsectormarines.battle.ui.panel.SquadPlanDebugPanel} when
 * the user wants to capture "why is this squad doing X right now" for
 * offline inspection. Overwrites on each click; copy the file out of
 * common/ if you want a history.
 */
public final class SquadStateDumper {

    private static final Logger LOG = Logger.getLogger(SquadStateDumper.class);
    /** Bumped when the dump shape changes — lets offline tools recognize older dumps. */
    private static final int SCHEMA_VERSION = 1;

    private SquadStateDumper() {}

    /**
     * Writes the dump and returns the common-folder-relative path the file
     * lands at on success, or {@code null} on any error (the call site
     * shows a brief status line either way; details land in the game log).
     */
    public static String dump(Squad squad, BattleSimulation sim, WorldState worldState) {
        if (squad == null || sim == null) return null;
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("simTickIndex", sim.simTickIndex);
            root.put("squad", buildSquadJson(squad, sim));
            root.put("members", buildMembersJson(squad, sim));
            root.put("currentGoal", buildGoalJson(squad));
            root.put("currentPlan", buildPlanJson(squad));
            root.put("worldState", buildPredicateJson(worldState));

            String path = pathFor(squad);
            Global.getSettings().writeJSONToCommon(path, root, true);
            LOG.info("SquadStateDumper: wrote SQ-" + squad.id + " state to saves/common/" + path);
            return path;
        } catch (Exception ex) {
            LOG.warn("SquadStateDumper: dump failed for SQ-" + squad.id, ex);
            return null;
        }
    }

    private static String pathFor(Squad squad) {
        return StarsectorMarinesModPlugin.MOD_ID + "/debug/squad_" + squad.id + ".json";
    }

    private static JSONObject buildSquadJson(Squad squad, BattleSimulation sim) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", squad.id);
        o.put("faction", squad.faction != null ? squad.faction.name() : null);
        o.put("alertLevel", squad.alertLevel != null ? squad.alertLevel.name() : null);
        o.put("aliveMembers", squad.aliveMembers);
        o.put("originalSize", squad.originalSize);
        o.put("centroidX", squad.centroidX);
        o.put("centroidY", squad.centroidY);
        o.put("currentZone", ZoneQueries.squadCurrentZone(squad, sim));
        o.put("morale", squad.morale);
        o.put("moraleBroken", squad.moraleBroken);
        o.put("timeSinceContact", squad.timeSinceContact);
        o.put("timeSinceReplan", squad.timeSinceReplan);
        o.put("leaderId", squad.leader != null ? squad.leader.id : null);
        o.put("assignedNode", squad.assignedNode != null ? squad.assignedNode.kind.name() : null);
        o.put("assignedObjective", buildAssignmentJson(squad.assignedObjective));
        // Garrison-specific flags — load-bearing for "why won't this squad fire" diagnostics.
        o.put("holdsFireUntilKillZone", squad.holdsFireUntilKillZone);
        o.put("killZoneLosTicks", squad.killZoneLosTicks);
        o.put("chokePointPortalId", squad.chokePointPortalId);
        o.put("fallbackTriggered", squad.fallbackTriggered);
        o.put("fallbackInProgress", squad.fallbackInProgress);
        return o;
    }

    private static JSONObject buildAssignmentJson(ObjectiveAssignment a) throws Exception {
        if (a == null) return null;
        JSONObject o = new JSONObject();
        o.put("kind", a.kind().name());
        o.put("targetZoneId", a.targetZoneId());
        o.put("targetNode", a.targetNode() != null ? a.targetNode().kind.name() : null);
        o.put("objectiveId", a.objectiveId());
        return o;
    }

    private static JSONArray buildMembersJson(Squad squad, BattleSimulation sim) throws Exception {
        JSONArray arr = new JSONArray();
        for (Unit u : sim.getUnits()) {
            if (u.squadId != squad.id) continue;
            JSONObject o = new JSONObject();
            o.put("id", u.id);
            o.put("alive", u.isAlive());
            o.put("role", u.role != null ? u.role.name() : null);
            o.put("cellX", u.cellX);
            o.put("cellY", u.cellY);
            o.put("currentZone", sim.getZoneGraph().zoneIdAt(u.cellX, u.cellY));
            o.put("hp", u.hp);
            o.put("maxHp", u.maxHp);
            o.put("moveProgress", u.moveProgress);
            o.put("targetId", u.target != null ? u.target.id : null);
            o.put("cooldownTimer", u.cooldownTimer);
            o.put("pathLen", u.path != null ? u.path.length / 2 : 0);
            arr.put(o);
        }
        return arr;
    }

    private static JSONObject buildGoalJson(Squad squad) throws Exception {
        JSONObject o = new JSONObject();
        if (squad.currentGoal == null) {
            o.put("name", JSONObject.NULL);
            o.put("priority", JSONObject.NULL);
            return o;
        }
        o.put("name", squad.currentGoal.name());
        o.put("priority", squad.currentGoal.priority().name());
        return o;
    }

    private static JSONObject buildPlanJson(Squad squad) throws Exception {
        JSONObject o = new JSONObject();
        SquadPlan plan = squad.currentPlan;
        if (plan == null) {
            o.put("present", false);
            return o;
        }
        o.put("present", true);
        o.put("stepCount", plan.stepCount());
        o.put("currentIndex", plan.currentIndex());
        o.put("complete", plan.isComplete());
        JSONArray steps = new JSONArray();
        List<SquadPlan.Step> stepList = plan.steps();
        for (int i = 0; i < stepList.size(); i++) {
            SquadPlan.Step step = stepList.get(i);
            JSONObject so = new JSONObject();
            so.put("index", i);
            so.put("isCurrent", i == plan.currentIndex() && !plan.isComplete());
            so.put("action", step.action.name());
            JSONObject slotJson = new JSONObject();
            for (Map.Entry<String, List<Unit>> e : step.assignments.entrySet()) {
                JSONArray ids = new JSONArray();
                for (Unit u : e.getValue()) ids.put(u.id);
                slotJson.put(e.getKey(), ids);
            }
            so.put("assignments", slotJson);
            steps.put(so);
        }
        o.put("steps", steps);
        return o;
    }

    private static JSONObject buildPredicateJson(WorldState state) throws Exception {
        JSONObject o = new JSONObject();
        if (state == null) return o;
        // Specified-vs-unspecified matters for diagnostics — a predicate that
        // isn't even in the world-state mask reads differently from one that's
        // explicitly false. Emit both columns so offline tools can tell.
        for (Predicate p : Predicate.values()) {
            JSONObject pj = new JSONObject();
            pj.put("specified", state.isSpecified(p));
            pj.put("value", state.get(p));
            o.put(p.name(), pj);
        }
        return o;
    }
}
