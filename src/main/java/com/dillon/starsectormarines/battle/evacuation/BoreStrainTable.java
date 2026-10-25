package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which strain a bore's surge is made of, keyed by tier
 * (`colony-breach-nouns.md`, Strains by tier).
 *
 * <p>Runners at fissure; brutes and spitters join at bore tier; maw weights
 * toward the heavier strains. The {@link Slot#MATRIARCH} never wins a draw: a
 * mother bore owes one the first time it releases at maw tier, and that body is
 * the Matriarch whatever the roll ({@link #pick(BoreTier, long, boolean)}). The {@link Slot#BOMBARD} is drawn at maw
 * tier only, at a small weight, and at most one lives per bore: a draw that lands on it while the bore's is alive
 * falls to the other strains ({@link #pick(BoreTier, long, boolean, boolean)}). Every body the table picks is spawned
 * through {@link SwarmStrains}, so it is issued its strain's kit.
 */
public final class BoreStrainTable {

    /** One strain a surge may contain. */
    public enum Slot {
        RUNNER(UnitType.SWARM_RUNNER),
        BRUTE(UnitType.SWARM_BRUTE),
        SPITTER(UnitType.SWARM_SPITTER),
        /** The mother bore's heavy strain: one per mother, owed at maw tier, never drawn. */
        MATRIARCH(UnitType.SWARM_MATRIARCH),
        /** The long arm: maw tier only, one living per bore. */
        BOMBARD(UnitType.SWARM_BOMBARD);

        /** The body this slot spawns, or {@code null} for a slot not yet filled. */
        public final UnitType unitType;

        Slot(UnitType unitType) { this.unitType = unitType; }
    }

    private static final Map<BoreTier, int[]> WEIGHTS = new EnumMap<>(BoreTier.class);

    static {
        // RUNNER, BRUTE, SPITTER, MATRIARCH, BOMBARD
        WEIGHTS.put(BoreTier.FISSURE, new int[]{1, 0, 0, 0, 0});
        WEIGHTS.put(BoreTier.BORE, new int[]{6, 2, 2, 0, 0});
        WEIGHTS.put(BoreTier.MAW, new int[]{5, 3, 2, 0, 1});
    }

    private BoreStrainTable() {}

    /** The slot's weight at a tier; zero for a slot that never spawns there. */
    public static int weight(BoreTier tier, Slot slot) {
        return slot.unitType == null ? 0 : WEIGHTS.get(tier)[slot.ordinal()];
    }

    /**
     * Whether a bore releasing at {@code tier} owes its Matriarch: a mother at maw tier that has not
     * released one yet.
     */
    public static boolean owesMatriarch(boolean mother, BoreTier tier, boolean matriarchReleased) {
        return mother && tier == BoreTier.MAW && !matriarchReleased;
    }

    /** {@link #pick(BoreTier, long)}, unless the bore owes its Matriarch, which then comes first. */
    public static Slot pick(BoreTier tier, long roll, boolean owesMatriarch) {
        return owesMatriarch ? Slot.MATRIARCH : pick(tier, roll);
    }

    /**
     * {@link #pick(BoreTier, long, boolean)}, with the Bombard out of the draw unless {@code bombardFree}: a bore
     * whose Bombard is alive, or already in this surge, draws the same roll over the other strains.
     */
    public static Slot pick(BoreTier tier, long roll, boolean owesMatriarch, boolean bombardFree) {
        if (owesMatriarch) return Slot.MATRIARCH;
        return pick(tier, roll, bombardFree ? null : Slot.BOMBARD);
    }

    /**
     * The slot a draw of {@code roll} picks at {@code tier}: the weights laid
     * end to end and {@code roll} reduced into them, so the same roll always
     * picks the same strain.
     */
    public static Slot pick(BoreTier tier, long roll) {
        return pick(tier, roll, null);
    }

    private static Slot pick(BoreTier tier, long roll, Slot excluded) {
        int total = 0;
        for (Slot slot : Slot.values()) if (slot != excluded) total += weight(tier, slot);
        long at = Math.floorMod(roll, (long) total);
        for (Slot slot : Slot.values()) {
            if (slot == excluded) continue;
            at -= weight(tier, slot);
            if (at < 0) return slot;
        }
        throw new IllegalStateException("no strain at " + tier);
    }
}
