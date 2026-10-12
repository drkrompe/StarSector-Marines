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
    private String failure = "";

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

    /** Whether hostile people stood at the site on the last tick, so no work was done. */
    public boolean contested() {
        return contested;
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
     * A contested site is not worked at all. Returns whether the site is now finished.
     */
    boolean work(float dt, int onSite, int fullCrew, boolean contested) {
        if (phase != Phase.WORKING) return false;
        this.contested = contested;
        if (!contested) progressSeconds += dt * rate(onSite, fullCrew);
        if (progressSeconds < option.buildSeconds()) return false;
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
