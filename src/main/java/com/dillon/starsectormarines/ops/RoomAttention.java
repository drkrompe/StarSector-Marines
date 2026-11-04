package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MechBay;

/**
 * Whether a room has something waiting on the player, for the mark on its page route: wounded or empty billets in
 * the line squads, a shuttle that cannot fly, a machine under repair or stripped. Read-only, and it says only where
 * to look; the room says what.
 */
final class RoomAttention {
    private RoomAttention() { }

    static boolean needed(MarineOpsPageNav.Page page, MarineRoster roster, float day) {
        if (roster == null) return false;
        return switch (page) {
            case BARRACKS -> barracks(roster);
            case BOAT_DECK -> shuttles(roster.boatDeck(), day);
            case MECH_LAB -> machines(roster.mechBay(), day);
            default -> false;
        };
    }

    private static boolean barracks(MarineRoster roster) {
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve() || squad.stationed()) continue;
            if (roster.vacancies(squad) > 0) return true;
            for (MarineSoldier soldier : roster.squadMembers(squad)) {
                if (soldier.status() == MarineSoldierStatus.WIA) return true;
            }
        }
        return false;
    }

    /** A boat that cannot fly, or berths with no boat that can; an empty berth beside a flying boat is a choice. */
    private static boolean shuttles(BoatDeck deck, float day) {
        for (CampaignBoat boat : deck.boats()) {
            if (boat != null && !boat.flightReady(day)) return true;
        }
        return deck.berths() > 0 && deck.flightReady(day).isEmpty();
    }

    private static boolean machines(MechBay bay, float day) {
        if (!bay.heldBack(day).isEmpty()) return true;
        for (CampaignMechSquad squad : bay.squads()) {
            for (CampaignMech mech : squad.mechs()) {
                if (mech.repairDaysRemaining(day) > 0f) return true;
            }
        }
        return false;
    }
}
