package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;

/** Authored Wayline chapter text and progress, derived from existing victory facts. */
public final class WaylineArc {
    public static final String TITLE = "WAYLINE / AN OPEN ROAD";
    public enum Stage { RELIEF, DEPOT, COMPLETE }

    private WaylineArc() { }

    public static Stage stage(MarineRoster roster) {
        if (roster != null && roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) {
            return Stage.COMPLETE;
        }
        return roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)
                ? Stage.DEPOT : Stage.RELIEF;
    }

    public static String progress(MarineRoster roster) {
        return switch (stage(roster)) {
            case RELIEF -> "CHAPTER 1 / 2 — RELIEF";
            case DEPOT -> "RELIEF COMPLETE / CHAPTER 2 — THE DEPOT";
            case COMPLETE -> "RELIEF COMPLETE / DEPOT COMPLETE";
        };
    }

    public static String introduction(MarineRoster roster) {
        return switch (stage(roster)) {
            case RELIEF -> "Wayline is a freight cooperative, not a government. Our drivers pooled their savings "
                    + "for militia; the raiders took our depot anyway. Help break their assault, then help us take it back.";
            case DEPOT -> "The assault is broken. Recovered traffic logs put the raiders at our freight depot. "
                    + "One more job: clear it, so our drivers can go back to earning a living.";
            case COMPLETE -> "The depot is ours again. Dispatch can start moving freight. I've passed your name "
                    + "to the Independent brokers. Our two jobs are closed; these are new postings from other crews.";
        };
    }

    public static String briefing(OpeningOperationKind kind, String planet) {
        String location = planet != null ? " on " + planet : "";
        return OpeningBroker.NAME + ": " + switch (kind) {
            case RELIEF -> "The raiders have our depot; now they're hitting our freight checkpoint" + location
                    + ". Land on the rear unloading apron and reinforce the gate. More raiders are approaching along the freight and warehouse service roads. Wayline provides militia and one shuttle. "
                    + "Eliminate the bandit force to win. Bring as many people home as you can. "
                    + "Cr. 6,000 on success; then we can deal with the depot.";
            case COUNTERATTACK -> "Their assault is broken. Recovered traffic logs identify the occupied Wayline depot"
                    + location + ". Land at the western staging apron and advance with our militia through the freight gate, loading yard and dispatch warehouse. "
                    + "Our Hound, Wayward, is offline in the maintenance berth just beyond the gate. Secure the berth and keep a squad there while they bring its chaingun online. "
                    + "Use it for the push into dispatch; direct control is optional. Win the depot and the walker is yours, including recovery and repair if it is knocked out. "
                    + "Expect gate guards and a reserve deeper inside. Eliminate the bandit force. We provide one shuttle; "
                    + "your company needs lift for two sorties. Cr. 9,000 on success, and I'll vouch for your company "
                    + "with Independent brokers. Let's give our drivers their road back.";
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
        String report;
        if (!outcome.victory) {
            report = kind == OpeningOperationKind.RELIEF
                    ? "The assault is still a threat. Refit before you try again; I can keep this job open."
                    : "The depot is still in raider hands. Get your company ready. The contract remains open.";
        } else {
            report = kind == OpeningOperationKind.RELIEF
                    ? "The assault is broken. Recovered traffic logs point to our occupied depot. "
                        + "Your payment is cleared. Come back for the second job when you're ready."
                    : "The depot is ours again. Dispatch can start moving freight. Your payment is cleared, "
                        + "and Independent brokers now have your name. You gave Wayline an open road. Thank you.";
        }
        if (outcome.alliesEngaged > 0 && outcome.alliesLost >= outcome.alliesEngaged) {
            report += " None of our militia came home. We'll have to rebuild that watch.";
        } else if (outcome.alliesLost > 0) {
            report += " We'll see to the families of the militia we lost.";
        }
        if (outcome.marinesLost > 0) report += " I'm sorry for your company's losses.";
        if (WaylineHoundAward.eligible(outcome)) report += outcome.waylineHound.disabled()
                ? " We've recovered Wayward's wreck and repaired the chassis. The Hound is yours: one chaingun, empty missile mounts. You'll find it in the Mech Lab. We'll arrange heavy lift for your active lance on the supply-route jobs."
                : " Wayward is signed over to your company: one Hound, one chaingun, empty missile mounts. You'll find it in the Mech Lab. We'll arrange heavy lift for your active lance on the supply-route jobs.";
        return report;
    }
}
