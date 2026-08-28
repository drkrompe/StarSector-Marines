/**
 * Actor domain — carrier-placed field objects.
 *
 * <p>Category: actor domain (placed entity + lifecycle service).
 * <br>Charter:  deployables — persistent objects a marine sets down out of the
 *           carried special-equipment slot, rather than throws, plants on a
 *           target, or fires. Today the point-defence emplacement
 *           ({@code DeployedEmplacement}, {@code PointDefenseService}): a
 *           bounded ordnance-denial platform that engages hostile warheads
 *           crossing its radius.
 * <br>Boundary: placement lifecycle and the intercept decision live here. The
 *           placed object's durability, geometry, and art come from the
 *           {@code turret/} structure catalog; the in-flight rounds it engages
 *           are owned by {@code combat/ShotService}; the carrier's channel and
 *           opportunity gate live with the other special-equipment executors in
 *           {@code infantry/}. Interception is not damage — nothing here routes
 *           through the damage pipeline against an engaged round.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.deployable;
