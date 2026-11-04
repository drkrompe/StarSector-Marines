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
}
