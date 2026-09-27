package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Squad posture: hold a zone until a compound capture completes.</b>
 * Terminal step in a {@link com.dillon.starsectormarines.battle.infantry.SecureCompoundGoal}
 * plan. Marines stay in the zone, engage enemies that enter, and report
 * {@link ActionStatus#SUCCESS} once the compound's state reaches
 * {@link CompoundService.CompoundState#MARINE_HELD}.
 *
 * <p>Capture progress is driven by {@link com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem}
 * at 1 Hz — this action's job is simply to keep marines present in the zone
 * so the capture timer accumulates. Engagement behavior mirrors
 * {@link ClearZone}: in-zone enemies are preferred, out-of-zone enemies are
 * shot opportunistically but not pursued.
 *
 * <p>Parameterized per-zone like {@link EnterZone} and {@link ClearZone};
 * not a singleton, not in {@code INFANTRY_ACTIONS}. Emitted only by
 * {@link com.dillon.starsectormarines.battle.infantry.SecureCompoundGoal}'s
 * custom plan.
 *
 * <p><b>Per-member spread.</b> When no enemies are in the zone, members
 * fan out to distinct hold cells ({@link #pickHoldCells}, farthest-point
 * sampled across the compound's singular capture room) bound
 * via {@link #roles}, rather than all freezing at the first cell they reach
 * inside the zone. Without this the whole squad piled onto the doorway /
 * anchor approach because {@code hold()} froze each member in place the
 * instant it crossed the zone boundary. Engagement (enemies present) still
 * defers to {@link #engageInZone}, whose firing-position picker already
 * spreads via occupancy + AOE-spread scoring.
 */
public final class HoldZone extends AbstractZoneAction {

    public static boolean localHoldPositionsEnabled() {
        return Boolean.parseBoolean(System.getProperty("battle.goap.localHoldPositions", "true"));
    }

    private final TacticalNode compoundNode;
    private final boolean localHoldPositions = localHoldPositionsEnabled();
    /**
     * Per-member hold cells, distinct and spread across the compound's capture
     * room (parallel x/y arrays). Picked once at plan-synthesis time
     * ({@link #pickHoldCells}); a member is bound to index {@code i} via the
     * {@code "hold:i"} role slot. Always holds at least one cell (anchor
     * fallback), so it is never null/empty in practice.
     */
    private final int[] holdX;
    private final int[] holdY;

    public HoldZone(int targetZoneId, TacticalNode compoundNode, int[] holdX, int[] holdY) {
        super(targetZoneId);
        this.compoundNode = compoundNode;
        this.holdX = holdX;
        this.holdY = holdY;
    }

    @Override public String name() { return "HoldZone[" + targetZoneId + "]"; }

    /**
     * One {@code "hold:i"} slot per hold cell (count 1, scored by proximity so
     * the nearest member claims each cell and crossings are minimized), plus a
     * lowest-priority {@code "hold:overflow"} catch-all so a squad with more
     * members than cells (a cramped room) still binds everyone — overflow
     * members share the first legal post. The large-negative overflow score keeps it
     * below every distinct-cell slot in {@link RoleAssigner}'s mean-score
     * ordering, so the spread cells fill first.
     */
    @Override
    public List<RoleAssigner.Slot<Long>> roles(Squad squad, BattleView sim) {
        if (holdX == null || holdX.length == 0) {
            return List.of(new RoleAssigner.Slot<>("hold:overflow",
                    Math.max(1, squad.aliveMembers), c -> 0f));
        }
        List<RoleAssigner.Slot<Long>> slots = new ArrayList<>(holdX.length + 1);
        for (int i = 0; i < holdX.length; i++) {
            final int hx = holdX[i];
            final int hy = holdY[i];
            slots.add(new RoleAssigner.Slot<>("hold:" + i, 1,
                    c -> -TacticalScoring.cellDistance(sim.world().x(c), sim.world().y(c), hx + 0.5f, hy + 0.5f)));
        }
        slots.add(new RoleAssigner.Slot<>("hold:overflow",
                Math.max(1, squad.aliveMembers), c -> -1_000_000f));
        return slots;
    }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        CompoundService.Record record = sim.getCompoundService().getRecord(compoundNode);
        if (record != null && record.state == CompoundService.CompoundState.MARINE_HELD) {
            return ActionStatus.SUCCESS;
        }

        // Overflow shares the first legal post, not a possibly out-of-zone anchor.
        int slot = assignedSlot(member, squad);
        int postX = postX(slot), postY = postY(slot);

        // Zone-entry rule (AbstractZoneAction): pull a member standing outside
        // the zone in toward its post before it holds/engages. Without it the
        // squad fights the room from the doorway and the capture stays
        // CONTESTED (one marine in, defenders in) forever.
        if (!memberInZone(member, sim)) {
            advanceIntoZone(member, squad, sim, postX, postY, false);
            return ActionStatus.RUNNING;
        }

        boolean enemiesInZone =
                !ZoneQueries.zoneClearOfHostiles(targetZoneId, squad.faction, sim);

        if (enemiesInZone) {
            return engageInZone(member, squad, sim);
        }

        // No enemies: fan out to the assigned post and hold there, rather than
        // freezing wherever the member first crossed into the zone.
        if (sim.movement().atCell(member, postX, postY)) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member), postX, postY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    int postX(int slot) {
        if (holdX != null && holdX.length > 0) {
            if (slot >= 0 && slot < holdX.length) return holdX[slot];
            if (localHoldPositions) return holdX[0];
        }
        return compoundNode.anchorX;
    }

    int postY(int slot) {
        if (holdY != null && holdY.length > 0) {
            if (slot >= 0 && slot < holdY.length) return holdY[slot];
            if (localHoldPositions) return holdY[0];
        }
        return compoundNode.anchorY;
    }

    /** This member's hold-cell index, or {@code -1} for overflow / no binding. */
    private static int assignedSlot(long member, Squad squad) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        if (step == null) return -1;
        String slotName = step.slotOf(member);
        if (slotName == null) return -1;
        int colon = slotName.indexOf(':');
        if (colon < 0) return -1;
        try {
            return Integer.parseInt(slotName.substring(colon + 1));
        } catch (NumberFormatException ex) {
            return -1;   // "hold:overflow"
        }
    }

    private ActionStatus engageInZone(long member, Squad squad, BattleControl sim) {
        long target = sim.targetOf(member);
        boolean targetOutOfZone = target != 0L
                && sim.getZoneGraph().zoneIdAt(sim.world().cellX(target), sim.world().cellY(target)) != targetZoneId;
        if (target == 0L
                || targetOutOfZone
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = pickInZoneTarget(member, sim, squad.faction);
            if (target == 0L) target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member, target);
        }
        if (target == 0L) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }

        float dist = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target));
        boolean inRange = dist <= sim.world().attackRange(member);
        boolean clearShot = sim.getTacticalScoring().hasClearShot(member, target);
        if (inRange && clearShot) {
            sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
            // Movement gate, not a fire gate — FiringSystem owns the cooldown
            // check for the shot itself. This read only preserves the old
            // control flow: on the ready tick the member stands to shoot;
            // between shots it keeps creeping toward a better firing position
            // (the block below).
            if (sim.combat().cooldownTimer(member) <= 0f) {
                return ActionStatus.RUNNING;
            }
        }

        if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(target), sim.world().cellY(target)) != targetZoneId) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        if (sim.movement().mayRepath(member)) {
            int[] dest = sim.getTacticalScoring().selectFiringPosition(
                    member, target, squad, sim.getSimTickIndex(), false);
            if (dest == null) {
                sim.world().setTargetId(member, 0L);
                hold(member, sim);
                return ActionStatus.RUNNING;
            }
            int[] path = GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    dest[0], dest[1], sim.getOccupancyMap());
            if (Paths.isEmpty(path)) {
                sim.getTacticalScoring().forgetFiringPosition(member);
            // Stage 1 of findFiringPosition scores LOS and range and does
            // not verify reachability, so a walled-off cell is an ordinary
            // answer from it. Its stage 2 vantage probe does pathfind, and
            // findReachableFiringPosition is the seam that falls through to
            // it -- so an empty path here is a question for the probe, not
            // a verdict. Dropping the target on it discards approaches that
            // exist, which is a squad refusing to walk round a building.
                dest = sim.getTacticalScoring().selectFiringPosition(
                        member, target, squad, sim.getSimTickIndex(), true);
                path = dest == null ? GridPathfinder.EMPTY_PATH
                        : GridPathfinder.findPath(sim.getGrid(),
                                sim.world().cellX(member), sim.world().cellY(member),
                                dest[0], dest[1], sim.getOccupancyMap());
            }
            if (Paths.isEmpty(path)) {
                // Both stages refuse: no approach exists from here.
                sim.getTacticalScoring().forgetFiringPosition(member);
                sim.world().setTargetId(member, 0L);
                hold(member, sim);
                return ActionStatus.RUNNING;
            }
            sim.setPath(member, path);
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    private long pickInZoneTarget(long self, BattleView sim, Faction selfFaction) {
        long best = 0L;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long other = sim.liveUnitAt(i);
            if (!selfFaction.hostileTo(sim.identity().faction(other))) continue;
            if (!sim.identity().type(other).combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(other), sim.world().cellY(other)) != targetZoneId) continue;
            if (!sim.getGrid().hasLineOfSight(sim.world().cellX(self), sim.world().cellY(self), sim.world().cellX(other), sim.world().cellY(other))) continue;
            float d = TacticalScoring.cellDistance(sim.world().x(self), sim.world().y(self), sim.world().x(other), sim.world().y(other));
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private static void hold(long member, BattleControl sim) {
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
    }

    /**
     * Pick up to {@code count} distinct, spread-out hold cells inside the
     * compound footprint AND exact capture zone. A breach merging that room
     * into the exterior must not spread the squad across the whole map.
     * With no local cells, use the nearest legal target-zone cell; only a
     * missing/empty zone falls back to the anchor.
     *
     * <p>Spread is farthest-point sampling: seed with the candidate nearest the
     * anchor (keep a presence on the objective cell), then repeatedly add the
     * candidate that maximizes the minimum distance to everything already
     * picked. Incremental minimum distances make this O(count·local cells).
     * Result is two parallel x/y arrays; pass straight into the
     * {@link HoldZone} constructor.
     */
    public static int[][] pickHoldCells(TacticalNode node, int targetZone, int count, BattleView sim) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile == null ? 0L : System.nanoTime();
        try {
            return localHoldPositionsEnabled()
                    ? HoldPositionPicker.pick(node, targetZone, count, sim.getGrid(), sim.getZoneGraph())
                    : pickLegacyHoldCells(node, targetZone, count, sim);
        } finally {
            if (profile != null) profile.record(TickInnerProfile.Bucket.HOLD_POSITION,
                    System.nanoTime() - started);
        }
    }

    /** Whole-zone allocation-heavy control retained for same-build profiling. */
    private static int[][] pickLegacyHoldCells(TacticalNode node, int targetZone, int count, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph zones = sim.getZoneGraph();
        int width = grid.getWidth();

        List<int[]> cand = new ArrayList<>();
        NavigationZone z = zones.zoneById(targetZone);
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (z != null) {
            for (int idx : z.getCellIndices()) {
                if (profile != null) profile.record(TickInnerProfile.Bucket.HOLD_POSITION_CELL, 0L);
                cand.add(new int[]{ idx % width, idx / width });
            }
        }
        if (cand.isEmpty()) {
            return new int[][]{ { node.anchorX }, { node.anchorY } };
        }

        int n = Math.max(1, Math.min(count, cand.size()));
        List<int[]> picked = new ArrayList<>(n);

        int[] seed = cand.get(0);
        float seedDist = dist2(seed[0], seed[1], node.anchorX, node.anchorY);
        for (int[] c : cand) {
            float d = dist2(c[0], c[1], node.anchorX, node.anchorY);
            if (d < seedDist) { seedDist = d; seed = c; }
        }
        picked.add(seed);

        while (picked.size() < n) {
            int[] best = null;
            float bestMin = 0f;
            for (int[] c : cand) {
                float minD = Float.MAX_VALUE;
                for (int[] p : picked) {
                    float d = dist2(c[0], c[1], p[0], p[1]);
                    if (d < minD) minD = d;
                }
                if (minD > bestMin) { bestMin = minD; best = c; }
            }
            if (best == null) break;   // every remaining candidate already picked
            picked.add(best);
        }

        int[] xs = new int[picked.size()];
        int[] ys = new int[picked.size()];
        for (int i = 0; i < picked.size(); i++) {
            xs[i] = picked.get(i)[0];
            ys[i] = picked.get(i)[1];
        }
        return new int[][]{ xs, ys };
    }

    private static float dist2(int ax, int ay, int bx, int by) {
        float dx = ax - bx;
        float dy = ay - by;
        return dx * dx + dy * dy;
    }
}
