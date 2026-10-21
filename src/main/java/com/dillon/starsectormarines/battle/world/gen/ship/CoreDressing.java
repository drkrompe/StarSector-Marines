package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * Which of the ship's cores still show their Deficit look on the deck.
 *
 * <p>Presentation input only. A core's tier is campaign state owned by the
 * ship-condition record; this is the one fact the deck's dressing reads from
 * it: whether each core has reached Restored. Dressing is state laid over the
 * fixed form, so nothing here moves a room, a door, a berth or a lane, and the
 * same answer always dresses the same cells. See {@code second-wind-refit-tree.md}.
 *
 * <p>Fabrication is not here: its Deficit is the buried workshop, which the
 * workshop chain already draws.
 */
public record CoreDressing(boolean power, boolean lifeSupport, boolean structure, boolean compute) {

    /** Every core restored: the clean interior, and every hull that is not the founding one. */
    public static final CoreDressing RESTORED = new CoreDressing(true, true, true, true);
    /** The battered start, every core at Deficit. */
    public static final CoreDressing DEFICIT = new CoreDressing(false, false, false, false);

    /** One of the dressed cores. */
    public enum Core { POWER, LIFE_SUPPORT, STRUCTURE, COMPUTE }

    public boolean restored(Core core) {
        return switch (core) {
            case POWER -> power;
            case LIFE_SUPPORT -> lifeSupport;
            case STRUCTURE -> structure;
            case COMPUTE -> compute;
        };
    }

    public CoreDressing with(Core core, boolean restored) {
        return new CoreDressing(core == Core.POWER ? restored : power,
                core == Core.LIFE_SUPPORT ? restored : lifeSupport,
                core == Core.STRUCTURE ? restored : structure,
                core == Core.COMPUTE ? restored : compute);
    }

    public boolean clean() { return power && lifeSupport && structure && compute; }

    /**
     * The seam the ship-condition record feeds: each core's tier read as
     * Restored or not.
     *
     * <p>Until that record owns core tiers, the original restoration hull shows
     * every core at Deficit except Power, whose Restored tier is the existing
     * distribution-circuits order. Any other hull, and the founding hull without
     * its restoration condition, is clean.
     */
    public static CoreDressing of(CompanyShip ship) {
        ShipRestoration state = ship == null ? null : ship.restoration();
        if (state == null || !state.applies()) return RESTORED;
        return DEFICIT.with(Core.POWER, state.power());
    }
}
