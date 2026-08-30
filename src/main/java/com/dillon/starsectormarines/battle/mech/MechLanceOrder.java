package com.dillon.starsectormarines.battle.mech;

/**
 * Battle-local cohesion order shared by every mech in one lance.
 * Doctrine still decides how each member fights; this order decides whether
 * the lance's ordinary formation constraints apply while it does so.
 */
public enum MechLanceOrder {
    /** Preserve the lance's ordinary ally lead clamp and formation steering. */
    FORM_ON_LEAD,
    /** Let each member execute its own doctrine without lance cohesion steering. */
    FREE_REIGN
}
