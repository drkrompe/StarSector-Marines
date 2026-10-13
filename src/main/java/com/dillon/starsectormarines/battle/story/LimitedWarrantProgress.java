package com.dillon.starsectormarines.battle.story;

/** Protected authentication, physical calibration recovery, and company-covered return. */
public final class LimitedWarrantProgress {
    public enum Phase { RECORDS, CALIBRATION, EGRESS, COMPLETE, FAILED }
    public static final float WORK_SECONDS = 8;
    public record Report(boolean recordsRecovered, boolean kitRecovered, boolean kitReturned,
                         boolean companyReturned, boolean cargoLost, int techniciansLost, int constablesLost) {
        public boolean complete() { return recordsRecovered && kitRecovered && kitReturned && companyReturned && !cargoLost; }
    }
    private Phase phase;
    private float work;
    private boolean records, kit, returned, companyReturned, cargoLost, safeWork;
    public LimitedWarrantProgress(boolean records) { this.records = records; phase = records ? Phase.CALIBRATION : Phase.RECORDS; }
    public Phase phase() { return phase; }
    public float work() { return work; }
    public boolean safeWork() { return safeWork; }
    public boolean recordsRecovered() { return records; }
    public boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }
    public Report report(int techniciansLost, int constablesLost) {
        return new Report(records, kit, returned, companyReturned, cargoLost, techniciansLost, constablesLost);
    }
    public void advance(float dt, boolean companyInPlay, boolean technicianAlive, boolean cargoAlive,
                        boolean protectedWork, boolean cargoAtEgress, boolean companyAtEgress) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        safeWork = protectedWork;
        cargoLost = !cargoAlive;
        if (!companyInPlay || cargoLost || !kit && !technicianAlive) { phase = Phase.FAILED; return; }
        if (phase == Phase.EGRESS) {
            if (cargoAtEgress && companyAtEgress && protectedWork) {
                returned = true; companyReturned = true; phase = Phase.COMPLETE;
            }
        } else {
            work = protectedWork ? work + dt : 0;
            if (work >= WORK_SECONDS) {
                if (phase == Phase.RECORDS) { records = true; phase = Phase.CALIBRATION; }
                else { kit = true; phase = Phase.EGRESS; }
                work = 0;
            }
        }
    }
}
