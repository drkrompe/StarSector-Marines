package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/** Receipts for the cooperative's claim and repair contracts; neither awards a fleet hull. */
public final class ShepherdRecoveryProgress implements Serializable {
    private static final long serialVersionUID = 917453231679962050L;
    public static final String PREFIX = "basin_salvage_v1_", COLD_PREFIX = "basin_cold_start_v1_";
    public static final int PAYMENT = 18000, COLD_PAYMENT = 22000;
    private int attempt = 1, inspectionLosses, repairLosses;
    private String activeId, activePlanet;
    private boolean claimSecured, cargoDelivered, shorePower, repaired;
    public boolean claimSecured() { return claimSecured; }
    public boolean cargoDelivered() { return cargoDelivered; }
    public boolean shorePower() { return shorePower; }
    public boolean repaired() { return repaired; }
    public int inspectionLosses() { return inspectionLosses; }
    public int repairLosses() { return repairLosses; }
    public int payment() { return claimSecured ? COLD_PAYMENT : PAYMENT; }
    public String missionId() { return repaired ? null : (claimSecured ? COLD_PREFIX : PREFIX) + attempt; }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }
    public static boolean coldStart(String id) { return id != null && id.startsWith(COLD_PREFIX); }
    public static boolean owns(String id) { return id != null && (id.startsWith(PREFIX) || coldStart(id)); }
    public boolean begin(String id, String planet) {
        if (!matches(id) || activeId != null || planet == null || planet.isBlank()) return false;
        activeId = id; activePlanet = planet; return true;
    }
    private boolean receipt(String id, String planet, boolean victory, int payment, int crewLost, int expected) {
        return matches(id) && id.equals(activeId) && activePlanet != null && activePlanet.equals(planet)
                && payment == (victory ? expected : 0) && crewLost >= 0 && crewLost <= 2 && (!victory || crewLost < 2);
    }
    private void close() { activeId = null; activePlanet = null; attempt++; }
    public int settle(String id, String planet, boolean victory, int payment, int crewLost, boolean inspected) {
        if (claimSecured || !receipt(id, planet, victory, payment, crewLost, PAYMENT) || victory != inspected) return -1;
        inspectionLosses += crewLost; close(); claimSecured = victory; return payment;
    }
    public int settleColdStart(String id, String planet, boolean victory, int payment, int crewLost,
                               boolean delivered, boolean powered, boolean turretConstructed, boolean complete) {
        if (!claimSecured || !coldStart(id) || !receipt(id, planet, victory, payment, crewLost, COLD_PAYMENT)
                || victory != complete || powered && !delivered || complete && (!delivered || !powered || !turretConstructed)
                || cargoDelivered && !delivered || shorePower && !powered) return -1;
        repairLosses += crewLost; cargoDelivered |= delivered; shorePower |= powered; repaired = complete;
        close(); return payment;
    }
}
