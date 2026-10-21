package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * How far each facility fitted into an existing room has been built, as the
 * deck shows it: hydroponics in the provisions store, the wardroom at one end
 * of the mess and the briefing room at the other, and racking in the hold.
 *
 * <p>Presentation input only, the sibling of {@link CoreDressing}. A facility's
 * tier is campaign state owned by the ship-condition record; this is what the
 * deck reads from it. Each value counts the tiers bought: 0 nothing, 1
 * Restored, 2 Improved I, 3 Improved II. The pieces are planned once from the
 * deck and its seed for every tier, and a tier only chooses how many of them
 * are drawn, so buying one never moves a piece that was already there, and
 * nothing here moves a room, a door, a berth or a lane. See
 * {@code second-wind-refit-tree.md}.
 */
public record FacilityDressing(int hydroponics, int wardroom, int briefing, int holds) {

    /** The facilities fitted into rooms already on the deck, and the room each dresses. */
    public enum Facility { HYDROPONICS, WARDROOM, BRIEFING, HOLDS }

    /** The highest tier a facility can show. */
    public static final int TOP = 3;

    /** Nothing bought: every hull but the founding one, and her battered start. */
    public static final FacilityDressing NONE = new FacilityDressing(0, 0, 0, 0);

    public FacilityDressing {
        hydroponics = clamp(hydroponics);
        wardroom = clamp(wardroom);
        briefing = clamp(briefing);
        holds = clamp(holds);
    }

    /** Every facility at the same tier. */
    public static FacilityDressing uniform(int tier) {
        return new FacilityDressing(tier, tier, tier, tier);
    }

    public int tier(Facility facility) {
        return switch (facility) {
            case HYDROPONICS -> hydroponics;
            case WARDROOM -> wardroom;
            case BRIEFING -> briefing;
            case HOLDS -> holds;
        };
    }

    public boolean none() { return hydroponics == 0 && wardroom == 0 && briefing == 0 && holds == 0; }

    private static int clamp(int tier) { return Math.max(0, Math.min(TOP, tier)); }
}
