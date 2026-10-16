package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/** Optional referral, custody decision and completed record in the existing HQ correspondence tile. */
record AgreusWorkGuide(AgreusArcProgress progress, OpeningBroker.Destination destination, Runnable plotCourse) implements MarkupPropertySource {
    public Object markupProperty(String property) { return switch (property) {
        case "classes" -> "opening-guide console-readout";
        case "crest", "portrait" -> OpeningBroker.CREST;
        case "broker" -> AgreusArcCampaign.NAME;
        case "affiliation" -> "House Rusk / Ko Combine concession";
        case "heading" -> AgreusArcCampaign.TITLE;
        case "mission" -> progress.yardCompleted() ? handoverMission() : progress.warrantCompleted() ? "NEW / The Yard's Owners" : progress.completed() ? "NEW / Limited Warrant" : "NEW / Water Under Seal";
        case "payment" -> progress.yardCompleted() ? handoverPayment()
                : progress.warrantCompleted() ? "36,000 cr + Recovered Arbalest card / qualifying victory"
                : progress.completed() ? "30,000 cr + Recovered Specter card / qualifying victory" : "26,000 cr / victory fee / no suit award";
        case "location" -> destination == null ? "Agreus / Arcadia" : destination.label();
        case "route" -> progress.yardCompleted() ? handoverRoute()
                : destination == null ? AgreusArcCampaign.accessReason() : "Marine operations > Baronet Elian Rusk / " + (progress.warrantCompleted() ? "The Yard's Owners" : progress.completed() ? "Limited Warrant" : "North Weir");
        case "preparation" -> progress.yardCompleted() ? handoverPreparation()
                : progress.warrantCompleted() ? "Three full squads recommended, fitted boats and working tender. Hold three sites and protect the tooling inspection. Optional owned mech; no bombardment."
                : progress.completed() ? "Review Rusk's bounded procurement warrant. Two squads, fitted boats and a working tender. Protect the supplier and bring the calibration load back. No deadline or yard agreement."
                : "Vale's departure reference, forwarded by Mara. Two squads, fitted boats and an owned working tender required. Replenish before leaving Corvus; no deadline.";
        case "courseDisabled" -> destination == null;
        case "courseAction" -> plotCourse;
        default -> throw new IllegalArgumentException("Unknown Agreus guide property: " + property);
    }; }

    private String handoverMission() {
        return switch (progress.handoverStage()) {
            case OPEN -> "DECISION / Latchkey custody agreements";
            case SIGNED -> "IN PREPARATION / Terms of Protection";
            case RATIFIED -> "RATIFIED / " + agreementName(progress.signedAgreement());
            case DECLINED -> "CLOSED / Further work declined";
            case NOT_REACHED -> "COMPLETE / Latchkey inspection";
        };
    }
    private String handoverPayment() {
        return switch (progress.handoverStage()) {
            case SIGNED -> "Signed: " + agreementName(progress.signedAgreement()) + " / ratified only on Chapter 4 victory"
                    + (progress.custodyPremiumDue() > 0 ? " / 12,000 cr premium on Chapter 5 victory" : " / premium forgone");
            case RATIFIED -> agreementName(progress.signedAgreement())
                    + (progress.custodyPremiumDue() > 0 ? " / 12,000 cr premium on Chapter 5 victory" : " / premium forgone");
            default -> "92,000 cr settled / Recovered Specter and Arbalest entitlements fulfilled";
        };
    }
    private String handoverRoute() {
        return switch (progress.handoverStage()) {
            case OPEN -> "Company HQ > Latchkey custody: read both agreements, ask Castle to compare, or leave them. Nothing is signed.";
            case SIGNED -> "Terms of Protection is in preparation; no deployment is available yet. Later escort work: "
                    + (progress.signedAgreement() == AgreusYardAgreement.HOUSE_CUSTODY ? "Rusk's office." : "Lenna Saye's association.");
            default -> "Learn both recovered-suit cargo cards, then fit Salvage-assisted Section in the Armory.";
        };
    }
    private String handoverPreparation() {
        return switch (progress.handoverStage()) {
            case OPEN -> "Signing commits one route and closes the other. You may instead decline further work and keep completed fees and cards.";
            case SIGNED, RATIFIED -> AgreusArcCampaign.VOSS_WARNING.speaker() + ": “" + AgreusArcCampaign.VOSS_WARNING.text() + "”";
            case DECLINED -> AgreusArcCampaign.DECLINED_RECORD;
            case NOT_REACHED -> "Issued gear is unchanged. Already learned or carried cards satisfy the award; selling a card does not renew it. Recovered equipment is below military issue.";
        };
    }
    static String agreementName(AgreusYardAgreement agreement) {
        return agreement == AgreusYardAgreement.HOUSE_CUSTODY ? "House Custody" : "Yard Compact";
    }
}
