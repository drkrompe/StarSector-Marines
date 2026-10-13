package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/** The North Weir contract's attempt ledger; later chapters have no runtime offer yet. */
public final class AgreusArcProgress implements Serializable {
    private static final long serialVersionUID = 462183374079572971L;
    public static final String PREFIX = "agreus_water_under_seal_v1_";
    public static final int PAYMENT = 26000;
    public static final String SITE = "north-weir";
    private int attempt = 1, techniciansLost, militiaLost, repairLoadsLost;
    private String activeId, activePlanetId, activePlanetName, activeSite;
    private boolean completed;

    public boolean completed() { return completed; }
    public int techniciansLost() { return techniciansLost; }
    public int militiaLost() { return militiaLost; }
    public int repairLoadsLost() { return repairLoadsLost; }
    public String missionId() { return completed ? null : PREFIX + attempt; }
    public static boolean owns(String id) { return id != null && id.startsWith(PREFIX); }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }

    public boolean begin(String id, String planetId) {
        return begin(id, planetId, planetId);
    }
    public boolean begin(String id, String planetId, String planetName) {
        if (!matches(id) || activeId != null || !"agreus".equals(planetId) || planetName == null || planetName.isBlank()) return false;
        activeId = id;
        activePlanetId = planetId;
        activePlanetName = planetName;
        activeSite = SITE;
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
}
