package com.dillon.starsectormarines.battle;

/**
 * Visual record of a single shot fired by a unit in {@link BattleSimulation}.
 * Emitted on every fire — hit or miss — so the renderer can draw a tracer
 * even when no damage lands. The endpoint is the target's cell on a hit and
 * a randomized near-miss offset on a miss, so the visual reads as a real
 * stray round rather than a dud.
 *
 * <p>Lifetime is in sim seconds, ticked by {@link BattleSimulation#advance}.
 * That keeps shots paused with the rest of the sim, but at 4× speed shots
 * flash by quickly — readability hasn't been a problem in playtest yet.
 */
public class ShotEvent {

    public final float fromX;
    public final float fromY;
    public final float toX;
    public final float toY;
    public final boolean hit;
    public final Faction shooterFaction;

    public float lifetime;

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.hit = hit;
        this.shooterFaction = shooterFaction;
        this.lifetime = lifetime;
    }
}
