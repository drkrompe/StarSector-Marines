package com.dillon.starsectormarines.battle.warship;

/**
 * A named refit a mission gives one hull for one fight: its flux pool and shield emitter tuned beyond the variant it
 * was fitted from. It is the seam by which a mission makes a matchup fair without touching the hull table, the
 * variant every other fight reads, or the rules every hull fight shares ({@code warship-behaviours.md}, ship against
 * ship). Only the flux and the shield, what a shielded hull fights on: never a gun, the plate or the hull.
 *
 * <ul>
 *   <li>{@code capacityScale} and {@code dissipationScale} multiply the pool's capacity and dissipation, the
 *       variant's capacitors and vents included.</li>
 *   <li>{@code shieldArcBonus} adds degrees to a shield's arc, never past all round.</li>
 *   <li>{@code shieldUpkeepScale} multiplies the share of dissipation a raised shield draws.</li>
 *   <li>{@code shieldEfficiencyScale} multiplies the flux each absorbed point of damage costs: under one, the
 *       shield turns the same round into less flux.</li>
 * </ul>
 *
 * @param name what the refit is called, for evidence and the debrief
 */
public record HullRefit(String name, float capacityScale, float dissipationScale, float shieldArcBonus,
                        float shieldUpkeepScale, float shieldEfficiencyScale) {

    /** The hull as its variant fits it. */
    public static final HullRefit NONE = new HullRefit("none", 1f, 1f, 0f, 1f, 1f);

    public HullRefit {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a refit is named");
        if (!(capacityScale > 0f && dissipationScale > 0f && shieldEfficiencyScale > 0f)) {
            throw new IllegalArgumentException("refit capacity, dissipation and efficiency scales must be positive");
        }
        if (!(shieldUpkeepScale >= 0f) || !(shieldArcBonus >= 0f)) {
            throw new IllegalArgumentException("refit upkeep scale and arc bonus must not be negative");
        }
    }
}
