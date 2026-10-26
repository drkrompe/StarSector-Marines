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
 * A finite prepaid consignment, held at one planet rather than in the ship's hold: credit the yard
 * holds against the first boat, and the first squad's stores.
 */
public final class PreparationDepot implements Serializable {
    private static final long serialVersionUID = 1L;
    /** The materials a boat was once billed in; on an older save they are turned into {@link #credits}. */
    private static final List<String> BOAT_MATERIALS = List.of("metals", "heavy_machinery", "rare_metals");
    private String marketId;
    private final Map<String, Integer> stock = new LinkedHashMap<>();
    // Null on legacy saves: their remaining stock keeps its original unrestricted policy.
    private Map<String, Integer> boatStock;
    private Map<String, Integer> squadStock;
    /** Prepaid yard credit for the first boat. Null on a save from before boats were priced in credits. */
    private Integer credits;
    public enum Purpose { GENERAL, BOAT, SQUAD, PREFLIGHT }

    public PreparationDepot(String marketId) {
        this(marketId, false);
    }
    public PreparationDepot(String marketId, boolean protectedStock) {
        this.marketId = marketId;
        if (protectedStock) { boatStock = new LinkedHashMap<>(); squadStock = new LinkedHashMap<>(); }
        credits = BoatFabricationCatalog.recipe(ShuttleType.HERMES).bill().credits();
        for (var line : SquadFoundingCost.STANDARD.lines())
            (protectedStock ? squadStock : stock).merge(line.commodityId(), line.quantity(), Integer::sum);
    }

    /**
     * An older depot held the first boat's materials. They are worth what they were billed at
     * ({@link FabricationCost#baseValue}), so they become yard credit: all of a protected depot's
     * boat stores, and an unrestricted depot's metals, machinery and transplutonics (its supplies
     * also feed the squad, so they stay supplies).
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
        return this;
    }

    public String marketId() { return marketId; }
    /** Moves the remaining stores to another port, for a depot whose own port was lost. Nothing is added. */
    public void relocate(String marketId) {
        if (marketId == null || marketId.isBlank()) throw new IllegalArgumentException("market id is required");
        this.marketId = marketId;
    }
    public boolean protectedStock() { return boatStock != null && squadStock != null; }
    public int available(String commodity) { return stock().getOrDefault(commodity, 0); }
    /** The commodity stores; the yard credit is {@link #credits()}. */
    public Map<String, Integer> stock() {
        if (!protectedStock()) return Map.copyOf(stock);
        var total = new LinkedHashMap<>(boatStock);
        squadStock.forEach((id, quantity) -> total.merge(id, quantity, Integer::sum));
        return Map.copyOf(total);
    }
    /** The yard credit still held for the first boat. */
    public int credits() { return credits == null ? 0 : credits; }
    /** Whether anything at all is left: credit or stores. */
    public boolean holdsAnything() {
        return credits() > 0 || stock().values().stream().anyMatch(quantity -> quantity > 0);
    }
    private Map<String, Integer> stockFor(Purpose purpose) {
        if (!protectedStock()) return stock;
        return switch (purpose) {
            case BOAT -> boatStock;
            case SQUAD -> squadStock;
            case GENERAL, PREFLIGHT -> Map.of();
        };
    }
    public int available(String commodity, Purpose purpose) {
        return purpose == Purpose.PREFLIGHT ? available(commodity) : stockFor(purpose).getOrDefault(commodity, 0);
    }

    /** The yard credit a job of {@code purpose} may draw: the boat's alone once the depot protects it. */
    public int credits(Purpose purpose) {
        if (purpose == Purpose.PREFLIGHT || purpose == Purpose.BOAT) return credits();
        return protectedStock() || purpose == Purpose.SQUAD ? 0 : credits();
    }

    /** Draws up to {@code requested} yard credit; the caller first validates the whole price. */
    public int drawCredits(int requested, Purpose purpose) {
        if (purpose == Purpose.PREFLIGHT) return 0;
        int drawn = Math.min(Math.max(0, requested), credits(purpose));
        if (drawn > 0) credits -= drawn;
        return drawn;
    }

    /** Caller first validates the whole combined depot/cargo bill. */
    public int draw(String commodity, int requested) {
        return draw(commodity, requested, Purpose.GENERAL);
    }
    public int draw(String commodity, int requested, Purpose purpose) {
        if (purpose == Purpose.PREFLIGHT) return 0;
        var source = stockFor(purpose);
        int drawn = Math.min(Math.max(0, requested), source.getOrDefault(commodity, 0));
        if (drawn > 0) source.put(commodity, source.get(commodity) - drawn);
        return drawn;
    }
}
