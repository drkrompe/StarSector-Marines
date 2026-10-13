package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.CorvusChapter;

import java.io.Serializable;

/** Save-owned chapter and attempt receipt; projection and briefing never advance it. */
public final class CorvusWaylineProgress implements Serializable {
    public static final String PREFIX = "wayline_corvus_v1_";
    private int version = 1;
    private int chapter;
    private int attempt = 1;
    private int paid;
    private String activeId;
    private String activePlanetName;
    // Null only in pre-recovery saves: their completed account already passed its debrief.
    private Boolean aftermathAcknowledged = false;
    public boolean aftermathAcknowledged() { return complete() && !Boolean.FALSE.equals(aftermathAcknowledged); }
    public void acknowledgeAftermath() { if(complete()) aftermathAcknowledged=true; }

    public boolean readable() {
        return version == 1 && chapter >= 0 && chapter <= CorvusChapter.values().length && attempt > 0;
    }
    public boolean complete() { return readable() && chapter == CorvusChapter.values().length; }
    public CorvusChapter chapter() { return !readable() || complete() ? null : CorvusChapter.values()[chapter]; }
    public int paid() { return paid; }
    public String missionId() { return chapter() == null ? null : PREFIX + chapter + "_" + attempt; }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }
    public boolean begin(String id, String planetName) {
        if (!matches(id) || activeId != null || planetName == null || planetName.isBlank()) return false;
        activeId = id; activePlanetName = planetName;
        return true;
    }
    /** Reject before any external write: duplicates, foreign targets, and incorrect awards. */
    public int settle(String id, String planetName, boolean victory, int payment) {
        if (!matches(id) || !id.equals(activeId) || !activePlanetName.equals(planetName)
                || payment != (victory ? chapter().payment : 0)) return -1;
        activeId = null; activePlanetName = null;
        attempt++;
        if (victory) { chapter++; paid += payment; }
        return payment;
    }
    public static CorvusChapter forMission(String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        String[] parts = id.substring(PREFIX.length()).split("_", -1);
        if (parts.length != 2) return null;
        try {
            int index = Integer.parseInt(parts[0]), attempt = Integer.parseInt(parts[1]);
            if (index < 0 || index >= CorvusChapter.values().length || attempt < 1
                    || !id.equals(PREFIX + index + "_" + attempt)) return null;
            return CorvusChapter.values()[index];
        } catch (NumberFormatException ignored) { return null; }
    }
}
