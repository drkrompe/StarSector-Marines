package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.Set;

/** Exact production deployment counts for the selected and whole line roster. */
public record PersonnelReadiness(int requiredSeats,
                                 int selectedReady,
                                 int companyReady,
                                 int selectedShortfall,
                                 int companyShortfall) {

    public static PersonnelReadiness assess(MarineRoster roster,
                                            Set<String> selectedSquadIds,
                                            int requiredSeats) {
        return assess(roster, selectedSquadIds, requiredSeats, false);
    }

    /** Captain-authorized selection; an empty set deliberately contributes zero seats. */
    public static PersonnelReadiness assessSelection(MarineRoster roster,
                                                     Set<String> selectedSquadIds,
                                                     int requiredSeats) {
        return assess(roster, selectedSquadIds, requiredSeats, true);
    }

    private static PersonnelReadiness assess(MarineRoster roster,
                                             Set<String> selectedSquadIds,
                                             int requiredSeats,
                                             boolean explicitSelection) {
        int required = Math.max(0, requiredSeats);
        int company = roster != null ? roster.lineReadySoldiers().size() : 0;
        int selected = explicitSelection
                ? selectedReady(roster, selectedSquadIds)
                : selectedSquadIds != null && !selectedSquadIds.isEmpty()
                        ? selectedReady(roster, selectedSquadIds) : company;
        return new PersonnelReadiness(required, selected, company,
                Math.max(0, required - selected),
                Math.max(0, required - company));
    }

    public boolean ready() {
        return selectedShortfall == 0;
    }

    public boolean needsRecruitment() {
        return companyShortfall > 0;
    }

    private static int selectedReady(MarineRoster roster, Set<String> selectedSquadIds) {
        if (roster == null) return 0;
        int ready = 0;
        for (String squadId : selectedSquadIds) {
            MarineSquad squad = roster.squadById(squadId);
            if (squad != null && !squad.reserve()) ready += roster.readyCount(squad);
        }
        return ready;
    }
}
