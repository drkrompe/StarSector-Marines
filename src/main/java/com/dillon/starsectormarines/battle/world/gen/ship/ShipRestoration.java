package com.dillon.starsectormarines.battle.world.gen.ship;

/** Original-hull facilities and distribution circuits; independent of vanilla hull damage. */
public record ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power,
                              boolean messHall, int additionalMechBerths) {
    public static final ShipRestoration NONE = new ShipRestoration(false, true, true, true, true);
    public static final ShipRestoration WRECK = new ShipRestoration(true, false, false, false, false);

    public ShipRestoration {
        if (additionalMechBerths < 0 || additionalMechBerths > 3)
            throw new IllegalArgumentException("A lance has three paid berths after its recovery berth");
    }

    public ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power, boolean messHall) {
        this(applies, mechBay, billets, power, messHall, 0);
    }

    /** The first berth belongs to the Hound recovery; subsequent repairs never bypass it. */
    public int workingMechBerths() { return applies ? (mechBay ? 1 + additionalMechBerths : 0) : 4; }

    public ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power) {
        this(applies, mechBay, billets, power, true);
    }
}
