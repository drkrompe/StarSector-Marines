package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.world.gen.Affordance;

/**
 * What a body visibly does while at a job: the motion a renderer plays, as
 * opposed to the job the simulation assigned.
 *
 * <p>An {@link Affordance} names the work and a job site's fixture names the
 * thing it is done to; together they choose one of these. The same affordance
 * reads differently at different fixtures — a cook stirs at the range and chops
 * at the prep bench, a gym hand runs on the treadmill and rows at the rower — so
 * the fixture's definition id refines the choice, and an unknown fixture falls
 * back to the affordance's ordinary motion rather than to nothing.
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
    LIFT(1.8f, true);

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
     * machine it is using. A gym's exercise point is the free cell beside a
     * treadmill or a rower, because nobody can path into a cell the machine
     * blocks, but nobody runs beside a treadmill: the runner is drawn on its belt.
     * Presentation only; the actor's position and claim stay on the standing cell.
     */
    public float mountCells() {
        return this == RUN || this == ROW ? 0.8f : 0f;
    }

    /** Whether the family's station arms are drawn out on the work for this motion. */
    public boolean handsOn() {
        return handsOn;
    }

    /**
     * The motion for {@code job} done at the fixture defined as {@code fixtureId},
     * which may be null where nothing is known about the fixture. A cell can hold
     * an assembly's several layers (a bed and its linen), so {@code fixtureId} may
     * be every definition id on the cell, space-separated.
     */
    public static WorkMotion of(Affordance job, String fixtureId) {
        if (job == null) return NONE;
        String fixture = fixtureId != null ? fixtureId : "";
        return switch (job) {
            case SERVICE -> WELD;
            case REPAIR -> WRENCH;
            case TEND -> fixture.contains("control") || fixture.contains("switchgear")
                    ? CONSOLE : WRENCH;
            case TREAT -> fixture.contains("bed") || fixture.contains("couch")
                    || fixture.contains("exam") || fixture.contains("treatment-table")
                    ? TREAT : CONSOLE;
            case COOK -> fixture.contains("range") || fixture.contains("stove") ? STIR
                    : fixture.contains("prep") ? CHOP : CONSOLE;
            case EXERCISE -> fixture.contains("treadmill") ? RUN
                    : fixture.contains("rower") || fixture.contains("ergometer") ? ROW
                    : fixture.contains("bench-press") ? PRESS : LIFT;
            case FABRICATE, STOW, READOUT, WATCH, ISSUE, MESS -> CONSOLE;
            case REST, WASH, PRACTICE, UNWIND, ROUNDS, GUARD -> NONE;
        };
    }

    /**
     * What is in the arms of somebody walking from {@code from} to the next stop.
     * Stores handling is the one job done between points rather than at one, so
     * the walk between two stowage stops is part of the work and carries a crate.
     */
    public static WorkMotion between(Affordance from, Affordance to) {
        return from == Affordance.STOW && to == Affordance.STOW ? CARRY : NONE;
    }
}
