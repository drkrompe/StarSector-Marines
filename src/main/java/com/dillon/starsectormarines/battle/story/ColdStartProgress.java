package com.dillon.starsectormarines.battle.story;

/** Mission referee. Completed deliveries survive a sortie; partial work never does. */
public final class ColdStartProgress {
    public enum Phase { ESCORT, POWER, CONSTRUCTION, REPAIR, COMPLETE, FAILED }
    public record Report(boolean cargoDelivered, boolean shorePower, boolean turretConstructed,
                         boolean repaired, int crewLost) { }
    public static final float UNLOAD_SECONDS = 6f, POWER_SECONDS = 35f, REPAIR_SECONDS = 80f;
    private Phase phase;
    private boolean delivered, powered, constructed, contested;
    private float work;
    private boolean companyPresent, crewPresent, carrierArrived, carrierAlive, crewAlive, turretComplete, responseReachedYard;

    public boolean companyPresent() { return companyPresent; }
    public boolean crewPresent() { return crewPresent; }
    public boolean carrierArrived() { return carrierArrived; }
    public boolean carrierAlive() { return carrierAlive; }
    public boolean crewAlive() { return crewAlive; }
    public boolean turretComplete() { return turretComplete; }
    public boolean responseReachedYard() { return responseReachedYard; }

    public ColdStartProgress(boolean cargoDelivered, boolean shorePower) {
        if (shorePower && !cargoDelivered) throw new IllegalArgumentException("Power needs delivered repair cargo");
        delivered = cargoDelivered;
        powered = shorePower;
        phase = powered ? Phase.CONSTRUCTION : delivered ? Phase.POWER : Phase.ESCORT;
    }
    public Phase phase() { return phase; }
    public boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }
    public float work() { return work; }
    public boolean contested() { return contested; }
    public void fail() { if (!terminal()) phase = Phase.FAILED; }
    public Report report(int crewLost) {
        return new Report(delivered, powered, constructed, phase == Phase.COMPLETE, Math.max(0, Math.min(2, crewLost)));
    }
    public void advance(float dt, boolean crewAlive, boolean carrierAlive, boolean carrierArrived,
                        boolean companyPresent, boolean crewPresent, boolean enemyPresent,
                        boolean turretComplete, boolean responseReachedYard) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        this.companyPresent = companyPresent;
        this.crewPresent = crewPresent;
        this.carrierArrived = carrierArrived;
        this.carrierAlive = carrierAlive;
        this.crewAlive = crewAlive;
        this.turretComplete = turretComplete;
        this.responseReachedYard = responseReachedYard;
        if (!crewAlive || (!delivered && !carrierAlive)) { phase = Phase.FAILED; return; }
        contested = enemyPresent;
        boolean safe = companyPresent && crewPresent && !enemyPresent;
        switch (phase) {
            case ESCORT -> {
                if (carrierArrived && safe) work += dt;
                if (work >= UNLOAD_SECONDS) { delivered = true; next(Phase.POWER); }
            }
            case POWER -> {
                if (safe) work += dt;
                if (work >= POWER_SECONDS) { powered = true; next(Phase.CONSTRUCTION); }
            }
            case CONSTRUCTION -> {
                if (turretComplete) { constructed = true; next(Phase.REPAIR); }
            }
            case REPAIR -> {
                if (safe) work = Math.min(REPAIR_SECONDS, work + dt);
                if (work >= REPAIR_SECONDS && safe && responseReachedYard) next(Phase.COMPLETE);
            }
            default -> { }
        }
    }
    private void next(Phase next) { phase = next; work = 0; }
}
