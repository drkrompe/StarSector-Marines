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

    /** One reusable boat suffices for the opening mission; compatible owned boats count. */
    public static List<FabricationCost.Line> remainingBill(MarineRoster roster, ShuttleType pattern) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        if (roster.boatDeck().airworthy().stream().noneMatch(b -> b.pattern() == pattern)) {
            BoatFabricationCatalog.Recipe recipe = BoatFabricationCatalog.recipe(pattern);
            if (recipe != null) for (var line : recipe.bill().lines())
                quantities.merge(line.commodityId(), line.quantity(), Integer::sum);
        }
        if (!hasSquad(roster)) for (var line : SquadFoundingCost.STANDARD.lines())
            quantities.merge(line.commodityId(), line.quantity(), Integer::sum);
        return quantities.entrySet().stream()
                .map(e -> new FabricationCost.Line(e.getKey(), e.getValue())).toList();
    }

    public static boolean canPrepare(MarineRoster roster, ShuttleType pattern, int berths,
                                     FabricationResources resources) {
        return pattern != null && berths > 0 && remainingBill(roster, pattern).stream()
                .allMatch(line -> resources.available(line.commodityId()) >= line.quantity());
    }
}
