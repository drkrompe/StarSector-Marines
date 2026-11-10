package com.dillon.starsectormarines.battle.derelict;

/**
 * What a destroyed walker or post of a zone leaves where it fell (`derelict-watch.md`, Repair). A repair drone with a
 * load works it until {@link #RAISE_COST} points are laid, and the unit is raised: it stands again, its zone's, at
 * {@link #RAISE_SHARE} of its plate and structure. A wreck is raised once; a raised unit destroyed again leaves scrap.
 *
 * <p>The company denies a wreck by standing infantry on it for {@link #SCRAP_SECONDS} with no hostile near, or by a
 * charge going off over it: a scrapped wreck is never raised.
 */
public final class Wreck {

    public enum Kind { WALKER, POST }

    public enum State { LYING, RAISED, SCRAPPED }

    /** Points a raise takes from the drone's load: one load. UNTUNED. */
    public static final float RAISE_COST = 150f;
    /** Points a second a drone lays on a wreck: a raise takes about 20 seconds of work. UNTUNED. */
    public static final float RAISE_PER_SECOND = 7.5f;
    /** The share of its plate and structure a raised unit stands with. UNTUNED. */
    public static final float RAISE_SHARE = 0.4f;
    /** Seconds infantry must stand on a wreck, uncontested, to scrap it. UNTUNED. */
    public static final float SCRAP_SECONDS = 5f;
    /** How near a wreck, in cells, infantry stands on it and a charge scraps it. */
    public static final float REACH_CELLS = 1.5f;

    public final long id;
    public final Kind kind;
    public final int zoneId;
    public final float x;
    public final float y;
    /** The walker's frame and its lance; null and -1 for a post. */
    public final DerelictWalkerFrame frame;
    public final int squadId;
    /** Whether the unit that fell here had already been raised once: its wreck is scrap. */
    public final boolean fromRaise;

    private State state;
    private float laid;
    private float scrapping;

    public Wreck(long id, Kind kind, int zoneId, float x, float y, DerelictWalkerFrame frame, int squadId,
                 boolean fromRaise) {
        this.id = id;
        this.kind = kind;
        this.zoneId = zoneId;
        this.x = x;
        this.y = y;
        this.frame = frame;
        this.squadId = squadId;
        this.fromRaise = fromRaise;
        this.state = fromRaise ? State.SCRAPPED : State.LYING;
    }

    public State state() {
        return state;
    }

    /** Whether a drone may still raise it. */
    public boolean raisable() {
        return state == State.LYING;
    }

    /** How far the raise is, 0 to 1. */
    public float progress() {
        return state == State.RAISED ? 1f : Math.min(1f, laid / RAISE_COST);
    }

    /** How far the company's scrapping is, 0 to 1. */
    public float scrapProgress() {
        return state == State.SCRAPPED ? 1f : Math.min(1f, scrapping / SCRAP_SECONDS);
    }

    /**
     * A drone lays {@code points} on the wreck.
     *
     * @return what it could not take (the wreck needed less, or is no longer raisable)
     */
    public float work(float points) {
        if (state != State.LYING) return points;
        float taken = Math.min(points, RAISE_COST - laid);
        laid += taken;
        if (laid >= RAISE_COST) state = State.RAISED;
        return points - taken;
    }

    /**
     * A step of the company's infantry on or off the wreck.
     *
     * @param companyOn   whether company infantry stands within {@link #REACH_CELLS}
     * @param hostileNear whether a unit of the watch contests it
     * @return whether this step scrapped it
     */
    public boolean scrap(float dt, boolean companyOn, boolean hostileNear) {
        if (state != State.LYING) return false;
        scrapping = companyOn && !hostileNear ? scrapping + dt : 0f;
        if (scrapping < SCRAP_SECONDS) return false;
        state = State.SCRAPPED;
        return true;
    }

    /** A charge goes off at a cell: within twice {@link #REACH_CELLS}, the wreck is scrap. Returns whether it scrapped it now. */
    public boolean blast(float bx, float by) {
        if (state != State.LYING) return false;
        float dx = bx - x;
        float dy = by - y;
        if (dx * dx + dy * dy > REACH_CELLS * REACH_CELLS * 4f) return false;
        state = State.SCRAPPED;
        return true;
    }
}
