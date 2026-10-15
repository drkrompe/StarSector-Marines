package com.dillon.starsectormarines.campaign.nexerelin;

/**
 * Which ground combat resolves a supported Nexerelin action, chosen per save ({@code nexerelin-nouns.md}).
 *
 * <p>Ordinals are persisted on {@code CampaignState.groundCombatPreference}: append only.
 */
public enum GroundCombatPreference {
    /** Offer the choice every time. A new save, and a save from before the setting existed, starts here. */
    ASK,
    /** Fight it as a Marine Operations battle without asking. */
    MARINE_OPERATIONS,
    /** Leave it to Nexerelin's own ground battle without asking. */
    NEXERELIN;

    /** The preference a saved ordinal names; anything unreadable is {@link #ASK}. */
    public static GroundCombatPreference of(int ordinal) {
        GroundCombatPreference[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ASK;
    }
}
