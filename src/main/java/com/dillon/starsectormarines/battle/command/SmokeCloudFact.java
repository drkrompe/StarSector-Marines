package com.dillon.starsectormarines.battle.command;

/**
 * A cloud of smoke standing on the field: public ground whoever threw it, since a side sees the smoke between itself and a gun
 * as plainly as the gun's side does. Read once per pulse for the plans that depend on a screen holding.
 *
 * @param x                centre of the cloud, in cells
 * @param y                centre of the cloud, in cells
 * @param radius           how far from the centre it blocks, in cells
 * @param remainingSeconds how much longer it stands
 */
public record SmokeCloudFact(float x, float y, float radius, float remainingSeconds) { }
