package com.dillon.starsectormarines.campaign;

/**
 * Lifecycle state of a {@link ContractType contract} — see
 * <code>roadmap/campaign/contracts.md</code> §"Lifecycle".
 *
 * <pre>
 *  ACTIVE ──► IN_PROGRESS ──► COMPLETED
 *     │                       FAILED
 *     │                       ABANDONED
 *     └──────────────────────► DEFAULTED   (patron stops paying)
 * </pre>
 *
 * <p>Mission-mode contracts spend most of their lifetime in {@code IN_PROGRESS}
 * across phases; stationing-mode contracts stay {@code ACTIVE} until the term
 * expires or the patron defaults.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .contractState[]} — never reorder.
 */
public enum ContractState {
    /** Offered or signed but no phase has fired yet. Stationing contracts stay here for their term. */
    ACTIVE,
    /** At least one phase / mission has fired but the contract isn't resolved yet. */
    IN_PROGRESS,
    /** All phases completed / term expired successfully. Pays full bonus. */
    COMPLETED,
    /** Player failed a phase / mission. Mission-mode terminates here; rep hit. */
    FAILED,
    /** Patron stopped paying (DEPOSED / promoted / political flip). Spawns extraction. */
    DEFAULTED,
    /** Player walked away mid-contract. Tanks rep + MRB. */
    ABANDONED;

    private static final ContractState[] VALUES = values();

    public static ContractState fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }

    /** Terminal states — no further mutation expected. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == DEFAULTED || this == ABANDONED;
    }
}
