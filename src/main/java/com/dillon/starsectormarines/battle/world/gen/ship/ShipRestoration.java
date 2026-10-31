package com.dillon.starsectormarines.battle.world.gen.ship;

import java.util.EnumSet;
import java.util.Set;

/**
 * Original-hull facilities and distribution circuits; independent of vanilla hull damage.
 *
 * <p>{@code openReserves} names the hull's refit compartments that have been
 * opened: furnished and entered rather than sealed. Every other reserve stays
 * closed, which is the default. Opening one changes only how its compartment is
 * treated, never where anything on the deck is (see {@link ReservedFacility}).
 */
public record ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power,
                              boolean messHall, int additionalMechBerths, Set<ReservedFacility> openReserves) {
    public static final ShipRestoration NONE = new ShipRestoration(false, true, true, true, true);
    public static final ShipRestoration WRECK = new ShipRestoration(true, false, false, false, false);

    /** Berths the Hound's recovery brings back together, so a second machine can be bought straight away. */
    public static final int RECOVERED_MECH_BERTHS = 2;

    public ShipRestoration {
        if (additionalMechBerths < 0 || additionalMechBerths > 4 - RECOVERED_MECH_BERTHS)
            throw new IllegalArgumentException("A lance has two paid berths after its recovered pair");
        openReserves = openReserves == null || openReserves.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(openReserves));
    }

    public ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power,
                           boolean messHall, int additionalMechBerths) {
        this(applies, mechBay, billets, power, messHall, additionalMechBerths, Set.of());
    }

    public ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power, boolean messHall) {
        this(applies, mechBay, billets, power, messHall, 0);
    }

    /** The first two berths belong to the Hound recovery; subsequent repairs never bypass it. */
    public int workingMechBerths() { return applies ? (mechBay ? RECOVERED_MECH_BERTHS + additionalMechBerths : 0) : 4; }

    public ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power) {
        this(applies, mechBay, billets, power, true);
    }

    /**
     * The same hull with exactly these reserves open and the rest sealed. The
     * campaign's facility tracks call this with the reserves whose Restored tier
     * has been bought.
     */
    public ShipRestoration withOpenReserves(Set<ReservedFacility> open) {
        return new ShipRestoration(applies, mechBay, billets, power, messHall, additionalMechBerths, open);
    }

    /** Whether this reserve has been opened; a reserve never named is sealed. */
    public boolean opens(ReservedFacility facility) {
        return facility != null && openReserves.contains(facility);
    }
}
