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
        String requirements = SupplyRouteCampaign.isBattle(mission) || CorvusWaylineCampaign.isBattle(mission) || ShepherdRecoveryCampaign.isBattle(mission)
                ? "The launch minimum is two squads with four ready marines each."
                : "Check the briefing's deployment requirements before committing.";
        StringBuilder copy = new StringBuilder("Commander, we are taking on larger jobs. This operation recommends ")
                .append(MissionForceEnvelope.recommendedPersonnel(mission)).append(" marines. ").append(requirements)
                .append("\n\nAboard: ").append(full).append(full == 1 ? " full squad / " : " full squads / ").append(ready).append(" ready marines.\n")
                .append(full >= 2 ? "You already have two full squads; no new squad is needed for this lesson."
                        : formations >= 2 ? "You already have two formations. Fill their vacant billets or let wounded soldiers recover before raising an extra squad."
                        : "Buy missing Marines, supplies and food at a market, then open Armory and choose Found Line Squad.")
                .append("\n\nOne new squad / Cost / Available / Buy\n");
        for (SquadFoundingCost.Line line : SquadFoundingCost.STANDARD.lines()) {
            int available = stores.available(line.commodityId());
            copy.append(stores.commodityName(line.commodityId())).append(" / ").append(line.quantity())
                    .append(" / ").append(available).append(" / ").append(Math.max(0, line.quantity() - available)).append("\n");
        }
        copy.append("\nFounding issues twelve soldiers and their starter kit. Other weapon or armor upgrades have a separate bill.\n\nIn Barracks, select the squads and click Assign Selected on their commander's card. Then return here and Assign Squads to include both in this deployment. Wounded soldiers keep their billets; fill vacancies or wait for recovery.");
        return new PreparationGuide.Lesson(3, "FROM ONE SQUAD TO TWO", copy.toString(),
                List.of("mission-prepare-armory", "mission-prepare-barracks", "mission-briefing-back"),
                "UNDERSTOOD", acknowledge, 480);
    }
}
