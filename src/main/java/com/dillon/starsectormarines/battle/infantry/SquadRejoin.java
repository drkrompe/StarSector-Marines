package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * A campaign marine who lands after their squad has stopped waiting for them
 * <b>closes on the squad before it does anything else</b>.
 *
 * <p>A squad assembles across lifts, and {@link SquadFormUpSystem} holds it at
 * its landing zone while it does. That gate has a timeout, so a shuttle shot
 * down on its second run, a squad split across two zones, or a replacement wave
 * all produce the same thing: a marine joining a squad that is already forty
 * cells away and fighting. Without a state of its own that marine became an
 * ordinary member on the tick it landed — handed a slot in a plan being
 * executed somewhere else, its cohesion pull only one of several movement
 * inputs, and its opportunity-fire reflexes perfectly willing to have it start
 * a fight of its own on the way across.
 *
 * <p><b>Rejoining is what it does instead.</b> It walks the cohesion pull, it
 * returns fire at what is already in range with a clear shot, it initiates
 * nothing, and it stops being rejoining the moment it is back inside
 * {@link InfantryCohesion#COHESION_RADIUS}. The squad plans around it in the
 * meantime — it is left out of role assignment and out of the arrival rules the
 * same way a broken fire team is, so a squad does not stall on a marine still
 * thirty cells back.
 *
 * <p><b>It is built on the cohesion seam rather than beside it.</b> The pull is
 * {@link RegroupPosture}, the distance is
 * {@link InfantryCohesion#COHESION_RADIUS}, and the return fire is
 * {@link InfantryUnitPrep#tryOpportunityPrimary} — the same helper the
 * dispatcher's own uncovered branches use, which authors a shot at whatever is
 * already shootable and deliberately does not touch the pursuit target. A
 * second cohesion mechanism, or a fresh target acquisition here, would be the
 * mission-inventing a reflex is not allowed to do.
 *
 * @see InfantryReflexes#REJOIN where it sits in the marine's reflex order
 */
public final class SquadRejoin {

    /**
     * Turns the rejoin state off for a control run:
     * {@code -Dbattle.infantry.rejoin=false}. On by default. With it off
     * nothing is ever marked, so the slot-assignment and arrival exclusions
     * cannot fire either and a control run is the tree without this behaviour.
     */
    public static final String PROPERTY = "battle.infantry.rejoin";

    /**
     * Read once at class load and settable for evidence. Volatile because a
     * scene's control loop flips it between loops on one thread while the unit
     * dispatch reads it from several.
     */
    private static volatile boolean enabled =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    private SquadRejoin() {}

    /** Whether the rejoin state is in effect. */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Evidence seam: turns the rejoin state on or off for the rest of this JVM.
     *
     * <p>Exists so one scene can play its subject and its control in a single
     * run — loops play sequentially on one thread, and a control that needed a
     * second JVM would be a second command nobody runs. Restore the previous
     * value in a {@code finally}; production never calls this, and the property
     * is what a control run switches.
     */
    public static void setEnabledForEvidence(boolean value) {
        enabled = value;
    }

    /**
     * Decides, at the moment a tagged campaign marine is set down, whether it
     * has arrived late — and marks it if so.
     *
     * <p>Three conditions, all of them necessary. The squad must have stopped
     * forming up, or this is an ordinary lift landing into a squad still
     * waiting at its own zone. Somebody else must be standing, since the first
     * marine off the first shuttle has no squad to close on. And the marine
     * must have landed outside {@link InfantryCohesion#COHESION_RADIUS} of
     * where the squad actually is: the last marine of a normal assembly lands
     * beside squadmates who have not moved, which is an arrival rather than a
     * late one.
     *
     * <p>Only the campaign-tagged join path calls this. Untagged generated
     * personnel — defenders, militia, debug fixtures — never carry the state.
     *
     * @param landedX the deboard cell's x, which is where the marine is
     *                standing; read from the cell rather than from the unit so
     *                the rule does not depend on the spawn having been adopted
     * @return whether the marine was marked
     */
    public static boolean markIfLateArrival(Squad squad, long unit,
                                            int landedX, int landedY,
                                            UnitRosterService roster) {
        if (!enabled || squad == null || roster == null) return false;
        // Only a squad that assembles across lifts can have a late arrival at
        // all. The tagged join path is the one caller, and this says so a
        // second time rather than trusting it: a generated defender squad has
        // no form-up gate, so it would read as "past form-up" on every spawn.
        if (squad.campaignSquadId == null && !squad.arrivalAssembly) return false;
        if (SquadFormUpSystem.formingUp(squad)) return false;
        float[] anchor = landingAnchor(squad, unit, roster);
        if (anchor == null) return false;
        if (InfantryCohesion.withinCohesion(landedX + 0.5f, landedY + 0.5f,
                anchor[0], anchor[1])) {
            return false;
        }
        squad.markRejoining(unit);
        return true;
    }

    /**
     * Where the squad already is, read off the ground rather than off the alert
     * pass's cached aggregate: its live leader when it has one that is not
     * {@code joining}, else the mean position of every other member standing.
     * Null when nobody else is standing.
     *
     * <p>The same anchor {@link InfantryCohesion} pulls a drifting member
     * toward, derived live because the cached centroid is refreshed once a tick
     * and a marine set down after that pass is not in it yet.
     */
    private static float[] landingAnchor(Squad squad, long joining,
                                         UnitRosterService roster) {
        World world = roster.world();
        long leader = squad.leaderId;
        if (leader != 0L && leader != joining && roster.isAliveById(leader)) {
            return new float[]{world.x(leader), world.y(leader)};
        }
        long[] members = roster.squadMemberArray(squad.id);
        int count = Math.min(roster.squadMemberCount(squad.id), members.length);
        float sumX = 0f;
        float sumY = 0f;
        int others = 0;
        for (int i = 0; i < count; i++) {
            long member = members[i];
            if (member == joining) continue;
            sumX += world.x(member);
            sumY += world.y(member);
            others++;
        }
        if (others == 0) return null;
        return new float[]{sumX / others, sumY / others};
    }

    /**
     * The rejoin itself, for one marine on one tick.
     *
     * <p>Consumes the tick when it acts, unlike {@link LaneSidestep}: the whole
     * point is that the squad's step — and the opportunity-fire initiation
     * inside it — does not run for a marine who is not with the squad yet.
     *
     * @return true when this marine spent the tick rejoining, false when it is
     *         an ordinary member again and its step should run
     */
    public static boolean rejoin(long unit, Squad squad, BattleControl sim) {
        if (squad == null || !squad.isRejoining(unit)) return false;
        // Deliberately leaves the flag alone: a control run switches the
        // property before a battle starts, so nothing is ever marked, and a
        // mid-run flip should not silently rewrite squad state.
        if (!enabled) return false;
        if (InfantryCohesion.withinCohesion(unit, sim)) {
            squad.clearRejoining(unit);
            return false;
        }
        // RegroupPosture is the pull and its own halt: it stands still when
        // this marine has a target in range with a clear shot, and walks the
        // cohesion anchor otherwise.
        RegroupPosture.INSTANCE.execute(unit, squad, sim);
        InfantryUnitPrep.tryOpportunityPrimary(unit, sim);
        return true;
    }
}
