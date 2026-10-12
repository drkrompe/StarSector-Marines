/**
 * Actor domain — the drone tender: a hull overhead that builds.
 *
 * <p>Category: system plus data owner. <br>
 * Charter: a tender is a {@code Warship} body (hover flight, a hull table, a
 * shadow) that carries a complement of drones and a small stock of build charges.
 * Its drones defend it and, when it is ordered to, work a build site until a
 * deployable that already exists stands there. <br>
 * Boundary: flight belongs to {@code battle.warship}; the drones are ordinary
 * {@code battle.drone} units and the structure a site becomes is an ordinary
 * {@code battle.turret} entity, so nothing here is a second flight, drone or
 * turret model. See {@code tender-ship.md} in the air roadmap.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.tender;
