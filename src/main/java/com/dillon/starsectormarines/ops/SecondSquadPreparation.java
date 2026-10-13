package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadFoundingCost;
import com.dillon.starsectormarines.marine.SquadFoundingResources;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;

import java.util.List;

/** Read-only preparation advice; acknowledgement never creates or equips personnel. */
final class SecondSquadPreparation {
    private SecondSquadPreparation() { }

    static boolean relevant(Mission mission, MarineRoster roster) {
        return mission != null && roster != null && !mission.source.isDebug()
                && !roster.officerInductionRequired()
                && MissionForceEnvelope.recommendedPersonnel(mission) >= 2 * MarineSquad.CAPACITY;
    }

    static PreparationGuide.Lesson lesson(Mission mission, MarineRoster roster,
                                         SquadFoundingResources stores, boolean replay, Runnable acknowledge) {
        if (!relevant(mission, roster) || roster.secondSquadPreparationSeen() && !replay) return null;
        int full = (int) roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()
                && roster.readyCount(s) == MarineSquad.CAPACITY).count();
        long formations = roster.squads().stream().filter(s -> !s.reserve() && !s.stationed()).count();
        int ready = roster.lineReadySoldiers().size();
        String requirements = LessonCopy.text(SupplyRouteCampaign.isBattle(mission) || CorvusWaylineCampaign.isBattle(mission) || ShepherdRecoveryCampaign.isBattle(mission) || AgreusArcCampaign.isBattle(mission)
                ? "lessonSecondMinimum" : "lessonSecondCheck");
        String advice = LessonCopy.text(full >= 2 ? "lessonSecondFull"
                : formations >= 2 ? "lessonSecondExisting" : "lessonSecondFound");
        StringBuilder costs = new StringBuilder();
        for (SquadFoundingCost.Line line : SquadFoundingCost.STANDARD.lines()) {
            int available = stores.available(line.commodityId());
            costs.append(CompanyResources.namedBill(stores.commodityName(line.commodityId()),
                    line.quantity(), available)).append("\n");
        }
        return new PreparationGuide.Lesson(3, LessonCopy.text("lessonSecondTitle"),
                LessonCopy.text("lessonSecondSummary", MissionForceEnvelope.recommendedPersonnel(mission),
                        requirements, full, ready, advice, costs.toString()),
                List.of("mission-prepare-armory", "mission-prepare-barracks", "mission-briefing-back"),
                LessonCopy.text("lessonUnderstood"), acknowledge, 480);
    }
}
