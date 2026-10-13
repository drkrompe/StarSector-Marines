package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/** Authored Agreus receipts. The original completion field remains the North Weir fact in older saves. */
public final class AgreusArcProgress implements Serializable {
    private static final long serialVersionUID = 462183374079572971L;
    public static final String PREFIX = "agreus_water_under_seal_v1_";
    public static final int PAYMENT = 26000;
    public static final String SITE = "north-weir";
    public static final String WARRANT_PREFIX = "agreus_limited_warrant_v1_";
    public static final int WARRANT_PAYMENT = 30000;
    public static final String SPECTER_CARD = "equipment-template:armor.specter-recovered";
    private int attempt = 1, techniciansLost, militiaLost, repairLoadsLost;
    private String activeId, activePlanetId, activePlanetName, activeSite;
    private boolean completed;
    private int warrantAttempt, warrantTechniciansLost, warrantConstablesLost, calibrationLoadsLost;
    private boolean warrantCompleted, recordsRecovered;

    public boolean completed() { return completed; }
    public int techniciansLost() { return techniciansLost; }
    public int militiaLost() { return militiaLost; }
    public int repairLoadsLost() { return repairLoadsLost; }
    public String missionId() { return completed ? null : PREFIX + attempt; }
    public static boolean owns(String id) { return id != null && (id.startsWith(PREFIX) || limitedWarrant(id)); }
    public static boolean limitedWarrant(String id) { return id != null && id.startsWith(WARRANT_PREFIX); }
    public boolean warrantCompleted() { return warrantCompleted; }
    public boolean recordsRecovered() { return recordsRecovered; }
    public int warrantTechniciansLost() { return warrantTechniciansLost; }
    public int warrantConstablesLost() { return warrantConstablesLost; }
    public int calibrationLoadsLost() { return calibrationLoadsLost; }
    public String nextMissionId() { return !completed ? missionId() : warrantCompleted ? null : WARRANT_PREFIX + Math.max(1, warrantAttempt); }
    public int nextPayment() { return completed ? WARRANT_PAYMENT : PAYMENT; }
    public boolean matchesNext(String id) { return id != null && id.equals(nextMissionId()); }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }

    public boolean begin(String id, String planetId) {
        return begin(id, planetId, planetId);
    }
    public boolean begin(String id, String planetId, String planetName) {
        if (!matchesNext(id) || activeId != null || !"agreus".equals(planetId) || planetName == null || planetName.isBlank()) return false;
        activeId = id;
        activePlanetId = planetId;
        activePlanetName = planetName;
        activeSite = limitedWarrant(id) ? "low-quay-bonded-stores" : SITE;
        return true;
    }
    public boolean acceptsTarget(String name) { return activePlanetName != null && activePlanetName.equals(name); }

    /** Accepts only the active named mobilization and its frozen physical result. */
    public int settle(String id, String planetId, boolean victory, int payment,
                      boolean cargoDelivered, boolean northReconnected, boolean southReconnected,
                      boolean turretConstructed, boolean attackResolved, boolean companySurvived,
                      int technicianLosses, int militiaLosses, boolean cargoLost) {
        boolean achieved = cargoDelivered && northReconnected && southReconnected && turretConstructed
                && attackResolved && companySurvived && technicianLosses < 2 && !cargoLost;
        if (!matches(id) || !id.equals(activeId) || !planetIdEquals(planetId)
                || !SITE.equals(activeSite) || payment != (victory ? PAYMENT : 0)
                || technicianLosses < 0 || technicianLosses > 2 || militiaLosses < 0 || militiaLosses > 8
                || victory != achieved) return -1;
        techniciansLost += technicianLosses;
        militiaLost += militiaLosses;
        if (cargoLost) repairLoadsLost++;
        completed = victory;
        activeId = null;
        activePlanetId = null;
        activePlanetName = null;
        activeSite = null;
        attempt++;
        return payment;
    }

    private boolean planetIdEquals(String planetId) { return activePlanetId != null && activePlanetId.equals(planetId); }

    /** An authenticated archive survives withdrawal; a fresh attempt explicitly replaces lost calibration stock. */
    public int settleWarrant(String id, String planetId, boolean victory, int payment,
                             boolean records, boolean kitRecovered, boolean kitReturned, boolean companyReturned,
                             boolean cargoLost, int technicianLosses, int constableLosses) {
        boolean achieved = records && kitRecovered && kitReturned && companyReturned && !cargoLost;
        if (!completed || !limitedWarrant(id) || !matchesNext(id) || !id.equals(activeId)
                || !planetIdEquals(planetId) || !"low-quay-bonded-stores".equals(activeSite)
                || payment != (victory ? WARRANT_PAYMENT : 0) || victory != achieved
                || technicianLosses < 0 || technicianLosses > 1 || constableLosses < 0 || constableLosses > 4
                || recordsRecovered && !records || kitRecovered && !records || kitReturned && (!kitRecovered || cargoLost)) return -1;
        recordsRecovered |= records;
        warrantTechniciansLost += technicianLosses;
        warrantConstablesLost += constableLosses;
        if (cargoLost) calibrationLoadsLost++;
        warrantCompleted = victory;
        activeId = null; activePlanetId = null; activePlanetName = null; activeSite = null;
        warrantAttempt = Math.max(1, warrantAttempt) + 1;
        return payment;
    }
}
