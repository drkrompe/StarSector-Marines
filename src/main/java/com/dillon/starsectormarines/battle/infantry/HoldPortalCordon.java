package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.combat.FireStance;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Squad posture: cordon a room's doorways while the planter channels.</b>
 * Story J's "1 planter + N portal-holders" composition lives entirely here:
 * a {@code "planter"} slot paths to the charge cell and dwells (so
 * {@link com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective}
 * accumulates progress), while {@code "portal:N"} slots each path once to
 * a guard cell adjacent to their doorway and hold position firing on
 * anything in LOS + range.
 *
 * <p>Cordon discipline emerges from positioning, not from target filtering:
 * the guard cell is inside the squad's zone facing the doorway, so the
 * holder's natural LOS arc is "their" doorway. A holder will still fire on
 * a visible enemy elsewhere when one drifts into LOS, but they don't
 * <em>move</em> off the post to chase — that's the rule that makes cordon
 * discipline read on screen.
 *
 * <p>The planter's role flip + on-site dwell logic that used to live in
 * {@code PlanterBehavior} now lives in this action's {@code "planter"}-slot
 * branch. The unit keeps {@link UnitRole#PLANTER} (so
 * {@code ChargeSiteObjective.tick} still ticks progress on it) but no
 * longer has a separate per-unit dispatch — it's a squad-plan slot like any
 * other holder.
 *
 * <p>Parameterized per-room — the goal's customPlan creates one instance
 * carrying the charge cell + the (portalId, guardCell) tuples for the
 * current zone. Not a singleton, not registered in
 * {@code GoapInfantryBehavior.INFANTRY_ACTIONS}: the backward-chaining
 * planner never sees it.
 */
public final class HoldPortalCordon implements Action {

    /** Slot name for the squad member who runs the plant. RoleAssigner prefers the {@link UnitRole#PLANTER} candidate via {@link #PLANTER_SCORE} so the planter consistently lands in this slot. */
    public static final String PLANTER_SLOT = "planter";

    /** Score the planter scorer returns for a PLANTER-role member. Large enough that the role-assigner's mean-score-orders-slots pass picks the planter slot first; everyone else scores 0 here and falls into portal slots. */
    private static final float PLANTER_SCORE = 1000f;

    /** One doorway worth of cordon — the portal we watch, plus the cell inside the zone we stand on. */
    public static final class GuardPost {
        public final int portalId;
        public final int cellX;
        public final int cellY;

        public GuardPost(int portalId, int cellX, int cellY) {
            this.portalId = portalId;
            this.cellX = cellX;
            this.cellY = cellY;
        }

        /** Slot name encoding — used both when declaring slots and when looking up the post for an assigned member. */
        public String slotName() { return "portal:" + portalId; }
    }

    private final int chargeCellX;
    private final int chargeCellY;
    private final List<GuardPost> posts;

    public HoldPortalCordon(int chargeCellX, int chargeCellY, List<GuardPost> posts) {
        this.chargeCellX = chargeCellX;
        this.chargeCellY = chargeCellY;
        this.posts = List.copyOf(posts);
    }

    public int chargeCellX() { return chargeCellX; }
    public int chargeCellY() { return chargeCellY; }
    public List<GuardPost> posts() { return posts; }

    @Override public String name() { return "HoldPortalCordon[" + posts.size() + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return Math.max(1, posts.size() + 1); }

    @Override
    public java.util.List<int[]> highlightCells(Squad squad, BattleView sim) {
        java.util.List<int[]> out = new java.util.ArrayList<>(posts.size());
        for (GuardPost p : posts) out.add(new int[]{p.cellX, p.cellY});
        return out;
    }

    /**
     * Planter slot first, then one portal slot per doorway. The planter slot
     * scores PLANTER-role members at {@link #PLANTER_SCORE} and everyone else
     * at 0 — RoleAssigner's mean-score-first ordering then picks the planter
     * for that slot before portals draw candidates, so the assignment is
     * stable as long as the squad has an alive planter.
     *
     * <p>Portal-slot scorer is negated distance from the candidate to the
     * guard cell, same as before — closest non-planter holder wins their
     * nearest doorway.
     */
    @Override
    public List<RoleAssigner.Slot<Entity>> roles(Squad squad, BattleView sim) {
        List<RoleAssigner.Slot<Entity>> slots = new ArrayList<>(posts.size() + 1);
        slots.add(new RoleAssigner.Slot<>(
                PLANTER_SLOT,
                1,
                c -> sim.role().role(c.entityId) == UnitRole.PLANTER ? PLANTER_SCORE : 0f));
        for (GuardPost post : posts) {
            slots.add(new RoleAssigner.Slot<>(
                    post.slotName(),
                    1,
                    c -> -TacticalScoring.cellDistance(sim.world().cellX(c.entityId), sim.world().cellY(c.entityId), post.cellX, post.cellY)));
        }
        return slots;
    }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        String slotName = step != null ? step.slotOf(member) : null;
        if (slotName == null) return ActionStatus.RUNNING;

