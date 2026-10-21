package com.dillon.starsectormarines.battle.world.gen.ship;

import java.util.List;
import java.util.Set;

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

    /** The cores that carry dressing, in paint and review order. Fabrication's is the workshop chain. */
    public static final List<ShipCore> DRESSED = List.of(ShipCore.POWER, ShipCore.LIFE_SUPPORT,
            ShipCore.STRUCTURE, ShipCore.COMPUTE);

    /** Whether this core's dressing is cleared. Fabrication carries none here, so it always reads restored. */
    public boolean restored(ShipCore core) {
        return switch (core) {
            case POWER -> power;
            case LIFE_SUPPORT -> lifeSupport;
            case STRUCTURE -> structure;
            case COMPUTE -> compute;
            case FABRICATION -> true;
        };
    }

    public CoreDressing with(ShipCore core, boolean restored) {
        return new CoreDressing(core == ShipCore.POWER ? restored : power,
                core == ShipCore.LIFE_SUPPORT ? restored : lifeSupport,
                core == ShipCore.STRUCTURE ? restored : structure,
                core == ShipCore.COMPUTE ? restored : compute);
    }

    /** The dressing for a ship whose restored cores are these: the ship-condition record's answer. */
    public static CoreDressing restored(Set<ShipCore> cores) {
        return new CoreDressing(cores.contains(ShipCore.POWER), cores.contains(ShipCore.LIFE_SUPPORT),
                cores.contains(ShipCore.STRUCTURE), cores.contains(ShipCore.COMPUTE));
    }

    public boolean clean() { return power && lifeSupport && structure && compute; }

    /**
     * The dressing a deck is laid out with before the campaign has said more:
     * the original restoration hull shows every core at Deficit except Power,
     * whose Restored tier is the existing distribution-circuits order. Any other
     * hull, and the founding hull without its restoration condition, is clean.
     * The live deck is then dressed from the ship-condition record through
     * {@link #restored(Set)}.
     */
    public static CoreDressing of(CompanyShip ship) {
        ShipRestoration state = ship == null ? null : ship.restoration();
        if (state == null || !state.applies()) return RESTORED;
        return DEFICIT.with(ShipCore.POWER, state.power());
    }
}
