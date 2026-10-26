package com.dillon.starsectormarines.battle.evacuation;

import java.util.Arrays;

/**
 * How far a bore has grown (`colony-breach-nouns.md`, Bore tier). A bore only
 * ever grows: fissure, then bore, then maw.
 *
 * <p>Each tier carries what the director and the umbrella read off it: how
 * many of each strain the bore holds the field at (its hold), how big and how often its surges
 * are, how many spore colonies it has sprouted by then and of which grade, and
 * how much growth it takes to reach the tier from a fresh fissure.
 *
 * <p>A colony is sprouted at the grade of the tier that grew it and keeps it:
 * a fissure's colony threatens light craft, a maw's reaches further and
 * penetrates enough to hurt gunships and dropships. The grade lives in the
 * colony's weapon (range, damage, penetration), not in a list of targets.
 */
public enum BoreTier {
    /** Runners only, a low rate, a short umbrella. */
    FISSURE("fissure", 0f, new int[]{10, 0, 0, 0, 0}, 6, 30f, 1),
    /** Adds brutes and spitters; the tier a mother bore opens at. */
    BORE("bore", 100f, new int[]{14, 3, 3, 0, 0}, 10, 22f, 2),
    /** Adds the heavy strains, the highest rate and the widest umbrella. */
    MAW("maw", 250f, new int[]{20, 6, 5, 0, 1}, 16, 15f, 3);

    /** The beat suffix, as in {@code bore_grew_N_bore}. */
    public final String beatName;
    /** Growth points from a fresh fissure at which a bore reaches this tier. */
    public final float growthThreshold;
    /** Swarm bodies this bore holds the field at, toward the director's population target: its hold summed. */
    public final int capacity;
    /**
     * The bore's hold (`colony-breach-nouns.md`, Hold): how many of each strain, by {@link BoreStrainTable.Slot}
     * ordinal, a bore at this tier keeps living on the field. Tunes each tier's step in difficulty. The Matriarch is
     * outside it: a mother owes hers whatever her hold.
     */
    private final int[] hold;
    /** Bodies in an unfed surge. */
    public final int surgeSize;
    /** Seconds between surges, before the seeded jitter. */
    public final float surgeIntervalSeconds;
    /** Spore colonies a bore has sprouted in all once it has reached this tier. */
    public final int sporeColonies;

    BoreTier(String beatName, float growthThreshold, int[] hold, int surgeSize,
             float surgeIntervalSeconds, int sporeColonies) {
        this.beatName = beatName;
        this.growthThreshold = growthThreshold;
        this.hold = hold;
        this.capacity = Arrays.stream(hold).sum();
        this.surgeSize = surgeSize;
        this.surgeIntervalSeconds = surgeIntervalSeconds;
        this.sporeColonies = sporeColonies;
    }

    /** How many bodies of {@code slot} a bore at this tier keeps living on the field. */
    public int hold(BoreStrainTable.Slot slot) {
        return hold[slot.ordinal()];
    }

    /** The structure of a spore colony sprouted at this tier. */
    public String sporeColonyStructure() {
        return "structure.spore-colony-" + beatName;
    }

    /** The highest tier whose threshold {@code growth} has reached. */
    public static BoreTier forGrowth(float growth) {
        BoreTier tier = FISSURE;
        for (BoreTier t : values()) if (growth >= t.growthThreshold) tier = t;
        return tier;
    }

    /** The tier after this one, or {@code null} at maw. */
    public BoreTier next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }
}
