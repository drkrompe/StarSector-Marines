package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipScale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One fortress battery in play: a multi-cell structure, the ship-grade guns it carries, and the fire control that
 * says whose they are. State only; {@link FortressBatterySystem} advances it.
 *
 * <p>The structure is an ordinary unit and keeps being one: it can be shot, it dies like any structure, and its death
 * silences the battery for good. What is added here is the guns, which aim at hulls, and the fire-control point,
 * which is taken by standing at it rather than by shooting.
 */
public final class FortressBattery {

    /** One large gun on the battery and the little state it needs between ticks. */
    public static final class Gun {
        public final VanillaWeapon weapon;
        float cooldown;
        int burstRemaining;
        float burstTimer;
        /** Seconds the gun has had a hull in reach and not yet fired on it; the warning a ship gets. */
        float lockSeconds;
        Warship target;
        /** Rounds this gun has fired, for evidence. */
        public int shotsFired;

        Gun(VanillaWeapon weapon) {
            this.weapon = weapon;
        }

        /** Seconds this gun has been settling on a hull in reach. */
        public float lockSeconds() {
            return lockSeconds;
        }

        /** The hull the gun is firing on or settling on, or {@code null}. */
        public Warship target() {
            return target;
        }
    }

    private final long structureId;
    private final StructureDef structure;
    private final float x;
    private final float y;
    private final FireControl fireControl;
    private final List<Gun> guns = new ArrayList<>();
    private final List<Gun> gunsView = Collections.unmodifiableList(guns);
    private final float envelopeCells;
    private boolean destroyed;

    FortressBattery(long structureId, StructureDef structure, float x, float y, Faction owner, BatteryLoadout loadout) {
        this.structureId = structureId;
        this.structure = structure;
        this.x = x;
        this.y = y;
        this.fireControl = new FireControl(owner);
        float reach = 0f;
        for (VanillaWeapon w : loadout.guns()) {
            guns.add(new Gun(w));
            reach = Math.max(reach, WarshipScale.cells(w.rangePx()));
        }
        this.envelopeCells = reach;
    }

    public long structureId() {
        return structureId;
    }

    public StructureDef structure() {
        return structure;
    }

    /** Centre of the structure, in cells. */
    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public FireControl fireControl() {
        return fireControl;
    }

    public List<Gun> guns() {
        return gunsView;
    }

    /** The side whose guns these are now. */
    public Faction owner() {
        return fireControl.holder();
    }

    /** The firing envelope's radius: the longest gun's reach at ground scale. Zero when the battery has no guns. */
    public float envelopeCells() {
        return envelopeCells;
    }

    /** Whether the structure has been destroyed; a battery never comes back. */
    public boolean destroyed() {
        return destroyed;
    }

    /** Records that the structure is gone; the battery never comes back. */
    public void markDestroyed() {
        destroyed = true;
    }

    /** Whether the guns can fire now: the structure stands, the fire control is held and nobody is contesting it. */
    public boolean operational() {
        return !destroyed && fireControl.operational() && envelopeCells > 0f;
    }
}
