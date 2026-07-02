package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

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
 * sampled across the compound's {@link GarrisonArea garrison rooms}) bound
 * via {@link #roles}, rather than all freezing at the first cell they reach
 * inside the zone. Without this the whole squad piled onto the doorway /
 * anchor approach because {@code hold()} froze each member in place the
 * instant it crossed the zone boundary. Engagement (enemies present) still
 * defers to {@link #engageInZone}, whose firing-position picker already
 * spreads via occupancy + AOE-spread scoring.
 */
public final class HoldZone extends AbstractZoneAction {

    /**
     * Cells of slack around the compound footprint when resolving its garrison
     * rooms for hold-cell placement — matches the capture pass's gate margin.
     */
    private static final int HOLD_GARRISON_MARGIN = 2;

    private final TacticalNode compoundNode;
    /**
     * Per-member hold cells, distinct and spread across the compound's garrison
     * rooms (parallel x/y arrays). Picked once at plan-synthesis time
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
     * members hold on the anchor. The large-negative overflow score keeps it
     * below every distinct-cell slot in {@link RoleAssigner}'s mean-score
     * ordering, so the spread cells fill first.
     */
    @Override
    public List<RoleAssigner.Slot<Entity>> roles(Squad squad, BattleView sim) {
        if (holdX == null || holdX.length == 0) {
            return List.of(new RoleAssigner.Slot<>("hold:overflow",
                    Math.max(1, squad.aliveMembers), c -> 0f));
        }
        List<RoleAssigner.Slot<Entity>> slots = new ArrayList<>(holdX.length + 1);
        for (int i = 0; i < holdX.length; i++) {
            final int hx = holdX[i];
            final int hy = holdY[i];
            slots.add(new RoleAssigner.Slot<>("hold:" + i, 1,
                    c -> -TacticalScoring.cellDistance(sim.world().cellX(c.entityId), sim.world().cellY(c.entityId), hx, hy)));
        }
        slots.add(new RoleAssigner.Slot<>("hold:overflow",
                Math.max(1, squad.aliveMembers), c -> -1_000_000f));
        return slots;
    }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        CompoundService.Record record = sim.getCompoundService().getRecord(compoundNode);
        if (record != null && record.state == CompoundService.CompoundState.MARINE_HELD) {
            return ActionStatus.SUCCESS;
        }

        // This member's assigned post — a distinct cell inside the compound, or
        // the anchor for an overflow/unslotted member.
        int postX, postY;
        int slot = assignedSlot(member, squad);
        if (slot >= 0 && holdX != null && slot < holdX.length) {
            postX = holdX[slot];
            postY = holdY[slot];
        } else {
            postX = compoundNode.anchorX;
            postY = compoundNode.anchorY;
        }

        // Zone-entry rule (AbstractZoneAction): pull a member standing outside
        // the zone in toward its post before it holds/engages. Without it the
        // squad fights the room from the doorway and the capture stays
        // CONTESTED (one marine in, defenders in) forever.
        if (!memberInZone(member, sim)) {
            advanceIntoZone(member, squad, sim, postX, postY, false);
            return ActionStatus.RUNNING;
        }

        Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
        boolean enemiesInZone = !ZoneQueries.zoneClear(targetZoneId, enemy, sim);

        if (enemiesInZone) {
            return engageInZone(member, squad, sim, enemy);
        }

        // No enemies: fan out to the assigned post and hold there, rather than
        // freezing wherever the member first crossed into the zone.
        if (sim.world().cellX(member.entityId) == postX && sim.world().cellY(member.entityId) == postY) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        if (sim.world().moveProgress(member.entityId) == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), postX, postY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    /** This member's hold-cell index from its {@code "hold:i"} role slot, or {@code -1} for the overflow slot / no binding (→ hold on the anchor). */
    private static int assignedSlot(Entity member, Squad squad) {
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

    private ActionStatus engageInZone(Entity member, Squad squad, BattleControl sim, Faction enemy) {
        Entity target = sim.targetOf(member);
        boolean targetOutOfZone = target != null
                && sim.getZoneGraph().zoneIdAt(sim.world().cellX(target.entityId), sim.world().cellY(target.entityId)) != targetZoneId;
        if (target == null
                || targetOutOfZone
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = pickInZoneTarget(member, sim, enemy);
            if (target == null) target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }
        if (target == null) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }

        float dist = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean inRange = dist <= sim.world().attackRange(member.entityId);
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        if (inRange && visible) {
            sim.combat().setFireIntent(member.entityId, Entity.idOf(target), FireStance.STANCED, false);
            // Movement gate, not a fire gate — FiringSystem owns the cooldown
            // check for the shot itself. This read only preserves the old
            // control flow: on the ready tick the member stands to shoot;
            // between shots it keeps creeping toward a better firing position
            // (the block below).
            if (sim.combat().cooldownTimer(member.entityId) <= 0f) {
                return ActionStatus.RUNNING;
            }
        }

        if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(target.entityId), sim.world().cellY(target.entityId)) != targetZoneId) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        if (sim.world().moveProgress(member.entityId) == 0f) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(member, target);
            if (dest == null) {
                sim.world().setTargetId(member.entityId, 0L);
                hold(member, sim);
                return ActionStatus.RUNNING;
            }
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    private Entity pickInZoneTarget(Entity self, BattleView sim, Faction enemy) {
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity other = sim.liveUnitAt(i);
            if (other.faction != enemy) continue;
            if (!other.type.combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(other.entityId), sim.world().cellY(other.entityId)) != targetZoneId) continue;
            if (!sim.getGrid().hasLineOfSight(sim.world().cellX(self.entityId), sim.world().cellY(self.entityId), sim.world().cellX(other.entityId), sim.world().cellY(other.entityId))) continue;
            float d = TacticalScoring.cellDistance(sim.world().cellX(self.entityId), sim.world().cellY(self.entityId), sim.world().cellX(other.entityId), sim.world().cellY(other.entityId));
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private static void hold(Entity member, BattleControl sim) {
        if (!Paths.isEmpty(sim.world().path(member.entityId))) sim.clearPath(member);
        sim.world().setMoveProgress(member.entityId, 0f);
        sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
    }

    /**
     * Pick up to {@code count} distinct, spread-out hold cells inside the
     * compound. Candidates are the walkable cells of the compound's
     * {@link GarrisonArea garrison rooms} — AABB-gated, so the open exterior and
     * 1-cell doorway zones are excluded; falls back to the target zone, then to
     * the anchor, so the result is never empty.
     *
     * <p>Spread is farthest-point sampling: seed with the candidate nearest the
     * anchor (keep a presence on the objective cell), then repeatedly add the
     * candidate that maximizes the minimum distance to everything already
     * picked. Squads cap ~8 and a room has tens of cells, so the O(count·cells)
     * scan is cheap. Result is two parallel x/y arrays; pass straight into the
     * {@link HoldZone} constructor.
     */
    public static int[][] pickHoldCells(TacticalNode node, int targetZone, int count, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        ZoneGraph zones = sim.getZoneGraph();
        int width = grid.getWidth();

        List<Integer> rooms = GarrisonArea.garrisonZones(node, HOLD_GARRISON_MARGIN, sim);
        if (rooms.isEmpty() && zones.zoneById(targetZone) != null) {
            rooms = List.of(targetZone);
        }
        List<int[]> cand = new ArrayList<>();
        for (int zoneId : rooms) {
            NavigationZone z = zones.zoneById(zoneId);
            if (z == null) continue;
            for (int idx : z.getCellIndices()) {
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
