package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * Everything the prepaid stores still have to build: the founding squad's kit, and one reusable boat for the
     * first ferry job. The squad comes first; the boat waits until after Relief. Compatible owned boats count.
     */
    public static List<FabricationCost.Line> remainingBill(MarineRoster roster, ShuttleType pattern) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (var line : boatBill(roster, pattern)) quantities.merge(line.commodityId(), line.quantity(), Integer::sum);
        for (var line : squadBill(roster)) quantities.merge(line.commodityId(), line.quantity(), Integer::sum);
        return lines(quantities);
    }

    /** The first lift's materials, or nothing once a compatible boat is airworthy. */
    public static List<FabricationCost.Line> boatBill(MarineRoster roster, ShuttleType pattern) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        if (roster.boatDeck().flightReady().stream().noneMatch(b -> b.pattern() == pattern)) {
            BoatFabricationCatalog.Recipe recipe = BoatFabricationCatalog.recipe(pattern);
            if (recipe != null) for (var line : recipe.bill().lines())
                quantities.merge(line.commodityId(), line.quantity(), Integer::sum);
        }
        return lines(quantities);
    }

    /** The founding squad's kit, or nothing once a full squad stands on the roll. */
    public static List<FabricationCost.Line> squadBill(MarineRoster roster) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        if (!hasSquad(roster)) for (var line : SquadFoundingCost.STANDARD.lines())
            quantities.merge(line.commodityId(), line.quantity(), Integer::sum);
        return lines(quantities);
    }

    private static List<FabricationCost.Line> lines(Map<String, Integer> quantities) {
        return quantities.entrySet().stream()
                .map(e -> new FabricationCost.Line(e.getKey(), e.getValue())).toList();
    }

    public static boolean canPrepare(MarineRoster roster, ShuttleType pattern, int berths,
                                     FabricationResources resources) {
        return pattern != null && berths > 0 && remainingBill(roster, pattern).stream()
                .allMatch(line -> resources.available(line.commodityId()) >= line.quantity());
    }
}
