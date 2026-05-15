package com.dillon.starsectormarines.ops;

/**
 * Identifier for each {@link Screen} in the marine ops dialog. Stored on
 * {@link MarineOpsContext}; the {@link MarineOpsPanelPlugin} maintains a
 * pre-built {@link java.util.EnumMap} of screen instances keyed by this enum
 * so transitions don't allocate.
 */
public enum ScreenId {
    MISSION_SELECT,
    BRIEFING,
    BATTLE,
    RESULTS,
    TILESET_DEBUG
}
