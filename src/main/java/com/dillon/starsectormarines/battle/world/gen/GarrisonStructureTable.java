package com.dillon.starsectormarines.battle.world.gen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each market structure adds to a garrison's mix, keyed by vanilla industry id. Data rather than code: the
 * shipped table is {@code data/world/garrison/garrison_structures.json}, read once by the launch boundary, so a
 * structure added by another mod can be given a contribution without touching Java.
 *
 * <p>An industry the table does not name contributes nothing. That is the whole compatibility rule: a modded
 * industry can never crash the estimate or invent defenders.
 *
 * @param contributions the per-industry contributions; copied, never null
 */
public record GarrisonStructureTable(Map<String, Contribution> contributions) {

    /** A table that knows no structures: every garrison is its size's base mix. */
    public static final GarrisonStructureTable EMPTY = new GarrisonStructureTable(Map.of());

    public GarrisonStructureTable {
        contributions = contributions == null || contributions.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(contributions));
    }

    /** The contribution of one industry, {@link Contribution#NONE} when the table does not name it. */
    public Contribution of(String industryId) {
        if (industryId == null) return Contribution.NONE;
        return contributions.getOrDefault(industryId, Contribution.NONE);
    }

    /**
     * What one structure adds to the garrison's weights, in the same units as the size base (one part bulk beside a
     * quarter or a half part elite). Weights, not headcount: the garrison's strength comes from the market's
     * ground-defence value, and these only decide how professional and how heavy that strength is.
     */
    public record Contribution(float bulk, float elite, float heavy) {

        public static final Contribution NONE = new Contribution(0f, 0f, 0f);

        public Contribution {
            bulk = nonNegative(bulk);
            elite = nonNegative(elite);
            heavy = nonNegative(heavy);
        }

        private static float nonNegative(float value) {
            return Float.isNaN(value) || value < 0f ? 0f : value;
        }
    }
}
