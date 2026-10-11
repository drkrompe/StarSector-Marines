package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A capital ship hanging over the battle: a hull, a position, and every gun the
 * hull's chosen variant puts in its slots.
 *
 * <p>It does not fly a sortie and never lands. It arrives, holds a station and,
 * when ordered, turns and fires; its guns are the game's own weapons, each placed
 * where the hull's own slot is and converted to ground scale through
 * {@link WarshipScale}. It turns at the hull's own turn rate and acceleration, so
 * a battleship swings slowly.
 *
 * <p>The ship is a plain object ticked by {@link WarshipSystem}, not yet a body
 * in the entity world: nothing on the ground can shoot it. That is the
 * answerability work, deferred until a mission wants a counter.
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
        /** Whether {@code aimX/aimY} is a live choice the barrel is swinging to. */
        boolean aimed;
        /** The sweep this beam mount has committed, while it lasts. */
        LanceSweepSystem.Active lance;
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

    /** Fallback handling for a hull the table does not list, in degrees per second and per second squared. */
    private static final float DEFAULT_TURN_RATE = 4f;
    private static final float DEFAULT_TURN_ACCEL = 4f;

    public final String hullId;
    public final String spritePath;
    /** Hull length along the nose, in cells. */
    public final float lengthCells;
    /** Offset from the hull's centre of gravity to its sprite centre, in the hull frame, in cells (localX, localY). */
    public final float pivotX;
    public final float pivotY;
    public final Faction faction;
    /** The hull's own maximum turn rate and turn acceleration. */
    public final float maxTurnDegPerSec;
    public final float turnAccelDegPerSec2;
    public float x;
    public float y;
    public float facingDegrees;
    /** The heading the hull is turning toward. */
    float desiredHeading;
    float turnVelocity;
    private WarshipOrder order = WarshipOrder.HoldFire.INSTANCE;
    private boolean headingChosen;
    private final List<Mount> mounts;
    private final List<Mount> mountsView;

    private Warship(String hullId, String spritePath, float lengthCells, float pivotX, float pivotY,
                    float maxTurn, float turnAccel,
                    Faction faction, float x, float y, float facingDegrees, List<Mount> mounts) {
        this.hullId = hullId;
        this.spritePath = spritePath;
        this.lengthCells = lengthCells;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.maxTurnDegPerSec = maxTurn;
        this.turnAccelDegPerSec2 = turnAccel;
        this.faction = faction;
        this.x = x;
        this.y = y;
        this.facingDegrees = facingDegrees;
        this.desiredHeading = facingDegrees;
        this.mounts = mounts;
        this.mountsView = Collections.unmodifiableList(mounts);
    }

    public List<Mount> mounts() {
        return mountsView;
    }

    public WarshipOrder order() {
        return order;
    }

    /** Gives the ship an order. A new order is carried out from scratch: the heading is chosen again. */
    public void order(WarshipOrder newOrder) {
        this.order = newOrder != null ? newOrder : WarshipOrder.HoldFire.INSTANCE;
        this.headingChosen = false;
    }

    boolean headingChosen() {
        return headingChosen;
    }

    void markHeadingChosen() {
        headingChosen = true;
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
        JSONArray c = hull.optJSONArray("center");
        // The same pivot every air hull uses: the sprite's centre, less the
        // authored centre of gravity, so the CoG stays fixed at the body.
        float pivotX = c == null ? 0f : (w / 2f - (float) c.optDouble(0)) * k;
        float pivotY = c == null ? 0f : (h / 2f - (float) c.optDouble(1)) * k;
        float[] turn = handling(hullId);
        return new Warship(hullId, hull.optString("spriteName"), h * k, pivotX, pivotY,
                turn[0], turn[1], faction, x, y, facingDegrees, mounts);
    }

    /** {@code [max turn rate, turn acceleration]} from the hull table, or the battleship default. */
    private static float[] handling(String hullId) {
        try {
            for (Map<String, String> row : VanillaData.csv("data/hulls/ship_data.csv")) {
                if (!hullId.equals(row.get("id"))) continue;
                return new float[]{
                        positive(row.get("max turn rate"), DEFAULT_TURN_RATE),
                        positive(row.get("turn acceleration"), DEFAULT_TURN_ACCEL)};
            }
        } catch (Exception e) {
            // A hull whose handling cannot be read turns like a battleship.
        }
        return new float[]{DEFAULT_TURN_RATE, DEFAULT_TURN_ACCEL};
    }

    private static float positive(String value, float fallback) {
        try {
            float v = Float.parseFloat(value.trim());
            return v > 0f ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** World X of a slot on a hull at {@code (shipX, ·)} pointing {@code facing}, rotated about its centre of gravity. */
    static float mountX(float shipX, WarshipSlot slot, float facing) {
        double r = Math.toRadians(facing);
        return shipX + slot.localX() * (float) Math.cos(r) - slot.localY() * (float) Math.sin(r);
    }

    /** World Y of a slot on a hull at {@code (·, shipY)} pointing {@code facing}, rotated about its centre of gravity. */
    static float mountY(float shipY, WarshipSlot slot, float facing) {
        double r = Math.toRadians(facing);
        return shipY + slot.localX() * (float) Math.sin(r) + slot.localY() * (float) Math.cos(r);
    }

    /** World X of a mount at the hull's current facing. */
    public float mountX(Mount m) {
        return mountX(x, m.slot, facingDegrees);
    }

    /** World Y of a mount at the hull's current facing. */
    public float mountY(Mount m) {
        return mountY(y, m.slot, facingDegrees);
    }

    /**
     * Whether a mount could fire on {@code (tx, ty)} from {@code (mx, my)} with the
     * hull pointing {@code shipFacing}: inside its weapon's reach, and inside the
     * slot's arc about the slot's own direction. A hardpoint's arc is a few degrees,
     * so it asks the hull to point at the target.
     */
    static boolean bears(Mount m, float shipFacing, float mx, float my, float tx, float ty) {
        float dx = tx - mx;
        float dy = ty - my;
        float range = WarshipScale.cells(m.weapon.rangePx());
        if (dx * dx + dy * dy > range * range) return false;
        float bearing = WarshipFacing.bearing(mx, my, tx, ty);
        float diff = Math.abs(WarshipFacing.wrap(bearing - (shipFacing + m.slot.angleDegrees())));
        return diff <= Math.max(m.slot.arcDegrees(), 1f) * 0.5f;
    }
}
