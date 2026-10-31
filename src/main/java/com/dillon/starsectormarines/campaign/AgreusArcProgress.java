package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/** Authored Agreus receipts. The original completion field remains the North Weir fact in older saves. */
public final class AgreusArcProgress implements Serializable {
    private static final long serialVersionUID = 462183374079572971L;
    public static final String PREFIX = "agreus_water_under_seal_v1_";
    public static final int PAYMENT = 26000;
    public static final String SITE = "north-weir";
    public static final String WARRANT_PREFIX = "agreus_limited_warrant_v1_";
    public static final int WARRANT_PAYMENT = 30000;
    /** The recovered Specter section the warrant's work concerns; the store sells it once the arc settles. */
    public static final String SPECTER_CARD = "loadout-card:sheet:armor:corporate-specter-section";
    private int attempt = 1, techniciansLost, militiaLost, repairLoadsLost;
    private String activeId, activePlanetId, activePlanetName, activeSite;
    private boolean completed;
    private int warrantAttempt, warrantTechniciansLost, warrantConstablesLost, calibrationLoadsLost;
    private boolean warrantCompleted, recordsRecovered;
    public static final String YARD_PREFIX = "agreus_yard_owners_v1_";
    public static final int YARD_PAYMENT = 36000;
    /** The recovered Arbalest mixed section the inspection concerns; no story beat hands it over. */
    public static final String ARBALEST_CARD = "loadout-card:sheet:armor:salvage-assisted";
    private int yardAttempt, yardTechniciansLost, yardConstablesLost, toolingLost;
    private boolean yardCompleted;
    /**
     * Whether *A Small War* (Chapter 5, Black Sluice) has settled with a victory. Its settlement
     * carries the procurement introduction that opens the Hegemony auxiliary warrant offer;
     * {@link #settleSmallWar} is the one writer.
     */
    private boolean smallWarSettled;
    public boolean smallWarSettled() { return smallWarSettled; }
    public static final String SMALL_WAR_PREFIX = "agreus_small_war_v1_";
    public static final int SMALL_WAR_PAYMENT = 44000;
    public static final String SMALL_WAR_SITE = "black-sluice";
    private int smallWarAttempt, smallWarCustodiansLost, smallWarHoldersLost, fuelInfrastructureLost, smallWarPremiumPaid, greyhookWithdrawn;
    public static boolean smallWar(String id) { return id != null && id.startsWith(SMALL_WAR_PREFIX); }
    public int smallWarCustodiansLost() { return smallWarCustodiansLost; }
    public int smallWarHoldersLost() { return smallWarHoldersLost; }
    public int fuelInfrastructureLost() { return fuelInfrastructureLost; }
    /** The premium actually paid with the Chapter 5 settlement; 0 under the compact or before victory. */
    public int smallWarPremiumPaid() { return smallWarPremiumPaid; }
    /** Greyhook people seen to walk off the field on the settled victory. */
    public int greyhookWithdrawn() { return greyhookWithdrawn; }
    /** Chapter 5 is offered only once a Terms of Protection victory has ratified the signed route. */
    public boolean smallWarAvailable() { return handoverStage() == HandoverStage.RATIFIED && !smallWarSettled; }

    /**
     * Chapter 5's once-only settlement. Accepts only the active Black Sluice attempt whose frozen
     * facts match its claimed result; victory sets {@link #smallWarSettled} and returns the fee plus
     * any ratified House Custody premium, exactly once. Defeat records confirmed employer losses and
     * opens a disclosed replacement mobilization. Stale, duplicate or foreign receipts write nothing.
     */
    public int settleSmallWar(String id, String planetId, boolean victory, int payment,
                              boolean customs, boolean fuelControl, boolean dispatch, boolean handedOver,
                              boolean company, boolean infrastructureLost, int custodianLosses, int holderLosses, int withdrawn) {
        boolean achieved = customs && fuelControl && dispatch && handedOver && company
                && !infrastructureLost && custodianLosses < 3;
        if (!smallWarAvailable() || !smallWar(id) || !matchesNext(id) || !id.equals(activeId)
                || !planetIdEquals(planetId) || !SMALL_WAR_SITE.equals(activeSite)
                || payment != (victory ? SMALL_WAR_PAYMENT : 0) || victory != achieved || handedOver && !achieved
                || custodianLosses < 0 || custodianLosses > 3 || holderLosses < 0 || holderLosses > 12 || withdrawn < 0) return -1;
        smallWarCustodiansLost += custodianLosses; smallWarHoldersLost += holderLosses;
        if (infrastructureLost) fuelInfrastructureLost++;
        activeId = null; activePlanetId = null; activePlanetName = null; activeSite = null;
        smallWarAttempt = Math.max(1, smallWarAttempt) + 1;
        if (!victory) return 0;
        smallWarSettled = true;
        greyhookWithdrawn = withdrawn;
        smallWarPremiumPaid = settleCustodyPremium();
        return payment + smallWarPremiumPaid;
    }

