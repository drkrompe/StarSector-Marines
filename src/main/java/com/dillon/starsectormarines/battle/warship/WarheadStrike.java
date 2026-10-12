package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One warhead a carrier has released and that has not yet detonated: a committed
 * detonation counting down to its aim point, drawn crossing from the split point
 * to it. It is presentation of a landing that was decided when the round was
 * fired; nothing here is steered, and nothing can intercept it.
 */
public final class WarheadStrike {

    public final VanillaWeapon weapon;
    public final Faction faction;
    public final float releaseX;
    public final float releaseY;
    public final float aimX;
    public final float aimY;
    public final float visualCells;
    /** Seconds from the split to the detonation. */
    public final float delaySeconds;
    private final PendingDetonation detonation;
    private float age;

    WarheadStrike(VanillaWeapon weapon, Faction faction, float releaseX, float releaseY,
                  WarshipShell.Warhead warhead) {
        this.weapon = weapon;
        this.faction = faction;
        this.releaseX = releaseX;
        this.releaseY = releaseY;
        this.aimX = warhead.aimX();
        this.aimY = warhead.aimY();
        this.visualCells = warhead.visualCells();
        this.delaySeconds = Math.max(warhead.delaySeconds(), 0.001f);
        this.detonation = warhead.detonation();
    }

    /** Seconds since the split. */
    public float age() {
        return age;
    }

    /** How far from the split point to the aim point it has come, 0 to 1. */
    public float progress() {
        return Math.min(1f, age / delaySeconds);
    }

    public float x() {
        return releaseX + (aimX - releaseX) * progress();
    }

    public float y() {
        return releaseY + (aimY - releaseY) * progress();
    }

    PendingDetonation detonation() {
        return detonation;
    }

    boolean advance(float dt) {
        age += dt;
        return age >= delaySeconds;
    }
}
