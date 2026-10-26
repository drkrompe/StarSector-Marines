package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.List;

/** Remaining first-mission stores, derived from the real workshop catalogs. */
public final class CompanyPreparation {
    private CompanyPreparation() { }

    public static boolean required(MarineRoster roster) {
        return roster != null && roster.officerInductionRequired()
                && roster.officerInduction().companyPreparation();
    }

    public static boolean hasSquad(MarineRoster roster) {
        return roster.squads().stream().anyMatch(s -> !s.reserve() && !s.stationed()
                && roster.readyCount(s) == MarineSquad.CAPACITY);
    }

    /**
     * The first lift's price in credits, or zero once a compatible boat is airworthy. The squad comes first; the
     * boat waits until after Relief. Compatible owned boats count.
     */
    public static int boatPrice(MarineRoster roster, ShuttleType pattern) {
        if (roster.boatDeck().flightReady().stream().anyMatch(b -> b.pattern() == pattern)) return 0;
        BoatFabricationCatalog.Recipe recipe = BoatFabricationCatalog.recipe(pattern);
        return recipe == null ? 0 : recipe.bill().credits();
    }

    /** The founding squad's stores, or nothing once a full squad stands on the roll. */
    public static List<SquadFoundingCost.Line> squadBill(MarineRoster roster) {
        return hasSquad(roster) ? List.of() : SquadFoundingCost.STANDARD.lines();
    }

    /** Whether everything the prepaid stores still have to build can be paid: the boat in credits, the squad in stores. */
    public static boolean canPrepare(MarineRoster roster, ShuttleType pattern, int berths,
                                     FabricationResources credits, SquadFoundingResources stores) {
        return pattern != null && berths > 0 && credits.credits() >= boatPrice(roster, pattern)
                && squadBill(roster).stream().allMatch(line -> stores.available(line.commodityId()) >= line.quantity());
    }
}
