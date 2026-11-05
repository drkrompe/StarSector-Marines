package com.dillon.starsectormarines.battle.vehicle;

/**
 * A family's turret head ({@code hegemony-armor-line.md}), sized from the hull's ring, never from the weapon
 * ({@link VehicleGun#headCells}), so one set of weapon drawings serves every hull of every family. A <em>closed</em>
 * head turns with the weapon and is drawn over it: the ring, then the weapon's own drawing, then the head, so only
 * the barrel or launcher face shows through its slot; a hull with an empty card shows the head with nothing in it.
 * An <em>open</em> head is a barbette fixed to the hull and drawn under the weapon in place of the ring: a weapon that
 * stands up to fire, a mortar or a launcher, turns and rises in its well where a closed head would hide it.
 */
public enum TurretHead {
    /** Tan and olive Hegemony plate with orange hazard panels, gun slot through the mantlet (art-source/vehicles/turret-head). */
    HEGEMONY("turret-head-hegemony.png", "turret-head-hegemony-wreck.png", true),
    /**
     * The open Hegemony barbette: a chamfered octagonal wall, thick across the front, round an open well, ammunition
     * lockers on the rear wall (art-source/vehicles/turret-barbette). Its front points down the hull's nose.
     */
    HEGEMONY_BARBETTE("turret-barbette-hegemony.png", "turret-barbette-hegemony-wreck.png", false);

    /** The head's sprite under {@code graphics/battle/vehicles/}, front to the top, centred on the ring's centre. */
    public final String texture;
    /** The same head wrecked, cut to the live head's square so the swap does not move it. */
    public final String wreckTexture;
    /** Closed, turning with the weapon and drawn over it; or open, fixed to the hull and drawn under it. */
    public final boolean closed;

    TurretHead(String texture, String wreckTexture, boolean closed) {
        this.texture = texture;
        this.wreckTexture = wreckTexture;
        this.closed = closed;
    }
}
