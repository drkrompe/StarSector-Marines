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
        case "mission" -> progress.completed() ? "COMPLETE / Water Under Seal" : "NEW / Water Under Seal";
        case "payment" -> progress.completed() ? "26,000 cr / settled once" : "26,000 cr / victory fee / no suit award";
        case "location" -> destination == null ? "Agreus / Arcadia" : destination.label();
        case "route" -> progress.completed() ? "North Weir restored. Rusk will contact us when the next contract is ready."
                : destination == null ? AgreusArcCampaign.accessReason() : "Marine operations > Baronet Elian Rusk / North Weir";
        case "preparation" -> progress.completed() ? "Confirmed employer losses: " + progress.techniciansLost() + " technicians, " + progress.militiaLost() + " constables, " + progress.repairLoadsLost() + " loads."
                : "Vale's departure reference, forwarded by Mara. Two squads, fitted boats and an owned working tender required. Replenish before leaving Corvus; no deadline.";
        case "courseDisabled" -> destination == null;
        case "courseAction" -> plotCourse;
        default -> throw new IllegalArgumentException("Unknown Agreus guide property: " + property);
    }; }
}
