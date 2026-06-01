package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Story L — Choke-point ambush. Fires for defender squads anchored to a
 * tactical node (GARRISON-routed) whose zone has at least one portal and at
 * least one known/visible enemy on the map. Synthesizes a single squad-plan
 * step via {@link #customPlan}:
 * <ul>
 *   <li><b>One portal</b> → {@link ChokePointHold}: every member binds to a
 *       pre-scored LOS-to-portal cell, holds, and the squad fires a
 *       concentrated burst when an enemy crosses the doorway.</li>
 *   <li><b>Multiple portals</b> → {@link GarrisonCordon}: members spread to
 *       the doorways, hold their post, fire opportunistically. Same shape as
 *       Story J's cordon minus the planter slot.</li>
 * </ul>
 *
 * <p>{@link Priority#MISSION} bucket — outranks the default
 * {@link EliminateEnemiesGoal} so a defender squad doesn't abandon its post
 * to chase a visible enemy when the right play is to hold the doorway and
 * concentrate fire. Mutually exclusive by relevance with {@link CordonForPlant}
 * (planter-bearing) and {@link SecureObjectiveZone} (objective in a different
 * zone) — those need marines, this needs defenders.
 *
 * <p>The garrison-routed check reads {@link Squad#holdsFireUntilKillZone} —
 * the same flag {@code OverwatchPosture} consults. Set true at squad mint
 * time by {@code BattleSetup} for GARRISON-routed defender squads. Patrol
 * and marine squads leave it false and don't trip this goal.
 */
public final class GarrisonAmbush implements Goal {

    public static final GarrisonAmbush INSTANCE = new GarrisonAmbush();

    /**
     * Zones with more than this many portals don't read as chokepoints — the
     * giant "outside" zone touches every building's doorway, so its portal
     * count is in the dozens on a typical conquest map. An outdoor GUARDPOST
     * squad in that zone would otherwise route to a random doorway and ignore
     * enemies standing right in front of it. Tuned slightly above what a
     * dense compound (military base, multi-doorway warehouse) produces so
     * legitimate indoor ambushes still fire.
     */
    private static final int MAX_AMBUSHABLE_PORTALS = 6;

    private GarrisonAmbush() {}

    @Override public String name() { return "GarrisonAmbush"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        // Garrison gate — same flag OverwatchPosture's preconditions read
        // through ENEMY_IN_KILL_ZONE. Set at squad mint for GARRISON nodes.
        if (!squad.holdsFireUntilKillZone) return 0f;
        int zoneId = ZoneQueries.squadCurrentZone(squad, sim);
        if (zoneId < 0) return 0f;
        NavigationZone zone = sim.getZoneGraph().zoneById(zoneId);
        if (zone == null || zone.getPortalIds().isEmpty()) return 0f;
        // Outdoor squads (GUARDPOST emplacements, beach garrisons) sit in the
        // giant outside zone whose portal list is every building doorway on
        // the map. An ambush plan there would pin the squad to a random
        // doorway while marines walk past in plain LoS — bail out so
        // EliminateEnemiesGoal wins for non-chokepoint zones.
        if (zone.getPortalIds().size() > MAX_AMBUSHABLE_PORTALS) return 0f;
        // If a squadmate already has LoS to an enemy, the ambush concept is
        // moot — the kill zone tripped, the squad can fire freely. Yielding
        // to EliminateEnemiesGoal here prevents a visible-but-not-yet-shot
        // squad from camping a doorway instead of engaging the threat in
        // front of it.
        if (state.get(Predicate.ENEMY_IN_KILL_ZONE)) return 0f;
        if (!enemyKnown(squad, sim)) return 0f;
        return 1.0f;
    }

    /**
     * True iff at least one alive enemy combatant is on the map. Stage 2
     * shape — Story L doesn't yet need "visible to a squadmate" (the goal
     * fires as soon as ENGAGED bumps the squad), and the trigger that
     * <em>does</em> require LOS — ENEMY_IN_PORTAL_CELL — lives on the action,
     * not the goal. If a future story wants the goal to gate on visibility,
     * swap in {@code state.get(Predicate.HAS_LOS_TO_TARGET)} here.
     */
    private static boolean enemyKnown(Squad squad, BattleView sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) { Unit u = sim.liveUnitAt(i);
            if (!u.type.combatant) continue;
            if (u.faction == squad.faction) continue;
            return true;
        }
        return false;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        int zoneId = ZoneQueries.squadCurrentZone(squad, sim);
        if (zoneId < 0) return null;
        NavigationZone zone = sim.getZoneGraph().zoneById(zoneId);
        if (zone == null) return null;
        List<Integer> portalIds = zone.getPortalIds();
        if (portalIds.isEmpty()) return null;

        if (portalIds.size() == 1) {
            return planSingleIngress(squad, sim, zoneId, portalIds.get(0));
        }
        return planMultiPortal(zone, zoneId, sim);
    }

    /**
     * Single-portal flavor: emit a {@link ChokePointHold} step carrying the
     * portal id, the doorway cell, and up to N pre-picked LOS-to-portal cells
     * (one per alive squadmate). The cells are scored at construction time
     * via {@link ChokePointHold#pickLosCells}; if the search returns fewer
     * cells than the squad has members (small zone), the slot count shrinks
     * to match — extra members idle on the same plan tick but the squad still
     * runs the hold.
     */
    private static SquadPlan planSingleIngress(Squad squad, BattleView sim,
                                                int zoneId, int portalId) {
        ZoneGraph graph = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        Portal portal = graph.portalById(portalId);
        if (portal == null) return null;
        int w = grid.getWidth();
        int dwIdx = portal.getDoorwayCellIdx();
        int portalX = dwIdx % w;
        int portalY = dwIdx / w;

        int anchorX = Math.round(squad.centroidX);
        int anchorY = Math.round(squad.centroidY);
        int wantCells = Math.max(1, squad.aliveMembers);

        List<int[]> losCells = ChokePointHold.pickLosCells(
                portalX, portalY, anchorX, anchorY, wantCells, sim);
        if (losCells.isEmpty()) return null;

        List<SquadPlan.Step> steps = new ArrayList<>(1);
        steps.add(new SquadPlan.Step(
                new ChokePointHold(portalId, portalX, portalY, losCells)));
        return new SquadPlan(steps);
    }

    /**
     * Multi-portal flavor: emit a {@link GarrisonCordon} step carrying one
     * {@link HoldPortalCordon.GuardPost} per portal of the squad's zone.
     * Guard cell selection mirrors {@link CordonForPlant} — the cardinal
     * neighbor of the doorway that lives inside the squad's zone, falling
     * back to the doorway cell itself in degenerate geometry.
     */
    private static SquadPlan planMultiPortal(NavigationZone zone, int zoneId, BattleView sim) {
        List<HoldPortalCordon.GuardPost> posts = buildGuardPosts(zone, zoneId, sim);
        if (posts.isEmpty()) return null;
        List<SquadPlan.Step> steps = new ArrayList<>(1);
        steps.add(new SquadPlan.Step(new GarrisonCordon(posts)));
        return new SquadPlan(steps);
    }

    /**
     * For each portal touching {@code zone}, finds the cell inside
     * {@code zone} that's a cardinal neighbor of the doorway cell — same
     * heuristic {@link CordonForPlant#buildGuardPosts} uses (forked here to
     * avoid the two stories depending on each other's internals). Falls back
     * to the doorway cell itself in degenerate map geometry.
     */
    private static List<HoldPortalCordon.GuardPost> buildGuardPosts(
            NavigationZone zone, int zoneId, BattleView sim) {
        ZoneGraph graph = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        int w = grid.getWidth();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        List<HoldPortalCordon.GuardPost> posts = new ArrayList<>(zone.getPortalIds().size());
        for (int portalId : zone.getPortalIds()) {
            Portal p = graph.portalById(portalId);
            if (p == null) continue;
            int dwIdx = p.getDoorwayCellIdx();
            int dwX = dwIdx % w;
            int dwY = dwIdx / w;
            int gx = dwX;
            int gy = dwY;
            for (int[] d : dirs) {
                int nx = dwX + d[0];
                int ny = dwY + d[1];
                if (graph.zoneIdAt(nx, ny) == zoneId) {
                    gx = nx;
                    gy = ny;
                    break;
                }
            }
            posts.add(new HoldPortalCordon.GuardPost(portalId, gx, gy));
        }
        return posts;
    }
}
