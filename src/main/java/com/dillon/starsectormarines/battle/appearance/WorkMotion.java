package com.dillon.starsectormarines.battle.appearance;

/**
 * What a body visibly does while at a job: the motion a renderer plays, as
 * opposed to the job the simulation assigned.
 *
 * <p>This is the drawn vocabulary only. Which motion a job shows is the ambient
 * model's decision ({@code battle.ambient.WorkMotions}), keyed on the job's
 * affordance, its fixture and the stop's activity, so it is the same for any
 * ambient actor on any map.
 *
 * <p>Presentation only. A motion never changes how long a job takes, where it
 * is done, or who may claim it; the ambient service writes it into the layered
 * flags ({@link LayeredAppearance#withWorkMotion}) beside the station flag and a
 * phase on the motion's own clock, and the compositor draws it.
 */
public enum WorkMotion {
    /** Nothing in the hands: standing by, sitting, sleeping, washing. */
    NONE(0f, false),
    /** Both hands sweeping across a console, bench or counter, half a cycle apart. */
    CONSOLE(1.7f, true),
    /** A torch in the right hand held on the work, sparks at its tip. */
    WELD(0.9f, true),
    /** Kneeling at a bed with the treatment kit open, as a squad medic does. */
    TREAT(0f, false),
    /** A crate held in front in both arms; played while walking between stores. */
    CARRY(0f, false),
    /** One hand circling over a pot, the other steadying it. */
    STIR(1.4f, true),
    /** One hand rising and falling on a board, the other holding the food. */
    CHOP(0.55f, true),
    /** Both hands on one wrench or valve wheel, heaved through an arc and reset. */
    WRENCH(1.6f, true),
    /** Running in place on a belt: feet and arms pumping. */
    RUN(0.62f, true),
    /** Seated and pulling a handle in to the chest, then reaching out again. */
    ROW(2.2f, true),
    /** Lying on a bench, arms pressing a bar up and lowering it. */
    PRESS(2.4f, true),
    /** Standing, curling a weight in each hand alternately. */
    LIFT(1.8f, true),
    /**
     * Running along the ground: arms pumping as {@link #RUN}'s do, but on the
     * deck rather than on a machine, so nothing draws the body ahead of where it
     * is. Played round a running track, while moving and at each marker.
     */
    JOG(0.5f, true),
    /**
     * Asleep in a bunk: the body lies along it, head on the pillow, drawn in the
     * bunk rather than on the passage cell the rest point stands on.
     */
    SLEEP(0f, false);

    private final float periodSeconds;
    private final boolean handsOn;

    WorkMotion(float periodSeconds, boolean handsOn) {
        this.periodSeconds = periodSeconds;
        this.handsOn = handsOn;
    }

    /** One full cycle of the motion, in seconds; zero for a held pose. */
    public float periodSeconds() {
        return periodSeconds;
    }

    /**
     * How far, in cells, the body is drawn ahead of where it stands, onto the
     * machine it is using. A gym's exercise point is the free cell at the foot
     * of a treadmill, rower or bench press, because nobody can path into a cell
     * the machine blocks, but nobody runs beside a treadmill: the user is drawn
     * on it. The machines are real-size and worked up their length from the
     * foot, so each reach lands the body where the machine holds it: the rear
     * of the belt, the middle of the seat's travel, the bench under the bar, and a
     * sleeper's head on the pillow at the far end of the bunk.
     * Presentation only; the actor's position and claim stay on the standing cell.
     */
    public float mountCells() {
        return switch (this) {
            case RUN -> 1.6f;
            case ROW -> 1.5f;
            case PRESS -> 1.4f;
            // A real-length bunk is three cells from the passage at its foot to
            // the pillow, and the sleeper's head is on the pillow.
            case SLEEP -> 2.55f;
            default -> 0f;
        };
    }

    /** Whether the family's station arms are drawn out on the work for this motion. */
    public boolean handsOn() {
        return handsOn;
    }
}
