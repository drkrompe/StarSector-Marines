package com.dillon.starsectormarines.campaign;

/**
 * The two written Latchkey custody agreements the captain may sign after The Yard's Owners.
 * Each constant carries only its standing campaign consequences; neither changes combat,
 * equipment access or a hidden standing score. Both keep Dormer's secured title, the yard
 * debt, the same core equipment rewards and the duty to remove Greyhook's coercion.
 */
public enum AgreusYardAgreement {
    /** Dormer's appointed custodians take tool access; workshops run on new leases. */
    HOUSE_CUSTODY(12_000, AgreusYardAgreement.RUSK_OFFICE, false),
    /** A joint custody party keeps inspected common-service access under repayment. */
    YARD_COMPACT(0, AgreusYardAgreement.LENNA_ASSOCIATION, true);

    /** Contact identity for later district-escort work under House Custody. */
    public static final String RUSK_OFFICE = "marines_elian_rusk";
    /** Contact identity for later district-escort work under the Yard Compact. */
    public static final String LENNA_ASSOCIATION = "marines_lenna_saye";

    /** Completion premium payable only with Chapter 5 victory; the compact forgoes it. */
    public final int completionPremium;
    /** Who offers the later district-escort work on this route. */
    public final String escortContactId;
    /**
     * Dormer's recorded assessment: an independent counterparty rather than his preferred
     * enforcement contractor. A record only; it creates no access penalty by itself.
     */
    public final boolean independentCounterparty;

    AgreusYardAgreement(int completionPremium, String escortContactId, boolean independentCounterparty) {
        this.completionPremium = completionPremium;
        this.escortContactId = escortContactId;
        this.independentCounterparty = independentCounterparty;
    }
}
