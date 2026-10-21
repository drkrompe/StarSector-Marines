package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/** Read-only HQ navigation and persistent story dossier. */
record SupplyRouteWorkGuide(BrokenSupplyRouteProgress progress, OpeningBroker.Destination destination,
                            Runnable plotCourse) implements MarkupPropertySource {
    @Override public Object markupProperty(String property) {
        String mission = Strings.get(progress == null || !progress.junctionWon() ? "supplyRouteJunctionName"
                : progress.closed() ? progress.successful() ? "supplyRouteSecurityName" : "supplyRouteThunderClosed"
                : "supplyRouteThunderName");
        return switch (property) {
            case "classes" -> "opening-guide console-readout" + (destination == null ? " opening-unavailable" : "");
            case "crest" -> OpeningBroker.CREST;
            case "portrait" -> OpeningBroker.PORTRAIT;
            case "broker" -> OpeningBroker.NAME;
            case "affiliation" -> Strings.get("supplyRouteGuideAffiliation");
            case "heading" -> Strings.get("supplyRouteGuideHeading");
            case "mission" -> mission;
            case "payment" -> progress != null && progress.closed()
                    ? Strings.format("supplyRouteGuidePaymentClosed", progress.storyPayment())
                    : Strings.get(progress != null && progress.operation() == BrokenSupplyRouteProgress.THUNDER
                            ? "supplyRouteGuidePaymentThunder" : "supplyRouteGuidePaymentJunction");
            case "location" -> progress != null
                    ? Strings.format("supplyRouteGuideLocation", progress.planetName(), progress.systemName())
                    : destination == null ? Strings.get("supplyRouteGuideUnavailable") : destination.label();
            case "route" -> destination == null ? Strings.get("supplyRouteGuideRouteBlocked")
                    : Strings.format("supplyRouteGuideRoute", OpeningBroker.NAME);
            case "preparation" -> progress == null ? Strings.get("supplyRouteGuideIntroduction")
                    : progress.status();
            case "courseDisabled" -> destination == null;
            case "courseAction" -> plotCourse;
            default -> throw new IllegalArgumentException("Unknown supply-route guidance property: " + property);
        };
    }
}
