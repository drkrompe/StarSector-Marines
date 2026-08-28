package com.dillon.starsectormarines.ops;

/**
 * Identifier for each {@link Screen} in the marine ops dialog. Stored on
 * {@link MarineOpsContext}; the {@link MarineOpsPanelPlugin} maintains a
 * pre-built {@link java.util.EnumMap} of screen instances keyed by this enum
 * so transitions don't allocate.
 */
public enum ScreenId {
    /**
     * The company's between-contracts home, opened from the campaign map with no
     * planet behind it. It and the other shipboard rooms tolerate a null
     * {@code MarineOpsContext.planet} — see {@link MarineOpsPanelPlugin}.
     */
    COMPANY_HQ,
    /** Read-only shipboard marine-quarters browser; safe without a planet. */
    BARRACKS,
    /** Which ship in the fleet the company lives aboard; safe without a planet. */
    SHIP_TRANSFER,
    /** The whole company ship, running, with the camera in the player's hands. */
    SHIP_VIEW,
    /** Dev-only retained document and host-capability proof; safe without a planet. */
    UI_WORKBENCH,
    MISSION_SELECT,
    /** Retained owned-company landing view; safe without a planet. */
    FLEET_ARMORY_OVERVIEW,
    /** Production retained formation/template/refit slice; safe without a planet. */
    FLEET_ARMORY,
    /** Retained support-lance and mech-subsystem room; safe without a planet. */
    MECH_LAB,
    BRIEFING,
    SQUAD_DEPLOYMENT,
    STATIONING,
    BATTLE,
    RESULTS,
    LOOT
}