        if (PLANTER_SLOT.equals(slotName)) {
            return executePlanter(member, sim);
        }
        GuardPost post = postForSlot(slotName);
        if (post == null) return ActionStatus.RUNNING;
        return executeHolder(member, post, sim);
    }

    /**
     * Planter slot: path to the charge cell, sit on it. No firing — the
     * planter is channelling. Matches the legacy {@code PlanterBehavior}
     * shape (path → arrive → dwell) without the standalone dispatch.
     * {@link com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective#tick}
     * keys off PLANTER role + on-site cell + {@code moveProgress == 0}, all
     * of which this branch maintains.
     */
    private ActionStatus executePlanter(Entity member, BattleControl sim) {
        boolean onSite = (sim.world().cellX(member.entityId) == chargeCellX && sim.world().cellY(member.entityId) == chargeCellY);
        if (onSite) {
            if (!Paths.isEmpty(sim.world().path(member.entityId))) sim.clearPath(member);
            sim.world().setMoveProgress(member.entityId, 0f);
            sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
            return ActionStatus.RUNNING;
        }
        if (sim.world().moveProgress(member.entityId) == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), chargeCellX, chargeCellY,
                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    /**
     * Portal-holder slot: walk to the assigned guard cell while firing
     * opportunistically, then hold position firing at anything in LOS +
     * range. The {@code setMoveProgress / setRenderPos} reset is what
     * pins the holder in place between shots — no micro-movement, the
     * Stage 2 cordon doesn't reposition (Slice 3's cover-aware reposition
     * is the layer that would change that).
     */
    private ActionStatus executeHolder(Entity member, GuardPost post, BattleControl sim) {
        boolean atPost = (sim.world().cellX(member.entityId) == post.cellX && sim.world().cellY(member.entityId) == post.cellY);
        if (!atPost) {
            // Transit fire — MOVING penalty applies; the holder is mid-step.
            opportunisticFire(member, sim, FireStance.MOVING);
            if (sim.world().moveProgress(member.entityId) == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), post.cellX, post.cellY,
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
            return ActionStatus.RUNNING;
        }
        if (!Paths.isEmpty(sim.world().path(member.entityId))) sim.clearPath(member);
        sim.world().setMoveProgress(member.entityId, 0f);
        sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
        // On-post fire — STANCED, full accuracy. This is the whole reason
        // we stop and hold: the cordon's lethality comes from stanced shots.
        opportunisticFire(member, sim, FireStance.STANCED);
        return ActionStatus.RUNNING;
    }

    /**
     * Shared one-shot fire pass: pick a target, author a fire intent when in
     * LOS + range; {@code battle.combat.FiringSystem} applies the cooldown
     * gate and executes the shot. Stance is caller-supplied because the same
     * helper serves the transit phase ({@link FireStance#MOVING}) and the
     * on-post phase ({@link FireStance#STANCED}) — neither one wants the
     * strict {@code stanceFor} heuristic, since the holder may have
     * moveProgress=0 mid-walk between consecutive cells. Burst follow-ups
     * queue the same way EngagePosture does it so machine-gun weapons still
     * rip a burst from the post.
     */
    private static void opportunisticFire(Entity member, BattleControl sim, FireStance stance) {
        Entity target = sim.targetOf(member);
        if (target == null
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }
        if (target == null) return;
        float d = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        if (d > sim.world().attackRange(member.entityId)) return;
        if (!sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId))) return;
        sim.combat().setFireIntent(member.entityId, Entity.idOf(target), stance, false);
    }

    private GuardPost postForSlot(String slotName) {
        if (slotName == null) return null;
        for (GuardPost p : posts) {
            if (p.slotName().equals(slotName)) return p;
        }
        return null;
    }
}
