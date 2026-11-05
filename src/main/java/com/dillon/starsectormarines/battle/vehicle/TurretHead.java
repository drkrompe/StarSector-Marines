package com.dillon.starsectormarines.battle.vehicle;

/**
 * A family's closed turret head, drawn over whatever weapon a hull has in its gun card ({@code hegemony-armor-line.md}):
 * the ring, then the weapon's own drawing, then the head, so only the barrel or launcher face shows through the head's
 * slot and one set of weapon drawings serves every hull of every family. A hull with an empty card shows the head with
 * nothing in its slot. The head is sized from the hull's ring, never from the weapon ({@link VehicleGun#headCells}).
 */
public enum TurretHead {
    /** Tan and olive Hegemony plate with orange hazard panels, gun slot through the mantlet (art-source/vehicles/turret-head). */
    HEGEMONY("turret-head-hegemony.png");

    /** The head's sprite under {@code graphics/battle/vehicles/}, gun slot to the top, centred on the ring's centre. */
    public final String texture;

    TurretHead(String texture) {
        this.texture = texture;
    }
}