    /** Where the Latchkey custody decision stands. Older saves without these fields read as unsigned. */
    public enum HandoverStage {
        /** The Yard's Owners has not settled with a victory. */
        NOT_REACHED,
        /** Both agreements are readable; nothing is committed. */
        OPEN,
        /** One agreement is signed; Chapter 4 victory has not yet ratified it. */
        SIGNED,
        /** Chapter 4 victory ratified the signed agreement. */
        RATIFIED,
        /** The company declined further work before signing, keeping completed fees and cards. */
        DECLINED
    }
    /** Null until an explicit signing action; never written by opening or previewing either text. */
    private AgreusYardAgreement signedAgreement;
    private boolean agreementRatified, furtherWorkDeclined, custodyPremiumSettled;

    public AgreusYardAgreement signedAgreement() { return signedAgreement; }
    public boolean agreementRatified() { return agreementRatified; }
    public boolean furtherWorkDeclined() { return furtherWorkDeclined; }
    public boolean custodyPremiumSettled() { return custodyPremiumSettled; }
    public HandoverStage handoverStage() {
        if (!yardCompleted) return HandoverStage.NOT_REACHED;
        if (furtherWorkDeclined) return HandoverStage.DECLINED;
        if (signedAgreement == null) return HandoverStage.OPEN;
        return agreementRatified ? HandoverStage.RATIFIED : HandoverStage.SIGNED;
    }

    /**
     * The explicit signing action: commits one route once, after a settled Chapter 3 victory and
     * outside any active mobilization. A second signature, of either agreement, is refused, so
     * defeat or reload can never yield both routes' terms.
     */
    public boolean signAgreement(AgreusYardAgreement agreement) {
        if (agreement == null || handoverStage() != HandoverStage.OPEN || activeId != null) return false;
        signedAgreement = agreement;
        return true;
    }

    /** Before signing, the company may stop here; completed fees and cards are untouched. Final. */
    public boolean declineFurtherWork() {
        if (handoverStage() != HandoverStage.OPEN || activeId != null) return false;
        furtherWorkDeclined = true;
        return true;
    }

    /** Chapter 4 (Terms of Protection) may be prepared only under a signed, unratified route. */
    public boolean termsOfProtectionInPreparation() { return handoverStage() == HandoverStage.SIGNED; }

    /**
     * Chapter 4 victory's ratification hook. The outcome freezes the agreement it launched under;
     * a receipt naming any other route, or arriving after ratification, writes nothing. Defeat
     * never calls this: the signed route simply stands for the next attempt.
     */
    public boolean ratifyAgreement(AgreusYardAgreement launchedUnder) {
        if (handoverStage() != HandoverStage.SIGNED || launchedUnder != signedAgreement) return false;
        agreementRatified = true;
        return true;
    }

    /** The disclosed premium still owed on Chapter 5 victory: 12,000 under House Custody, else 0. */
    public int custodyPremiumDue() {
        return signedAgreement == null || custodyPremiumSettled ? 0 : signedAgreement.completionPremium;
    }

    /**
     * Chapter 5 victory's premium hook: returns the credits to pay, once, only for a ratified
     * House Custody route. The compact, an unratified route or a repeat call pays nothing.
     */
    public int settleCustodyPremium() {
        if (handoverStage() != HandoverStage.RATIFIED || custodyPremiumSettled) return 0;
        custodyPremiumSettled = true;
        return signedAgreement.completionPremium;
    }
    // ---- Chapter 4: Terms of Protection ----
    public static final String TERMS_PREFIX = "agreus_terms_of_protection_v1_";
    public static final int TERMS_PAYMENT = 32000;
    public static final String TERMS_SITE = "latchkey-handover";
    /** The Boarding Jack section the commissioning work refurbishes; the store sells it once the arc settles. */
    public static final String BOARDING_JACK_CARD = "loadout-card:sheet:armor:boarding-jack-section";
    private int termsAttempt, termsRepresentativesLost, termsTechniciansLost, termsEmployerLost, recordsCarriersLost;
    private boolean termsCompleted;
    /** The agreement the active Chapter 4 mobilization launched under; frozen at begin. */
    private AgreusYardAgreement activeAgreement;
    /**
     * The retired Boarding Jack cargo award, kept only so saves that recorded it still load.
     * Nothing reads or writes them; the store sells the section instead.
     */
    @SuppressWarnings("unused")
    private boolean boardingJackEntitled, boardingJackDelivered;

