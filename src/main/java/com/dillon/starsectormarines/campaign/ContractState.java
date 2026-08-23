package com.dillon.starsectormarines.campaign;

/**
 * Lifecycle state of a {@link ContractType contract} — see
 * <code>roadmap/campaign/contracts/design/contracts-nouns.md</code>.
 *
 * <p>{@code OFFERED} is the pre-commitment state and may expire. Accepted
 * stationing work remains {@code ACTIVE}; phased missions use
 * {@code IN_PROGRESS}. Ordinary mission offers currently settle directly from
 * {@code OFFERED}; {@code mission-offer-acceptance.md} tracks moving that path
 * through the persisted acceptance boundary before launch.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .contractState[]} — never reorder.
 */
public enum ContractState {
    /** Player has accepted; no phase has fired yet. Stationing contracts stay here for their term. */
    ACTIVE,
    /** At least one phase / mission has fired but the contract isn't resolved yet. */
    IN_PROGRESS,
    /** All phases completed / term expired successfully. Pays full bonus. */
    COMPLETED,
    /** Player failed a phase / mission. Mission-mode terminates here; rep hit. */
    FAILED,
    /** Patron breached a stationing agreement. Spawns recovery work. */
    DEFAULTED,
    /** Player walked away mid-contract. Tanks rep + MRB. */
    ABANDONED,
    /**
     * Patron has put this on the table but the player hasn't accepted yet.
     * Stationing and civil-war acceptance flip OFFERED → ACTIVE; ordinary
     * mission acceptance still awaits the persisted boundary tracked in
     * {@code mission-offer-acceptance.md}. The offer-aging branch of
     * {@code ContractLifecycleSystem} flips OFFERED → {@link #EXPIRED} when the
     * offer window lapses. Ordinal kept stable for save compatibility with
     * releases that predated EXPIRED.
     */
    OFFERED,
    /**
     * Offer lapsed before the player accepted. Terminal — filters out of the
     * offer list. The contract ID remains stable for callers; the owning
     * maintenance path may later compact the backing row storage and rebuild
     * its lookup index.
     * Appended after {@link #OFFERED} so existing ordinals stay stable.
     */
    EXPIRED;

    private static final ContractState[] VALUES = values();

    public static ContractState fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }

    /** Terminal states — no further mutation expected. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == DEFAULTED
                || this == ABANDONED || this == EXPIRED;
    }
}
