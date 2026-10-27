package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;

/** Authored Wayline chapter text and progress, derived from existing victory facts. */
public final class WaylineArc {
    public enum Stage { RELIEF, DEPOT, COMPLETE }

    private WaylineArc() { }

    public static String title() {
        return Strings.get("waylineArcTitle");
    }

    public static Stage stage(MarineRoster roster) {
        if (roster != null && roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) {
            return Stage.COMPLETE;
        }
        return roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)
                ? Stage.DEPOT : Stage.RELIEF;
    }

    public static String progress(MarineRoster roster) {
        return switch (stage(roster)) {
            case RELIEF -> Strings.get("waylineProgressRelief");
            case DEPOT -> Strings.get("waylineProgressDepot");
            case COMPLETE -> Strings.get("waylineProgressComplete");
        };
    }

    public static String introduction(MarineRoster roster) {
        return switch (stage(roster)) {
            case RELIEF -> Strings.get("waylineIntroRelief");
            case DEPOT -> Strings.get("waylineIntroDepot");
            case COMPLETE -> Strings.get("waylineIntroComplete");
        };
    }

    public static String briefing(OpeningOperationKind kind, String planet) {
        String location = planet != null ? Strings.format("waylineBriefingOnPlanet", planet) : "";
        return switch (kind) {
            // Relief's briefing is Zoey's recorded introduction; this is the unvoiced summary beside it,
            // and her tactical calls are radio traffic in the mission itself.
            case RELIEF -> Strings.format("waylineBriefingRelief", location);
            case COUNTERATTACK -> Strings.format("waylineBriefingCounterattack", OpeningBroker.NAME, location);
        };
    }

    public static OpeningOperationKind chapter(MissionOutcome outcome) {
        if (outcome == null || outcome.missionSource != MissionSource.STORY) return null;
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            if (kind.missionId.equals(outcome.missionId)) return kind;
        }
        return null;
    }

    public static String debrief(MissionOutcome outcome) {
        OpeningOperationKind kind = chapter(outcome);
        if (kind == null) return "";
        // A defeat is a retry, not an alternate story, so the broker says nothing (owner, 2026-10-27).
        // Castle reports the Hound in its handover (HoundSalvageDebrief), not here.
        if (!outcome.victory) return "";
        return Strings.get(kind == OpeningOperationKind.RELIEF ? "waylineDebriefReliefWon" : "waylineDebriefDepotWon");
    }
}
