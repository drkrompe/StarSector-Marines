package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.weapons.FireStance;

/**
 * <b>Squad posture: move into a target zone.</b> Each member paths to a
 * representative cell inside {@link #targetZoneId} and walks. The plan
 * advances to {@link ClearZone} as soon as the <em>first</em> member's
 * logical cell crosses into the target zone — matches Stage 1's
 * first-arrival semantics on {@link ApproachPosture}. Stragglers catch up
 * inside the next step (ClearZone keeps moving members not yet in zone).
 *
 * <p>Parameterized per-zone — Story K's customPlan creates one instance per
 * zone in the BFS path. Not a singleton (unlike Stage 1's postures), and not
 * registered in {@code GoapInfantryBehavior.INFANTRY_ACTIONS}: the
 * backward-chaining planner never sees these; they're emitted only by
 * {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureObjectiveZone}'s
 * custom plan.
 *
 * <p>Empty preconditions/effects: this action doesn't participate in the
 * predicate-chain search, so there's nothing to declare. Cost is also
 * irrelevant for the custom-plan path.
 */
public final class EnterZone implements Action {

    private final int targetZoneId;
    /** Destination cell inside the target zone — chosen at construction so all members aim at the same spot and the pathfinder routes them through the portal naturally. */
    private final int destX;
    private final int destY;

    public EnterZone(int targetZoneId, int destX, int destY) {
        this.targetZoneId = targetZoneId;
        this.destX = destX;
        this.destY = destY;
    }

    /**
     * Picks a representative interior cell for {@code zone} — the middle entry
     * in its flat cell-index array. Good-enough centroid stand-in without
     * needing to compute coordinates for every cell: cellIndices is in
     * detection order (roughly scan-line), so the middle is usually deep in
     * the zone rather than at a portal edge.
     */
    public static EnterZone forZone(NavigationZone zone, NavigationGrid grid) {
        int[] cells = zone.getCellIndices();
        int pick = cells.length > 0 ? cells[cells.length / 2] : 0;
        int x = pick % grid.getWidth();
        int y = pick / grid.getWidth();
        return new EnterZone(zone.getZoneId(), x, y);
    }

    public int targetZoneId() { return targetZoneId; }
    public int destX() { return destX; }
    public int destY() { return destY; }

    @Override public String name() { return "EnterZone[" + targetZoneId + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /**
     * Minimum sim-seconds since the last squad replan before
     * contact-halt is allowed to force another. Without this throttle a
     * pinned squad would replan every tick — the planner would re-pick
     * EnterZone (no morale break, no other relevant goal), the marine would
     * halt again, replan would fire again, ad infinitum. 1.0s gives a clean
     * one-replan-per-contact-event under the 2.0s base period.
     */
    private static final float CONTACT_HALT_REPLAN_THROTTLE = 1.0f;

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (sim.getZoneGraph().zoneIdAt(member.cellX, member.cellY) == targetZoneId) {
            return ActionStatus.SUCCESS;
        }
        // Drop a stale-but-alive target when it's no longer worth pursuing
        // (out of LoS / out of range, or a closer visible enemy has appeared).
        // Without the pursuit gate, a member who acquired a target on the
        // approach keeps walking past a garrison ambush that just opened up:
        // the cached pick is still alive, so the null/dead-only check never
        // re-runs the picker, and the per-member contact-halt below reads
        // false against the stale target while the new shooters fire freely.
        // Sister gate to EngagePosture / ClearZone — see refreshTargetIfNotShootable.
        if (member.target == null
                || !member.target.isAlive()
                || !TacticalScoring.shouldKeepPursuing(member, member.target, sim)) {
            member.target = TacticalScoring.findBestTarget(member, sim);
        }

        // Contact-halt: a visible enemy inside attackRange stops the path so
        // the marine fights in place instead of charging past. Scoped to
        // EnterZone (an "advance into a zone" action) — never applied to
        // BreakContact / BreakLOS, whose whole purpose is to keep moving
        // away from contact. Without this gate marines following an EnterZone
        // step walked straight through enemy formations on opportunistic
        // single-cooldown shots.
        boolean inContact = false;
        if (member.target != null) {
            float d = TacticalScoring.cellDistance(member.cellX, member.cellY,
                    member.target.cellX, member.target.cellY);
            boolean visible = sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                    member.target.cellX, member.target.cellY);
            inContact = d <= member.attackRange && visible;
            if (inContact && member.cooldownTimer <= 0f) {
                sim.fireShot(member, member.target, FireStance.STANCED);
                member.cooldownTimer = member.attackCooldown;
                member.beginBurst(member.target);
            }
        }

        if (inContact) {
            if (!member.pathEmpty()) sim.clearPath(member);
            member.moveProgress = 0f;
            member.renderX = member.cellX;
            member.renderY = member.cellY;
            // Accelerate the squad replan so SurviveContact (or any other
            // engagement-tier goal) can take over within a tick or two
            // instead of waiting the full 2s replan period. Throttled so we
            // don't replan every frame: only fire when the last replan was
            // long enough ago that the situation might have meaningfully
            // changed. Without this the unit looked frozen for up to 2s
            // before the morale-broken signal could route to BreakContact.
            if (squad.timeSinceReplan >= CONTACT_HALT_REPLAN_THROTTLE) {
                squad.timeSinceReplan = GoapInfantryBehavior.REPLAN_PERIOD;
            }
            return ActionStatus.RUNNING;
        }

        if (member.moveProgress == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.cellX, member.cellY, destX, destY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }
}
