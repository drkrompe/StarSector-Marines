package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One round in flight from a warship's mount to the ground.
 *
 * <p>The ship owns it from the muzzle to the landing, so there is one clock for
 * what the player watches and what the ground suffers: the round is drawn
 * crossing the distance, and lands — damage, breach and blast together — on the
 * tick it arrives. The weapon id is the simulation's fact about which gun fired;
 * what that gun's round looks like is the renderer's business.
 */
public final class WarshipShell {

    public final VanillaWeapon weapon;
    /** The side that fired it. */
    public final Faction faction;
    public final float fromX;
    public final float fromY;
    public final float toX;
    public final float toY;
    public final float flightSeconds;
    /** The blast the round makes, in cells. */
    public final float blastCells;
    private final PendingDetonation landing;
    private float elapsed;
    /** Set when point defence has shot the round down; it is dropped without landing. */
    boolean intercepted;

    WarshipShell(VanillaWeapon weapon, Faction faction, float fromX, float fromY, float toX, float toY,
                 float flightSeconds, float blastCells, PendingDetonation landing) {
        this.weapon = weapon;
        this.faction = faction;
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.flightSeconds = Math.max(flightSeconds, 0.001f);
        this.blastCells = blastCells;
        this.landing = landing;
    }

    /** How far along the flight, 0 at the muzzle to 1 on the ground. */
    public float progress() {
        return Math.min(1f, elapsed / flightSeconds);
    }

    /** World X of the round now. */
    public float x() {
        return fromX + (toX - fromX) * progress();
    }

    /** World Y of the round now. */
    public float y() {
        return fromY + (toY - fromY) * progress();
    }

    /** Cells per second along the flight. */
    float speedCells() {
        float dx = toX - fromX;
        float dy = toY - fromY;
        return (float) Math.sqrt(dx * dx + dy * dy) / flightSeconds;
    }

    boolean advance(float dt) {
        elapsed += dt;
        return elapsed >= flightSeconds;
    }

    PendingDetonation landing() {
        return landing;
    }
}
