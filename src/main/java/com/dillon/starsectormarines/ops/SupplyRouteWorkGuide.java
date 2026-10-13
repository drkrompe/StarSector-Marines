package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/** Read-only HQ navigation and persistent story dossier. */
record SupplyRouteWorkGuide(BrokenSupplyRouteProgress progress, OpeningBroker.Destination destination,
                            Runnable plotCourse) implements MarkupPropertySource {
    @Override public Object markupProperty(String property) {
        String mission = progress == null || !progress.junctionWon() ? "Open the Junction"
                : progress.closed() ? progress.successful() ? "Route Security" : "Thunder Run / closed"
                : "Thunder Run";
        return switch (property) {
            case "classes" -> "opening-guide console-readout" + (destination == null ? " opening-unavailable" : "");
            case "crest" -> OpeningBroker.CREST;
            case "portrait" -> OpeningBroker.PORTRAIT;
            case "broker" -> OpeningBroker.NAME;
            case "affiliation" -> "Wayline cooperative";
            case "heading" -> "BROKEN SUPPLY ROUTE";
            case "mission" -> mission;
            case "payment" -> progress != null && progress.closed() ? "Contract payments: " + progress.storyPayment() + " cr"
                    : progress != null && progress.operation() == BrokenSupplyRouteProgress.THUNDER ? "6,000 + 4,000 / delivered" : "Cr. 12,000 / Junction";
            case "location" -> progress != null ? progress.planetName() + " / " + progress.systemName()
                    : destination == null ? "CONTACT UNAVAILABLE" : destination.label();
            case "route" -> destination == null ? "Access unavailable. Accepted work stays at its original site."
                    : "Marine operations > " + OpeningBroker.NAME;
            case "preparation" -> progress == null ? "Venn introduces dispatch coordinator Iona Serr. Open their dossier to begin."
                    : progress.status();
            case "courseDisabled" -> destination == null;
            case "courseAction" -> plotCourse;
            default -> throw new IllegalArgumentException("Unknown supply-route guidance property: " + property);
        };
    }
}