    public static boolean termsOfProtection(String id) { return id != null && id.startsWith(TERMS_PREFIX); }
    public boolean termsCompleted() { return termsCompleted; }
    public int termsRepresentativesLost() { return termsRepresentativesLost; }
    public int termsTechniciansLost() { return termsTechniciansLost; }
    /** Confirmed losses among the route's other employer people: estate guards or fitters, and constables. */
    public int termsEmployerLost() { return termsEmployerLost; }
    public int recordsCarriersLost() { return recordsCarriersLost; }
    public AgreusYardAgreement activeAgreement() { return activeAgreement; }

    /**
     * Chapter 4 settlement. Accepts only the active Terms of Protection mobilization, under the
     * agreement it froze at launch, with its complete physical receipt. Victory pays once and
     * ratifies that agreement; defeat or withdrawal
     * records confirmed losses and leaves the signed route standing for a new mobilization.
     */
    public int settleTerms(String id, String planetId, boolean victory, int payment, AgreusYardAgreement route,
                           boolean delivered, boolean signed, boolean commissioned, boolean resolved, boolean ratified,
                           boolean company, boolean carrierLost, int representativeLosses, int technicianLosses, int employerLosses) {
        boolean achieved = delivered && signed && commissioned && resolved && ratified && company && !carrierLost
                && representativeLosses < 2;
        if (!termsOfProtection(id) || !matchesNext(id) || !id.equals(activeId) || !planetIdEquals(planetId)
                || !TERMS_SITE.equals(activeSite) || route == null || route != activeAgreement || route != signedAgreement
                || handoverStage() != HandoverStage.SIGNED
                || payment != (victory ? TERMS_PAYMENT : 0) || victory != achieved || ratified && !achieved
                || signed && !delivered || commissioned && !signed
                || representativeLosses < 0 || representativeLosses > 2 || technicianLosses < 0 || technicianLosses > 2
                || employerLosses < 0 || employerLosses > 8) return -1;
        termsRepresentativesLost += representativeLosses;
        termsTechniciansLost += technicianLosses;
        termsEmployerLost += employerLosses;
        if (carrierLost) recordsCarriersLost++;
        if (victory) {
            if (!ratifyAgreement(route)) return -1;
            termsCompleted = true;
        }
        activeId = null; activePlanetId = null; activePlanetName = null; activeSite = null; activeAgreement = null;
        termsAttempt = Math.max(1, termsAttempt) + 1;
        return payment;
    }

    public boolean yardCompleted() { return yardCompleted; }
    public int yardTechniciansLost() { return yardTechniciansLost; }
    public int yardConstablesLost() { return yardConstablesLost; }
    public int toolingLost() { return toolingLost; }
    public static boolean yardOwners(String id) { return id != null && id.startsWith(YARD_PREFIX); }

    public boolean completed() { return completed; }
    public int techniciansLost() { return techniciansLost; }
    public int militiaLost() { return militiaLost; }
    public int repairLoadsLost() { return repairLoadsLost; }
    public String missionId() { return completed ? null : PREFIX + attempt; }
    public static boolean owns(String id) { return id != null && (id.startsWith(PREFIX) || limitedWarrant(id) || yardOwners(id) || termsOfProtection(id) || smallWar(id)); }
    public static boolean limitedWarrant(String id) { return id != null && id.startsWith(WARRANT_PREFIX); }
    public boolean warrantCompleted() { return warrantCompleted; }
    public boolean recordsRecovered() { return recordsRecovered; }
    public int warrantTechniciansLost() { return warrantTechniciansLost; }
    public int warrantConstablesLost() { return warrantConstablesLost; }
    public int calibrationLoadsLost() { return calibrationLoadsLost; }
    public String nextMissionId() {
        if (!completed) return missionId();
        if (!warrantCompleted) return WARRANT_PREFIX + Math.max(1, warrantAttempt);
        if (!yardCompleted) return YARD_PREFIX + Math.max(1, yardAttempt);
        if (handoverStage() == HandoverStage.SIGNED) return TERMS_PREFIX + Math.max(1, termsAttempt);
        return smallWarAvailable() ? SMALL_WAR_PREFIX + Math.max(1, smallWarAttempt) : null;
    }
    public int nextPayment() {
        return handoverStage() == HandoverStage.RATIFIED ? SMALL_WAR_PAYMENT : yardCompleted ? TERMS_PAYMENT
                : warrantCompleted ? YARD_PAYMENT : completed ? WARRANT_PAYMENT : PAYMENT;
    }
    public boolean matchesNext(String id) { return id != null && id.equals(nextMissionId()); }
    public boolean matches(String id) { return id != null && id.equals(missionId()); }

