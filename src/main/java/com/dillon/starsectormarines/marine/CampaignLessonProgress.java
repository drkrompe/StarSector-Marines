package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** Learning receipts only. Rewards and equipment remain with their existing owners. */
public final class CampaignLessonProgress implements Serializable {
    private String damagedBoatId;
    private boolean boatRecoveryReviewed;

    public void observeBoatDamage(String boatId) {
        if (damagedBoatId == null && boatId != null) damagedBoatId = boatId;
    }
    public String damagedBoatId() { return damagedBoatId; }
    public boolean boatRecoveryPending() { return damagedBoatId != null && !boatRecoveryReviewed; }
    public void reviewBoatRecovery() { boatRecoveryReviewed = true; }

    private boolean investmentsReviewed;
    private boolean junctionPrepared;
    /** Formation count when the player set the formation lesson aside, plus one; zero when never set aside. */
    private int formationsDeferredBelow;
    private boolean supportDelivered;
    private boolean fieldGuidanceReviewed;

    public boolean investmentsReviewed() { return investmentsReviewed; }
    public void reviewInvestments() { investmentsReviewed = true; }
    public boolean junctionPrepared() { return junctionPrepared; }
    /** The formation lesson stays set aside until the company has more formations than it did then. */
    public void deferFormations(int currentFormations) { formationsDeferredBelow = currentFormations + 1; }
    public boolean formationsDeferred(int currentFormations) { return currentFormations < formationsDeferredBelow; }
    public void observePreparation(boolean twoDeployableSquads, boolean supportSelected) {
        if (twoDeployableSquads && supportSelected) junctionPrepared = true;
    }
    public boolean supportDelivered() { return supportDelivered; }
    public void observeDelivery(boolean campaignPractice, boolean supportCalled, boolean friendlyMechPresent) {
        if (campaignPractice && supportCalled && friendlyMechPresent) supportDelivered = true;
    }
    public boolean fieldGuidanceReviewed() { return fieldGuidanceReviewed; }
    public void reviewFieldGuidance() { if (supportDelivered) fieldGuidanceReviewed = true; }
}
