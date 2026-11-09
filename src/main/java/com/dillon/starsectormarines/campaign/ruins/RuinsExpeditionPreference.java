package com.dillon.starsectormarines.campaign.ruins;

/**
 * How the player's exploration of a world's ruins is carried out, chosen per save ({@code ruins-expedition.md}).
 *
 * <p>Separate from the raid and invasion preferences. Ordinals are persisted on
 * {@code CampaignState.ruinsExpeditionPreference}: append only.
 */
public enum RuinsExpeditionPreference {
    /** Offer the choice every time. A new save, and a save from before the setting existed, starts here. */
    ASK,
    /** Send the company in as a Marine Operations expedition without asking. */
    MARINE_OPERATIONS,
    /** Send vanilla's salvage team, as the game always has. */
    STANDARD;

    /** The preference a saved ordinal names; anything unreadable is {@link #ASK}. */
    public static RuinsExpeditionPreference of(int ordinal) {
        RuinsExpeditionPreference[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ASK;
    }
}
