package com.dillon.starsectormarines.battle.story;

/**
 * Terms of Protection as separate physical facts: the party delivered to the dispatch handover
 * bay, the handover signed there by a living representative, the captured rig tooling commissioned
 * by a living technician, every committed Greyhook assault resolved, and ratification by a
 * representative still alive at dispatch. Proximity alone signs nothing, and the end of an attack
 * ratifies nothing.
 */
public final class TermsOfProtectionProgress {
    /** Which signed agreement the mobilization carries. The battle reads it; it never chooses it. */
    public enum Route { HOUSE_CUSTODY, YARD_COMPACT }
    public enum Phase { ESCORT, SIGNING, COMMISSIONING, DEFENCE, COMPLETE, FAILED }
    /** Why an attempt failed, in the order the story lists its failure conditions. */
    public enum Failure { NONE, COMPANY_LOST, REPRESENTATIVES_LOST, CARRIER_LOST, TECHNICIANS_LOST }
    public static final float SIGNING_SECONDS = 8, WORK_SECONDS = 75;
    public static final int REPRESENTATIVES = 2, TECHNICIANS = 2;

    /** The frozen battle receipt. Losses are confirmed counts of the authored employer parties. */
    public record Report(Route route, boolean delivered, boolean signed, boolean dispatchSecured, boolean commissioned,
                         boolean assaultsResolved, boolean ratified, boolean companyPresent, boolean carrierLost,
                         int representativesLost, int techniciansLost, int fittersLost, int guardsLost, int constablesLost) {
        public boolean complete() {
            return delivered && signed && commissioned && assaultsResolved && ratified && companyPresent && !carrierLost
                    && representativesLost < REPRESENTATIVES;
        }
    }

    /** One tick of observed facts. */
    public record Observation(boolean companyInPlay, boolean carrierAlive, boolean carrierAtHandover,
                              int representativesAlive, boolean representativeAtHandover, int techniciansAlive,
                              boolean technicianAtWork, boolean companyAtDispatch, boolean dispatchContested,
                              boolean companyInHall, boolean hallContested, boolean assaultsResolved) { }

    private final Route route;
    private boolean delivered, signed, commissioned, ratified, failed, dispatchSecured, companyPresent;
    private float signing, work;
    private Failure failure = Failure.NONE;

    public TermsOfProtectionProgress(Route route) {
        if (route == null) throw new IllegalArgumentException("Terms of Protection launches under a signed route");
        this.route = route;
    }

    public Route route() { return route; }
    public boolean delivered() { return delivered; }
    public boolean signed() { return signed; }
    public boolean commissioned() { return commissioned; }
    public boolean ratified() { return ratified; }
    public float signing() { return signing; }
    public float work() { return work; }
    public Failure failure() { return failure; }
    public boolean terminal() { return ratified || failed; }

    public Phase phase() {
        if (ratified) return Phase.COMPLETE;
        if (failed) return Phase.FAILED;
        if (!delivered) return Phase.ESCORT;
        if (!signed) return Phase.SIGNING;
        if (!commissioned) return Phase.COMMISSIONING;
        return Phase.DEFENCE;
    }

    /** An outright win, every objective met and nobody lost: what the Debug key's Win reports. */
    public Report wonReport() {
        return new Report(route, true, true, true, true, true, true, true, false, 0, 0, 0, 0, 0);
    }
    public Report report(boolean carrierLost, int representativesLost, int techniciansLost, int fittersLost,
                         int guardsLost, int constablesLost, boolean assaultsResolved) {
        return new Report(route, delivered, signed, dispatchSecured, commissioned, assaultsResolved, ratified, companyPresent,
                carrierLost, representativesLost, techniciansLost, fittersLost, guardsLost, constablesLost);
    }

    public void advance(float dt, Observation o) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        companyPresent = o.companyInPlay();
        dispatchSecured = o.companyAtDispatch() && !o.dispatchContested();
        if (!o.companyInPlay()) { fail(Failure.COMPANY_LOST); return; }
        if (o.representativesAlive() <= 0) { fail(Failure.REPRESENTATIVES_LOST); return; }
        if (!o.carrierAlive()) { fail(Failure.CARRIER_LOST); return; }
        if (o.techniciansAlive() <= 0 && !commissioned) { fail(Failure.TECHNICIANS_LOST); return; }
        if (!delivered) {
            delivered = o.carrierAtHandover() && o.representativeAtHandover();
            return;
        }
        if (!signed) {
            // A signature is a representative at the bay under a secured dispatch, not company proximity.
            signing = o.representativeAtHandover() && dispatchSecured ? signing + dt : 0;
            if (signing >= SIGNING_SECONDS) signed = true;
            return;
        }
        if (!commissioned) {
            // Interrupted work pauses; it does not start over.
            if (o.technicianAtWork() && o.companyInHall() && !o.hallContested()) work += dt;
            if (work >= WORK_SECONDS) commissioned = true;
            return;
        }
        if (o.assaultsResolved() && o.representativeAtHandover() && dispatchSecured) ratified = true;
    }

    /** A withdrawal or exhausted means ends the attempt without completion. */
    public void fail(Failure why) {
        if (terminal()) return;
        failed = true;
        failure = why == null ? Failure.NONE : why;
    }
}
