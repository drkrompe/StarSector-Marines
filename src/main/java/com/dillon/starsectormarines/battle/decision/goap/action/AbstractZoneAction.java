package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

/**
 * Shared base for the per-zone squad-push actions
 * ({@link EnterZone}, {@link ClearZone}, {@link HoldZone}). All three carry a
 * {@link #targetZoneId}, take no part in the backward-chaining planner (empty
 * preconditions/effects, flat cost), and obey one common rule: <b>a member
 * isn't performing the action until it is actually inside the target zone.</b>
 *
 * <p>That rule is the zone-entry precondition shared across the family —
 * {@link #memberInZone}. A member standing outside {@code targetZoneId} must
 * first consolidate <em>into</em> it via {@link #advanceIntoZone} rather than
 * engaging from an adjacent room: without this, a member with a firing
 * solution across a portal sits at the threshold trading fire forever, and a
 * compound capture stays permanently contested while the squad fights the
 * room from the doorway instead of pushing in.
 *
 * <p>The one tactical knob between members of the family is whether the
 * advance may <em>commit</em> to contact. {@link EnterZone} is the approach
 * step: it scores local force, retreat posture, and distance from the advance
 * axis every tick, then either presses with shots of opportunity or fights
 * inside a bounded off-axis leash. {@link ClearZone}/{@link HoldZone} are
 * commitment steps: the squad is taking the room, so they push through contact
 * while firing suppressively instead of freezing at the threshold. That's the
 * {@code haltOnContact} flag, not duplicated movement code.
 */
abstract class AbstractZoneAction implements Action {

    /**
     * Minimum sim-seconds since the last squad replan before a contact-halt is
     * allowed to force another. Without this throttle a pinned squad would
     * replan every tick — the planner would re-pick the same approach action
     * (no morale break, no other relevant goal), the marine would halt again,
     * replan would fire again, ad infinitum. 1.0s gives a clean
     * one-replan-per-contact-event under the 2.0s base period. Only consulted
     * on the {@code haltOnContact} path.
     */
    protected static final float CONTACT_HALT_REPLAN_THROTTLE = 1.0f;
    /** Raw threat weight that flips a pressing squad into committed contact. */
    static final float ADVANCE_COMMIT_THRESHOLD = 0.55f;
    /** Lower threshold that releases a committed squad back onto the objective route. */
    static final float ADVANCE_RELEASE_THRESHOLD = 0.30f;
    /** Minimum useful off-axis firing-position radius once the squad commits. */
    static final float ADVANCE_LEASH_MIN = 4f;
    /** Maximum off-axis firing-position radius at full threat weight. */
    static final float ADVANCE_LEASH_MAX = 12f;

    protected final int targetZoneId;

    protected AbstractZoneAction(int targetZoneId) {
        this.targetZoneId = targetZoneId;
    }

    public final int targetZoneId() { return targetZoneId; }

    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /** True iff {@code member}'s logical cell lies inside {@link #targetZoneId}. */
    protected final boolean memberInZone(long member, BattleView sim) {
        return sim.getZoneGraph().zoneIdAt(sim.world().cellX(member), sim.world().cellY(member)) == targetZoneId;
    }

    /**
     * Advance a member that is standing <em>outside</em> the target zone toward
     * {@code (destX, destY)} — an interior cell — taking opportunistic shots at
     * a visible in-range enemy while it moves. Caller invokes this only when
     * {@link #memberInZone} is false and then returns {@link ActionStatus#RUNNING}.
     *
     * <p>Target handling mirrors the in-zone engage paths: a stale-but-alive
     * target that's no longer worth pursuing is dropped and re-picked via
     * {@link TacticalScoring#findBestTarget}, so a member that fixated on the
     * approach doesn't walk past fresh shooters.
     *
     * @param haltOnContact when true (approach semantics), the squad's
     *        threat-scored advance leash may stop the member to prosecute a
     *        route contact; when false (commitment semantics), the member keeps
     *        pushing into the zone while firing.
     */
    protected final void advanceIntoZone(long member, Squad squad, BattleControl sim,
                                         int destX, int destY, boolean haltOnContact) {
        boolean committed = false;
        float engageLeash = 0f;
        long advanceThreat = 0L;
        int threatAnchorX = -1;
        int threatAnchorY = -1;
        if (haltOnContact) {
            updateAdvanceThreat(squad, sim, destX, destY);
            committed = squad.advanceEngageCommitted;
            engageLeash = squad.advanceEngageLeash;
            advanceThreat = squad.advanceThreatId;
            threatAnchorX = squad.advanceThreatAnchorX;
            threatAnchorY = squad.advanceThreatAnchorY;
        }

        long target = sim.targetOf(member);
        if (committed && sim.resolveUnit(advanceThreat) != 0L) {
            target = advanceThreat;
            sim.world().setTargetId(member, target);
        } else if (target == 0L || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member, target);
        }

