package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/**
 * Save-owned ledger for District Escort, the repeatable job that follows *A Small War*. It is
 * offered only after Chapter 5 has settled under a ratified agreement, through that agreement's
 * escort contact, and is otherwise independent of the arc: it reads the arc's receipts and never
 * writes them. A job pays its fee once, awards no equipment, and touches no ratification,
 * premium, promotion, MRB tier, stationing contract or faction commission.
 *
 * <p>Every attempt carries a fresh identity ({@code job} and a never-reused {@code attempt});
 * the consignment and its handover party are new on each. Only the active attempt, at its frozen
 * site, with a receipt consistent with its own victory, may settle, and only once.
 */
public final class AgreusDistrictEscortProgress implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String PREFIX = "agreus_district_escort_v1_";
    public static final int PAYMENT = 24_000;
    public static final String SITE = "low-quay-freight-lane";
    public static final int PARTY = 2, CONSTABLES = 4;

    private final AgreusYardAgreement agreement;
    private int job = 1, attempt = 1;
    private String activeId, activePlanetId, activePlanetName;
    private int jobsCompleted, paid, partyLost, constablesLost, consignmentsLost;
    private String lastMissionId;
    private boolean lastVictory;
    private int lastPayment;

    public AgreusDistrictEscortProgress(AgreusYardAgreement agreement) {
        if (agreement == null) throw new IllegalArgumentException("District Escort runs under a signed agreement");
        this.agreement = agreement;
    }

    /**
     * The offer gate: *A Small War* settled, and the agreement it ran under was signed and ratified.
     * The arc's own receipts are the only inputs; nothing here can produce them.
     */
    public static boolean offerable(boolean smallWarSettled, AgreusArcProgress.HandoverStage stage, AgreusYardAgreement signed) {
        return smallWarSettled && stage == AgreusArcProgress.HandoverStage.RATIFIED && signed != null;
    }
    public static boolean offerable(AgreusArcProgress arc) {
        return arc != null && offerable(arc.smallWarSettled(), arc.handoverStage(), arc.signedAgreement());
    }
    public static boolean owns(String id) { return id != null && id.startsWith(PREFIX); }
    /** A route code inside the identity, so an attempt under one agreement can never settle another. */
    static String code(AgreusYardAgreement agreement) { return agreement == AgreusYardAgreement.HOUSE_CUSTODY ? "custody" : "compact"; }
    /** The agreement an escort identity was issued under, or null for any other identity. */
    public static AgreusYardAgreement agreementOf(String id) {
        if (!owns(id)) return null;
        for (var agreement : AgreusYardAgreement.values())
            if (id.startsWith(PREFIX + code(agreement) + "_")) return agreement;
        return null;
    }

    public AgreusYardAgreement agreement() { return agreement; }
    /** Who issues the work: Dormer's office under House Custody, Lenna's association under the compact. */
    public String contactId() { return agreement.escortContactId; }
    public int job() { return job; }
    public int attempt() { return attempt; }
    public int jobsCompleted() { return jobsCompleted; }
    public int paid() { return paid; }
    public int partyLost() { return partyLost; }
    public int constablesLost() { return constablesLost; }
    public int consignmentsLost() { return consignmentsLost; }
    public String lastMissionId() { return lastMissionId; }
    public boolean lastVictory() { return lastVictory; }
    public int lastPayment() { return lastPayment; }
    public boolean active() { return activeId != null; }

    public String missionId() { return PREFIX + code(agreement) + "_" + job + "_" + attempt; }
    /** The consignment's manifest number; new for every attempt, never a retried old load. */
    public String consignmentId() { return "DE-" + job + "-" + attempt; }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }

    public boolean begin(String id, String planetId, String planetName) {
        if (!matches(id) || activeId != null || !"agreus".equals(planetId) || planetName == null || planetName.isBlank()) return false;
        activeId = id; activePlanetId = planetId; activePlanetName = planetName;
        return true;
    }
    public boolean acceptsTarget(String name) { return activePlanetName != null && activePlanetName.equals(name); }

    /**
     * Accepts only the active attempt's frozen physical receipt. Returns the fee (zero on a
     * defeat), or -1 when the outcome is stale, duplicate, foreign or inconsistent, writing nothing.
     */
    public int settle(String id, String planetId, boolean victory, int payment, boolean linkedUp, boolean cargoDelivered,
                      boolean partyDelivered, boolean companyAtEgress, boolean cargoLost, int partyLosses, int constableLosses) {
        boolean achieved = linkedUp && cargoDelivered && partyDelivered && companyAtEgress && !cargoLost && partyLosses < PARTY;
        if (!matches(id) || !id.equals(activeId) || activePlanetId == null || !activePlanetId.equals(planetId)
                || payment != (victory ? PAYMENT : 0) || victory != achieved
                || cargoDelivered && cargoLost || (cargoDelivered || partyDelivered) && !linkedUp
                || partyLosses < 0 || partyLosses > PARTY || constableLosses < 0 || constableLosses > CONSTABLES) return -1;
        partyLost += partyLosses;
        constablesLost += constableLosses;
        if (cargoLost) consignmentsLost++;
        lastMissionId = id; lastVictory = victory; lastPayment = payment;
        paid += payment;
        if (victory) { jobsCompleted++; job++; }
        attempt++;
        activeId = null; activePlanetId = null; activePlanetName = null;
        return payment;
    }

    public String status() {
        return "Jobs delivered: " + jobsCompleted + " / paid " + paid + " cr"
                + (partyLost + constablesLost + consignmentsLost == 0 ? "" : " / confirmed losses: " + partyLost
                + " party, " + constablesLost + " constables, " + consignmentsLost + " consignments");
    }
}
