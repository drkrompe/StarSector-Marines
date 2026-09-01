package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.infantry.ReinforceContact;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAssaultPicture;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>Squad posture: attack move.</b> Advance to a cell and destroy what is met
 * on the way, then carry on. The squad keeps the order through contact instead
 * of surrendering it.
 *
 * <p>This is the counterpart to {@link DefendTrack}'s {@code ADVANCE_TRACK},
 * which abandons its destination on the first remembered hostile and hands the
 * squad to an engagement goal that has never heard of the objective. That
 * produced a squad which forgot where it was going the moment it remembered an
 * enemy, and pursued without a leash. Here the destination survives the fight:
 * the squad commits when the route threat earns it, fights from bounded firing
 * positions anchored on the route, and resumes when the threat releases —
 * {@link AbstractZoneAction#advanceIntoZone} with {@code haltOnContact}, the
 * same machinery {@link EnterZone} uses to cross to an assigned room.
 *
 * <p><b>Cooperation is the point of the order.</b> Several squads converging on
 * one position each reach the same conclusion on their own — form a line and
 * shoot — so three squads produce three frontal lines and no maneuver.
 * {@code AssaultCoordinationSystem} publishes a role per squad, and this action
 * spends it: a {@code BASE_OF_FIRE} squad plants and holds the enemy's
 * attention from where it stands, while a {@code MANEUVER} sibling aims at a
 * bearing off the fixing squad's axis rather than at the objective. When no
 * sibling shares the contact the role is {@code NONE} and this is an ordinary
 * bounded advance, which is the degenerate case rather than a special one.
 *
 * <p>Roles choose the manner, never the destination. A squad whose flank is
 * structurally unreachable falls back to advancing on its own objective, and
 * morale, a lost fire team, or a fresh assignment still pull it off the role
 * the ordinary way.
 */
public final class AttackMove extends AbstractZoneAction {

    /** Cells from the destination within which the attack move is done. */
    public static final float ARRIVAL_RADIUS = 2f;
    /**
     * Cells from the destination within which the whole squad — not just the
     * member being asked — counts as arrived. An attack move plan is one step
     * shared by the squad, so the first member inside {@link #ARRIVAL_RADIUS}
     * cannot be allowed to complete it alone: {@code GoapInfantryBehavior}
     * advances the plan off any member reporting {@code SUCCESS}, and every
     * other member then finds the plan complete and skips its movement call
     * for that tick, freezing the squad in place while it replans. Deliberately
     * looser than {@code ARRIVAL_RADIUS} — this is a footprint check for a
     * body of people, not a single member's cell, and it has to tolerate an
     * ordinary trailing member without pinning the squad on a straggler that
     * can never quite close the gap.
     */
    static final float SQUAD_ARRIVAL_RADIUS = 5f;
    /** Off-axis firing radius a fixing squad may use while holding the contact. */
    static final float BASE_OF_FIRE_LEASH = 8f;
    /**
     * Cells from the contact at which a maneuvering squad aims its bearing.
     * Wider than the fire-team flank inside one squad: this is a whole squad
     * coming from another direction, and it needs room to arrive as a body
     * rather than trickling into the fixing squad's line of fire.
     */
    static final float SQUAD_FLANK_RADIUS = 14f;

    private final int destX;
    private final int destY;

    public AttackMove(int destX, int destY) {
        // The zone is context rather than a target: advanceIntoZone works off
        // the destination cell, and arrival here is reaching that cell rather
        // than crossing a portal. An attack move across open ground often
        // starts and ends inside one enormous outdoor zone.
        super(-1);
        this.destX = destX;
        this.destY = destY;
    }

    public int destX() { return destX; }
    public int destY() { return destY; }

    @Override public String name() { return "AttackMove"; }

    @Override
    public Map<String, List<Long>> assignRoles(Squad squad, BattleView sim,
                                                List<Long> candidates) {
        return FireTeamGroups.assignments(FIRE_TEAM, candidates, sim.squad());
    }

    /**
     * The action authors its own fire on every branch — the fixing plant, the
     * committed firing line, and the moving shots of opportunity inside
     * {@code advanceIntoZone}. The dispatcher's primary auto-fill would only
     * overwrite a stance this action chose deliberately.
     */
    @Override public boolean permitsOpportunityFire() { return false; }

    /**
     * A squad the player pointed at a cell goes to that cell. It still fights
     * what it meets on the way — the route-threat commit, the firing line, the
     * shots of opportunity are all untouched — but it does not decide on its
     * own to walk off and prosecute a contact somewhere else instead.
     *
     * <p>This is the one branch that discards the destination outright.
     * Prosecution anchors the firing-position search on the squad's own
     * centroid at {@link AbstractZoneAction#ADVANCE_LEASH_MAX}, so the ordered
     * cell is never routed to at all, and the release condition belongs to the
     * contact picture rather than to the order: doctrine leaves HOLD only at a
     * risk score of -2 or better, which a squad with the upper hand and a live
     * contact in front of it never reaches. A player order given in that state
     * was accepted, replanned on, and then silently ignored for as long as the
     * contact stayed alive.
     *
     * <p><b>Deliberately only prosecution.</b> A hard hold — doctrine HOLD with
     * {@code RECEIVE} initiative — still plants the squad, because that is a
     * squad being shot at rather than a squad choosing a fight, and a player
     * watching marines go to ground under fire can see why they stopped.
     * Watching them stroll toward an enemy thirty cells off the ordered
     * bearing, they cannot.
     */
    @Override
    protected boolean prosecutesContactOffRoute(Squad squad) {
        return !squad.hasPlayerTacticalOrder(AssignmentKind.ATTACK_MOVE);
    }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null
                || assignment.kind() != AssignmentKind.ATTACK_MOVE
                || assignment.targetCellX() != destX
                || assignment.targetCellY() != destY) {
            sim.clearPath(member);
            return ActionStatus.FAILURE;
        }

        SquadAssaultPicture assault = squad.assaultPicture;
        if (assault.isBaseOfFire()) {
            long contact = sim.resolveUnit(assault.sharedContactId());
            if (contact != 0L) {
                holdBaseOfFire(member, contact, sim);
                return ActionStatus.RUNNING;
            }
        }

        if (TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                destX + 0.5f, destY + 0.5f) <= ARRIVAL_RADIUS) {
            if (squadHasArrived(squad, sim)) {
                return ActionStatus.SUCCESS;
            }
            // This member is on the objective, but the plan step is shared
            // by the whole squad and reporting SUCCESS here would complete
            // it off this one arrival, freezing every other member's
            // movement for the tick (see SQUAD_ARRIVAL_RADIUS). Hold this
            // member's ground and keep it shooting rather than idling —
            // doing nothing here just moves the freeze down to one marine
            // instead of fixing it.
            holdArrival(member, sim);
            return ActionStatus.RUNNING;
        }

        int[] aim = maneuverAim(squad, assault, sim);

        // Deliberately no bound, though the shared machinery is right here and
        // this order is the obvious candidate for it.
        //
        // Held off by decision rather than by measurement: over the canonical
        // matrix, bounding attack moves moved neither fixture by a single tick
        // in either direction. A control run at the commit before the bound was
        // added returned the same numbers as every run after it, so the earlier
        // reading of a large cost was a concurrent change to the reinforcement
        // system arriving on a merge, not this. The bound has no measured price
        // here and no measured benefit either, which is its own result: these
        // two fixtures cannot see it, and a claim about it needs a scene built
        // to ask the question rather than a whole-battle harness.
        clearBounding(squad);

        // Deliberately no quiet echelon. That hold exists so a squad crossing
        // to an assigned room arrives with a readable team footprint, and it
        // fires only when the squad has no contacts at all — on an attack move
        // that is formation decoration charged against the one order whose job
        // is to get somewhere and fight. Enabling it here multiplied quiet
        // non-closing time ninefold and cost both canonical fixtures their
        // terminal result.
        advanceIntoZone(member, squad, sim, aim[0], aim[1], true);
        return ActionStatus.RUNNING;
    }

    /**
     * Where this member is trying to get to. Normally the objective; for a
     * squad told to maneuver, a bearing off the fixing squad's axis so the two
     * squads arrive on the enemy from different directions instead of stacking
     * on one.
     */
    private int[] maneuverAim(Squad squad, SquadAssaultPicture assault, BattleView sim) {
        if (!assault.isManeuvering()) return new int[]{destX, destY};
        long contact = sim.resolveUnit(assault.sharedContactId());
        if (contact == 0L) return new int[]{destX, destY};

        float contactX = sim.world().x(contact);
        float contactY = sim.world().y(contact);
        // Perpendicular to the fixing squad's line of sight, on whichever side
        // this squad already stands — crossing the fixing squad's line to reach
        // the far side would walk through its fire.
        float perpX = -assault.axisY();
        float perpY = assault.axisX();
        if ((squad.centroidX - contactX) * perpX
                + (squad.centroidY - contactY) * perpY < 0f) {
            perpX = -perpX;
            perpY = -perpY;
        }
        int rawX = (int) Math.floor(contactX + perpX * SQUAD_FLANK_RADIUS);
        int rawY = (int) Math.floor(contactY + perpY * SQUAD_FLANK_RADIUS);
        int[] flank = ReinforceContact.snapToReachable(rawX, rawY, squad, sim);

        // snapToReachable answers with the squad's own ground when the building
        // will not support a flank. That is a refusal, not a destination: fall
        // back to the objective rather than ordering the squad to stand still.
        if (TacticalScoring.cellDistance(flank[0] + 0.5f, flank[1] + 0.5f,
                squad.centroidX, squad.centroidY) <= 1f) {
            return new int[]{destX, destY};
        }
        return flank;
    }

    /**
     * Whether the squad, not just {@code member}, has closed on the
     * destination. Read off the plan's current step so an arriving member
     * only waits on the squadmates actually assigned this step — a member
     * that has fallen out of the step (dropped a fire team, reassigned) never
     * pins one that is still in it. Falls back to the squad's whole live
     * roster when the plan or its current step is unavailable, since a
     * member executing this action always belongs to some squad whether or
     * not a plan currently names it.
     */
    private boolean squadHasArrived(Squad squad, BattleControl sim) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        if (step != null) {
            for (long assigned : step.allAssignedMembers()) {
                long resolved = sim.resolveUnit(assigned);
                if (resolved == 0L) continue; // no longer alive; not a straggler
                if (!withinSquadArrival(resolved, sim)) return false;
            }
            return true;
        }
        int count = sim.squadMemberCount(squad.id);
        for (int i = 0; i < count; i++) {
            if (!withinSquadArrival(sim.squadMemberAt(squad.id, i), sim)) return false;
        }
        return true;
    }

    private boolean withinSquadArrival(long unit, BattleControl sim) {
        return withinSquadArrival(unit, destX, destY, sim);
    }

    private static boolean withinSquadArrival(long unit, int destX, int destY,
                                              BattleView sim) {
        return TacticalScoring.cellDistance(sim.world().x(unit), sim.world().y(unit),
                destX + 0.5f, destY + 0.5f) <= SQUAD_ARRIVAL_RADIUS;
    }

    /**
     * Whether {@code squad} has arrived at {@code (destX, destY)} by the rule
     * {@link #execute} completes on: somebody is on the objective, inside
     * {@link #ARRIVAL_RADIUS}, and everybody still alive is inside the
     * {@link #SQUAD_ARRIVAL_RADIUS} footprint. Offered so whoever decides an
     * attack move is <em>over</em> agrees with the action about what arriving
     * is.
     *
     * <p>The player order release used to test the squad centroid against
     * {@link #ARRIVAL_RADIUS} instead. Six people who have each stopped inside
     * the footprint settle with a centroid a little under three cells out, so
     * the action was satisfied and the release never was: the squad parked on
     * the ground it was pointed at with the player's order still standing over
     * its mission for the rest of the battle. Two rules for one arrival is one
     * too many.
     */
    public static boolean squadHasArrived(Squad squad, int destX, int destY,
                                          BattleView sim) {
        int count = sim.squadMemberCount(squad.id);
        boolean onObjective = false;
        boolean any = false;
        for (int i = 0; i < count; i++) {
            long member = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
            if (member == 0L) continue;
            any = true;
            float distance = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    destX + 0.5f, destY + 0.5f);
            if (distance > SQUAD_ARRIVAL_RADIUS) return false;
            if (distance <= ARRIVAL_RADIUS) onObjective = true;
        }
        return any && onObjective;
    }

    /**
     * Plant here once arrived rather than idle while the rest of the squad
     * closes. Mirrors the target-then-opportunistic-fire idiom
     * {@link AbstractZoneAction#advanceIntoZone} uses for a moving member:
     * keep pursuing a live worthwhile target, fall back to
     * {@link TacticalScoring#findBestTarget} when it goes stale, and fire
     * {@code STANCED} — planted, not moving — either on that target in range
     * with a clear shot or, failing that, on whatever enemy is closest and
     * actually shootable from here.
     *
     * <p>No post-fire cover reposition, unlike the committed firing line.
     * That hook authors a short path which the next tick's {@code clearPath}
     * here would simply throw away — {@code advanceIntoZone} pays for an
     * {@code activeCoverReposition} check to avoid exactly that — and the
     * move it authors can carry this member back outside
     * {@link #ARRIVAL_RADIUS}, un-arriving the marine whose arrival is the
     * only reason this branch is running. Holding the objective cell is the
     * point; drifting off it for better cover is a different order.
     */
    private void holdArrival(long member, BattleControl sim) {
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        long target = sim.targetOf(member);
        if (target == 0L || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member, target);
        }
        if (target != 0L) {
            float distance = TacticalScoring.cellDistance(
                    sim.world().x(member), sim.world().y(member),
                    sim.world().x(target), sim.world().y(target));
            if (distance <= sim.world().attackRange(member)
                    && sim.getTacticalScoring().hasClearShot(member, target)) {
                sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
                return;
            }
        }
        long opportune = sim.getTacticalScoring().closestEnemyInAttackRange(
                member, sim.combat().reflexTargetId(member),
                TacticalScoring.OPPORTUNITY_RETARGET_DISTANCE_MARGIN);
        if (opportune != 0L) {
            sim.combat().setFireIntent(member, opportune, FireStance.STANCED, false);
        }
    }

    /**
     * Hold the shared contact's attention from here. The squad is buying a
     * sibling's movement, so it plants rather than closing; an out-of-range
     * member improves its position inside a short leash instead of joining the
     * assault it is supposed to be covering.
     */
    private void holdBaseOfFire(long member, long contact, BattleControl sim) {
        sim.world().setTargetId(member, contact);
        float distance = TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                sim.world().x(contact), sim.world().y(contact));
        boolean clearShot = sim.getTacticalScoring().hasClearShot(member, contact);
        if (distance <= sim.world().attackRange(member) && clearShot) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            sim.combat().setFireIntent(member, contact, FireStance.STANCED, true);
            return;
        }
        int[] firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                member, contact, sim.world().cellX(member), sim.world().cellY(member),
                BASE_OF_FIRE_LEASH);
        if (firingPos == null) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return;
        }
        // A fixing squad that cannot walk to the better position plants where
        // it stands rather than freezing against it: holding the contact's
        // attention is the job, and it is done from here or not at all.
        if (advanceToReachableFiringPosition(member, sim, firingPos)
                != FiringApproach.MOVED) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        }
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        // While bounding, the useful picture is where the moving team is
        // headed rather than the far objective.
        if (squad.boundingActive) {
            int[] xs = squad.boundingTargetXs;
            int[] ys = squad.boundingTargetYs;
            int count = Math.min(xs.length, ys.length);
            List<int[]> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) cells.add(new int[]{xs[i], ys[i]});
            return cells;
        }
        return List.of(new int[]{destX, destY});
    }
}
