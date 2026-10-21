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
            // Relief's briefing is Mara's recorded introduction; this is the unvoiced summary beside it,
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
        boolean relief = kind == OpeningOperationKind.RELIEF;
        String key = (relief ? "waylineDebriefRelief" : "waylineDebriefDepot") + (outcome.victory ? "Won" : "Lost");
        String allies = "";
        if (outcome.alliesEngaged > 0 && outcome.alliesLost >= outcome.alliesEngaged) {
            allies = Strings.get("waylineDebriefAlliesAllLost");
        } else if (outcome.alliesLost > 0) {
            allies = Strings.get("waylineDebriefAlliesSomeLost");
        }
        return Strings.join("waylineSentenceJoin", Strings.get(key), allies,
                outcome.marinesLost > 0 ? Strings.get("waylineDebriefMarinesLost") : "",
                WaylineHoundAward.eligible(outcome) ? Strings.get("waylineDebriefHoundAward") : "");
    }
}
