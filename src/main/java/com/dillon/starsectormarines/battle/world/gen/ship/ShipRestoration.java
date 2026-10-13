package com.dillon.starsectormarines.battle.world.gen.ship;

/** Original-hull facilities and distribution circuits; independent of vanilla hull damage. */
public record ShipRestoration(boolean applies, boolean mechBay, boolean billets, boolean power) {
    public static final ShipRestoration NONE = new ShipRestoration(false, true, true, true);
    public static final ShipRestoration WRECK = new ShipRestoration(true, false, false, false);
}
