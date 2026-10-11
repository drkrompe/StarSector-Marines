package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/** Read-only HQ navigation and persistent story dossier. */
record SupplyRouteWorkGuide(BrokenSupplyRouteProgress progress, OpeningBroker.Destination destination,
                            Runnable plotCourse) implements MarkupPropertySource {
    @Override public Object markupProperty(String property) {
        boolean recovery = progress != null && progress.readable() && progress.branch() == BrokenSupplyRouteProgress.LIFT;
        String mission = progress == null ? "Open the Junction" : progress.choicePending() ? "Choose local support"
                : progress.closed() ? progress.successful() ? recovery ? "Crew Recovery" : "Route Security" : "Last Lift / closed"
                : recovery ? "Last Lift" : progress.branch() == BrokenSupplyRouteProgress.RELAY ? "Relay Nine" : "Open the Junction";
        return switch (property) {
            case "classes" -> "opening-guide console-readout" + (destination == null ? " opening-unavailable" : "");
            case "crest" -> OpeningBroker.CREST;
            case "portrait" -> recovery ? SupplyRouteCampaign.IONA_PORTRAIT : OpeningBroker.PORTRAIT;
            case "broker" -> recovery ? "Iona Serr" : OpeningBroker.NAME;
            case "affiliation" -> "Wayline cooperative";
            case "heading" -> "BROKEN SUPPLY ROUTE";
            case "mission" -> mission;
            case "payment" -> progress != null && progress.closed() ? "Story paid: " + progress.storyPayment() + " cr"
                    : recovery ? "4,000 + 500 / recovered" : progress != null && progress.branch() == BrokenSupplyRouteProgress.RELAY ? "Cr. 16,000" : "Cr. 12,000 / Junction";
            case "location" -> progress != null ? progress.planetName() + " / " + progress.systemName()
                    : destination == null ? "CONTACT UNAVAILABLE" : destination.label();
            case "route" -> destination == null ? "Access unavailable. Accepted work stays at its original site."
                    : "Marine operations > " + OpeningBroker.NAME;
            case "preparation" -> progress == null ? "Venn introduces dispatch coordinator Iona Serr. Open their dossier to begin."
                    : progress.closed() && recovery ? "Rill: " + progress.storyCrew().rescuedCount() + " rescued / "
                            + progress.storyCrew().deadCount() + " dead / " + progress.storyCrew().stranded().size()
                            + " stranded. Relay job not undertaken; route suspended."
                    : progress.status();
            case "courseDisabled" -> destination == null;
            case "courseAction" -> plotCourse;
            default -> throw new IllegalArgumentException("Unknown supply-route guidance property: " + property);
        };
    }
}
