package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Squad posture: cordon a room's doorways.</b> Each member is assigned a
 * distinct portal of the squad's current zone (Story J's "1 planter + N
 * portal-holders" rule, sans planter — see
 * {@link com.dillon.starsectormarines.battle.ai.goap.goals.CordonForPlant}
 * for the broader composition). Holders path once to a guard cell adjacent
 * to their doorway, then hold position and fire on anything in
 * LOS + range.
 *
 * <p>Cordon discipline emerges from positioning, not from target filtering:
 * the guard cell is inside the squad's zone facing the doorway, so the
 * member's natural LOS arc is "their" doorway. A holder will still fire on
 * a visible enemy elsewhere when one drifts into LOS, but they don't
 * <em>move</em> off the post to chase — that's the rule that makes
 * cordon discipline read on screen.
 *
 * <p>Parameterized per-room — the goal's customPlan creates one instance
 * carrying the (portalId, guardCell) tuples for the current zone. Not a
 * singleton, not registered in
 * {@code GoapInfantryBehavior.INFANTRY_ACTIONS}: the backward-chaining
 * planner never sees it.
 */
public final class HoldPortalCordon implements Action {

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

    private final List<GuardPost> posts;

    public HoldPortalCordon(List<GuardPost> posts) {
        this.posts = List.copyOf(posts);
    }

    public List<GuardPost> posts() { return posts; }

    @Override public String name() { return "HoldPortalCordon[" + posts.size() + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return Math.max(1, posts.size()); }

    /**
     * One slot per portal with count {@code 1} — distinct doorways draw
     * distinct members. Scorer is negated distance from the candidate to the
     * guard cell: closest holder wins their nearest doorway, which dovetails
     * with RoleAssigner's swap-improvement pass to produce a sensible
     * member→portal pairing without exhaustive search.
     */
    @Override
    public List<RoleAssigner.Slot<Unit>> roles(Squad squad, BattleSimulation sim) {
        List<RoleAssigner.Slot<Unit>> slots = new ArrayList<>(posts.size());
        for (GuardPost post : posts) {
            slots.add(new RoleAssigner.Slot<>(
                    post.slotName(),
                    1,
                    c -> -TacticalScoring.cellDistance(c.cellX, c.cellY, post.cellX, post.cellY)));
        }
        return slots;
    }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Walk back to the current plan step to figure out which portal this
        // member was assigned. The Stage 2 dispatch (GoapInfantryBehavior.update)
        // already proves slotOf(member) is non-null before calling execute,
        // so this only no-ops on adversarial state.
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        String slotName = step != null ? step.slotOf(member) : null;
        GuardPost post = postForSlot(slotName);
        if (post == null) return ActionStatus.RUNNING;

        // Phase 1: path to the guard cell. Once we're standing on it, drop
        // the path and switch to stationary fire mode. Movement and shooting
        // overlap on the way — we don't want the member to ignore an enemy
        // shooting them while they walk to their post.
        boolean atPost = (member.cellX == post.cellX && member.cellY == post.cellY);
        if (!atPost) {
            opportunisticFire(member, sim);
            if (member.moveProgress == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.cellX, member.cellY, post.cellX, post.cellY,
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
            return ActionStatus.RUNNING;
        }

        // Phase 2: stay on post, fire at anything in LOS + range. We
        // explicitly clear any leftover path so the member doesn't continue
        // a stale walk; matches Stage 1's "arrived, stand still" pattern.
        if (!member.pathEmpty()) sim.clearPath(member);
        member.moveProgress = 0f;
        member.renderX = member.cellX;
        member.renderY = member.cellY;
        opportunisticFire(member, sim);
        return ActionStatus.RUNNING;
    }

    /**
     * Shared one-shot fire pass: pick a target, fire if in LOS + range with
     * cooldown ready. No movement — that's the cordon's whole point. Burst
     * follow-ups are queued the same way EngagePosture does it so machine-gun
     * weapons still rip a burst from the post.
     */
    private static void opportunisticFire(Unit member, BattleSimulation sim) {
        if (member.target == null
                || !member.target.isAlive()
                || !TacticalScoring.shouldKeepPursuing(member, member.target, sim)) {
            member.target = TacticalScoring.findBestTarget(member, sim);
        }
        if (member.target == null || member.cooldownTimer > 0f) return;
        float d = TacticalScoring.cellDistance(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        if (d > member.attackRange) return;
        if (!sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY)) return;
        sim.fireShot(member, member.target);
        member.cooldownTimer = member.attackCooldown;
        if (member.primaryWeapon != null && member.primaryWeapon.burstCount > 1) {
            member.burstRemaining = member.primaryWeapon.burstCount - 1;
            member.burstTimer = member.primaryWeapon.burstSpacing;
            member.burstTarget = member.target;
        }
    }

    private GuardPost postForSlot(String slotName) {
        if (slotName == null) return null;
        for (GuardPost p : posts) {
            if (p.slotName().equals(slotName)) return p;
        }
        return null;
    }
}
