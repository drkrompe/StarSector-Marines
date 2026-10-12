package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
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
        /** Hostile ordnance a point-defence mount has shot down, for evidence. */
        public int intercepts;
        /** Where the barrel points now, in the hull-facing convention; rests along the slot until it has a target. */
        public float headingDegrees;
        /** Seconds since this mount last fired; large until it first does. */
        public float secondsSinceFired = 1000f;

        Mount(WarshipSlot slot, VanillaWeapon weapon) {
            this.slot = slot;
            this.weapon = weapon;
        }

        /** Whether this mount only shoots down ordnance; it is {@link WarshipPointDefence}'s, not an area-fire gun. */
        public boolean pointDefence() {
            return weapon.pointDefenceOnly();
        }
    }

    /** A hit whose strength is under this share of the armour glances off and does nothing. */
    static final float GLANCE_SHARE = 0.1f;
    private static final float DEFAULT_HIT_POINTS = 10000f;
    private static final float DEFAULT_ARMOUR = 1000f;

    /** The hull's handling from its table row, in cells for travel and degrees for turning. */
    private record Handling(float maxSpeed, float accel, float decel, float turnRate, float turnAccel) {}

    /** A battleship's handling, used for a hull the table does not list. */
    private static final Handling DEFAULT_HANDLING = new Handling(1.1f, 0.4f, 0.4f, 4f, 4f);

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
    /** Hover translation limits, in cells per second and per second squared. */
    public final float maxSpeed;
    public final float acceleration;
    public final float deceleration;
    float velocityX;
    float velocityY;
    private float stationX;
    private float stationY;
    private boolean hasStation;
    private float directorRadius;
    private boolean directorBringsGunsToBear;
    private boolean directing;
    float areaEmptySeconds;
    float headingChosenAtX;
    float headingChosenAtY;
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
                    Handling handling, float widthCells, float hitPoints, float armour,
                    Faction faction, float x, float y, float facingDegrees, List<Mount> mounts) {
        this.hullId = hullId;
        this.spritePath = spritePath;
        this.lengthCells = lengthCells;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.maxTurnDegPerSec = handling.turnRate();
        this.turnAccelDegPerSec2 = handling.turnAccel();
        this.maxSpeed = handling.maxSpeed();
        this.acceleration = handling.accel();
        this.deceleration = handling.decel();
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
        headingChosenAtX = x;
        headingChosenAtY = y;
    }

    /** Orders the ship to hover over {@code (stationX, stationY)}, arriving at its own speed and braking onto it. */
    public void stationTo(float targetX, float targetY) {
        this.stationX = targetX;
        this.stationY = targetY;
        this.hasStation = true;
    }

    /** The waypoints of a scripted flight, or {@code null} when the ship is not flying one. */
    private float[][] route;
    private int routeIndex;
    private boolean routeLoops;

    /**
     * Scripts a continuous flight: the ship passes through each waypoint at speed
     * without stopping, brakes onto the last, or, if {@code loop}, goes round
     * again forever. Whatever it has been ordered to fire on, it keeps firing.
     */
    public void flyRoute(float[][] waypoints, boolean loop) {
        if (waypoints == null || waypoints.length == 0) {
            this.route = null;
            return;
        }
        this.route = waypoints.clone();
        this.routeIndex = 0;
        this.routeLoops = loop;
        stationTo(waypoints[0][0], waypoints[0][1]);
    }

    /** Whether the ship is still on legs it passes through rather than stops at. */
    public boolean isPassingThrough() {
        return route != null && (routeLoops || routeIndex < route.length - 1);
    }

    /** Index of the waypoint the ship is flying to, or -1 when it has no route. */
    public int routeIndex() {
        return route == null ? -1 : routeIndex;
    }

    /** Moves on to the next waypoint once the ship is within {@code radius} cells of this one. */
    void advanceRoute(float radius) {
        if (!isPassingThrough()) return;
        float dx = route[routeIndex][0] - x;
        float dy = route[routeIndex][1] - y;
        if (dx * dx + dy * dy > radius * radius) return;
        routeIndex = (routeIndex + 1) % route.length;
        stationTo(route[routeIndex][0], route[routeIndex][1]);
    }

    boolean hasStation() {
        return hasStation;
    }

    float stationX() {
        return stationX;
    }

    float stationY() {
        return stationY;
    }

    /** Whether the ship is still travelling to its station. */
    public boolean isMoving() {
        return velocityX != 0f || velocityY != 0f
                || (hasStation && (x != stationX || y != stationY));
    }

    /**
     * Puts the ship's targeting in the hands of a director: it picks the densest
     * hostile concentration within {@code radius} that is not already being worked,
     * and picks again when that ground is empty. The order it issues is an
     * ordinary {@link WarshipOrder.FireOnArea}.
     */
    public void direct(float radius, boolean bringGunsToBear) {
        if (!(radius > 0f)) throw new IllegalArgumentException("a director needs an area radius");
        this.directorRadius = radius;
        this.directorBringsGunsToBear = bringGunsToBear;
        this.directing = true;
        this.areaEmptySeconds = Float.MAX_VALUE;
    }

    /** Takes targeting back from the director; the standing order stays until replaced. */
    public void stopDirecting() {
        this.directing = false;
    }

    boolean isDirected() {
        return directing;
    }

    float directorRadius() {
        return directorRadius;
    }

    boolean directorBringsGunsToBear() {
        return directorBringsGunsToBear;
    }

    /**
     * Fits {@code hullId} as {@code variantId} names, over {@code (x, y)}. A slot
     * whose weapon is unknown is left empty. Point defence is mounted like any
     * gun and told apart by {@link Mount#pointDefence()}.
     */
    public static Warship fit(String hullId, String variantId, Faction faction,
                              float x, float y, float facingDegrees) throws Exception {
        return fit(new StockVariantLoadout(hullId, variantId), faction, x, y, facingDegrees);
    }

    /** Fits a campaign fleet member as its own fitted variant arms it. */
    public static Warship fit(FleetMemberAPI member, Faction faction,
                              float x, float y, float facingDegrees) throws Exception {
        return fit(new VariantApiLoadout(member.getVariant()), faction, x, y, facingDegrees);
    }

    /** Fits whatever {@code loadout} names; the stock and fleet-member paths both end here. */
    public static Warship fit(WarshipLoadout loadout, Faction faction,
                              float x, float y, float facingDegrees) throws Exception {
        String hullId = loadout.hullId();
        JSONObject hull = VanillaData.json("data/hulls/" + hullId + ".ship");
        List<Mount> mounts = new ArrayList<>();
        for (WarshipSlot slot : WarshipSlot.parse(hull)) {
            VanillaWeapon weapon = VanillaWeapons.get(loadout.weaponInSlot(slot.id()));
            if (weapon == null) continue;
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
        Handling handling = handling(hullId);
        float[] toughness = toughness(hullId);
        return new Warship(hullId, hull.optString("spriteName"), h * k, pivotX, pivotY,
                handling, w * k, toughness[0], toughness[1], faction, x, y, facingDegrees, mounts);
    }

    /** The hull's handling from the table, or a battleship's. */
    private static Handling handling(String hullId) {
        try {
            for (Map<String, String> row : VanillaData.csv("data/hulls/ship_data.csv")) {
                if (!hullId.equals(row.get("id"))) continue;
                return new Handling(
                        WarshipScale.cells(positive(row.get("max speed"), DEFAULT_HANDLING.maxSpeed() / WarshipScale.cells(1f))),
                        WarshipScale.cells(positive(row.get("acceleration"), DEFAULT_HANDLING.accel() / WarshipScale.cells(1f))),
                        WarshipScale.cells(positive(row.get("deceleration"), DEFAULT_HANDLING.decel() / WarshipScale.cells(1f))),
                        positive(row.get("max turn rate"), DEFAULT_HANDLING.turnRate()),
                        positive(row.get("turn acceleration"), DEFAULT_HANDLING.turnAccel()));
            }
        } catch (Exception e) {
            // A hull whose handling cannot be read handles like a battleship.
        }
        return DEFAULT_HANDLING;
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
