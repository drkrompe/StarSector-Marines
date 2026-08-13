package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Whole-fireteam command rules shared by briefing and deployment UI. */
public final class CaptainDeploymentPolicy {

    private CaptainDeploymentPolicy() {}

    /** Roster-ordered home formation, bounded by the captain's current rank. */
    public static List<String> defaultSquadIds(MarineRoster roster,
                                               MarineCaptain captain) {
        if (!canLead(captain) || roster == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (MarineSquad squad : roster.squadsCommandedBy(captain.id())) {
            if (result.size() >= captain.rank().fireteamCap()) break;
            result.add(squad.id());
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean canLead(MarineCaptain captain) {
        return captain != null && captain.status() == Status.ACTIVE;
    }

    public static int selectedCount(MarineRoster roster, Set<String> squadIds) {
        if (roster == null || squadIds == null) return 0;
        int count = 0;
        for (String squadId : squadIds) {
            MarineSquad squad = roster.squadById(squadId);
            if (squad != null && !squad.reserve()) count++;
        }
        return count;
    }

    public static boolean canAdd(MarineRoster roster, MarineCaptain captain,
                                 Set<String> selectedIds, String squadId) {
        if (!canLead(captain) || roster == null || squadId == null) return false;
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.reserve()) return false;
        if (selectedIds != null && selectedIds.contains(squadId)) return true;
        return selectedCount(roster, selectedIds) < captain.rank().fireteamCap();
    }

    public static boolean isValidCommand(MarineRoster roster,
                                         MarineCaptain captain,
                                         Set<String> selectedIds) {
        return canLead(captain)
                && selectedCount(roster, selectedIds) <= captain.rank().fireteamCap();
    }
}
