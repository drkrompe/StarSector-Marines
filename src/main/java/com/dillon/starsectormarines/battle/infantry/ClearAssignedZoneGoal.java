package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;

import java.util.List;

/**
 * Commander-driven push-into-zone goal. Fires for squads whose
 * {@link Squad#assignedObjective} has
 * {@link AssignmentKind#CLEAR_ZONE} — written by a
 * {@link com.dillon.starsectormarines.battle.command.MissionCommand} during
 * its slow tick to spread squads across objective-bearing zones (e.g. the
 * three charge-site zones in a SABOTAGE mission, so cover-fire squads
 * don't all dogpile the nearest fight).
 *
 * <p>Sister to {@link SecureObjectiveZone}: both live in the
 * {@link Priority#MISSION} bucket, both synthesize via
 * {@link ZoneQueries#synthesizeZonePushPlan}. The difference is the
 * <em>source</em> of the target zone — {@link SecureObjectiveZone} reads a
 * unit-level planter's {@code Entity.assignedObjective}; this goal reads the
 * squad-level commander assignment. A squad can in principle have both;
 * {@link Goal#pickMostRelevant} resolves by raw relevance score within the
 * MISSION bucket (the planter-driven goal currently outscores this one,
 * keeping mid-route planter teams on their original target).
 *
 * <p>Custom-plan goal — bypasses the backward-chaining planner via
 * {@link #customPlan}. {@link #desiredState} is set for diagnostic
 * symmetry with the rest of the goal library but isn't load-bearing
 * (the {@code ZONE_CLEAR} predicate evaluator stays {@code STUB_FALSE} —
 * the action library tracks zone-clear via {@link ZoneQueries#zoneClear}).
 */
public final class ClearAssignedZoneGoal implements Goal {

    public static final ClearAssignedZoneGoal INSTANCE = new ClearAssignedZoneGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ZONE_CLEAR, true);

    private ClearAssignedZoneGoal() {}

    @Override public String name() { return "ClearAssignedZone"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null) return 0f;
        if (assignment.kind() != AssignmentKind.CLEAR_ZONE) return 0f;
        int targetZone = assignment.targetZoneId();
        if (targetZone < 0) return 0f;
        // Yield to SurviveContact when the squad is morale-broken. The
        // commander layer is a strategic hint; a broken squad pulls back
        // regardless of orders, and the commander re-evaluates on its next
        // slow tick. Distinct from {@link SecureObjectiveZone}'s override
        // of SurviveContact — that goal is a unit-level mission objective
        // (planter must plant); this one is a commander suggestion that
        // can defer to survival without breaking gameplay.
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        // Objective satisfied — assigned zone has no live enemies left.
        // Yield so the next replan picks up a fresh assignment (commander
        // will re-pick its nearest defender on next slow tick).
        Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
        if (ZoneQueries.zoneClear(targetZone, enemy, sim)) return 0f;
        int currentZone = ZoneQueries.squadCurrentZone(squad, sim);
        if (currentZone < 0) return 0f;
        // Reachability gate — disconnected target zone means the commander's
        // assignment is unrealizable; fall through to ENGAGEMENT defaults so
        // the squad still does something useful while waiting for re-assignment.
        // Note: when currentZone == targetZone the BFS short-returns
        // {[currentZone]}; we DON'T treat that as unreachable — we just
        // emit a ClearZone-only plan in {@link #customPlan} below.
        if (currentZone != targetZone
                && ZoneQueries.zonePathBfs(currentZone, targetZone, sim).size() < 2) return 0f;
        // Below SecureObjectiveZone's 1.0 — a squad with both a planter
        // (unit-level objective) and a commander assignment keeps following
        // the planter's target. The commander's task is the fallback for
        // squads without their own unit-level objective.
        return 0.8f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.CLEAR_ZONE) return null;
        int to = assignment.targetZoneId();
        // Plan stickiness: if the squad is already running a zone-push plan
        // whose terminal step targets this exact zone, keep it. Without this,
        // the 2-second periodic replan re-runs the BFS from the squad's
        // current zone every tick — and when the squad straddles a portal
        // mid-sweep the BFS path can flip-flop (e.g., [2, 217, 0] vs.
        // [217, 0] depending on which side of the doorway the leader is on),
        // oscillating members in and out of the same building. See
        // memory/breakcontact_no_sticky_dest.md for the sibling fix on the
        // BreakContact fallback cell — same family of "replan churn under
        // partial-completion state" bug.
        if (ZoneQueries.planEndsAtZone(squad.currentPlan, to)) {
            return squad.currentPlan;
        }
        int from = ZoneQueries.squadCurrentZone(squad, sim);
        if (from == to) {
            // Squad already in the target zone — emit a ClearZone-only
            // plan instead of falling through {@code synthesizeZonePushPlan}'s
            // "from == to → null" branch. Without this, every replan while
            // the squad is inside the target zone (but the zone isn't yet
            // clear) would yield to the next-priority goal — that goal
            // would move members differently, the centroid would drift to
            // an adjacent zone, the relevance check would flip back on,
            // and the customPlan would re-emit EnterZone(target). The
            // squad oscillates entering and re-entering the building.
            return new SquadPlan(List.of(new SquadPlan.Step(new ClearZone(to))));
        }
        return ZoneQueries.synthesizeZonePushPlan(from, to, sim);
    }
}
