package com.dillon.starsectormarines.battle.story;

/** Chapter-one referee: safe work and physical freight precede a real defensive handover. */
public final class WaterUnderSealProgress {
    public enum Phase { ESCORT, NORTH_ROOM, SOUTH_ROOM, CONSTRUCTION, ATTACK, COMPLETE, FAILED }
    public record Report(boolean cargoDelivered, boolean northRoomReconnected, boolean southRoomReconnected,
                         boolean turretConstructed, boolean attackResolved, boolean companySurvived,
                         int techniciansLost, int militiaLost, boolean cargoLost) {
        public boolean reconnected() { return northRoomReconnected && southRoomReconnected; }
        public boolean complete() { return cargoDelivered && reconnected() && turretConstructed && attackResolved
                && companySurvived && !cargoLost && techniciansLost < 2; }
    }
    public static final float UNLOAD_SECONDS = 6, RECONNECT_SECONDS = 24;
    private Phase phase = Phase.ESCORT;
    private float work;
    private boolean delivered, north, south, constructed, resolved, cargoLost;
    private boolean safeWork, carrierArrived, positionLost;
    public boolean positionLost() { return positionLost; }
    public boolean safeWork() { return safeWork; }
    public boolean carrierArrived() { return carrierArrived; }
    public Phase phase() { return phase; }
    public float work() { return work; }
    public boolean terminal() { return phase == Phase.COMPLETE || phase == Phase.FAILED; }
    public void fail() { if (!terminal()) phase = Phase.FAILED; }
    public Report report(boolean company, int techniciansLost, int militiaLost) {
        return new Report(delivered, north, south, constructed, resolved, company,
                Math.max(0, Math.min(2, techniciansLost)), Math.max(0, Math.min(8, militiaLost)), cargoLost);
    }
    public void advance(float dt, boolean companyAlive, boolean techniciansAlive, boolean carrierAlive,
                        boolean carrierArrived, boolean safeWork, boolean commissioned, boolean attackResolved) {
        if (!Float.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid mission time");
        if (terminal()) return;
        this.safeWork = safeWork; this.carrierArrived = carrierArrived;

        if (!carrierAlive) cargoLost = true;
        if (!companyAlive || !techniciansAlive || cargoLost) { fail(); return; }
        switch (phase) {
            case ESCORT -> {
                if (carrierArrived && safeWork) work += dt;
                if (work >= UNLOAD_SECONDS) { delivered = true; next(Phase.NORTH_ROOM); }
            }
            case NORTH_ROOM, SOUTH_ROOM -> {
                if (safeWork) work += dt;
                if (work >= RECONNECT_SECONDS) {
                    if (phase == Phase.NORTH_ROOM) { north = true; next(Phase.SOUTH_ROOM); }
                    else { south = true; next(Phase.CONSTRUCTION); }
                }
            }
            case CONSTRUCTION -> {
                if (commissioned) { constructed = true; next(Phase.ATTACK); }
            }
            case ATTACK -> {
                if (!commissioned) { constructed = false; positionLost = true; next(Phase.CONSTRUCTION); }
                else if (safeWork && attackResolved) { resolved = true; next(Phase.COMPLETE); }
            }
            default -> { }
        }
    }
    private void next(Phase next) { phase = next; work = 0; }
}
