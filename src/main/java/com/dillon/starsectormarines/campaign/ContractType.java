package com.dillon.starsectormarines.campaign;

/**
 * Contract archetype — drives generation, payment structure, and chain
 * contribution per <code>roadmap/campaign/contracts/overview.md</code>.
 *
 * <p>Two modes hang off this:
 * <ul>
 *   <li><b>Mission-mode</b> (STRIKE, ESCORT, PLANETARY_ASSAULT, EXTRACTION):
 *       one or more single-battle missions; lump-sum or staged payout.</li>
 *   <li><b>Stationing-mode</b> (GARRISON, CADRE): drop-off marines / equipment,
 *       hold territory or train, retainer payment over time.</li>
 * </ul>
 *
 * <p>{@link #EXTRACTION} is system-generated (spawned on default), not
 * patron-offered — it's the campaign tier's bridge out of a failed contract.
 *
 * <p>Backed by {@link #ordinal()} into the {@code byte} slot in
 * {@link CampaignState}{@code .contractType[]} — never reorder.
 */
public enum ContractType {
    STRIKE,
    ESCORT,
    PLANETARY_ASSAULT,
    GARRISON,
    CADRE,
    EXTRACTION;

    private static final ContractType[] VALUES = values();

    public static ContractType fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }

    /** True for retainer-paid, time-bounded contracts that hold a market without per-battle work. */
    public boolean isStationing() {
        return this == GARRISON || this == CADRE;
    }

    /** True for contracts that spawn one or more discrete battle missions. */
    public boolean isMissionMode() {
        return !isStationing();
    }
}
