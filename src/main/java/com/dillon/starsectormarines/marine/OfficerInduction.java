package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.Arrays;

/** New-company introduction. Null on a roster means a grandfathered company. */
public final class OfficerInduction implements Serializable {
    public static final String MIRA_PORTRAIT = "graphics/portraits/mira-hale.png";

    /** Upgrade only the shipped starter likeness; preserve player-customized portraits. */
    public static void migrateMiraPortrait(MarineRoster roster) {
        for (MarineCaptain officer : roster.all()) {
            if ("Mira Hale".equals(officer.name())
                    && "graphics/portraits/portrait_mercenary01.png".equals(officer.portraitSprite()))
                officer.setPortraitSprite(MIRA_PORTRAIT);
        }
    }

    public enum Step { INTRODUCTION, SPECIALTY, FORMATION, ASSIGNMENT, READY, COMPLETE }

    private final String miraId;
    private boolean introduced;
    private boolean complete;

    public OfficerInduction(String miraId) { this.miraId = miraId; }
    public String miraId() { return miraId; }
    public boolean complete() { return complete; }

    /** A lost/dismissed story officer is never resurrected to repair a tutorial. */
    public MarineCaptain officer(MarineRoster roster) {
        MarineCaptain mira = roster.byId(miraId);
        if (mira != null && mira.status() != Status.KIA) return mira;
        return roster.all().stream().filter(c -> c.status() != Status.KIA).findFirst().orElse(null);
    }

    public void introduce(MarineCaptain officer) {
        if (officer != null && officer.status() != Status.KIA) introduced = true;
    }

    public Step step(MarineRoster roster) {
        if (complete) return Step.COMPLETE;
        MarineCaptain officer = officer(roster);
        if (!introduced || officer == null) return Step.INTRODUCTION;
        if (Arrays.stream(OfficerSpecialty.values()).noneMatch(s -> officer.career().level(s) > 0))
            return Step.SPECIALTY;
        var squads = roster.squads().stream()
                .filter(s -> !s.reserve() && !s.stationed() && roster.readyCount(s) > 0).toList();
        if (squads.isEmpty()) return Step.FORMATION;
        if (squads.stream().noneMatch(s -> officer.id().equals(s.homeCaptainId()))) return Step.ASSIGNMENT;
        return Step.READY;
    }

    /** Acknowledgement cannot replace training or an actual squad assignment. */
    public boolean finish(MarineRoster roster) {
        if (step(roster) != Step.READY) return false;
        complete = true;
        return true;
    }
}
