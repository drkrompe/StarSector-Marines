package com.dillon.starsectormarines.battle.creep;

/**
 * What standing on creep does to the battle. Kept few on purpose: the swarm runs faster over its own ground, and
 * colonists will not run onto it to hide. Marines and colonists are never slowed, so the creep reads as the swarm's
 * advantage rather than a mud everyone wades through.
 */
public final class CreepEffects {

    /** A swarm body's ground speed on living creep, against bare ground. */
    public static final float SWARM_SPEED_ON_CREEP = 1.25f;

    private CreepEffects() { }

    /** Whether the cell under a point in cells is living creep; {@code false} with no field. */
    public static boolean onCreep(CreepField field, float x, float y) {
        return field != null && field.isCreeped((int) Math.floor(x), (int) Math.floor(y));
    }

    /** Ground speed multiplier for a body at a point: the creep's boost for the swarm, nothing for anyone else. */
    public static float groundSpeed(CreepField field, boolean swarm, float x, float y) {
        return swarm && onCreep(field, x, y) ? SWARM_SPEED_ON_CREEP : 1f;
    }

    /** Whether a colonist refuses a cell as somewhere to run to or hide: living creep is never refuge. */
    public static boolean refusedAsRefuge(CreepField field, int x, int y) {
        return field != null && field.isCreeped(x, y);
    }
}
