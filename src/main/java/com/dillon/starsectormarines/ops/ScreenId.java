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
     * planet behind it. The only screen here that tolerates a null
     * {@code MarineOpsContext.planet} — see {@link MarineOpsPanelPlugin}.
     */
    COMPANY_HQ,
    /** Dev-only retained document and host-capability proof; safe without a planet. */
    UI_WORKBENCH,
    MISSION_SELECT,
    ARMORY,
    BRIEFING,
    SQUAD_DEPLOYMENT,
    STATIONING,
    BATTLE,
    RESULTS,
    LOOT
}
