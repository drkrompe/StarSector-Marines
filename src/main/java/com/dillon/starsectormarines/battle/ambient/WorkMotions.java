package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.appearance.WorkMotion;
import com.dillon.starsectormarines.battle.world.gen.Affordance;

/**
 * Which {@link WorkMotion} an ambient actor shows, chosen from what the ambient
 * model already knows about the work: the job's {@link Affordance}, the fixture
 * it is done at, and the stop's {@link AmbientActivity}.
 *
 * <p>Keyed on those and on nothing narrower. Not on a role, because the same
 * motion belongs to whoever does the job — a colonist at a workbench and a ship's
 * machinist at a lathe; not on a ship's rooms, because a job site is a ship's
 * compartment, a building interior or a walled yard alike. Any actor the ambient
 * service drives, on any map, in a mission or aboard, gets the same motion for
 * the same work, and any layered family with station arms can show it.
 */
public final class WorkMotions {

    private WorkMotions() {}

    /**
     * The motion for {@code job} done at the fixture defined as {@code fixtureIds},
     * which may be null where nothing is known about the fixture. A cell can hold
     * an assembly's several layers (a bed and its linen), so {@code fixtureIds}
     * may be every definition id on the cell, space-separated.
     */
    public static WorkMotion forJob(Affordance job, String fixtureIds) {
        if (job == null) return WorkMotion.NONE;
        String fixture = fixtureIds != null ? fixtureIds : "";
        return switch (job) {
            case SERVICE -> WorkMotion.WELD;
            case REPAIR -> WorkMotion.WRENCH;
            case TEND -> fixture.contains("control") || fixture.contains("switchgear")
                    ? WorkMotion.CONSOLE : WorkMotion.WRENCH;
            case TREAT -> fixture.contains("bed") || fixture.contains("couch")
                    || fixture.contains("exam") || fixture.contains("treatment-table")
                    ? WorkMotion.TREAT : WorkMotion.CONSOLE;
            case COOK -> fixture.contains("range") || fixture.contains("stove") ? WorkMotion.STIR
                    : fixture.contains("prep") ? WorkMotion.CHOP : WorkMotion.CONSOLE;
            case EXERCISE -> fixture.contains("treadmill") ? WorkMotion.RUN
                    : fixture.contains("rower") || fixture.contains("ergometer") ? WorkMotion.ROW
                    : fixture.contains("bench-press") ? WorkMotion.PRESS : WorkMotion.LIFT;
            case LAP -> WorkMotion.JOG;
            // Only a bed to lie in, worked from its foot: the ship's two-cell bunk
            // and the ground's three-cell residential single.
            case REST -> fixture.contains("barracks-bunks.bed") ? WorkMotion.SLEEP_BUNK
                    : fixture.contains("residential-bed-") ? WorkMotion.SLEEP : WorkMotion.NONE;
            case FABRICATE, STOW, READOUT, WATCH, ISSUE, MESS -> WorkMotion.CONSOLE;
            case WASH, PRACTICE, UNWIND, ROUNDS, GUARD -> WorkMotion.NONE;
        };
    }

    /**
     * The motion for a stop known only by its activity: an authored route whose
     * stop names no job on the board, such as a mission's scripted townsfolk.
     * Work there is done with the hands the ordinary way; anything else shows
     * no motion of its own.
     */
    public static WorkMotion forActivity(AmbientActivity activity) {
        if (activity == null) return WorkMotion.NONE;
        return switch (activity) {
            case WORKING -> WorkMotion.CONSOLE;
            case EXERCISING -> WorkMotion.LIFT;
            case IDLE, RESTING, SOCIALIZING, INSPECTING, FIRING_PRIMARY,
                 PRACTICING_EQUIPMENT, WALKING -> WorkMotion.NONE;
        };
    }

    /**
     * What is in the arms of somebody walking from {@code from} to the next stop.
     * Two jobs are done between points rather than at one: stores handling,
     * whose walk between two stowage stops carries a crate, and a lap, whose
     * walk between two markers is the run itself. Onto the track from anywhere
     * else is an ordinary walk.
     */
    public static WorkMotion between(Affordance from, Affordance to) {
        if (from == Affordance.STOW && to == Affordance.STOW) return WorkMotion.CARRY;
        if (from == Affordance.LAP && to == Affordance.LAP) return WorkMotion.JOG;
        return WorkMotion.NONE;
    }
}
