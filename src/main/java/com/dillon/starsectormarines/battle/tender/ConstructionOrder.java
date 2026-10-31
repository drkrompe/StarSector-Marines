package com.dillon.starsectormarines.battle.tender;

/**
 * One site a tender has been told to build: where, what, and how far along.
 *
 * <p>Pure bookkeeping. The order knows nothing of the simulation; the system
 * tells it how many builders are at the site and whether the ground is contested,
 * and reads back whether it is finished. That keeps the arithmetic that decides
 * how long a build takes testable without standing a battle up.
 *
 * <p>The phases run {@code QUEUED -> TRANSIT -> WORKING -> COMPLETE}. An order
 * can leave the line at any point as {@code CANCELLED} (somebody withdrew it) or
 * {@code FAILED} (the world stopped it), and both give the tender's charge back.
 *
 * <p>A staged build ({@link BuildOption#plot}) completes its plot's stages as the progress crosses each one's share;
 * the system lays each on the ground as it falls {@linkplain #stageDue due}. A stage once laid stays laid whatever
 * happens to the order after: only the gun waits for completion, and it also waits, at full progress, while
 * something stands on its cell ({@link #gunHeld}).
 */
public final class ConstructionOrder {

    /** Where the order stands. {@code COMPLETE}, {@code CANCELLED} and {@code FAILED} are terminal. */
    public enum Phase {
        /** Laid, waiting its turn behind the order the tender is on. */
        QUEUED,
        /** The tender is flying to its standoff point; no builder is out. */
        TRANSIT,
        /** The tender is on station and its builders are working the site. */
        WORKING,
        COMPLETE,
        CANCELLED,
        FAILED;

        public boolean terminal() {
            return this == COMPLETE || this == CANCELLED || this == FAILED;
        }
    }

    private final BuildOption option;
    private final float siteX;
    private final float siteY;
    private Phase phase = Phase.QUEUED;
    private float progressSeconds;
    private float standoffX;
    private float standoffY;
    private boolean contested;
    private boolean underFire;
    private String failure = "";
    private int stagesLaid;
    private int facingX;
    private int facingY = 1;
    private boolean faced;
    private boolean gunHeld;

    public ConstructionOrder(BuildOption option, float siteX, float siteY) {
        this.option = option;
        this.siteX = siteX;
        this.siteY = siteY;
    }

    /**
     * The share of full speed {@code onSite} builders work at, out of a crew of
     * {@code fullCrew}: linear, capped at one. More drones than the job has room
     * for do not make it faster, and none stop it.
     */
    public static float rate(int onSite, int fullCrew) {
        if (onSite <= 0 || fullCrew <= 0) return 0f;
        return Math.min(1f, (float) onSite / fullCrew);
    }

    public BuildOption option() {
        return option;
    }

    /** Site centre, in cells. */
    public float siteX() {
        return siteX;
    }

    public float siteY() {
        return siteY;
    }

    public Phase phase() {
        return phase;
    }

    /** Share of the work done, 0 to 1. */
    public float fraction() {
        return Math.min(1f, progressSeconds / option.buildSeconds());
    }

    /** Whether the work was stopped on the last tick (a known hostile near, a builder hit, or the pilot out of reach). */
    public boolean contested() {
        return contested;
    }

    /** Whether the work is stopped because a builder was hit: the site is blocked, not merely watched. */
    public boolean underFire() {
        return underFire;
    }

    void underFire(boolean underFire) {
        this.underFire = underFire;
    }

    /** The cardinal direction the plot's forward side faces, as {@code {x, y}}; north until the order sets out. */
    public int[] facing() {
        return new int[]{facingX, facingY};
    }

    void face(int[] facing) {
        this.facingX = facing[0];
        this.facingY = facing[1];
        this.faced = true;
    }

    /** Whether the plot's facing has been chosen; it is chosen once, on the first tick after the lay. */
    public boolean faced() {
        return faced;
    }

    /** Whether the finished work is waiting for its gun's cell to clear: the site is done but the gun not yet up. */
    public boolean gunHeld() {
        return gunHeld && progressSeconds >= option.buildSeconds();
    }

    /** Tells the order whether its gun's cell is taken; while it is, the work stops at full progress short of done. */
    void holdGun(boolean held) {
        this.gunHeld = held;
    }

    /** How many of the plot's stages are on the ground. */
    public int stagesLaid() {
        return stagesLaid;
    }

    /**
     * The next plot stage whose share the work has passed and which is not yet laid, or {@code null}. The caller lays
     * it and calls {@link #laid()}; asking again gives the one after, so a long tick that crosses two lays both.
     */
    public BuildPlot.Stage stageDue() {
        BuildPlot plot = option.plot();
        if (plot == null || stagesLaid >= plot.stages().size()) return null;
        BuildPlot.Stage next = plot.stages().get(stagesLaid);
        return fraction() >= plot.end(next) ? next : null;
    }

    void laid() {
        stagesLaid++;
    }

    /** Why the order failed, or the empty string. */
    public String failure() {
        return failure;
    }

    /** Where the tender holds station while it works, set when it sets out. */
    void standoff(float x, float y) {
        this.standoffX = x;
        this.standoffY = y;
    }

    float standoffX() {
        return standoffX;
    }

    float standoffY() {
        return standoffY;
    }

    void begin() {
        if (phase == Phase.QUEUED) phase = Phase.TRANSIT;
    }

    void startWork() {
        if (phase == Phase.TRANSIT) phase = Phase.WORKING;
    }

    /**
     * Does {@code dt} seconds of work with {@code onSite} of {@code fullCrew} builders present.
     * A contested site is not worked at all, and a {@linkplain #gunHeld held} one stops at full progress. Returns
     * whether the site is now finished.
     */
    boolean work(float dt, int onSite, int fullCrew, boolean contested) {
        if (phase != Phase.WORKING) return false;
        this.contested = contested;
        if (!contested) progressSeconds += dt * rate(onSite, fullCrew);
        if (progressSeconds < option.buildSeconds()) return false;
        if (gunHeld) {
            progressSeconds = option.buildSeconds();
            return false;
        }
        phase = Phase.COMPLETE;
        return true;
    }

    void cancel() {
        if (!phase.terminal()) phase = Phase.CANCELLED;
    }

    void fail(String reason) {
        if (phase.terminal()) return;
        phase = Phase.FAILED;
        failure = reason;
    }
}
