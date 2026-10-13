package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.CompanyPreparation;
import com.dillon.starsectormarines.marine.MarineRoster;

/** Every campaign entrance resumes the same saved first-company preparation. */
final class CompanyPreparationRoute {
    private CompanyPreparationRoute() { }

    static ScreenId resolve(ScreenId requested, MarineRoster roster, boolean debugMission) {
        if (requested == null || !CompanyPreparation.required(roster)) return requested;
        if (debugMission && (requested == ScreenId.BRIEFING || requested == ScreenId.SQUAD_DEPLOYMENT))
            return requested;
        return switch (requested) {
            case BARRACKS, SHIP_TRANSFER, SHIP_VIEW, FLEET_ARMORY_OVERVIEW, FLEET_ARMORY,
                    MECH_LAB, BOAT_DECK, MISSION_SELECT, BRIEFING, SQUAD_DEPLOYMENT ->
                    switch (roster.officerInduction().step(roster)) {
                        case SHIP -> ScreenId.SHIP_TRANSFER;
                        case LIFT -> ScreenId.BOAT_DECK;
                        case FORMATION -> requested == ScreenId.FLEET_ARMORY
                                ? ScreenId.FLEET_ARMORY : ScreenId.BARRACKS;
                        default -> ScreenId.BARRACKS;
                    };
            // HQ already hosts Castle's handoff. Battle settlement and developer tools
            // must never be interrupted by an unrelated campaign preparation state.
            default -> requested;
        };
    }
}
