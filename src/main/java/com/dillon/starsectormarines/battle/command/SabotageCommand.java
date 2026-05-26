package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.objective.Objective;

import java.util.ArrayList;
import java.util.List;

/**
 * Marine-side strategic commander for the SABOTAGE mission — what the design
 * doc calls the "Conquest commander" (the doc's <em>Conquest</em> pattern of
 * per-zone progressive objective taking maps to the code's
 * {@link com.dillon.starsectormarines.ops.MissionType#SABOTAGE}, which scatters
 * multiple {@link ChargeSiteObjective}s across the map).
 *
 * <p>Job: keep squads spread across the unfinished charge-site zones. Per
 * slow tick, find every marine squad without a live planter and route it
 * toward the closest unfinished charge site via {@link AssignmentKind#CLEAR_ZONE}.
 * Squads <em>with</em> a planter are left alone — their unit-level
 * {@code Unit.assignedObjective} already drives
 * {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureObjectiveZone}
 * to the right zone, and overwriting their squad assignment would create
 * a goal collision in the MISSION bucket.
 *
 * <p>Without this commander, a planter death turns the squad into an
 * "ambient combat" floater that joins the nearest fight — typically the
 * one closest to where the planter died. With it, the squad pushes on to
 * support a sibling planter on a still-unfinished site, preserving the
 * mission's multi-site spread even when planters drop.
 */
public final class SabotageCommand implements MissionCommand {

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleSimulation sim) {
        List<ChargeSiteObjective> sites = collectUnfinishedSites(sim);
        if (sites.isEmpty()) {
            // All charges planted — nothing left to assign. Clear any
            // lingering assignments so squads aren't held to a CLEAR_ZONE
            // that no longer reflects mission state.
            clearAllAssignments(sim);
            return;
        }
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            if (squad.aliveMembers <= 0) continue;
            if (hasLivePlanter(squad, sim)) {
                // Don't override the planter's unit-level target. If a prior
                // commander tick had already assigned this squad (e.g.,
                // planter was killed then revived — not a real game scenario
                // but a possible test fixture), clear it now.
                squad.assignedObjective = null;
                continue;
            }
            ChargeSiteObjective target = pickClosestSite(squad, sites);
            if (target == null) {
                squad.assignedObjective = null;
                continue;
            }
            int zoneId = sim.getZoneGraph().zoneIdAt(target.cellX(), target.cellY());
            if (zoneId < 0) {
                // Charge site cell isn't in any walkable zone — bad map data,
                // skip rather than write a -1 zone the goal would reject anyway.
                squad.assignedObjective = null;
                continue;
            }
            // Idempotent re-assignment: only replace the record when the
            // chosen zone actually changed, so the squad's current plan
            // (which the goal will re-customPlan against the new target
            // on its next replan) stays stable when the commander makes
            // the same call twice in a row.
            ObjectiveAssignment cur = squad.assignedObjective;
            if (cur == null
                    || cur.kind() != AssignmentKind.CLEAR_ZONE
                    || cur.targetZoneId() != zoneId) {
                squad.assignedObjective = ObjectiveAssignment.clearZone(squad.id, zoneId);
            }
        }
    }

    private static List<ChargeSiteObjective> collectUnfinishedSites(BattleSimulation sim) {
        List<ChargeSiteObjective> out = new ArrayList<>(3);
        for (Objective o : sim.getObjectives()) {
            if (o instanceof ChargeSiteObjective cs && !cs.isComplete()) {
                out.add(cs);
            }
        }
        return out;
    }

    private static void clearAllAssignments(BattleSimulation sim) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE) continue;
            squad.assignedObjective = null;
        }
    }

    /**
     * True iff the squad has any alive {@link UnitRole#PLANTER} member whose
     * unit-level {@code assignedObjective} is an unfinished charge site.
     * Drives the "leave this squad alone" branch — see
     * {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureObjectiveZone}
     * for the unit-level path the planter's targeting feeds.
     */
    private static boolean hasLivePlanter(Squad squad, BattleSimulation sim) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.role != UnitRole.PLANTER) continue;
            if (u.assignedObjective instanceof ChargeSiteObjective cs && !cs.isComplete()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closest unfinished site to the squad's centroid by squared Manhattan-
     * equivalent distance (cell-units, sqrt skipped — only the ordering
     * matters). Returns {@code null} only when {@code sites} is empty;
     * callers gate on that already.
     */
    private static ChargeSiteObjective pickClosestSite(Squad squad, List<ChargeSiteObjective> sites) {
        if (sites.isEmpty()) return null;
        ChargeSiteObjective best = null;
        float bestDistSq = Float.MAX_VALUE;
        for (ChargeSiteObjective cs : sites) {
            float dx = cs.cellX() - squad.centroidX;
            float dy = cs.cellY() - squad.centroidY;
            float d = dx * dx + dy * dy;
            if (d < bestDistSq) {
                bestDistSq = d;
                best = cs;
            }
        }
        return best;
    }
}
