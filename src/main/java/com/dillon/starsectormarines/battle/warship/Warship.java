package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
        /** Rounds left in this mount's own magazine, or {@code -1} when it has none to run out of. */
        int ammoRemaining = UNLIMITED;

        /** Rounds left in this mount's own magazine, or {@code -1} when it is unlimited. */
        public int ammoRemaining() {
            return ammoRemaining;
        }

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

    /** The value a getter reports for a budget that was never set. */
    public static final int UNLIMITED = -1;
    /** The longest a ship takes to leave the map once it has begun to depart before it is removed anyway. */
    public static final float DEPARTURE_LIMIT_SECONDS = 180f;

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
    /** How many areas a director holds at once unless told otherwise. */
    public static final int DEFAULT_MAX_AREAS = 3;

    /** One ground a ship is firing on, with how many hostiles it held when last looked at and how long it has held none. */
    static final class Held {
        final WarshipOrder.FireOnArea area;
        int weight;
        float emptySeconds;
        /** Whether this is a landing zone denied before anybody stands on it; it is aimed at as ground, not at a unit. */
        boolean denial;
        /** For a denied landing zone: seconds until the boat touches down, as of the last tick. */
        float denialEta;

        Held(WarshipOrder.FireOnArea area, int weight) {
            this.area = area;
            this.weight = weight;
        }
    }

    private float directorRadius;
    private boolean directorBringsGunsToBear;
    private boolean directing;
    private int directorMaxAreas = DEFAULT_MAX_AREAS;
    /** Whether the held areas are one a caller ordered by hand; the director adds nothing beside it. */
    private boolean manual;
    private final List<Held> held = new ArrayList<>();
    private final List<WarshipOrder.FireOnArea> areasView = new ArrayList<>();
    private final List<WarshipOrder.FireOnArea> areasReadOnly = Collections.unmodifiableList(areasView);
    float retrySeconds;
    /** Rotates the areas the mounts are dealt, so a battery spreads over all of them. */
    int dealCounter;
    float headingChosenAtX;
    float headingChosenAtY;
    public float x;
    public float y;
    public float facingDegrees;
    /** The heading the hull is turning toward. */
    float desiredHeading;
    float turnVelocity;
    private boolean headingChosen;
    private float hitPoints;
    /** Seconds of station time left, counted down while the ship is on station; infinite when unbounded. */
    private float stationSecondsRemaining = Float.POSITIVE_INFINITY;
    private boolean departing;
    private float departingSeconds;
    private boolean departed;
    /** Rounds left in the ship-wide pool, or {@link #UNLIMITED}. */
    private int ordnanceRemaining = UNLIMITED;
    private final List<Mount> mounts;
    private final List<Mount> mountsView;
    private final WarshipFlux flux;
    private final WarshipShield shield;
    private boolean fluxConfigured;

    /**
     * Share of the hull table's flux a directed ship runs on. Capacity and
     * dissipation are halved together, so a brawler's guns surge and rest on a
     * rhythm a player can read (at the hull table's own figures a ground
     * bombardment never nears an Onslaught's capacity). This was once a stand-in
     * for what a shield would take; shields are modelled now ({@link WarshipShield}:
     * upkeep, no venting while raised, hits absorbed as flux) and draw on this same
     * scaled pool, but a shield is only raised when something threatens the hull,
     * so in a ground fight nothing is drawing and the half remains the brawler's
     * deliberate fire-rate setting rather than a placeholder for shield cost.
     */
    public static final float BRAWLER_FLUX_SCALE = 0.5f;

    /** The old name of {@link #BRAWLER_FLUX_SCALE}, from when it stood in for an unmodelled shield. */
    @Deprecated
    public static final float SHIELD_RESERVE_SCALE = BRAWLER_FLUX_SCALE;

    private Warship(String hullId, String spritePath, float lengthCells, float pivotX, float pivotY,
                    Handling handling, float widthCells, float hitPoints, float armour,
                    Faction faction, float x, float y, float facingDegrees, List<Mount> mounts,
                    float[] fluxFigures, WarshipShield shield) {
        this.flux = new WarshipFlux(fluxFigures[0], fluxFigures[1]);
        this.shield = shield;
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

    /**
     * Puts the ship's guns on the hull's flux: every shot and beam-second adds to
     * one pool that drains at the hull's dissipation rate, and the ship surges and
     * rests (see {@link WarshipFlux}). Off unless this is called. The limit is
     * rhythm, not balance: no weapon does less damage for it.
     */
    public void limitFlux() {
        fluxConfigured = true;
        flux.enabled(true);
    }

    /** Takes the ship off flux again: the pool empties and the guns are never held back. This is the default. */
    public void unlimitedFlux() {
        fluxConfigured = true;
        flux.enabled(false);
    }

    /** Whether the ship's fire is governed by flux. */
    public boolean fluxLimited() {
        return flux.enabled();
    }

    /** Scales the hull's flux capacity and dissipation together; 1 is the hull table's own. */
    public void fluxScale(float scale) {
        fluxConfigured = true;
        flux.scale(scale);
    }

    public float fluxScale() {
        return flux.scale();
    }

    /**
     * Whether the ship holds fire for the soft threshold (the default): it stops
     * starting new bursts at 85% of capacity and resumes at 55%. Off, only a real
     * overload ever silences it.
     */
    public void fluxSoftLimit(boolean on) {
        flux.softLimit(on);
    }

    /** Flux now, in the hull table's units. */
    public float flux() {
        return flux.flux();
    }

    public float fluxCapacity() {
        return flux.capacity();
    }

    /** Flux as a share of capacity, 0 to 1; zero when the ship is not on flux. */
    public float fluxFraction() {
        return flux.fraction();
    }

    /** Whether the ship is overloaded and every mount silent. */
    public boolean overloaded() {
        return flux.overloaded();
    }

    /** Seconds until the overload ends; zero when the ship is not overloaded. */
    public float overloadSecondsRemaining() {
        return flux.overloadSecondsRemaining();
    }

    /** How many times the ship has overloaded. */
    public int overloadCount() {
        return flux.overloads();
    }

    /** One word for the HUD: {@code off}, {@code firing}, {@code venting} or {@code overloaded}. */
    public String fluxState() {
        return flux.enabled() ? flux.state().label : "off";
    }

    WarshipShield shieldModel() {
        return shield;
    }

    /** Whether the hull carries a shield at all, and the ship is on flux to power it. */
    public boolean hasShield() {
        return shield.present() && flux.enabled();
    }

    /** One word for the HUD and the evidence: {@code none}, {@code front} or {@code omni}. */
    public String shieldType() {
        return shield.type().name().toLowerCase(Locale.ROOT);
    }

    /** Whether the shield is raised: drawing upkeep and holding the pool from venting. */
    public boolean shieldUp() {
        return hasShield() && shield.raised();
    }

    /** 0 to 1: how far the shield has risen; hits are absorbed only at 1. */
    public float shieldLevel() {
        return hasShield() ? shield.level() : 0f;
    }

    /** Whether a hit from inside the arc would be absorbed this instant. */
    public boolean shieldAbsorbing() {
        return hasShield() && shield.absorbing();
    }

    /** Brightness of the last absorbed hit, 1 fading to 0. */
    public float shieldHitFlash() {
        return shield.hitFlash();
    }

    /** Brightness of the last collapse, 1 fading to 0. */
    public float shieldDropFlash() {
        return shield.dropFlash();
    }

    /** Degrees of arc: 360 for a full bubble. */
    public float shieldArcDegrees() {
        return shield.arcDegrees();
    }

    /** The bubble's radius in cells. */
    public float shieldRadiusCells() {
        return shield.radiusCells();
    }

    /** World X of the bubble's centre. */
    public float shieldCentreX() {
        double r = Math.toRadians(facingDegrees);
        return x + shield.localX() * (float) Math.cos(r) - shield.localY() * (float) Math.sin(r);
    }

    /** World Y of the bubble's centre. */
    public float shieldCentreY() {
        double r = Math.toRadians(facingDegrees);
        return y + shield.localX() * (float) Math.sin(r) + shield.localY() * (float) Math.cos(r);
    }

    /**
     * Where the arc faces: the hull's heading for a front shield. An omni shield
     * has no fixed facing and also reports the heading, which only places its
     * drawing; every bearing is covered.
     */
    public float shieldFacingDegrees() {
        return facingDegrees;
    }

    /** Hits the shield has absorbed, for evidence. */
    public int shieldHits() {
        return shield.hits();
    }

    /** Damage the shield has absorbed, in the hits' own units. */
    public float shieldAbsorbedDamage() {
        return shield.absorbedDamage();
    }

    /** Flux the shield has taken on from absorbed hits (not counting upkeep). */
    public float shieldAbsorbedFlux() {
        return shield.absorbedFlux();
    }

    /** How many times the shield has gone down under load. */
    public int shieldCollapses() {
        return shield.collapses();
    }

    /** Holds the shield up or down as commanded, leaving automatic mode. It has no effect on a ship that is not on flux. */
    public void raiseShield(boolean up) {
        shield.raise(up);
    }

    /** Puts the shield in, or takes it out of, automatic mode (the default): it rises when the hull is threatened and drops once the threat has passed. */
    public void shieldAuto(boolean auto) {
        shield.auto(auto);
    }

    public boolean shieldAuto() {
        return shield.auto();
    }

    /** One word for the HUD: {@code none}, {@code off} (not on flux), {@code down}, {@code rising} or {@code up}. */
    public String shieldState() {
        if (!shield.present()) return "none";
        if (!flux.enabled()) return "off";
        if (!shield.raised()) return "down";
        return shield.absorbing() ? "up" : "rising";
    }

    /** Whether the shield turns a blast at {@code (px, py)} aside: it is fully up and the blast falls inside its arc. */
    boolean shieldCovers(float px, float py) {
        return shieldAbsorbing() && arcCovers(px, py);
    }

    /** Whether {@code (px, py)} lies inside the arc, up or not. */
    boolean arcCovers(float px, float py) {
        float dx = px - shieldCentreX();
        float dy = py - shieldCentreY();
        // Right on the centre there is no bearing; the blast is taken as head-on.
        float bearing = dx * dx + dy * dy < 0.0625f ? facingDegrees : AirBody.facingToward(dx, dy);
        return shield.covers(facingDegrees, bearing);
    }

    WarshipFlux fluxPool() {
        return flux;
    }

    /**
     * The ship's standing order: the first (densest) area it holds, or hold fire
     * when it holds none. A directed ship may hold several; {@link #areas()} lists
     * them all.
     */
    public WarshipOrder order() {
        return held.isEmpty() ? WarshipOrder.HoldFire.INSTANCE : held.get(0).area;
    }

    /** Ground batteries may train while their triggers are withheld. PD remains independent. */
    public enum GroundFire { HOLD, HEAVY, ALL }
    private GroundFire groundFire = GroundFire.ALL;

    public void groundFire(GroundFire permission) {
        groundFire = permission;
        for (Mount mount : mounts) if (!mount.pointDefence() && !groundFireAllowed(mount)) mount.burstRemaining = 0;
    }

    boolean groundFireAllowed(Mount mount) {
        return groundFire == GroundFire.ALL || groundFire == GroundFire.HEAVY
                && mount.weapon.damagePerShot() >= 100f;
    }

    public boolean headingSettled() {
        return Math.abs(WarshipFacing.wrap(desiredHeading - facingDegrees)) < .01f
                && Math.abs(turnVelocity) < .01f;
    }

    /** Heavy mounts already trained on legal ground, ready to start the opening volley. */
    public int readyHeavyMounts() {
        int ready = 0;
        for (Mount m : mounts) {
            if (m.pointDefence() || m.weapon.beam() || m.weapon.damagePerShot() < 100f
                    || !m.aimed || m.cooldown > 0f || !hasOrdnance(m)) continue;
            float bearing = (float) Math.toDegrees(Math.atan2(m.aimY - mountY(m), m.aimX - mountX(m))) - 90f;
            if (Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) <= 2f) ready++;
        }
        return ready;
    }

    /** Every area the ship is firing on now, densest first; empty when it holds fire. */
    public List<WarshipOrder.FireOnArea> areas() {
        return areasReadOnly;
    }

    /**
     * Gives the ship an order by hand. It replaces every area the ship held, even a
     * director's, and is carried out from scratch: the heading is chosen again. A
     * director beside it adds nothing until this ground has been emptied.
     */
    public void order(WarshipOrder newOrder) {
        held.clear();
        if (newOrder instanceof WarshipOrder.FireOnArea area) {
            held.add(new Held(area, 1));
            manual = true;
        } else {
            manual = false;
            for (Mount mount : mounts) mount.burstRemaining = 0;
        }
        refreshAreas();
        this.headingChosen = false;
    }

    private void refreshAreas() {
        areasView.clear();
        for (Held h : held) areasView.add(h.area);
    }

    List<Held> held() {
        return held;
    }

    boolean manualOrder() {
        return manual;
    }

    /** Replaces the held areas with a director's; the heading is chosen again. */
    void setDirectedAreas(List<Held> next) {
        held.clear();
        held.addAll(next);
        manual = false;
        refreshAreas();
        headingChosen = false;
    }

    /** Keeps the densest area first, which is the one the mounts are dealt twice as often and {@link #order()} names. */
    void resortByWeight() {
        boolean sorted = true;
        for (int i = 1; i < held.size(); i++) {
            if (held.get(i).weight > held.get(i - 1).weight) sorted = false;
        }
        if (sorted) return;
        held.sort((a, b) -> Integer.compare(b.weight, a.weight));
        refreshAreas();
    }

    /** Whether {@code (px, py)} lies in any area the ship holds. */
    boolean holdsGround(float px, float py) {
        for (Held h : held) if (h.area.contains(px, py)) return true;
        return false;
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
     * Bounds the ship's time on station: after {@code seconds} spent on its station
     * (counted only while it is not travelling to it) it stops firing and hovers
     * away off the map, and is then removed. Unbounded unless this is called.
     */
    public void stayFor(float seconds) {
        if (!(seconds >= 0f)) throw new IllegalArgumentException("station time cannot be negative");
        this.stationSecondsRemaining = seconds;
    }

    /** Station time left, in seconds; {@link Float#POSITIVE_INFINITY} when unbounded, zero once departing. */
    public float stationSecondsRemaining() {
        return stationSecondsRemaining;
    }

    /** Whether the ship has begun to leave; it fires nothing new from here on. */
    public boolean departing() {
        return departing;
    }

    /** Whether the ship has left the map and is gone from the battle. */
    public boolean departed() {
        return departed;
    }

    float departingSeconds() {
        return departingSeconds;
    }

    void addDepartingSeconds(float dt) {
        departingSeconds += dt;
    }

    /** Counts station time down while on station; returns whether the ship is now due to leave. */
    boolean spendStationTime(float dt) {
        if (departing || stationSecondsRemaining == Float.POSITIVE_INFINITY) return false;
        if (!isMoving()) stationSecondsRemaining = Math.max(0f, stationSecondsRemaining - dt);
        return stationSecondsRemaining <= 0f;
    }

    /** Begins leaving: no order, no director, and every burst in progress is abandoned. */
    void beginDeparture() {
        departing = true;
        stationSecondsRemaining = 0f;
        directing = false;
        order(WarshipOrder.HoldFire.INSTANCE);
        for (Mount m : mounts) {
            m.burstRemaining = 0;
            m.aimed = false;
        }
    }

    void markDeparted() {
        departed = true;
    }

    /**
     * Limits the whole ship to {@code rounds} of area ordnance: every round an
     * area-fire gun lands, and every lance sweep a beam lays, spends one, from
     * one pool shared by all mounts. Point defence is not ordnance and spends
     * nothing. When the pool is empty the mounts go silent. Unbounded unless
     * this is called.
     */
    public void limitOrdnance(int rounds) {
        if (rounds < 0) throw new IllegalArgumentException("an ordnance budget cannot be negative");
        this.ordnanceRemaining = rounds;
    }

    /**
     * Gives every area-fire mount the magazine its own weapon carries, read from
     * the {@code ammo} column of the weapon table (a blank column is a gun that
     * does not run out and stays unlimited). Independent of, and combinable with,
     * the shared pool of {@link #limitOrdnance}.
     */
    public void limitOrdnanceToWeaponAmmo() {
        for (Mount m : mounts) {
            if (m.pointDefence()) continue;
            int ammo = VanillaWeapons.ammo(m.weapon.id());
            if (ammo > 0) m.ammoRemaining = ammo;
        }
    }

    /** Rounds left in the ship-wide pool, or {@link #UNLIMITED}. */
    public int ordnanceRemaining() {
        return ordnanceRemaining;
    }

    /** Whether {@code m} still has a round to spend, from its own magazine and the shared pool. */
    boolean hasOrdnance(Mount m) {
        return !departing && ordnanceRemaining != 0 && m.ammoRemaining != 0;
    }

    /** Spends one round of {@code m}. */
    void spendOrdnance(Mount m) {
        if (ordnanceRemaining > 0) ordnanceRemaining--;
        if (m.ammoRemaining > 0) m.ammoRemaining--;
    }

    // ---- doctrine: posture, stance and the mission's declarations -------------------------------------

    private WarshipDoctrine doctrine;
    private WarshipPosture pinnedPosture;
    private WarshipPosture posture = WarshipPosture.BOMBARD;
    private String postureReason = "no doctrine";
    private float postureSeconds;
    private int postureTransitions;
    private PostureProfile profile = PostureProfile.BOMBARD;
    private MissionStance stance = MissionStance.HOLD;
    private boolean dangerClose;
    private float declaredThreat;
    private float declaredThreatSeconds;
    private final List<float[]> focusPoints = new ArrayList<>();
    private final List<float[]> protectedGround = new ArrayList<>();
    private int friendlyRefusals;
    private boolean holdingForFriendlies;
    private float originalStationX;
    private float originalStationY;
    private boolean standoffApplied;
    private boolean originalHadStation;
    /** Seconds since the last doctrine pass; the system staggers the first one per ship. */
    float doctrineClock = 1f;

    /**
     * Puts the ship's posture in the hands of a {@link WarshipDoctrine}, which
     * re-assesses once a second. A ship without one behaves as it always did
     * (BOMBARD). Pinning a posture ({@link #pinPosture}) stops the doctrine.
     */
    public void useDoctrine() {
        if (doctrine == null) doctrine = new WarshipDoctrine();
    }

    public boolean hasDoctrine() {
        return doctrine != null;
    }

    WarshipDoctrine doctrine() {
        return doctrine;
    }

    /** A mission pins the posture; the doctrine is not consulted while it stands. */
    public void pinPosture(WarshipPosture pinned) {
        if (pinned == null) throw new IllegalArgumentException("pin a posture or unpin");
        this.pinnedPosture = pinned;
        setPosture(pinned, "pinned by the mission");
    }

    /** Hands the posture back to the doctrine (or to BOMBARD when the ship has none). */
    public void unpinPosture() {
        this.pinnedPosture = null;
        if (doctrine == null) setPosture(WarshipPosture.BOMBARD, "no doctrine");
    }

    public WarshipPosture pinnedPosture() {
        return pinnedPosture;
    }

    /** What the ship is trying to do now. */
    public WarshipPosture posture() {
        return posture;
    }

    /** Why, in a sentence; never empty. */
    public String postureReason() {
        return postureReason;
    }

    /** Seconds the ship has held its current posture. */
    public float postureSeconds() {
        return postureSeconds;
    }

    /** How many times the posture has changed, for evidence that it does not flap. */
    public int postureTransitions() {
        return postureTransitions;
    }

    PostureProfile profile() {
        return profile;
    }

    /**
     * Whether the posture lets {@code m} start a burst at directed ground: under
     * a posture that limits bursts to the secondary battery, a mount whose burst
     * costs more than its share of the pool, and every lance, holds fire. Always
     * true for a ship off flux (there is no pool to spend) and for a posture with
     * no such limit.
     */
    boolean mayFireAtGround(Mount m) {
        float share = profile.maxBurstShare();
        if (share <= 0f || !flux.enabled()) return true;
        VanillaWeapon w = m.weapon;
        if (w.beam()) return false;
        return w.fluxPerShot() * w.burstSize() <= share * flux.capacity();
    }

    void ageDoctrine(float dt) {
        postureSeconds += dt;
        if (declaredThreatSeconds > 0f) {
            declaredThreatSeconds -= dt;
            if (declaredThreatSeconds <= 0f) {
                declaredThreatSeconds = 0f;
                declaredThreat = 0f;
            }
        }
    }

    /** Takes {@code next}, with the sentence that explains it. A change restarts the clock and counts as a transition. */
    void setPosture(WarshipPosture next, String reason) {
        postureReason = reason;
        if (next == posture) return;
        posture = next;
        profile = PostureProfile.of(next);
        postureSeconds = 0f;
        postureTransitions++;
        flux.thresholds(profile.softLimit(), profile.resumeLevel());
        // The ground held under the old posture was chosen by its rules; the new one chooses afresh.
        if (!manual) {
            held.clear();
            refreshAreas();
            headingChosen = false;
            retrySeconds = Float.MAX_VALUE;
            for (Mount m : mounts) {
                m.aimed = false;
                // A burst under way was aimed under the old posture's rules.
                m.burstRemaining = 0;
            }
        }
        if (next == WarshipPosture.WITHDRAW) stayFor(0f);
    }

    /** What the mission wants done about its objective; may be changed at any time. */
    public void stance(MissionStance stance) {
        if (stance == null) throw new IllegalArgumentException("a stance is needed");
        this.stance = stance;
    }

    public MissionStance stance() {
        return stance;
    }

    /**
     * Lets the ship fire where friendlies stand within the blast and its margin.
     * Off by default: a weighted director refuses such ground. It still prefers
     * the ground with fewer friendlies. A manual order is never refused either way.
     */
    public void dangerClose(boolean on) {
        this.dangerClose = on;
    }

    public boolean dangerClose() {
        return dangerClose;
    }

    private AirFireOrder airFire = AirFireOrder.AUTO;

    /** What the ship's weapons may fire at in the air; see {@link AirFireOrder}. */
    public void airFire(AirFireOrder order) {
        if (order == null) throw new IllegalArgumentException("an air-fire order is needed");
        this.airFire = order;
    }

    public AirFireOrder airFire() {
        return airFire;
    }

    /**
     * Declares a threat to this ship (the fleet is busy with hostile fleets, a
     * scripted ambush) of {@code strength} that lasts {@code seconds}; the
     * doctrine scores it with the threats it can see. A later declaration
     * replaces an earlier one.
     */
    public void declareAirThreat(float strength, float seconds) {
        if (!(strength >= 0f) || !(seconds >= 0f)) throw new IllegalArgumentException("a threat has a size and a length");
        this.declaredThreat = strength;
        this.declaredThreatSeconds = seconds;
    }

    /** Withdraws a declared threat now. */
    public void clearAirThreat() {
        declaredThreat = 0f;
        declaredThreatSeconds = 0f;
    }

    public float declaredAirThreat() {
        return declaredThreat;
    }

    /** Declares ground the mission wants supported or defended, beside whatever the objectives and squads say. */
    public void declareFocus(float fx, float fy) {
        focusPoints.add(new float[]{fx, fy});
    }

    public void clearFocus() {
        focusPoints.clear();
    }

    List<float[]> declaredFocus() {
        return focusPoints;
    }

    /** Declares friendly ground (an objective, a held keep) a weighted director must not shell, to {@code radius}. */
    public void declareProtected(float px, float py, float radius) {
        protectedGround.add(new float[]{px, py, radius});
    }

    public void clearProtected() {
        protectedGround.clear();
    }

    List<float[]> protectedGround() {
        return protectedGround;
    }

    /** Director passes in which every candidate was refused for friendlies; a starved ship is visible, not mistaken for broken. */
    public int friendlyRefusals() {
        return friendlyRefusals;
    }

    /** Whether the last director pass found hostiles in reach and held fire for friendlies. */
    public boolean holdingForFriendlies() {
        return holdingForFriendlies;
    }

    void noteFriendlyPass(boolean held) {
        holdingForFriendlies = held;
        if (held) friendlyRefusals++;
    }

    /** Remembers the station the mission gave, and moves to {@code (sx, sy)} clear of a hazard. */
    void standoffTo(float sx, float sy) {
        if (!standoffApplied) {
            originalStationX = stationX;
            originalStationY = stationY;
            originalHadStation = hasStation;
            standoffApplied = true;
        }
        stationTo(sx, sy);
    }

    /** Goes back to the station the mission gave once the standoff posture is over. */
    void endStandoff() {
        if (!standoffApplied) return;
        standoffApplied = false;
        if (originalHadStation) {
            stationTo(originalStationX, originalStationY);
        } else {
            hasStation = false;
        }
    }

    /** The held denied landing zone that contains {@code (px, py)}, or {@code null} when none does. */
    Held denialHeldAt(float px, float py) {
        for (Held h : held) if (h.denial && h.area.contains(px, py)) return h;
        return null;
    }

    /** The held denied landing zone whose area is {@code area}, or {@code null} when it is not one. */
    Held denialHeld(WarshipOrder.FireOnArea area) {
        for (Held h : held) if (h.denial && h.area == area) return h;
        return null;
    }

    /**
     * Puts the ship's targeting in the hands of a director: it picks the densest
     * hostile concentration within {@code radius} that is not already being worked,
     * and picks again when that ground is empty. The order it issues is an
     * ordinary {@link WarshipOrder.FireOnArea}.
     */
    public void direct(float radius, boolean bringGunsToBear) {
        direct(radius, bringGunsToBear, DEFAULT_MAX_AREAS);
    }

    /**
     * As {@link #direct(float, boolean)}, holding up to {@code maxAreas} separate
     * areas at once: densest first, none overlapping another, each dropped once
     * it has held no hostile for a few seconds and replaced.
     */
    public void direct(float radius, boolean bringGunsToBear, int maxAreas) {
        if (!(radius > 0f)) throw new IllegalArgumentException("a director needs an area radius");
        if (maxAreas < 1) throw new IllegalArgumentException("a director holds at least one area");
        this.directorRadius = radius;
        this.directorBringsGunsToBear = bringGunsToBear;
        this.directorMaxAreas = maxAreas;
        this.directing = true;
        this.retrySeconds = Float.MAX_VALUE;
        // A directed ship is governed by flux unless its owner said otherwise; a
        // scripted (cinematic) ship with only explicit orders is not.
        if (!fluxConfigured) {
            flux.scale(BRAWLER_FLUX_SCALE);
            flux.enabled(true);
        }
    }

    int directorMaxAreas() {
        return Math.min(directorMaxAreas, profile.maxAreas());
    }

    /** Takes targeting back from the director; the standing order stays until replaced. */
    public void stopDirecting() {
        this.directing = false;
    }

    boolean isDirected() {
        return directing;
    }

    float directorRadius() {
        return directorRadius * profile.radiusScale();
    }

    boolean directorBringsGunsToBear() {
        return directorBringsGunsToBear && profile.bringGunsToBear();
    }

    /**
     * How far from a mount a directed ground target may lie under the current
     * posture, in cells; unlimited unless the posture sets an opportunity range
     * (a share of the ship's longest reach). A manual order is never limited.
     */
    public float opportunityRange() {
        return profile.opportunityShare() > 0f
                ? profile.opportunityShare() * WarshipDirector.reach(this) : Float.POSITIVE_INFINITY;
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
                handling, w * k, toughness[0], toughness[1], faction, x, y, facingDegrees, mounts,
                fluxFigures(hullId), WarshipShield.read(hull, hullRow(hullId)));
    }

    /** The hull's row of the hull table, or {@code null} when it has none. */
    private static Map<String, String> hullRow(String hullId) {
        try {
            for (Map<String, String> row : VanillaData.csv("data/hulls/ship_data.csv")) {
                if (hullId.equals(row.get("id"))) return row;
            }
        } catch (Exception e) {
            // A hull whose row cannot be read has no shield.
        }
        return null;
    }

    private static final float DEFAULT_FLUX_CAPACITY = 10000f;
    private static final float DEFAULT_FLUX_DISSIPATION = 500f;

    /** {@code [max flux, flux dissipation]} from the hull table, or a middling capital's. */
    private static float[] fluxFigures(String hullId) {
        try {
            for (Map<String, String> row : VanillaData.csv("data/hulls/ship_data.csv")) {
                if (!hullId.equals(row.get("id"))) continue;
                return new float[]{
                        positive(row.get("max flux"), DEFAULT_FLUX_CAPACITY),
                        positive(row.get("flux dissipation"), DEFAULT_FLUX_DISSIPATION)};
            }
        } catch (Exception e) {
            // A hull whose flux cannot be read has a middling capital's.
        }
        return new float[]{DEFAULT_FLUX_CAPACITY, DEFAULT_FLUX_DISSIPATION};
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
        return bears(m, shipFacing, mx, my, tx, ty, 1f);
    }

    /** Standoff bombardment is an explicit stationary-structure envelope. */
    public static final float STRUCTURAL_RANGE_MULTIPLIER = 3f;

    float groundRangeMultiplier(float tx, float ty) {
        return areas().stream().anyMatch(a -> a.bombardStructures() && a.contains(tx, ty))
                ? STRUCTURAL_RANGE_MULTIPLIER : 1f;
    }

    static boolean bears(Mount m, float shipFacing, float mx, float my, float tx, float ty, float rangeMultiplier) {
        float dx = tx - mx;
        float dy = ty - my;
        float range = WarshipScale.cells(m.weapon.rangePx()) * rangeMultiplier;
        if (dx * dx + dy * dy > range * range) return false;
        float bearing = WarshipFacing.bearing(mx, my, tx, ty);
        float diff = Math.abs(WarshipFacing.wrap(bearing - (shipFacing + m.slot.angleDegrees())));
        return diff <= Math.max(m.slot.arcDegrees(), 1f) * 0.5f;
    }
}
