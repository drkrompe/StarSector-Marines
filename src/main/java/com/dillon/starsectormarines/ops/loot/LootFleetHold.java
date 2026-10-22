package com.dillon.starsectormarines.ops.loot;

/** Used and total space in each vanilla capacity bucket, read before settlement. */
public record LootFleetHold(float cargoUsed, float cargoMax,
                            float fuelUsed, float fuelMax,
                            float crewUsed, float crewMax) {

    public float used(LootCapacityBucket bucket) {
        return switch (bucket) {
            case FUEL -> fuelUsed;
            case PERSONNEL -> crewUsed;
            case CARGO -> cargoUsed;
        };
    }

    public float max(LootCapacityBucket bucket) {
        return switch (bucket) {
            case FUEL -> fuelMax;
            case PERSONNEL -> crewMax;
            case CARGO -> cargoMax;
        };
    }
}
