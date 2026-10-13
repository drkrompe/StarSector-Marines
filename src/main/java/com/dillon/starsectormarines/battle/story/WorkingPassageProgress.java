package com.dillon.starsectormarines.battle.story;

/** Departure referee: physical position and surviving field construction gate every leg. */
public final class WorkingPassageProgress {
    public enum Phase { BEND, BEND_BUILD, NARROWS, NARROWS_BUILD, CLIMB_OUT, SPOOL, DEPARTURE, COMPLETE, FAILED }
    public record Report(boolean departed, boolean disabled, boolean aborted, int militiaLost) { }
    public static final float CLEAR_SECONDS = 5f, SPOOL_SECONDS = 55f;
    private Phase phase = Phase.BEND;
    private float work;
    private boolean disabled, aborted, companyPresent, contested, tenderArrived, constructionReady, responseReached;
    public Phase phase() { return phase; }
    public float work() { return work; }
    public boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }
    public boolean companyPresent() { return companyPresent; }
    public boolean contested() { return contested; }
    public boolean tenderArrived() { return tenderArrived; }
    public boolean constructionReady() { return constructionReady; }
    public boolean responseReached() { return responseReached; }
    public void fail(boolean criticalDisable) {
        if (terminal()) return;
        disabled = criticalDisable;
        aborted = true;
        phase = Phase.FAILED;
    }
    public Report report(int militiaLost) {
        return new Report(phase == Phase.COMPLETE, disabled, aborted || phase != Phase.COMPLETE,
                Math.max(0, Math.min(8, militiaLost)));
    }
    public void advance(float dt, boolean alive, boolean criticalDisable, boolean company,
                        boolean hostile, boolean arrived, boolean built, boolean response, boolean departed) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        companyPresent = company; contested = hostile; tenderArrived = arrived;
        constructionReady = built; responseReached = response;
        // A hull physically beyond the map is no longer an active drone anchor.
        if (phase == Phase.DEPARTURE && departed && !criticalDisable) { next(Phase.COMPLETE); return; }
        if (!alive || criticalDisable) { fail(true); return; }
        boolean safe = company && !hostile;
        switch (phase) {
            case BEND, NARROWS, CLIMB_OUT -> {
                work = safe ? Math.min(CLEAR_SECONDS, work + dt) : 0;
                if (work >= CLEAR_SECONDS && arrived) next(phase == Phase.BEND ? Phase.BEND_BUILD
                        : phase == Phase.NARROWS ? Phase.NARROWS_BUILD : Phase.SPOOL);
            }
            case BEND_BUILD -> { if (safe && arrived && built && response) next(Phase.NARROWS); }
            case NARROWS_BUILD -> { if (safe && arrived && built && response) next(Phase.CLIMB_OUT); }
            case SPOOL -> {
                if (safe && arrived) work = Math.min(SPOOL_SECONDS, work + dt);
                if (safe && arrived && response && work >= SPOOL_SECONDS) next(Phase.DEPARTURE);
            }
            case DEPARTURE -> { if (departed) next(Phase.COMPLETE); }
            default -> { }
        }
    }
    private void next(Phase next) { phase = next; work = 0; }
}
