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
            case RELIEF -> "Mara Venn. Castle sent your particulars, and I've read them twice. I broker for Wayline, and since "
                    + "the spring I run it, because our director took a post off-world and somebody had to. A year ago we ran freighters "
                    + "between worlds. We lost three cargoes in a season and sold the hulls to cover them, so now we're drivers on one "
                    + "planet's roads, pooling what's left so nobody has to sell their truck as well.";
            case DEPOT -> "The assault is broken. Recovered traffic logs put the raiders at our freight depot. "
                    + "You have bought us breathing room. Now clear the depot, so our drivers can go back to earning a living.";
            case COMPLETE -> "The depot is ours again. Dispatch can start moving freight. I've passed your name "
                    + "to the Independent brokers. When we met, I was trying to keep Wayline from coming apart. You gave us somewhere to rebuild. These are new postings from other crews.";
        };
    }

    public static String briefing(OpeningOperationKind kind, String planet) {
        String location = planet != null ? " on " + planet : "";
        return switch (kind) {
            // Relief's briefing is Mara's recorded introduction; this is the unvoiced summary beside it,
            // and her tactical calls are radio traffic in the mission itself.
            case RELIEF -> "Land on the rear unloading apron at the freight checkpoint" + location + " and reinforce the gate. "
                    + "Raiders will come up the freight and warehouse service roads. Wayline supplies its militia and one shuttle. "
                    + "Break the assault and bring as many people home as you can.";
            case COUNTERATTACK -> OpeningBroker.NAME + ": Their assault is broken. Recovered traffic logs identify the occupied Wayline depot"
                    + location + ". Land at the western staging apron and advance with our militia through the freight gate, loading yard and dispatch warehouse. "
                    + "Our Hound, Wayward, is offline in the maintenance berth just beyond the gate. Secure the berth and keep a squad there while they bring its chaingun online. "
                    + "Use it for the push into dispatch and the defense that follows; direct control is optional. Win the depot and the walker is yours, including recovery and repair if it is knocked out. "
                    + "Clear the warehouse and hold company units inside for eight seconds. Then defend dispatch against two attacks entering along both service roads. Defeat the finite raider force and secure the warehouse to win. We provide one shuttle; "
                    + "your company needs lift for two sorties. You'll be paid on success, and I'll vouch for your company "
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
                    ? "Castle sent the report. This isn't how either of us wanted our first contract to go. The assault is still a threat, "
                        + "so I can't authorize the fee. Get your company in order before you try again; I can keep this job open."
                    : "The depot is still in raider hands. Get your company ready. The contract remains open.";
        } else {
            report = kind == OpeningOperationKind.RELIEF
                    ? "The assault is broken. Recovered traffic logs point to our occupied depot. "
                        + "Your first Wayline payment is cleared. I put your company's name on the authorization myself. "
                        + "Castle has our line-support and scout-screen specs; choose one transfer in Barracks Equipment, with free fitting in your Machine Shop. "
                        + "We can talk about the depot when you're ready. I'd rather keep working with the people who answered this call."
                    : "The depot is ours again. Dispatch can start moving freight. Your payment is cleared, "
                        + "and Independent brokers now have your name. We have a place to load, keep records and organize work again. One thing I can't account for: they left the strongroom alone and took the dispatch board, every route and every schedule on it. You gave Wayline an open road. Take time to put your own company in order; you have earned more than another emergency call.";
        }
        if (outcome.alliesEngaged > 0 && outcome.alliesLost >= outcome.alliesEngaged) {
            report += " None of our militia came home. We'll have to rebuild that watch.";
        } else if (outcome.alliesLost > 0) {
            report += " We'll see to the families of the militia we lost.";
        }
        if (outcome.marinesLost > 0) report += " I'm sorry for your company's losses.";
        if (WaylineHoundAward.eligible(outcome)) report +=
                " Castle has filed Wayward's transfer: one repaired Hound and its chaingun, with empty missile mounts. Castle is waiting in the Mech Lab with a spare SRM-5 Frag rack and a fitting walkthrough. The chassis is in your Mech Bay; Wayline supplies heavy lift on the supply-route jobs.";
        return report;
    }
}
