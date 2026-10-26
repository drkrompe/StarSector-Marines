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
 * holds against the first boat, and the first squad's marines and the credit for its stores.
 */
public final class PreparationDepot implements Serializable {
    private static final long serialVersionUID = 1L;
    /** The materials a boat was once billed in; on an older save they are turned into {@link #credits}. */
    private static final List<String> BOAT_MATERIALS = List.of("metals", "heavy_machinery", "rare_metals");
    /** The stores a squad was once billed in; on an older save they are turned into {@link #squadCredits}. */
    private static final List<String> SQUAD_STORES = List.of("supplies", "food");
    private String marketId;
    private final Map<String, Integer> stock = new LinkedHashMap<>();
    // Null on legacy saves: their remaining stock keeps its original unrestricted policy.
    private Map<String, Integer> boatStock;
    private Map<String, Integer> squadStock;
    /** Prepaid yard credit for the first boat. Null on a save from before boats were priced in credits. */
    private Integer credits;
    /** Prepaid credit for the first squad's stores. Null on a save from before squads were priced in credits. */
    private Integer squadCredits;
    public enum Purpose { GENERAL, BOAT, SQUAD, PREFLIGHT }

    public PreparationDepot(String marketId) {
        this(marketId, false);
    }
    public PreparationDepot(String marketId, boolean protectedStock) {
        this.marketId = marketId;
        if (protectedStock) { boatStock = new LinkedHashMap<>(); squadStock = new LinkedHashMap<>(); }
        credits = BoatFabricationCatalog.recipe(ShuttleType.HERMES).bill().credits();
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
    /** All the prepaid credit still held: the first boat's and the first squad's. */
    public int credits() { return boatCredits() + squadCredits(); }
    private int boatCredits() { return credits == null ? 0 : credits; }
    private int squadCredits() { return squadCredits == null ? 0 : squadCredits; }
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

    /**
     * The prepaid credit a job of {@code purpose} may draw: once the depot protects its stock, the
     * boat's credit is the boat's and the squad's the squad's.
     */
    public int credits(Purpose purpose) {
        return boatShare(purpose) + squadShare(purpose);
    }
    private int boatShare(Purpose purpose) {
        if (purpose == Purpose.PREFLIGHT || purpose == Purpose.BOAT) return boatCredits();
        return protectedStock() || purpose == Purpose.SQUAD ? 0 : boatCredits();
    }
    private int squadShare(Purpose purpose) {
        if (purpose == Purpose.PREFLIGHT || purpose == Purpose.SQUAD) return squadCredits();
        return protectedStock() ? 0 : squadCredits();
    }

    /** Draws up to {@code requested} prepaid credit, its own purpose's first; the caller first validates the whole price. */
    public int drawCredits(int requested, Purpose purpose) {
        if (purpose == Purpose.PREFLIGHT) return 0;
        int wanted = Math.max(0, requested);
        int fromSquad, fromBoat;
        if (purpose == Purpose.SQUAD) {
            fromSquad = Math.min(wanted, squadShare(purpose));
            fromBoat = Math.min(wanted - fromSquad, boatShare(purpose));
        } else {
            fromBoat = Math.min(wanted, boatShare(purpose));
            fromSquad = Math.min(wanted - fromBoat, squadShare(purpose));
        }
        if (fromBoat > 0) credits -= fromBoat;
        if (fromSquad > 0) squadCredits -= fromSquad;
        return fromBoat + fromSquad;
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
