package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** Learning receipts only. Rewards and equipment remain with their existing owners. */
public final class CampaignLessonProgress implements Serializable {
    private boolean investmentsReviewed;
    private boolean junctionPrepared;
    private boolean supportDelivered;
    private boolean fieldGuidanceReviewed;

    public boolean investmentsReviewed() { return investmentsReviewed; }
    public void reviewInvestments() { investmentsReviewed = true; }
    public boolean junctionPrepared() { return junctionPrepared; }
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
