package com.dillon.starsectormarines.battle.command;

/**
 * A cloud of smoke standing on the field: public ground whoever threw it, since a side sees the smoke between itself and a gun
 * as plainly as the gun's side does. Read once per pulse for the plans that depend on a screen holding, and for the
 * {@link SmokePartition} of sight lines the standing screens cut.
 *
 * <p>The fact is what the cloud <em>holds</em>, not all that is left of it: a long-lasting screen thins over its last stretch
 * (`SmokeFieldService`), and a plan counts on it only at the radius it was thrown at for as long as it keeps it.
 *
 * @param x                centre of the cloud, in cells
 * @param y                centre of the cloud, in cells
 * @param radius           how far from the centre it blocks while it is whole, in cells
 * @param remainingSeconds how much longer it stands whole (zero once it has begun to thin)
 */
public record SmokeCloudFact(float x, float y, float radius, float remainingSeconds) { }
