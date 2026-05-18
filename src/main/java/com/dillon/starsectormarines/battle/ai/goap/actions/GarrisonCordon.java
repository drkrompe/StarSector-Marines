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
import com.dillon.starsectormarines.battle.weapons.FireStance;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Squad posture: multi-portal garrison ambush.</b> Story L's multi-portal
 * flavor — defender squads in a room with two or more portals spread to the
 * doorways and fire opportunistically as enemies become visible. No planter
 * slot (unlike {@link HoldPortalCordon}); the squad is defending its post,
 * not channelling a charge.
 *
 * <p>Forked from {@link HoldPortalCordon}'s portal-holder branch — same
 * guard-cell pattern, same {@link FireStance#STANCED} on-post fire, same
 * cordon-discipline-by-positioning. The fork (rather than refactoring out a
 * shared base) follows the project rule of "ship parallel classes until a
 * fourth caller justifies extraction." When a future story needs the same
 * multi-doorway hold shape we'll fold both into a shared base.
 *
 * <p>Parameterized per-zone — the {@link com.dillon.starsectormarines.battle.ai.goap.goals.GarrisonAmbush}
 * goal's customPlan creates one instance carrying one
 * {@link HoldPortalCordon.GuardPost} per portal of the squad's zone. Not a
 * singleton, not registered in {@code GoapInfantryBehavior.INFANTRY_ACTIONS}:
 * the backward-chaining planner never sees it.
 *
 * <p>Cordon discipline emerges from positioning: each guard cell is on the
 * friendly side of its doorway, inside the squad's zone — same shape Story
 * J's {@link HoldPortalCordon} establishes. A holder will fire on any visible
 * enemy in LOS + range (their natural arc is "their" doorway), but they don't
 * <em>move</em> off the post to chase.
 */
public final class GarrisonCordon implements Action {

    private final List<HoldPortalCordon.GuardPost> posts;

    public GarrisonCordon(List<HoldPortalCordon.GuardPost> posts) {
        this.posts = List.copyOf(posts);
    }

    public List<HoldPortalCordon.GuardPost> posts() { return posts; }

    @Override public String name() { return "GarrisonCordon[" + posts.size() + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return Math.max(1, posts.size()); }

    /**
     * One slot per doorway. Slot scorer is negated distance from the
     * candidate to the guard cell — closest holder wins their nearest
     * doorway. Identical scoring to {@link HoldPortalCordon}'s portal slots
     * (no planter slot to compete with).
     */
    @Override
    public List<RoleAssigner.Slot<Unit>> roles(Squad squad, BattleSimulation sim) {
        List<RoleAssigner.Slot<Unit>> slots = new ArrayList<>(posts.size());
        for (HoldPortalCordon.GuardPost post : posts) {
            slots.add(new RoleAssigner.Slot<>(
                    post.slotName(),
                    1,
                    c -> -TacticalScoring.cellDistance(c.cellX, c.cellY, post.cellX, post.cellY)));
        }
        return slots;
    }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        String slotName = step != null ? step.slotOf(member) : null;
        if (slotName == null) return ActionStatus.RUNNING;

        HoldPortalCordon.GuardPost post = postForSlot(slotName);
        if (post == null) return ActionStatus.RUNNING;
        return executeHolder(member, post, sim);
    }

    /**
     * Identical to {@link HoldPortalCordon}'s portal-holder branch (no
     * planter wiring) — walk to the assigned guard cell while firing
     * opportunistically (MOVING stance), then hold position firing at
     * anything in LOS + range (STANCED). The reset on
     * {@code moveProgress / renderX / renderY} pins the holder in place
     * between bursts so they don't drift off-post.
     */
    private ActionStatus executeHolder(Unit member, HoldPortalCordon.GuardPost post, BattleSimulation sim) {
        boolean atPost = (member.cellX == post.cellX && member.cellY == post.cellY);
        if (!atPost) {
            opportunisticFire(member, sim, FireStance.MOVING);
            if (member.moveProgress == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        member.cellX, member.cellY, post.cellX, post.cellY,
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
            return ActionStatus.RUNNING;
        }
        if (!member.pathEmpty()) sim.clearPath(member);
        member.moveProgress = 0f;
        member.renderX = member.cellX;
        member.renderY = member.cellY;
        opportunisticFire(member, sim, FireStance.STANCED);
        return ActionStatus.RUNNING;
    }

    /**
     * One-shot fire pass — pick a target, fire if in LOS + range with
     * cooldown ready. Mirrors {@link HoldPortalCordon}'s helper of the same
     * shape; if a fourth opportunistic-fire caller appears we'll lift this
     * to a shared static.
     */
    private static void opportunisticFire(Unit member, BattleSimulation sim, FireStance stance) {
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
        sim.fireShot(member, member.target, stance);
        member.cooldownTimer = member.attackCooldown;
        if (member.primaryWeapon != null && member.primaryWeapon.burstCount > 1) {
            member.burstRemaining = member.primaryWeapon.burstCount - 1;
            member.burstTimer = member.primaryWeapon.burstSpacing;
            member.burstTarget = member.target;
        }
    }

    private HoldPortalCordon.GuardPost postForSlot(String slotName) {
        if (slotName == null) return null;
        for (HoldPortalCordon.GuardPost p : posts) {
            if (p.slotName().equals(slotName)) return p;
        }
        return null;
    }
}
