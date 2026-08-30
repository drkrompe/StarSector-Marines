package com.dillon.starsectormarines.battle.combat.fx;

/**
 * How a round put onto the ground arrives — the composable property the
 * presentation keys on, deliberately not who delivered it.
 *
 * <p>An aircraft on an attack run is the only carrier that publishes these
 * today, but nothing here says "aircraft": a future orbital gun or a
 * ground-launched aerial delivery publishes the same three kinds and gets the
 * same treatment for free. Consumers filter on the delivery, never on the
 * carrier — the same rule {@code ShotFx} keeps for weapon fire.
 *
 * <p>The three are genuinely different events to watch, which is the whole
 * reason the presentation splits here rather than scaling one effect by
 * calibre: a shell is thrown and lands short of where it was aimed, a beam
 * arrives the instant it is fired and is dragged, and a bomb falls for long
 * enough that you can watch it come down.
 */
public enum OrdnanceDelivery {
    /** A thrown round: a streak in flight, a crater and a kick of dirt at the end. */
    SHELL,
    /** Energy on the ground the moment it is released: a line, and a scorch where it touches. */
    BEAM,
    /** A heavy body released and falling: no muzzle, a long drop, and a blast. */
    BOMB
}