        boolean inContact = false;
        if (target != 0L) {
            float d = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member), sim.world().cellY(member),
                    sim.world().cellX(target), sim.world().cellY(target));
            inContact = d <= sim.world().attackRange(member) && visible;
        }

        if (inContact) {
            sim.combat().setFireIntent(member, target,
                    committed ? FireStance.STANCED : FireStance.MOVING, false);
        } else {
            // Opportunistic return fire while advancing. The pursuit target
            // is out of range/LoS (or absent) — across the open approach
            // that left members marching past enemies they could hit,
            // eating shots without returning any. Fire on the nearest enemy
            // actually in range and LoS, MOVING stance, without halting or
            // touching the pursuit target: the squad still commits to the
            // zone push, the trigger just stops it being a sitting duck.
            // FiringSystem's beginBurst tracks the intent target, so the
            // follow-up burst tracks the enemy we shot, not the pursuit target.
            long opportune = sim.getTacticalScoring().closestEnemyInAttackRange(member);
            if (opportune != 0L) {
                sim.combat().setFireIntent(member, opportune, FireStance.MOVING, false);
            }
        }

        if (committed && inContact) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            if (squad.timeSinceReplan >= CONTACT_HALT_REPLAN_THROTTLE) {
                squad.timeSinceReplan = Planner.REPLAN_PERIOD;
            }
            return;
        }

        if (committed && target != 0L && threatAnchorX >= 0 && threatAnchorY >= 0) {
            int[] firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                    member, target, threatAnchorX, threatAnchorY, engageLeash);
            if (firingPos != null) {
                if (sim.movement().mayRepath(member)) {
                    sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                            sim.world().cellX(member), sim.world().cellY(member),
                            firingPos[0], firingPos[1], sim.getOccupancyMap()));
                }
                sim.advanceMovement(member);
                return;
            }
        }

        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member), destX, destY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
    }

    /**
     * Recomputes the squad-level route threat at most once per sim tick and
     * applies commit/release hysteresis. The synchronized section is required
     * because members of one squad can execute in parallel; the tally itself
     * is member-independent, so whichever member arrives first may author the
     * cache without making behavior order-dependent.
     */
    private static void updateAdvanceThreat(Squad squad, BattleControl sim, int destX, int destY) {
        int tick = sim.getSimTickIndex();
        if (squad.advanceThreatTick == tick) return;
        synchronized (squad.lock) {
            if (squad.advanceThreatTick == tick) return;
            TacticalScoring.AdvanceThreat threat = sim.getTacticalScoring()
                    .assessAdvanceThreat(squad, destX, destY);
            squad.advanceEngageWeight = threat.weight();
            squad.advanceEngageCommitted = shouldCommitAdvance(
                    squad.advanceEngageCommitted, threat.weight());
            squad.advanceEngageLeash = squad.advanceEngageCommitted
                    ? Math.max(ADVANCE_LEASH_MIN, ADVANCE_LEASH_MAX * threat.weight())
                    : 0f;
            squad.advanceThreatId = threat.primaryThreatId();
            squad.advanceThreatFoes = threat.foes();
            squad.advanceThreatFriends = threat.friends();
            squad.advanceThreatAnchorX = threat.axisAnchorX();
            squad.advanceThreatAnchorY = threat.axisAnchorY();
            squad.advanceThreatRetreating = threat.primaryRetreating();
            squad.advanceThreatTick = tick;
        }
    }

    static boolean shouldCommitAdvance(boolean wasCommitted, float weight) {
        return wasCommitted ? weight >= ADVANCE_RELEASE_THRESHOLD
                : weight >= ADVANCE_COMMIT_THRESHOLD;
    }

    /**
     * Representative interior cell of {@code zone} — the middle entry in its
     * flat cell-index array. {@code cellIndices} is in detection order (roughly
     * scan-line), so the middle usually lands deep in the zone rather than at a
     * portal edge. Returns {@code null} for a missing or empty zone.
     */
    protected static int[] interiorCell(NavigationZone zone, NavigationGrid grid) {
        if (zone == null) return null;
        int[] cells = zone.getCellIndices();
        if (cells.length == 0) return null;
        int pick = cells[cells.length / 2];
        return new int[]{ pick % grid.getWidth(), pick / grid.getWidth() };
    }

    /** {@link #interiorCell} resolved from a zone id against the live graph/grid. */
    protected static int[] interiorCellOf(int zoneId, BattleView sim) {
        return interiorCell(sim.getZoneGraph().zoneById(zoneId), sim.getGrid());
    }
}
