package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/** Optional referral and completed record in the existing HQ correspondence tile. */
record AgreusWorkGuide(AgreusArcProgress progress, OpeningBroker.Destination destination, Runnable plotCourse) implements MarkupPropertySource {
    public Object markupProperty(String property) { return switch (property) {
        case "classes" -> "opening-guide console-readout";
        case "crest", "portrait" -> OpeningBroker.CREST;
        case "broker" -> AgreusArcCampaign.NAME;
        case "affiliation" -> "House Rusk / Ko Combine concession";
        case "heading" -> AgreusArcCampaign.TITLE;
        case "mission" -> progress.yardCompleted() ? "COMPLETE / Latchkey inspection" : progress.warrantCompleted() ? "NEW / The Yard's Owners" : progress.completed() ? "NEW / Limited Warrant" : "NEW / Water Under Seal";
        case "payment" -> progress.yardCompleted() ? "92,000 cr settled / Recovered Specter and Arbalest entitlements fulfilled"
                : progress.warrantCompleted() ? "36,000 cr + Recovered Arbalest card / qualifying victory"
                : progress.completed() ? "30,000 cr + Recovered Specter card / qualifying victory" : "26,000 cr / victory fee / no suit award";
        case "location" -> destination == null ? "Agreus / Arcadia" : destination.label();
        case "route" -> progress.yardCompleted() ? "Learn both recovered-suit cargo cards, then fit Salvage-assisted Section in the Armory. Yard custody remains unsigned."
                : destination == null ? AgreusArcCampaign.accessReason() : "Marine operations > Baronet Elian Rusk / " + (progress.warrantCompleted() ? "The Yard's Owners" : progress.completed() ? "Limited Warrant" : "North Weir");
        case "preparation" -> progress.yardCompleted() ? "Issued gear is unchanged. Already learned or carried cards satisfy the award; selling a card does not renew it. Recovered equipment is below military issue."
                : progress.warrantCompleted() ? "Three full squads recommended, fitted boats and working tender. Hold three sites and protect the tooling inspection. Optional owned mech; no bombardment."
                : progress.completed() ? "Review Rusk's bounded procurement warrant. Two squads, fitted boats and a working tender. Protect the supplier and bring the calibration load back. No deadline or yard agreement."
                : "Vale's departure reference, forwarded by Mara. Two squads, fitted boats and an owned working tender required. Replenish before leaving Corvus; no deadline.";
        case "courseDisabled" -> destination == null;
        case "courseAction" -> plotCourse;
        default -> throw new IllegalArgumentException("Unknown Agreus guide property: " + property);
    }; }
}
