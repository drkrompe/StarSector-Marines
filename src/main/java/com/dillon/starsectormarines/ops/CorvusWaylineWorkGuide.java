package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/** HQ reads the same destination and progression as the local offer board. */
record CorvusWaylineWorkGuide(CorvusWaylineProgress progress, OpeningBroker.Destination destination,
                              Runnable plotCourse) implements MarkupPropertySource {
    @Override public Object markupProperty(String property) {
        CorvusChapter chapter = progress.chapter();
        return switch (property) {
            case "classes" -> "opening-guide console-readout" + (destination == null ? " opening-unavailable" : "");
            case "crest" -> OpeningBroker.CREST;
            case "portrait" -> OpeningBroker.PORTRAIT;
            case "broker" -> OpeningBroker.NAME;
            case "affiliation" -> Strings.get("corvusGuideAffiliation");
            case "heading" -> CorvusWaylineCampaign.title();
            case "mission" -> chapter != null ? chapter.title() + (destination == null ? Strings.get("corvusGuidePausedSuffix") : "")
                    : Strings.get(progress.complete() ? "corvusGuideAccountClosed" : "corvusGuideOperationsPaused");
            case "payment" -> chapter != null ? Strings.format("corvusGuideChapterPayment", chapter.payment)
                    : Strings.format("corvusGuideContractPayments", progress.paid());
            case "location" -> destination != null ? destination.label()
                    : chapter != null ? Strings.format("corvusGuideLocation", chapter.planetName)
                    : Strings.get("corvusGuideRegion");
            case "route" -> chapter == null ? Strings.get("corvusGuideRouteLocal")
                    : destination == null ? Strings.format("corvusGuideRoutePaused", chapter.planetName)
                    : Strings.format("corvusGuideRouteBoard", OpeningBroker.NAME);
            case "preparation" -> chapter != null ? Strings.format("corvusGuidePreparation", chapter.squads)
                    : Strings.get(progress.complete() ? "corvusGuideBroken" : "corvusGuideUnrecognized");
            case "courseDisabled" -> destination == null;
            case "courseAction" -> plotCourse;
            default -> throw new IllegalArgumentException("Unknown Corvus guidance property: " + property);
        };
    }
}
