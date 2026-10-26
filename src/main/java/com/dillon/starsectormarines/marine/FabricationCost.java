package com.dillon.starsectormarines.marine;

import java.util.List;

/**
 * What one workshop commit costs, in credits.
 *
 * <p>The seam is the company's purse rather than any one room's: a mech's chassis, a weapon
 * assembly and a boat's plating are all paid for out of the same account, and the price has
 * never known which bench it was standing at.
 *
 * <p>Prices were material bills once; they were converted at the vanilla base price of each
 * commodity ({@link #SUPPLIES}, {@link #HEAVY_MACHINERY}, {@link #METALS}, {@link #TRANSPLUTONICS}, {@link #FOOD}
 * credits a unit), so the balance stayed where it was.
 */
public record FabricationCost(int credits) {

    /** The base prices old material bills and legacy prepaid stores convert at. */
    public static final int SUPPLIES = 100;
    public static final int HEAVY_MACHINERY = 150;
    public static final int METALS = 30;
    public static final int TRANSPLUTONICS = 200;
    public static final int FOOD = 20;

    public FabricationCost {
        if (credits <= 0) throw new IllegalArgumentException("a fabrication cost is a positive credit price");
    }

    /** The price, or null for nothing to pay. */
    public static FabricationCost of(long credits) {
        return credits <= 0 ? null : new FabricationCost((int) Math.min(Integer.MAX_VALUE, credits));
    }

    /** {@code numerator / denominator} of the price, rounded down; null when that comes to nothing. */
    public FabricationCost share(int numerator, int denominator) {
        return of((long) credits * numerator / denominator);
    }

    /** The price times {@code factor}, rounded up and never below one credit. */
    public FabricationCost scaled(float factor) {
        return new FabricationCost((int) Math.max(1, Math.ceil(credits * (double) factor - 1e-4)));
    }

    /** The prices together, or zero for none; nulls count as nothing. */
    public static long total(List<FabricationCost> costs) {
        long sum = 0;
        for (FabricationCost cost : costs) if (cost != null) sum += cost.credits();
        return sum;
    }

    /** What {@code quantity} units of a vanilla commodity were worth as a bill; zero for one no bill used. */
    public static long baseValue(String commodityId, int quantity) {
        int price = switch (commodityId == null ? "" : commodityId) {
            case "supplies" -> SUPPLIES;
            case "heavy_machinery" -> HEAVY_MACHINERY;
            case "metals" -> METALS;
            case "rare_metals" -> TRANSPLUTONICS;
            case "food" -> FOOD;
            default -> 0;
        };
        return (long) price * Math.max(0, quantity);
    }
}
