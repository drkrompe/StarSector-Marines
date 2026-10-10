package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.text.NumberFormat;

/** HQ directions to authored broker work, derived from existing roster completion. */
record OpeningWorkGuide(boolean visible, OpeningOperationKind kind, String preparation)
        implements MarkupPropertySource {
    static OpeningWorkGuide from(MarineRoster roster) {
        if (roster == null || roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) {
            return new OpeningWorkGuide(false, OpeningOperationKind.RELIEF, "");
        }
        boolean reliefWon = roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId);
        boolean hasSquad = roster.squads().stream().anyMatch(squad -> !squad.reserve());
        String preparation = !hasSquad
                ? "Choose a company ship, then found and equip your first squad in Armory."
                : reliefWon ? "Relief completed. The depot job is unlocked; Relief remains repeatable."
                : "Bring ready marines and ship's boats. The briefing checks personnel and lift.";
        return new OpeningWorkGuide(true, reliefWon ? OpeningOperationKind.COUNTERATTACK
                : OpeningOperationKind.RELIEF, preparation);
    }

    @Override public Object markupProperty(String property) {
        return switch (property) {
            case "classes" -> visible ? "opening-guide console-readout" : "opening-guide-hidden";
            case "crest" -> "graphics/factions/crest_neutral_traders.png";
            case "heading" -> kind == OpeningOperationKind.RELIEF ? "FIRST CONTRACT" : "FOLLOW-UP WORK";
            case "mission" -> kind.displayName;
            case "payment" -> "Cr. " + NumberFormat.getIntegerInstance().format(kind.payout) + "  /  repeatable";
            case "route" -> "Visit a populated planet. Choose Marine operations, then the independent client.";
            case "preparation" -> preparation;
            default -> throw new IllegalArgumentException("Unknown opening-work guidance property: " + property);
        };
    }
}
