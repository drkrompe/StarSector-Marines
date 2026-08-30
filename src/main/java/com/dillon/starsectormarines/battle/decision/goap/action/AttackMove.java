package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.infantry.ReinforceContact;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAssaultPicture;

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
    static final float ARRIVAL_RADIUS = 2f;
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
            return ActionStatus.SUCCESS;
        }

        int[] aim = maneuverAim(squad, assault, sim);

        // Deliberately no bound, though the shared machinery is right here and
        // this order is the obvious candidate for it. Measured over the
        // canonical matrix, bounding attack moves cost reinforced-south 7300
        // ticks and 46 defender kills and returned nothing on full-strength-west.
        // Gating it to the threat's beaten zone changed neither fixture by a
        // single tick, which says the price is the tactic itself and not
        // bounding at nothing: moving half a squad at a time up a long route
        // is simply slower than the ground is dangerous. A room crossing is
        // short enough to afford it; an attack move across a map is not.
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
        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    firingPos[0], firingPos[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
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
