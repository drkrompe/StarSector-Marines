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
    POWER("POWER");

    private final String label;

    FortAspect(String label) {
        this.label = label;
    }

    /** The name the map shows for this aspect. */
    public String label() {
        return label;
    }
}