    public boolean begin(String id, String planetId) {
        return begin(id, planetId, planetId);
    }
    public boolean begin(String id, String planetId, String planetName) {
        if (!matchesNext(id) || activeId != null || !"agreus".equals(planetId) || planetName == null || planetName.isBlank()) return false;
        activeId = id;
        activePlanetId = planetId;
        activePlanetName = planetName;
        activeSite = smallWar(id) ? SMALL_WAR_SITE : termsOfProtection(id) ? TERMS_SITE : yardOwners(id) ? "latchkey-yard" : limitedWarrant(id) ? "low-quay-bonded-stores" : SITE;
        activeAgreement = termsOfProtection(id) ? signedAgreement : null;
        return true;
    }
    /** Closes an unsettled launch as if it never happened: the same attempt can launch again. */
    public boolean abandon(String id) {
        if (id == null || !id.equals(activeId)) return false;
        activeId = null; activePlanetId = null; activePlanetName = null; activeSite = null; activeAgreement = null;
        return true;
    }
    public boolean acceptsTarget(String name) { return activePlanetName != null && activePlanetName.equals(name); }

    /** Accepts only the active named mobilization and its frozen physical result. */
    public int settle(String id, String planetId, boolean victory, int payment,
                      boolean cargoDelivered, boolean northReconnected, boolean southReconnected,
                      boolean turretConstructed, boolean attackResolved, boolean companySurvived,
                      int technicianLosses, int militiaLosses, boolean cargoLost) {
        boolean achieved = cargoDelivered && northReconnected && southReconnected && turretConstructed
                && attackResolved && companySurvived && technicianLosses < 2 && !cargoLost;
        if (!matches(id) || !id.equals(activeId) || !planetIdEquals(planetId)
                || !SITE.equals(activeSite) || payment != (victory ? PAYMENT : 0)
                || technicianLosses < 0 || technicianLosses > 2 || militiaLosses < 0 || militiaLosses > 8
                || victory != achieved) return -1;
        techniciansLost += technicianLosses;
        militiaLost += militiaLosses;
        if (cargoLost) repairLoadsLost++;
        completed = victory;
        activeId = null;
        activePlanetId = null;
        activePlanetName = null;
        activeSite = null;
        attempt++;
        return payment;
    }

    private boolean planetIdEquals(String planetId) { return activePlanetId != null && activePlanetId.equals(planetId); }

    /** An authenticated archive survives withdrawal; a fresh attempt explicitly replaces lost calibration stock. */
    public int settleWarrant(String id, String planetId, boolean victory, int payment,
                             boolean records, boolean kitRecovered, boolean kitReturned, boolean companyReturned,
                             boolean cargoLost, int technicianLosses, int constableLosses) {
        boolean achieved = records && kitRecovered && kitReturned && companyReturned && !cargoLost;
        if (!completed || !limitedWarrant(id) || !matchesNext(id) || !id.equals(activeId)
                || !planetIdEquals(planetId) || !"low-quay-bonded-stores".equals(activeSite)
                || payment != (victory ? WARRANT_PAYMENT : 0) || victory != achieved
                || technicianLosses < 0 || technicianLosses > 1 || constableLosses < 0 || constableLosses > 4
                || recordsRecovered && !records || kitRecovered && !records || kitReturned && (!kitRecovered || cargoLost)) return -1;
        recordsRecovered |= records;
        warrantTechniciansLost += technicianLosses;
        warrantConstablesLost += constableLosses;
        if (cargoLost) calibrationLoadsLost++;
        warrantCompleted = victory;
        activeId = null; activePlanetId = null; activePlanetName = null; activeSite = null;
        warrantAttempt = Math.max(1, warrantAttempt) + 1;
        return payment;
    }
    /** Inspection opens equipment access, never a custody signature or next-stage ratification. */
    public int settleYard(String id, String planetId, boolean victory, int payment,
                          boolean dispatch, boolean tools, boolean entrance, boolean inspected,
                          boolean company, boolean lostTooling, int technicianLosses, int constableLosses) {
        boolean achieved = dispatch && tools && entrance && inspected && company && !lostTooling && technicianLosses < 2;
        if (!warrantCompleted || !yardOwners(id) || !matchesNext(id) || !id.equals(activeId)
                || !planetIdEquals(planetId) || !"latchkey-yard".equals(activeSite)
                || payment != (victory ? YARD_PAYMENT : 0) || victory != achieved
                || inspected && !achieved || technicianLosses < 0 || technicianLosses > 2 || constableLosses < 0 || constableLosses > 8) return -1;
        yardTechniciansLost += technicianLosses; yardConstablesLost += constableLosses;
        if (lostTooling) toolingLost++;
        yardCompleted = victory;
        activeId = null; activePlanetId = null; activePlanetName = null; activeSite = null;
        yardAttempt = Math.max(1, yardAttempt) + 1;
        return payment;
    }

}
