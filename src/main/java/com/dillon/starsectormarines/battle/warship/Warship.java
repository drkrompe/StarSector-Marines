package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A capital ship hanging over the battle: a hull, a position, and every gun the
 * hull's chosen variant puts in its slots.
 *
 * <p>It does not fly a sortie and never lands. It arrives, holds a station and
 * fires; its guns are the game's own weapons, each placed where the hull's own
 * slot is and converted to ground scale through {@link WarshipScale}.
 *
 * <p>The ship is a plain object ticked by {@link WarshipSystem}, not yet a body
 * in the entity world: nothing on the ground can shoot it. That is the
 * answerability work, and it is deliberately the next step rather than a
 * half-measure here.
 */
public final class Warship {

    /** One fitted weapon and the little state it needs between ticks. */
    public static final class Mount {
        public final WarshipSlot slot;
        public final VanillaWeapon weapon;
        float cooldown;
        int burstRemaining;
        float burstTimer;
        float aimX;
        float aimY;
        /** Rounds this mount has fired, for evidence. */
        public int shotsFired;
        /** Where the barrel points now, in the hull-facing convention; rests along the slot until it has a target. */
        public float headingDegrees;
        /** Seconds since this mount last fired; large until it first does. */
        public float secondsSinceFired = 1000f;

        Mount(WarshipSlot slot, VanillaWeapon weapon) {
            this.slot = slot;
            this.weapon = weapon;
        }
    }

    public final String hullId;
    public final String spritePath;
    /** Hull length along the nose, in cells. */
    public final float lengthCells;
    /** Offset from the hull's centre of gravity to its sprite centre, in the hull frame, in cells (localX, localY). */
    public final float pivotX;
    public final float pivotY;
    public final Faction faction;
    public float x;
    public float y;
    public float facingDegrees;
    private final List<Mount> mounts;
    private final List<Mount> mountsView;

    private Warship(String hullId, String spritePath, float lengthCells, float pivotX, float pivotY,
                    Faction faction, float x, float y, float facingDegrees, List<Mount> mounts) {
        this.hullId = hullId;
        this.spritePath = spritePath;
        this.lengthCells = lengthCells;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.faction = faction;
        this.x = x;
        this.y = y;
        this.facingDegrees = facingDegrees;
        this.mounts = mounts;
        this.mountsView = Collections.unmodifiableList(mounts);
    }

    public List<Mount> mounts() {
        return mountsView;
    }

    /**
     * Fits {@code hullId} as {@code variantId} names, over {@code (x, y)}. A slot
     * whose weapon is unknown, or is point defence that only shoots down
     * ordnance, is left empty.
     */
    public static Warship fit(String hullId, String variantId, Faction faction,
                              float x, float y, float facingDegrees) throws Exception {
        JSONObject hull = VanillaData.json("data/hulls/" + hullId + ".ship");
        Map<String, String> loadout = WarshipVariants.weaponsBySlot(hullId, variantId);
        List<Mount> mounts = new ArrayList<>();
        for (WarshipSlot slot : WarshipSlot.parse(hull)) {
            VanillaWeapon weapon = VanillaWeapons.get(loadout.get(slot.id()));
            if (weapon == null || weapon.pointDefenceOnly()) continue;
            Mount mount = new Mount(slot, weapon);
            mount.headingDegrees = facingDegrees + slot.angleDegrees();
            mounts.add(mount);
        }
        float k = WarshipScale.cells(1f);
        float w = (float) hull.optDouble("width", 0.0);
        float h = (float) hull.optDouble("height", 0.0);
        org.json.JSONArray c = hull.optJSONArray("center");
        // The same pivot every air hull uses: the sprite's centre, less the
        // authored centre of gravity, so the CoG stays fixed at the body.
        float pivotX = c == null ? 0f : (w / 2f - (float) c.optDouble(0)) * k;
        float pivotY = c == null ? 0f : (h / 2f - (float) c.optDouble(1)) * k;
        return new Warship(hullId, hull.optString("spriteName"), h * k, pivotX, pivotY,
                faction, x, y, facingDegrees, mounts);
    }

    /** World X of a mount, rotated by the hull's facing about its centre of gravity. */
    public float mountX(Mount m) {
        double r = Math.toRadians(facingDegrees);
        return x + m.slot.localX() * (float) Math.cos(r) - m.slot.localY() * (float) Math.sin(r);
    }

    /** World Y of a mount, rotated by the hull's facing about its centre of gravity. */
    public float mountY(Mount m) {
        double r = Math.toRadians(facingDegrees);
        return y + m.slot.localX() * (float) Math.sin(r) + m.slot.localY() * (float) Math.cos(r);
    }
}
