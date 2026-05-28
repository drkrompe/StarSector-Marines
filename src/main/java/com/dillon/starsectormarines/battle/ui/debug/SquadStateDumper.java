package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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
    private static final int SCHEMA_VERSION = 3;

    private SquadStateDumper() {}

    /**
     * Writes the dump and returns the common-folder-relative path the file
     * lands at on success, or {@code null} on any error (the call site
     * shows a brief status line either way; details land in the game log).
     *
     * <p>{@code selectedUnitId} is optional: when non-null, the dump tags
     * the matching member with {@code "selected": true} and surfaces a
     * top-level {@code selectedMemberId} so offline inspection of "this
     * specific mech is misbehaving while its squadmates look fine" can
     * jump straight to the right row.
     */
    public static String dump(Squad squad, BattleSimulation sim, WorldState worldState,
                              String selectedUnitId) {
        if (squad == null || sim == null) return null;
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("simTickIndex", sim.simTickIndex);
            root.put("selectedMemberId", selectedUnitId != null ? selectedUnitId : JSONObject.NULL);
            root.put("squad", buildSquadJson(squad, sim));
            root.put("members", buildMembersJson(squad, sim, selectedUnitId));
            root.put("currentGoal", buildGoalJson(squad));
            root.put("currentPlan", buildPlanJson(squad));
            root.put("worldState", buildPredicateJson(worldState));
            JSONObject clearZone = buildClearZoneReachabilityJson(squad, sim);
            if (clearZone != null) root.put("clearZoneReachability", clearZone);

            String path = pathFor(squad);
            Global.getSettings().writeJSONToCommon(path, root, true);
            // SettingsAPI.writeJSONToCommon appends ".data" to the path on disk
            // (sandbox quirk) — log the actual filename so the reader can find it.
            LOG.info("SquadStateDumper: wrote SQ-" + squad.id + " state to saves/common/" + path + ".data");
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

    private static JSONArray buildMembersJson(Squad squad, BattleSimulation sim,
                                              String selectedUnitId) throws Exception {
        JSONArray arr = new JSONArray();
        for (Unit u : sim.getUnits()) {
            if (u.squadId != squad.id) continue;
            JSONObject o = new JSONObject();
            o.put("id", u.id);
            if (selectedUnitId != null && selectedUnitId.equals(u.id)) {
                o.put("selected", true);
            }
            o.put("alive", u.isAlive());
            o.put("role", u.role != null ? u.role.name() : null);
            o.put("cellX", u.getCellX());
            o.put("cellY", u.getCellY());
            // homeCell{X,Y} = -1 sentinel for units without a post (marines,
            // patrols). Emit anyway so the dump distinguishes "no home" from
            // "home but drifted off" — key signal for diagnosing why a
            // garrison unit's findFiringPositionWithin returned null.
            o.put("homeCellX", u.homeCellX);
            o.put("homeCellY", u.homeCellY);
            o.put("currentZone", sim.getZoneGraph().zoneIdAt(u.getCellX(), u.getCellY()));
            o.put("hp", u.getHp());
            o.put("maxHp", u.getMaxHp());
            o.put("moveProgress", u.getMoveProgress());
            Unit dumpTarget = sim.targetOf(u);
            o.put("targetId", dumpTarget != null ? dumpTarget.id : null);
            // Pathfinder reachability of the unit's current target. False
            // here means the squad is fixated on someone the pathfinder
            // can't route to from this member — e.g. an enemy behind walls
            // in the same flood-filled zone (see [[zone_graph_ignores_edges]]).
            // Future make-passage actions (breach door, blow wall) should
            // key off this flag. JSONObject.NULL when the unit has no target.
            o.put("targetReachable", computeTargetReachable(u, sim));
            o.put("cooldownTimer", u.getCooldownTimer());
            o.put("pathLen", u.path != null ? u.path.length / 2 : 0);
            arr.put(o);
        }
        return arr;
    }

    private static Object computeTargetReachable(Unit self, BattleSimulation sim) {
        Unit target = sim.targetOf(self);
        if (target == null) return JSONObject.NULL;
        int[] path = GridPathfinder.findPath(sim.getGrid(),
                self.getCellX(), self.getCellY(), target.getCellX(), target.getCellY());
        return path.length > 0;
    }

    /**
     * When the squad's current plan step is a {@link ClearZone}, scans alive
     * enemies inside the target zone and reports whether each can be reached
     * by any alive squadmate via {@link GridPathfinder#findPath}. Returns
     * {@code null} when the squad has no plan, the plan is complete, or the
     * current step isn't ClearZone — the field is then omitted from the dump.
     *
     * <p>Signal hook for future "make-passage" actions: if an enemy is in
     * the target zone but no squadmate can pathfind to it, the squad is
     * geometrically stuck and needs a door-breach / wall-demo action to
     * progress rather than another retry of the same plan (the SQ-82
     * motivator — see {@code roadmap/sessions/}).
     */
    private static JSONObject buildClearZoneReachabilityJson(Squad squad, BattleSimulation sim) throws Exception {
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return null;
        SquadPlan.Step step = plan.currentStep();
        if (!(step.action instanceof ClearZone)) return null;
        int targetZoneId = ((ClearZone) step.action).targetZoneId();

        Faction enemyFaction = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;

        List<Unit> squadmates = new ArrayList<>();
        for (Unit u : sim.getUnits()) {
            if (u.squadId == squad.id && u.isAlive()) squadmates.add(u);
        }

        JSONArray enemies = new JSONArray();
        boolean anyUnreachable = false;
        for (Unit e : sim.getUnits()) {
            if (!e.isAlive()) continue;
            if (e.faction != enemyFaction) continue;
            if (sim.getZoneGraph().zoneIdAt(e.getCellX(), e.getCellY()) != targetZoneId) continue;
            boolean reachable = false;
            for (Unit m : squadmates) {
                int[] path = GridPathfinder.findPath(sim.getGrid(),
                        m.getCellX(), m.getCellY(), e.getCellX(), e.getCellY());
                if (path.length > 0) { reachable = true; break; }
            }
            if (!reachable) anyUnreachable = true;
            JSONObject eo = new JSONObject();
            eo.put("id", e.id);
            eo.put("cellX", e.getCellX());
            eo.put("cellY", e.getCellY());
            eo.put("reachableFromAnyMember", reachable);
            enemies.put(eo);
        }

        JSONObject o = new JSONObject();
        o.put("targetZoneId", targetZoneId);
        o.put("enemies", enemies);
        // Convenience top-level bit so future make-passage triggers check
        // one field instead of walking the enemies array.
        o.put("anyEnemyUnreachable", anyUnreachable);
        return o;
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
