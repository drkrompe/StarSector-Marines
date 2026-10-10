package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.text.NumberFormat;

/** HQ lead to the same contact location used by local mission eligibility. */
record OpeningWorkGuide(boolean visible, OpeningOperationKind kind, String preparation,
                        OpeningBroker.Destination destination, Runnable plotCourse)
        implements MarkupPropertySource {
    static OpeningWorkGuide from(MarineRoster roster) {
        return from(roster, OpeningBroker.current(), () -> { });
    }

    static OpeningWorkGuide from(MarineRoster roster, OpeningBroker.Destination destination, Runnable plotCourse) {
        if (roster == null || roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) {
            return new OpeningWorkGuide(false, OpeningOperationKind.RELIEF, "", destination, plotCourse);
        }
        boolean reliefWon = roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId);
        boolean hasSquad = roster.squads().stream().anyMatch(squad -> !squad.reserve());
        String preparation = !hasSquad
                ? "Found and equip your first squad in Armory before deployment."
                : reliefWon ? "Wayline's line held. Return to Venn for the depot job."
                : "Bring ready marines and ship's boats. Venn provides militia support.";
        return new OpeningWorkGuide(true, reliefWon ? OpeningOperationKind.COUNTERATTACK
                : OpeningOperationKind.RELIEF, preparation, destination, plotCourse);
    }

    @Override public Object markupProperty(String property) {
        return switch (property) {
            case "classes" -> visible ? "opening-guide console-readout" + (destination == null ? " opening-unavailable" : "") : "opening-guide-hidden";
            case "crest" -> OpeningBroker.CREST;
            case "portrait" -> OpeningBroker.PORTRAIT;
            case "broker" -> OpeningBroker.NAME;
            case "affiliation" -> "Independent broker";
            case "heading" -> kind == OpeningOperationKind.RELIEF ? "FIRST CONTRACT" : "THE DEPOT JOB";
            case "mission" -> kind.displayName;
            case "payment" -> "Cr. " + NumberFormat.getIntegerInstance().format(kind.payout);
            case "location" -> destination == null ? "CONTACT UNAVAILABLE" : destination.label();
            case "route" -> destination == null
                    ? "No accessible planetary office. Check back when a port is available."
                    : "Marine operations > " + OpeningBroker.NAME;
            case "preparation" -> preparation;
            case "courseDisabled" -> !visible || destination == null;
            case "courseAction" -> plotCourse;
            default -> throw new IllegalArgumentException("Unknown opening-work guidance property: " + property);
        };
    }
}
