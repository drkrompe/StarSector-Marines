package com.dillon.starsectormarines.battle.fort;

/**
 * One function a fort performs, housed in one place that can be reached. Each can be shut down on its own, by taking
 * its place or by wrecking its core. See {@code forts.md}.
 */
public enum FortAspect {
    /** The bunker whose holder the guns obey. */
    FIRE_CONTROL("FIRE CONTROL"),
    /** The hardened store that feeds sustained fire. */
    MAGAZINE("MAGAZINE"),
    /** The plant every other aspect draws on. */
    POWER("POWER"),
    /** The shield generator itself: the dome's emitter. Wrecking it ends the dome for good. Not held by presence. */
    EMITTER("EMITTER"),
    /** The room whose holder the dome obeys: whoever holds it is the side the dome protects. */
    CONTROL_ROOM("CONTROL ROOM");

    private final String label;

    FortAspect(String label) {
        this.label = label;
    }

    /** The name the map shows for this aspect. */
    public String label() {
        return label;
    }
}
