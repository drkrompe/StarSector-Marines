package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Story M — Room breach. Attacker-side maneuver that fires when a squad's
 * effective combat target sits across an adjacent portal AND no in-zone
 * enemies demand immediate attention. The squad stacks up at the doorway,
 * pushes through, and dashes to forward cover on the far side instead of
 * trading shots one-by-one from the doorway frame.
 *
 * <p><b>Mutually exclusive with:</b>
 * <ul>
 *   <li>{@link GarrisonAmbush} — defender-side hold, never relevant for the
 *       attacker squads this goal targets ({@code holdsFireUntilKillZone}
 *       false).</li>
 *   <li>{@link CordonForPlant} / {@link SecureObjectiveZone} — mission-bucket
 *       goals; they outrank ENGAGEMENT bucket regardless. If the squad has a
 *       planter target the cordon/secure flow runs first.</li>
 *   <li>{@link SurviveContact} — survival outranks engagement; a broken
 *       squad pulls back instead of breaching.</li>
 *   <li>{@link EliminateEnemiesGoal} — same bucket. Relevance is higher when
 *       the across-portal condition holds (so the planner picks this over
 *       Eliminate); falls through to Eliminate when not relevant.</li>
 * </ul>
 *
 * <p><b>Relevance gate</b> (all must hold):
 * <ol>
 *   <li>Squad not garrison-routed.</li>
 *   <li>Squad not morale-broken.</li>
 *   <li>Squad has a current zone.</li>
 *   <li>Some alive enemy combatant has LoS from any squadmate (or any
 *       squadmate carries a non-null {@code unit.target}) — and that target
 *       sits in a zone different from the squad's current zone.</li>
 *   <li>No enemy combatant in the squad's current zone with LoS to any
 *       squadmate — that's an in-zone fight, EliminateEnemies handles it.
 *       (The zone-mismatch target-scoring bias does most of this work in
 *       per-unit target selection; this explicit check is the squad-level
 *       safety belt.)</li>
 *   <li>A walkable zone path exists from squad to target.</li>
 * </ol>
 *
 * <p><b>customPlan</b>: synthesizes a single {@link BreachAndAdvance} step
 * carrying the per-slot stack-up cells (cells on the friendly side of the
 * doorway) and forward cells (cells in a search box past the doorway,
 * chosen via cover-aware scoring). The role assigner binds members to
 * slots; once the breach completes (all members at their forward cell)
 * the squad replans and normal engagement resumes from the new posture.
 */
public final class BreachToEngage implements Goal {

    public static final BreachToEngage INSTANCE = new BreachToEngage();

    /** Stack-up cell pre-pass radius — how far from the doorway we'll consider cells for the friendly-side stack zone. Small; the stack-up cells are deliberately near the door so the visual reads as "gathering." */
    private static final int STACKUP_SEARCH_RADIUS = 3;
    /** Forward search-box depth (in the threat direction) past the portal. */
    private static final int FORWARD_BOX_DEPTH = 5;
    /** Forward search-box half-width (perpendicular to threat direction). 3 ⇒ 7-cell-wide window. */
    private static final int FORWARD_BOX_HALF_WIDTH = 3;

    private BreachToEngage() {}

