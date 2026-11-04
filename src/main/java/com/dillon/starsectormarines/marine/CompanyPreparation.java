package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.List;

/** What the first mission still has to pay for, derived from the real workshop catalogs and the free first orders. */
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
     * The first lift's price in credits: zero while the first boat is free, or once a compatible boat is airworthy.
     * The squad comes first; the boat waits until after Relief. Compatible owned boats count.
     */
    public static int boatPrice(MarineRoster roster, ShuttleType pattern) {
        if (roster.boatDeck().firstBoatFree()) return 0;
        if (roster.boatDeck().flightReady().stream().anyMatch(b -> b.pattern() == pattern)) return 0;
        BoatFabricationCatalog.Recipe recipe = BoatFabricationCatalog.recipe(pattern);
        return recipe == null ? 0 : recipe.bill().credits();
    }

    /** The founding squad's marines, or nothing while the first squad is free or once a full squad stands on the roll. */
    public static List<SquadFoundingCost.Line> squadBill(MarineRoster roster) {
        return hasSquad(roster) || roster.firstSquadFree() ? List.of() : SquadFoundingCost.STANDARD.lines();
    }

    /** The founding squad's price for its stores, or zero while the first squad is free or once a full squad stands on the roll. */
    public static int squadPrice(MarineRoster roster) {
        return hasSquad(roster) || roster.firstSquadFree() ? 0 : SquadFoundingCost.STANDARD.credits();
    }

    /**
     * Whether everything the first mission still needs can be paid: the boat and the squad's stores in credits, the
     * squad's marines from cargo. A new company owes nothing here, so only a berth can stop it; a company from an
     * older save may still be paying.
     */
    public static boolean canPrepare(MarineRoster roster, ShuttleType pattern, int berths,
                                     FabricationResources credits, SquadFoundingResources stores) {
        return pattern != null && berths > 0
                && credits.credits() >= (long) boatPrice(roster, pattern) + squadPrice(roster)
                && squadBill(roster).stream().allMatch(line -> stores.available(line.commodityId()) >= line.quantity());
    }
}
