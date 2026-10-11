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
 * <p>The ship is a plain object ticked by {@link WarshipSystem}, not a body in
 * the entity world. It is answerable only through {@link #takeDamage}: it has the
 * hull table's own hit points and armour, and {@link #effectiveDamage} says how
 * little a small round does against that armour and how much ordnance does.
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

    /** A hit whose strength is under this share of the armour glances off and does nothing. */
    static final float GLANCE_SHARE = 0.1f;
    private static final float DEFAULT_HIT_POINTS = 10000f;
    private static final float DEFAULT_ARMOUR = 1000f;

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
    /** Hull width across the beam, in cells. */
    public final float widthCells;
    /** The hull table's hit points and armour rating. */
    public final float maxHitPoints;
    public final float armour;
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
    private float hitPoints;
    private final List<Mount> mounts;
    private final List<Mount> mountsView;

    private Warship(String hullId, String spritePath, float lengthCells, float pivotX, float pivotY,
                    float maxTurn, float turnAccel, float widthCells, float hitPoints, float armour,
                    Faction faction, float x, float y, float facingDegrees, List<Mount> mounts) {
        this.hullId = hullId;
        this.spritePath = spritePath;
        this.lengthCells = lengthCells;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.maxTurnDegPerSec = maxTurn;
        this.turnAccelDegPerSec2 = turnAccel;
        this.faction = faction;
        this.widthCells = widthCells;
        this.maxHitPoints = hitPoints;
        this.hitPoints = hitPoints;
        this.armour = armour;
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

    public float hitPoints() {
        return hitPoints;
    }

    /** Remaining hull as a share of the whole: 1 when untouched, 0 when destroyed. */
    public float hullFraction() {
        return hitPoints / maxHitPoints;
    }

    public boolean destroyed() {
        return hitPoints <= 0f;
    }

    /**
     * The armour rule. A hit's strength is its damage times its penetration, so a
     * round has to be both heavy and able to bite. The share of its damage that
     * gets through the plate is {@code strength / (armour + strength)}: the game's
     * own armour curve, with penetration standing in for the damage type's
     * multiplier. A hit weaker than a tenth of the armour glances off and does
     * nothing, so small arms cannot whittle a hull however many fire: an
     * 18-damage, 5-penetration rifle round is 90 against the Onslaught's 1750. A
     * 600-damage, 60-penetration anti-ship round is 36000 and takes 95%.
     */
    public static float effectiveDamage(float damage, float penetration, float armour) {
        if (damage <= 0f || penetration <= 0f) return 0f;
        float strength = damage * penetration;
        if (strength < armour * GLANCE_SHARE) return 0f;
        return damage * strength / (armour + strength);
    }

    /** Applies one hit through the armour rule and returns the hull points it took. A destroyed hull takes nothing more. */
    public float takeDamage(float damage, float penetration) {
        if (destroyed()) return 0f;
        float taken = Math.min(hitPoints, effectiveDamage(damage, penetration, armour));
        hitPoints -= taken;
        return taken;
    }

    /**
     * The gap, in cells, between {@code (px, py)} and the hull's footprint (its
     * sprite rectangle about the sprite's centre); zero inside it.
     */
    public float distanceTo(float px, float py) {
        double r = Math.toRadians(facingDegrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float cx = x + pivotX * c - pivotY * s;
        float cy = y + pivotX * s + pivotY * c;
        float dx = px - cx;
        float dy = py - cy;
        float lx = dx * c + dy * s;
        float ly = -dx * s + dy * c;
        float ox = Math.max(0f, Math.abs(lx) - widthCells * 0.5f);
        float oy = Math.max(0f, Math.abs(ly) - lengthCells * 0.5f);
        return (float) Math.sqrt(ox * ox + oy * oy);
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
        float[] toughness = toughness(hullId);
        return new Warship(hullId, hull.optString("spriteName"), h * k, pivotX, pivotY,
                turn[0], turn[1], w * k, toughness[0], toughness[1], faction, x, y, facingDegrees, mounts);
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

    /** {@code [hit points, armour]} from the hull table, or a middling capital's. */
    private static float[] toughness(String hullId) {
        try {
            for (Map<String, String> row : VanillaData.csv("data/hulls/ship_data.csv")) {
                if (!hullId.equals(row.get("id"))) continue;
                return new float[]{
                        positive(row.get("hitpoints"), DEFAULT_HIT_POINTS),
                        positive(row.get("armor rating"), DEFAULT_ARMOUR)};
            }
        } catch (Exception e) {
            // A hull whose toughness cannot be read is a middling capital.
        }
        return new float[]{DEFAULT_HIT_POINTS, DEFAULT_ARMOUR};
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
