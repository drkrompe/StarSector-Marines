package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.ai.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.weapons.FireStance;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Squad posture: single-ingress choke-point ambush.</b> Story L's
 * single-portal flavor — every assigned member binds to a pre-scored
 * LOS-to-portal cell inside the squad's zone and holds the position. When an
 * enemy combatant crosses the watched portal cell, the
 * {@link Predicate#ENEMY_IN_PORTAL_CELL} trigger flips true and <em>all</em>
 * on-post members with LoS fire on the intruder this tick — a deterministic
 * concentrated burst, not a per-member RNG.
 *
 * <p>Cordon discipline by positioning, not target filtering: the bound cells
 * face the doorway, so the squad's natural LoS arc converges on it. When the
 * trigger is false the squad holds (no opportunistic fire — the multi-portal
 * variant {@link GarrisonCordon} owns that doctrine; single-portal hold is
 * about the burst).
 *
 * <p>Parameterized per-portal — the {@link GarrisonAmbush} goal's customPlan
 * constructs one instance carrying the portal id, portal cell, and the
 * pre-picked LOS cells. Not a singleton, not registered in
 * {@code GoapInfantryBehavior.INFANTRY_ACTIONS}: the backward-chaining planner
 * never sees it. The {@link com.dillon.starsectormarines.battle.ai.goap.Predicate#ENEMY_IN_PORTAL_CELL}
 * evaluator uses {@link Squad#chokePointPortalId} to know which portal cell to
 * sample — this action stamps that id on first execute (idempotent).
 *
 * <p>Cell pre-pass: at construction time, we pick up to N LOS-to-portal cells
 * inside the search radius by repeatedly calling
 * {@link TacticalScoring#bestCoverCell} and marking previously-picked cells.
 * If the search returns fewer than N distinct cells (small zone, limited
 * cover), we fill the remainder with whatever LoS-cells the search produces
 * even at lower cover — better than dropping slots.
 */
public final class ChokePointHold implements Action {

    /** Search radius (cells) used during the construction-time LOS-cell pre-pass. Five is wide enough to cover a one-room garrison without spilling into adjacent zones. */
    public static final int SEARCH_RADIUS = 5;

    /** Portal id this hold watches. Stamped onto {@link Squad#chokePointPortalId} on first execute so the {@link Predicate#ENEMY_IN_PORTAL_CELL} evaluator can scope the predicate to this portal. */
    private final int portalId;
    /** Doorway cell of {@link #portalId}, cached at construction so the evaluator + fire pass don't have to re-look-up. */
    private final int portalX;
    private final int portalY;
    /** Pre-picked LOS-to-portal cells, one per role slot. Member assigned to slot {@code losCell:i} paths to {@code losCells.get(i)} and holds there. */
    private final List<int[]> losCells;

    public ChokePointHold(int portalId, int portalX, int portalY, List<int[]> losCells) {
        this.portalId = portalId;
        this.portalX = portalX;
        this.portalY = portalY;
        this.losCells = List.copyOf(losCells);
    }

    public int portalId()       { return portalId; }
    public int portalX()        { return portalX; }
    public int portalY()        { return portalY; }
    public List<int[]> losCells() { return losCells; }

    @Override public String name() { return "ChokePointHold[" + portalId + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return Math.max(1, losCells.size()); }

    @Override
    public java.util.List<int[]> highlightCells(Squad squad, BattleSimulation sim) {
        // LOS cells + the watched portal cell so the player can see both the
        // firing posts AND the doorway that triggers concentrated fire.
        java.util.List<int[]> out = new java.util.ArrayList<>(losCells.size() + 1);
        for (int[] c : losCells) out.add(new int[]{c[0], c[1]});
        out.add(new int[]{portalX, portalY});
        return out;
    }

    /**
     * Picks up to {@code maxCells} LOS-to-portal cells inside the squad's
     * search radius. Uses {@link TacticalScoring#bestCoverCell} iteratively —
     * each round chooses the best remaining cell, then marks it taken so the
     * next round doesn't return it. When {@code bestCoverCell} runs out of
     * candidates (small zone, few LoS positions), the result is shorter than
     * {@code maxCells} — the goal trims slots to match.
     *
     * <p>Anchor is the squad's centroid: search around where the defenders
     * are, not where the portal sits — that keeps the picked cells inside the
     * defender's zone instead of out in the corridor on the far side of the
     * doorway. The portal coordinates are the LOS reference, not the anchor.
     */
    public static List<int[]> pickLosCells(int portalX, int portalY,
                                           int anchorX, int anchorY,
                                           int maxCells,
                                           BattleSimulation sim) {
        if (maxCells <= 0) return List.of();
        List<int[]> picked = new ArrayList<>(maxCells);
        for (int i = 0; i < maxCells; i++) {
            int[] best = bestCoverCellExcluding(portalX, portalY, anchorX, anchorY,
                    SEARCH_RADIUS, sim, picked);
            if (best == null) break;
            picked.add(best);
        }
        return picked;
    }

    /**
     * Wraps {@link TacticalScoring#bestCoverCell} with an exclusion list. The
     * underlying scorer doesn't know about already-picked cells, so we re-run
     * it and reject any candidate that matches the exclusion list. For small
     * exclusion sets (cap N=8 for an 8-marine squad) and the 5-radius window
     * (~100 cells), the overhead is in the noise.
     *
     * <p>Implementation note: we re-do the bestCoverCell search and skip
     * excluded cells in a sweep, mirroring the inner loop of bestCoverCell.
     * Keeping the TacticalScoring API stable matches the project rule of
     * "don't bypass TacticalScoring — extend it if a new primitive is needed."
     * Until a second caller wants exclusion, the duplication lives here and
     * we'll fold a {@code bestCoverCellExcluding} primitive into the scorer
     * if a third caller appears.
     */
    private static int[] bestCoverCellExcluding(int threatX, int threatY,
                                                int nearX, int nearY, int radius,
                                                BattleSimulation sim,
                                                List<int[]> exclude) {
        NavigationGrid grid = sim.getGrid();
        int[] best = null;
        int bestCover = -1;
        float bestDist = Float.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = nearX + dx;
                int cy = nearY + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > radius) continue;
                if (!grid.hasLineOfSight(cx, cy, threatX, threatY)) continue;
                boolean excluded = false;
                for (int[] e : exclude) {
                    if (e[0] == cx && e[1] == cy) { excluded = true; break; }
                }
                if (excluded) continue;
                int combined = grid.getCoverAt(cx, cy) + sim.getDoodadCoverAt(cx, cy);
                if (combined > bestCover || (combined == bestCover && d < bestDist)) {
                    bestCover = combined;
                    bestDist = d;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }

    /**
     * One {@code losCell:i} slot per pre-picked cell. Score is negated
     * distance from the candidate to the slot's cell — closest member wins.
     * Slot ordering matters less than for the planter-bearing cordon because
     * every slot is symmetric (no priority role like the planter); the
     * role-assigner's mean-score-orders-slots pass keeps the assignment
     * stable in practice.
     */
    @Override
    public List<RoleAssigner.Slot<Unit>> roles(Squad squad, BattleSimulation sim) {
        List<RoleAssigner.Slot<Unit>> slots = new ArrayList<>(losCells.size());
        for (int i = 0; i < losCells.size(); i++) {
            final int idx = i;
            final int cellX = losCells.get(i)[0];
            final int cellY = losCells.get(i)[1];
            slots.add(new RoleAssigner.Slot<>(
                    slotName(idx),
                    1,
                    c -> -TacticalScoring.cellDistance(c.getCellX(), c.getCellY(), cellX, cellY)));
        }
        return slots;
    }

    /** Slot name encoding — used both when declaring slots and when looking up the cell for an assigned member. */
    public static String slotName(int idx) { return "losCell:" + idx; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Stamp portal id idempotently — the predicate evaluator needs to know
        // which portal cell to sample. Writing the same value on every tick is
        // harmless and saves a "has the squad been stamped" flag. Under the
        // per-unit parallel dispatch every member writes the same value here,
        // but we still wrap the write in the squad lock so the JMM guarantees
        // visibility of the stamp to the sibling worker that immediately
        // reads it via WorldStateBuilder, and so a future ChokePointHold
        // taking over for this squad on the same tick can't tear int writes
        // of two different portal ids.
        synchronized (squad.lock) {
            squad.chokePointPortalId = portalId;
        }

        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        String slotName = step != null ? step.slotOf(member) : null;
        if (slotName == null) return ActionStatus.RUNNING;

        int slotIdx = parseSlotIdx(slotName);
        if (slotIdx < 0 || slotIdx >= losCells.size()) return ActionStatus.RUNNING;
        int[] cell = losCells.get(slotIdx);
        int targetX = cell[0];
        int targetY = cell[1];

        boolean atPost = (member.getCellX() == targetX && member.getCellY() == targetY);
        if (!atPost) {
            // Transit: walk to the bound LOS cell. No opportunistic fire —
            // single-portal hold is about the concentrated burst, the squad
            // holds discipline en route as well as on-post.
            if (member.moveProgress == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.getCellX(), member.getCellY(), targetX, targetY,
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
            return ActionStatus.RUNNING;
        }

        // On-post — pin in place between bursts.
        if (!member.pathEmpty()) sim.clearPath(member);
        member.moveProgress = 0f;
        member.renderX = member.getCellX();
        member.renderY = member.getCellY();

        // Concentrated-fire trigger: ENEMY_IN_PORTAL_CELL true this tick →
        // every on-post member with LoS to the portal cell fires. The
        // predicate consult here is deterministic across members (everyone
        // sees the same world state in the same tick), so this naturally
        // produces the "everybody shoots" burst.
        if (!triggerActive(squad, sim)) return ActionStatus.RUNNING;

        // Build enemy target — alive combatant standing on the portal cell.
        Unit portalIntruder = enemyOnPortalCell(squad, sim);
        if (portalIntruder == null) return ActionStatus.RUNNING;

        // LoS gate is by-cell: bound cells were picked with LoS at
        // construction, but a movable doodad or transient cover change might
        // have closed it. Re-check before firing.
        if (!sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(), portalX, portalY)) {
            return ActionStatus.RUNNING;
        }
        if (member.cooldownTimer > 0f) return ActionStatus.RUNNING;
        float d = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(), portalX, portalY);
        if (d > member.attackRange) return ActionStatus.RUNNING;

        // STANCED fire — on-post, deliberate. Full accuracy. Same shape as
        // HoldPortalCordon's on-post branch so burst follow-ups behave
        // identically (machine guns rip a burst when the trigger fires).
        sim.fireShot(member, portalIntruder, FireStance.STANCED);
        member.setTarget(portalIntruder);
        member.cooldownTimer = member.attackCooldown;
        member.beginBurst(portalIntruder);
        return ActionStatus.RUNNING;
    }

    /**
     * Returns true when this tick's world state has
     * {@link Predicate#ENEMY_IN_PORTAL_CELL} set for this squad. Rebuilds the
     * snapshot here on demand (per member, per tick) rather than threading a
     * cached state through {@link #execute}: the per-tick cost is one O(units)
     * scan plus the evaluator suite — bounded and small for stage-2 sizes.
     *
     * <p>Stamping {@link Squad#chokePointPortalId} <em>before</em> calling
     * this is required: the evaluator reads that field to know which portal
     * cell to sample. {@link #execute} sets it as the first thing it does.
     */
    private boolean triggerActive(Squad squad, BattleSimulation sim) {
        WorldState ws = WorldStateBuilder.build(squad, sim);
        return ws.get(Predicate.ENEMY_IN_PORTAL_CELL);
    }

    /** First alive enemy combatant standing on the portal cell, or null. Matches the rule the evaluator uses. */
    private Unit enemyOnPortalCell(Squad squad, BattleSimulation sim) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || !u.type.combatant) continue;
            if (u.faction == squad.faction) continue;
            if (u.getCellX() == portalX && u.getCellY() == portalY) return u;
        }
        return null;
    }

    /** Parse the trailing index off a {@code losCell:N} slot name; -1 on bad input. */
    private static int parseSlotIdx(String slotName) {
        int colon = slotName.indexOf(':');
        if (colon < 0) return -1;
        try {
            return Integer.parseInt(slotName.substring(colon + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

}