    @Override public String name() { return "BreachToEngage"; }
    @Override public Priority priority() { return Priority.ENGAGEMENT; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        if (squad.holdsFireUntilKillZone) return 0f;
        if (squad.moraleBroken) return 0f;
        int squadZone = ZoneQueries.squadCurrentZone(squad, sim);
        if (squadZone < 0) return 0f;
        Unit target = effectiveTarget(squad, sim);
        if (target == null) return 0f;
        int targetZone = sim.getZoneGraph().zoneIdAt(target.getCellX(), target.getCellY());
        if (targetZone < 0 || targetZone == squadZone) return 0f;
        if (anyInZoneEnemyVisible(squad, squadZone, sim)) return 0f;
        // Reachability check — fall through to EliminateEnemies if the target
        // zone is disconnected (no walkable portal route). Two-element BFS
        // path means at least one portal hop is available.
        if (ZoneQueries.zonePathBfs(squadZone, targetZone, sim).size() < 2) return 0f;
        return 1.0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        // Diagnostic only — custom-plan path bypasses Planner.plan. No
        // predicate captures "we're in the same zone as the threat" today;
        // leaving empty so the HUD shows the goal name without a misleading
        // desired-state row.
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        int squadZone = ZoneQueries.squadCurrentZone(squad, sim);
        Unit target = effectiveTarget(squad, sim);
        if (target == null || squadZone < 0) return null;
        int targetZone = sim.getZoneGraph().zoneIdAt(target.getCellX(), target.getCellY());
        if (targetZone < 0 || targetZone == squadZone) return null;

        List<Integer> path = ZoneQueries.zonePathBfs(squadZone, targetZone, sim);
        if (path.size() < 2) return null;
        int nextZone = path.get(1);
        // The detector treats each doorway cell as its own 1-cell zone, so
        // the BFS path frequently reads [room, doorway, room, doorway, room].
        // Skip the doorway micro-zone(s) immediately past the squad's room so
        // forward cells land in actual breachable territory (a marine sliding
        // to "cover behind the doorway" is the failure mode this whole story
        // exists to prevent).
        int forwardZone = nextZone;
        for (int i = 1; i < path.size(); i++) {
            int zid = path.get(i);
            var z = sim.getZoneGraph().zoneById(zid);
            if (z != null && z.getCellCount() > 1) {
                forwardZone = zid;
                break;
            }
        }

        Portal portal = portalBetween(squadZone, nextZone, sim);
        if (portal == null) return null;

        NavigationGrid grid = sim.getGrid();
        int dwIdx = portal.getDoorwayCellIdx();
        int dwX = dwIdx % grid.getWidth();
        int dwY = dwIdx / grid.getWidth();

        // Member count — slot count matches alive members so each member has
        // a stack-up + forward cell pair. Slot 0 binds the closest member.
        int aliveCount = 0;
        for (Unit u : sim.getUnits()) {
            if (u.isAlive() && u.squadId == squad.id) aliveCount++;
        }
        if (aliveCount <= 0) return null;

        int[] stackX = new int[aliveCount];
        int[] stackY = new int[aliveCount];
        int[] forwardX = new int[aliveCount];
        int[] forwardY = new int[aliveCount];

        // Stack-up cells: walkable cells in the squad's zone within
        // STACKUP_SEARCH_RADIUS of the doorway. Prefer cells closer to the
        // doorway. If we run out of qualifying cells, fall back to the doorway
        // cell itself (members will overlap visually, but the maneuver still
        // commits — better than not breaching at all).
        if (!pickFriendlySideCells(squadZone, dwX, dwY, aliveCount, stackX, stackY, sim)) {
            return null;
        }

        // Forward cells: walkable cells in the target zone, inside a search
        // box oriented from the doorway toward the target. Cover-aware
        // scoring picks the best per slot; reject already-picked cells so
        // members spread.
        int dirX = Integer.signum(target.getCellX() - dwX);
        int dirY = Integer.signum(target.getCellY() - dwY);
        if (dirX == 0 && dirY == 0) {
            // Target on the doorway — degenerate; pick any cardinal away
            // from the squad's zone.
            dirX = 1;
        }
        if (!pickForwardCells(forwardZone, dwX, dwY, dirX, dirY, target.getCellX(), target.getCellY(),
                aliveCount, forwardX, forwardY, sim)) {
            return null;
        }

        BreachAndAdvance action = new BreachAndAdvance(portal.getPortalId(), stackX, stackY, forwardX, forwardY);
        return new SquadPlan(List.of(new SquadPlan.Step(action)));
    }

    /**
     * Picks {@code count} walkable cells on the friendly side of the doorway
     * — same-zone, within {@link #STACKUP_SEARCH_RADIUS} cells. Sorted by
     * proximity to the doorway, distinct from each other. Falls back to
     * doorway cell if no candidates qualify (degenerate but defensive).
     */
    private static boolean pickFriendlySideCells(int squadZone, int dwX, int dwY, int count,
                                                 int[] outX, int[] outY, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph zones = sim.getZoneGraph();
        List<float[]> candidates = new ArrayList<>();
        for (int dy = -STACKUP_SEARCH_RADIUS; dy <= STACKUP_SEARCH_RADIUS; dy++) {
            for (int dx = -STACKUP_SEARCH_RADIUS; dx <= STACKUP_SEARCH_RADIUS; dx++) {
                int cx = dwX + dx;
                int cy = dwY + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (zones.zoneIdAt(cx, cy) != squadZone) continue;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                candidates.add(new float[]{d, cx, cy});
            }
        }
        candidates.sort((a, b) -> Float.compare(a[0], b[0]));
        int filled = 0;
        for (float[] c : candidates) {
            if (filled >= count) break;
            outX[filled] = (int) c[1];
            outY[filled] = (int) c[2];
            filled++;
        }
        // Pad with the doorway cell if we didn't find enough — better than
        // leaving slots without a destination.
        while (filled < count) {
            outX[filled] = dwX;
            outY[filled] = dwY;
            filled++;
        }
        return true;
    }

