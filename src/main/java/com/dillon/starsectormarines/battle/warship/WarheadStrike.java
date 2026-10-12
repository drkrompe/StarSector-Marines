package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.Interceptable;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One warhead a carrier has released and that has not yet detonated: a body in
 * flight along its own {@link BloomPath} from the split point to its aim point,
 * carrying a detonation committed when the missile was fired.
 *
 * <p>It is a body in the full sense: point defence sees it where it is drawn, at
 * the speed it is crossing, and a warhead shot down never detonates. Nothing
 * about it is steered; where it is is a function of its age.
 */
public final class WarheadStrike implements Interceptable {

    public final VanillaWeapon weapon;
    public final Faction faction;
    /** The ship whose missile released it. */
    final Warship shooter;
    public final float releaseX;
    public final float releaseY;
    public final float aimX;
    public final float aimY;
    public final float visualCells;
    /** Seconds from the split to the detonation. */
    public final float delaySeconds;
    private final PendingDetonation detonation;
    private final BloomPath path;
    private final float hitpoints;
    private final String spriteId;
    private float age;
    boolean intercepted;

    WarheadStrike(VanillaWeapon weapon, Faction faction, Warship shooter, float releaseX, float releaseY,
                  WarshipShell.Warhead warhead, String spriteId) {
        this.weapon = weapon;
        this.faction = faction;
        this.shooter = shooter;
        this.releaseX = releaseX;
        this.releaseY = releaseY;
        this.aimX = warhead.aimX();
        this.aimY = warhead.aimY();
        this.visualCells = warhead.visualCells();
        this.delaySeconds = Math.max(warhead.delaySeconds(), 0.001f);
        this.detonation = warhead.detonation();
        this.path = warhead.path();
        this.hitpoints = warhead.hitpoints();
        this.spriteId = spriteId;
    }

    /** Seconds since the split. */
    public float age() {
        return age;
    }

    /** How far through its flight it is, 0 at the split to 1 at the aim point. */
    public float progress() {
        return Math.min(1f, age / delaySeconds);
    }

    /** Its flight as a function of time since the split, for whoever draws a trail behind it. */
    public BloomPath path() {
        return path;
    }

    @Override
    public float x() {
        return path.x(age);
    }

    @Override
    public float y() {
        return path.y(age);
    }

    /** Which way the warhead's nose points now, in the hull-facing convention. */
    public float headingDegrees() {
        return path.headingDegrees(age);
    }

    @Override
    public float speedCells() {
        return path.speed(age);
    }

    @Override
    public Faction faction() {
        return faction;
    }

    @Override
    public float hitpoints() {
        return hitpoints;
    }

    @Override
    public boolean isIntercepted() {
        return intercepted;
    }

    @Override
    public void intercept() {
        intercepted = true;
    }

    /** The warhead's own sprite, or {@code null} to use the weapon's projectile art. */
    public String spriteId() {
        return spriteId;
    }

    PendingDetonation detonation() {
        return detonation;
    }

    boolean advance(float dt) {
        age += dt;
        return age >= delaySeconds;
    }
}
