package com.dillon.starsectormarines.ops.loot;

import java.util.ArrayList;
import java.util.List;

/** Pure capacity allocator and 75%-fence calculator for selected salvage. */
public final class LootSettlementPlanner {

    public static final int FENCE_PERCENT = 75;
    private static final String FUEL_ID = "fuel";
    private static final String MARINES_ID = "marines";
    private static final float EPSILON = 0.0001f;

    private LootSettlementPlanner() {}

    public static LootSettlementPlan plan(LootSelection selection,
                                          LootCapacitySnapshot capacity) {
        if (selection == null) return new LootSettlementPlan(null);
        LootCapacitySnapshot safeCapacity = capacity != null
                ? capacity
                : new LootCapacitySnapshot(0f, 0f, 0f);
        float cargoLeft = safeCapacity.cargo;
        float fuelLeft = safeCapacity.fuel;
        float personnelLeft = safeCapacity.personnel;
        List<LootSettlementLine> lines = new ArrayList<>();

        for (int index : selection.selectedIndices()) {
            LootStack stack = selection.manifest().stacks.get(index);
            LootCapacityBucket bucket = bucketOf(stack);
            float unitSpace = unitSpace(stack, bucket);
            float available;
            switch (bucket) {
                case FUEL:      available = fuelLeft; break;
                case PERSONNEL: available = personnelLeft; break;
                case CARGO:
                default:        available = cargoLeft; break;
            }
            int fits = unitSpace <= 0f
                    ? stack.quantity
                    : Math.max(0, (int) Math.floor((available + EPSILON) / unitSpace));
            int kept = Math.min(stack.quantity, fits);
            int fenced = stack.quantity - kept;
            int fencedCredits = fenceCredits(stack.unitValue, fenced);
            lines.add(new LootSettlementLine(index, stack, bucket, kept, fenced, fencedCredits));

            float consumed = kept * unitSpace;
            switch (bucket) {
                case FUEL:      fuelLeft = Math.max(0f, fuelLeft - consumed); break;
                case PERSONNEL: personnelLeft = Math.max(0f, personnelLeft - consumed); break;
                case CARGO:
                default:        cargoLeft = Math.max(0f, cargoLeft - consumed); break;
            }
        }
        return new LootSettlementPlan(lines);
    }

    static LootCapacityBucket bucketOf(LootStack stack) {
        if (stack.kind == LootKind.COMMODITY && FUEL_ID.equals(stack.itemId)) {
            return LootCapacityBucket.FUEL;
        }
        if (stack.kind == LootKind.COMMODITY && MARINES_ID.equals(stack.itemId)) {
            return LootCapacityBucket.PERSONNEL;
        }
        return LootCapacityBucket.CARGO;
    }

    private static float unitSpace(LootStack stack, LootCapacityBucket bucket) {
        return bucket == LootCapacityBucket.CARGO ? stack.cargoPerUnit : 1f;
    }

    private static int fenceCredits(int unitValue, int quantity) {
        long baseValue = (long) unitValue * quantity;
        long credits = baseValue * FENCE_PERCENT / 100L;
        return credits > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) credits;
    }
}
