package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;

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
    public final float fromX;
    public final float fromY;
    public final float toX;
    public final float toY;
    public final float flightSeconds;
    /** The blast the round makes, in cells. */
    public final float blastCells;
    private final PendingDetonation landing;
    private float elapsed;

    WarshipShell(VanillaWeapon weapon, float fromX, float fromY, float toX, float toY,
                 float flightSeconds, float blastCells, PendingDetonation landing) {
        this.weapon = weapon;
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

    boolean advance(float dt) {
        elapsed += dt;
        return elapsed >= flightSeconds;
    }

    PendingDetonation landing() {
        return landing;
    }
}
