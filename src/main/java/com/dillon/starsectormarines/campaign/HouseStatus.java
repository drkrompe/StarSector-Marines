package com.dillon.starsectormarines.campaign;

/**
 * Lifecycle status of a house in {@link CampaignState}. {@code ACTIVE} is the
 * normal case; the others encode narrative content layers.
 *
 * <p>{@code HIDDEN_PRETENDER} / {@code DEPOSED} exist in the table but are
 * filtered out of normal house-list views; they surface only via discovery
 * (story missions, captain SCOUT trait rolls).
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .houseStatus[]} — never reorder.
 */
public enum HouseStatus {
    /** Normal house, fully simulated. */
    ACTIVE,
    /** Wiped from active politics but not deleted; may re-emerge. */
    DORMANT,
    /** Hidden heir, claim against another house — discoverable but not visible by default. */
    HIDDEN_PRETENDER,
    /** Once-active house that lost its seat — may be helped back via ELEVATE_HEIR chain. */
    DEPOSED;

    private static final HouseStatus[] VALUES = values();

    public static HouseStatus fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
