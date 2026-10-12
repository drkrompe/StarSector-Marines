package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
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
            case "affiliation" -> "Wayline cooperative";
            case "heading" -> CorvusWaylineCampaign.TITLE;
            case "mission" -> chapter != null ? chapter.title : progress.complete() ? "Corvus account closed" : "Operations paused";
            case "payment" -> chapter != null ? "Cr. " + chapter.payment : "Contract payments: " + progress.paid() + " cr";
            case "location" -> destination != null ? destination.label()
                    : chapter != null ? chapter.planetName + " / Corvus" : "Corvus";
            case "route" -> chapter == null ? "Local Wayline arrangements remain at the original supply-route site."
                    : destination == null ? "Destination unavailable. No substitute site has been assigned."
                    : "Marine operations > " + OpeningBroker.NAME;
            case "preparation" -> chapter != null ? chapter.squads + " squads recommended / owned mech lance supported."
                    : progress.complete() ? "Sable Wake's operation against Wayline has been broken." : "Unrecognized story record.";
            case "courseDisabled" -> destination == null;
            case "courseAction" -> plotCourse;
            default -> throw new IllegalArgumentException("Unknown Corvus guidance property: " + property);
        };
    }
}