    /**
     * Picks {@code count} forward cover cells in a search box past the
     * doorway, oriented in {@code (dirX, dirY)}. Each cell is scored by
     * (cover toward the target − distance from the doorway centerline) and
     * picked greedily, rejecting already-picked cells so members spread.
     */
    private static boolean pickForwardCells(int targetZone, int dwX, int dwY, int dirX, int dirY,
                                            int threatX, int threatY,
                                            int count, int[] outX, int[] outY, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph zones = sim.getZoneGraph();
        // Build the search box: depth cells in dir, half-width cells perpendicular.
        // Perpendicular vector: rotate (dirX, dirY) 90°.
        int perpX = -dirY;
        int perpY = dirX;
        Set<Long> picked = new HashSet<>();
        int filled = 0;
        for (int slot = 0; slot < count; slot++) {
            int[] best = null;
            float bestScore = Float.MAX_VALUE;
            for (int d = 1; d <= FORWARD_BOX_DEPTH; d++) {
                for (int w = -FORWARD_BOX_HALF_WIDTH; w <= FORWARD_BOX_HALF_WIDTH; w++) {
                    int cx = dwX + dirX * d + perpX * w;
                    int cy = dwY + dirY * d + perpY * w;
                    long key = ((long) cx << 32) | (cy & 0xFFFFFFFFL);
                    if (picked.contains(key)) continue;
                    if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                    if (zones.zoneIdAt(cx, cy) != targetZone) continue;
                    int fdx = threatX - cx;
                    int fdy = threatY - cy;
                    int cover = grid.getCoverAt(cx, cy, fdx, fdy) + sim.getDoodadCoverAt(cx, cy, fdx, fdy);
                    float distFromCenter = (float) Math.sqrt(w * w);
                    // Lower score wins. Subtract cover (bigger is better),
                    // add distance-from-center (closer to centerline wins
                    // among equal-cover cells).
                    float score = distFromCenter - cover * 2.0f;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new int[]{cx, cy};
                    }
                }
            }
            if (best == null) {
                // Couldn't fill a forward slot. Fall back to the doorway cell
                // itself — the member crosses but doesn't get a cover cell.
                outX[filled] = dwX;
                outY[filled] = dwY;
            } else {
                outX[filled] = best[0];
                outY[filled] = best[1];
                picked.add(((long) best[0] << 32) | (best[1] & 0xFFFFFFFFL));
            }
            filled++;
        }
        return filled > 0;
    }

    /**
     * Returns the {@link Portal} linking {@code zoneA} and {@code zoneB}, or
     * {@code null} when no direct portal exists. Used to find the doorway
     * BFS told us to cross.
     */
    private static Portal portalBetween(int zoneA, int zoneB, BattleSimulation sim) {
        ZoneGraph graph = sim.getZoneGraph();
        var zone = graph.zoneById(zoneA);
        if (zone == null) return null;
        for (int portalId : zone.getPortalIds()) {
            Portal p = graph.portalById(portalId);
            if (p == null) continue;
            if (p.otherZone(zoneA) == zoneB) return p;
        }
        return null;
    }

    /**
     * "Effective target" — what does this squad want to engage right now?
     * Read any alive squadmate's {@code unit.target} first (fresh from the
     * last engagement loop); fall back to a quick best-target scan from the
     * squad centroid using {@link TacticalScoring#findBestTarget} so we
     * still have an answer for newly-spawned squads that haven't ticked
     * targeting yet.
     */
    private static Unit effectiveTarget(Squad squad, BattleSimulation sim) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            Unit t = sim.targetOf(u);
            if (t != null) return t;
        }
        int cx = Math.round(squad.centroidX);
        int cy = Math.round(squad.centroidY);
        return sim.getTacticalScoring().findBestTarget(cx, cy, squad.faction, squad.id, null);
    }

    /**
     * True iff at least one alive enemy combatant stands in the squad's
     * current zone AND has LoS from at least one squadmate. The squad-level
     * "engage in-zone before breach" safety check that complements the
     * per-unit zone-mismatch scoring bias.
     */
    private static boolean anyInZoneEnemyVisible(Squad squad, int squadZone, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph zones = sim.getZoneGraph();
        List<Unit> units = sim.getUnits();
        for (Unit enemy : units) {
            if (!enemy.isAlive() || !enemy.type.combatant) continue;
            if (enemy.faction == squad.faction) continue;
            if (zones.zoneIdAt(enemy.getCellX(), enemy.getCellY()) != squadZone) continue;
            for (Unit member : units) {
                if (!member.isAlive() || member.squadId != squad.id) continue;
                if (grid.hasLineOfSight(member.getCellX(), member.getCellY(), enemy.getCellX(), enemy.getCellY())) {
                    return true;
                }
            }
        }
        return false;
    }
}
