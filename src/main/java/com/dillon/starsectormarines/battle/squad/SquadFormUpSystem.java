package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Holds a landing campaign squad at its LZ until it has assembled.
 *
 * <p>Lift capacity is denominated in four-marine fire teams, so a twelve-marine
 * squad normally arrives across two or three passes. Pushing each team forward
 * as it lands is defeat in detail, and under the shipped 9x lethality scale
 * that is a wipe rather than a setback — the first team is in contact alone for
 * however long the shuttle takes to fly back.
 *
 * <p>The gate is deliberately one line of leverage rather than a new behaviour:
 * while a squad is still assembling {@link Squad#assignmentForExecution()}
 * masks its authoritative {@link Squad#assignedObjective} without deleting or
 * rewriting it. The commander can therefore update intent while the squad
 * falls through to its ambient goals, and the newest directive becomes
 * executable when assembly ends.
 * Ambient means it
 * still defends itself — {@code EliminateEnemiesGoal} needs enemies it can
 * actually see — and marines never patrol ({@code RoutinePatrol} is
 * DEFENDER-only), so an unassigned marine squad with nothing in sight simply
 * stays where it landed. That is exactly the behaviour wanted, and it costs no
 * new posture.
 *
 * <p>Running after the commander pass rather than inside each commander is what
 * keeps this to one place: all six {@code MissionCommand} implementations are
 * covered, and a new one is covered the day it is written.
 *
 * <p>The timeout is the deadlock guard. A shuttle shot down on its second run,
 * or a squad split across two landing zones (where the per-zone half can never
 * reach the whole squad's expected strength), must not leave marines standing
 * at an LZ for the rest of the mission.
 */
public final class SquadFormUpSystem {

    /**
     * Sim-seconds a squad will wait at its LZ for the rest of itself before
     * stepping off short-handed. Long enough to cover a shuttle's return leg
     * plus its re-arm, short enough that a lost lift costs one delay rather
     * than the mission. Wants a play pass; see the story's open question.
     */
    public static final float FORM_UP_TIMEOUT = 60f;

    private final UnitRosterService roster;

    public SquadFormUpSystem(UnitRosterService roster) {
        this.roster = roster;
    }

    /** True while this squad is still expecting marines and still willing to wait. */
    public static boolean formingUp(Squad squad) {
        return squad != null
                && (squad.campaignSquadId != null || squad.arrivalAssembly)
                && squad.expectedSize > 0
                && squad.originalSize < squad.expectedSize
                && squad.formUpElapsed < FORM_UP_TIMEOUT;
    }

    public void tick(float dt) {
        for (Squad squad : roster.getSquads()) {
            if (formingUp(squad)) {
                squad.formUpElapsed += dt;
            }
        }
    }
}
