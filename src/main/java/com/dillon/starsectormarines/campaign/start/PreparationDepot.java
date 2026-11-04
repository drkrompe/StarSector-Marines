package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFabricationCatalog;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.SquadFoundingCost;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retired: the prepaid stores an older save still holds for the first boat and the first squad. No
 * new company gets one, since those two orders are now simply free ({@code MarineRoster.firstSquadFree},
 * {@code BoatDeck.firstBoatFree}). The class stays under this name and with these fields because saves
 * name both; {@link CerberusCompanyStart#settleOldDepot} reads one once, through {@link #settle}, and
 * drops it.
 */
public final class PreparationDepot implements Serializable {
    private static final long serialVersionUID = 1L;
    /** The materials a boat was once billed in; on an older save they are turned into {@link #credits}. */
    private static final List<String> BOAT_MATERIALS = List.of("metals", "heavy_machinery", "rare_metals");
    /** The stores a squad was once billed in; on an older save they are turned into {@link #squadCredits}. */
    private static final List<String> SQUAD_STORES = List.of("supplies", "food");
    private String marketId;
    private final Map<String, Integer> stock = new LinkedHashMap<>();
    // Null on legacy saves: their remaining stock had an unrestricted policy.
    private Map<String, Integer> boatStock;
    private Map<String, Integer> squadStock;
    /** Prepaid yard credit for the first boat. Null on a save from before boats were priced in credits. */
    private Integer credits;
    /** Prepaid credit for the first squad's stores. Null on a save from before squads were priced in credits. */
    private Integer squadCredits;

    /** A depot as an older save holds it, whole; saves are the only real source, so this is for tests. */
    PreparationDepot(String marketId, boolean protectedStock) {
        this.marketId = marketId;
        if (protectedStock) { boatStock = new LinkedHashMap<>(); squadStock = new LinkedHashMap<>(); }
        credits = hermesPrice();
        squadCredits = SquadFoundingCost.STANDARD.credits();
        for (var line : SquadFoundingCost.STANDARD.lines())
            (protectedStock ? squadStock : stock).merge(line.commodityId(), line.quantity(), Integer::sum);
    }

    /**
     * An older depot held the first boat's materials. They are worth what they were billed at
     * ({@link FabricationCost#baseValue}), so they become yard credit: all of a protected depot's
     * boat stores, and an unrestricted depot's metals, machinery and transplutonics. Likewise the
     * first squad's supplies and food become its squad credit; its marines stay marines.
     */
    private Object readResolve() {
        if (credits == null) {
            long value = 0;
            if (boatStock != null) {
                for (var line : boatStock.entrySet()) value += FabricationCost.baseValue(line.getKey(), line.getValue());
                boatStock.clear();
            } else {
                for (String material : BOAT_MATERIALS) {
                    Integer held = stock.remove(material);
                    if (held != null) value += FabricationCost.baseValue(material, held);
                }
            }
            credits = (int) Math.min(Integer.MAX_VALUE, value);
        }
        if (squadCredits == null) {
            Map<String, Integer> source = squadStock != null ? squadStock : stock;
            long value = 0;
            for (String material : SQUAD_STORES) {
                Integer held = source.remove(material);
                if (held != null) value += FabricationCost.baseValue(material, held);
            }
            squadCredits = (int) Math.min(Integer.MAX_VALUE, value);
        }
        return this;
    }

    public String marketId() { return marketId; }

    /**
     * What this depot is worth to the company now: a whole allotment still held becomes that free order, and
     * whatever else is left comes aboard, so nothing is lost and nothing can be spent twice. A squad's allotment
     * is its marines and its credit; a boat's is the credit for one Hermes.
     */
    Settlement settle() {
        Map<String, Integer> goods = new LinkedHashMap<>(stock);
        if (boatStock != null) boatStock.forEach((id, quantity) -> goods.merge(id, quantity, Integer::sum));
        if (squadStock != null) squadStock.forEach((id, quantity) -> goods.merge(id, quantity, Integer::sum));

        int forBoat = credits == null ? 0 : credits;
        boolean freeBoat = forBoat >= hermesPrice();
        long cash = freeBoat ? forBoat - hermesPrice() : forBoat;

        int forSquad = squadCredits == null ? 0 : squadCredits;
        boolean freeSquad = forSquad >= SquadFoundingCost.STANDARD.credits()
                && SquadFoundingCost.STANDARD.lines().stream()
                        .allMatch(line -> goods.getOrDefault(line.commodityId(), 0) >= line.quantity());
        cash += freeSquad ? forSquad - SquadFoundingCost.STANDARD.credits() : forSquad;
        if (freeSquad) SquadFoundingCost.STANDARD.lines()
                .forEach(line -> goods.merge(line.commodityId(), -line.quantity(), Integer::sum));

        goods.values().removeIf(quantity -> quantity <= 0);
        return new Settlement(freeSquad, freeBoat, (int) Math.min(Integer.MAX_VALUE, cash), Map.copyOf(goods));
    }

    private static int hermesPrice() {
        return BoatFabricationCatalog.recipe(ShuttleType.HERMES).bill().credits();
    }

    /** One depot's worth: the free orders it becomes, and the credits and goods that come aboard. */
    record Settlement(boolean freeSquad, boolean freeBoat, int credits, Map<String, Integer> goods) { }
}
