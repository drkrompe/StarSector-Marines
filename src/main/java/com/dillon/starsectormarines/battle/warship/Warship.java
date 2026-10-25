package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
        /** The velocity, in cells per second, of the unit {@code aimX/aimY} was taken from; zero for open ground. */
        float aimVx;
        float aimVy;
        /** Whether {@code aimX/aimY} is a live choice the barrel is swinging to. */
        boolean aimed;
        /**
         * Set by the hull fire on a tick it holds its next round (cooling down, venting, waiting on the volley or a
         * missile window) with a hull in reach and arc: the barrel stays laid on {@link #hullLayBearing} rather than
         * swinging back to rest between rounds. Cleared every tick the hull fire looks at the mount.
         */
        boolean hullLaid;
        float hullLayBearing;
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
        /** The magazine's size on the duel footing, or {@code -1} when it has none. */
        int ammoMax = UNLIMITED;
        /** Rounds a regaining magazine takes back per hull-fight second (the table's {@code ammo/sec}); zero for none. */
        float reloadPerSecond;
        /** Rounds a regaining magazine takes back at a time (the table's {@code reload size}). */
        int reloadSize = 1;
        /** Rounds earned toward the next reload. */
        float reloadProgress;
        /** Rounds this mount's magazine has taken back over the battle, for evidence. */
        int reloaded;
        /** Whether the plating cell under this mount's slot has been holed through, taking the mount with it. */
        boolean knockedOut;
        /** When a silenced mount comes back into action, in battle seconds; NaN while it is not being repaired. */
        float repairAt = Float.NaN;
        /** When this mount was last brought back into action, in battle seconds; NaN if never. */
        float repairedAt = Float.NaN;
        /** Whether the mount is gone for good: its section was holed again after its repair. */
        boolean lost;

        /** Whether this mount is out of action: silenced by a breach under it, or lost for good. */
        public boolean knockedOut() {
            return knockedOut;
        }

        /** Whether this mount is gone for good rather than silenced and under repair. */
        public boolean lost() {
            return lost;
        }

        /** Rounds left in this mount's own magazine, or {@code -1} when it is unlimited. */
        public int ammoRemaining() {
            return ammoRemaining;
        }

        /** The magazine's size on the duel footing, or {@code -1} when it has none. */
        public int ammoMax() {
            return ammoMax;
        }

        /** Rounds this mount's magazine has taken back over the battle. */
        public int reloaded() {
            return reloaded;
        }

        Mount(WarshipSlot slot, VanillaWeapon weapon) {
            this.slot = slot;
            this.weapon = weapon;
        }

        /** Whether this mount only shoots down ordnance; it is {@link WarshipPointDefence}'s, not an area-fire gun. */
        public boolean pointDefence() {
            return weapon.pointDefenceOnly();
        }

        /** Which battery a pilot lays this mount's area with: missiles apart, every other area-fire gun (lances included) together. */
        public FireGroup group() {
            return weapon.missile() ? FireGroup.MISSILES : FireGroup.GUNS;
        }

        /** Whether this is a beam strong enough to cut ground; weaker beams are anti-ship and silent against it. */
        public boolean lance() {
            return weapon.beam() && weapon.damagePerSecond() >= WarshipSystem.LANCE_MIN_DPS;
        }

        /** Seconds until the mount may start another burst; zero or less when it is ready. */
        public float cooldownSeconds() {
            return cooldown;
        }

        /** Rounds left in the burst under way. */
        public int burstRemaining() {
            return burstRemaining;
        }

        /** Whether a lance sweep is committed and still lasting. */
        public boolean sweeping() {
            return lance != null;
        }
    }

    /** The two batteries a pilot lays areas for. Point defence is neither: it shoots at the air on its own. */
    public enum FireGroup { GUNS, MISSILES }

    /** A hit whose strength is under this share of the armour glances off and does nothing. */
    static final float GLANCE_SHARE = 0.1f;
    private static final float DEFAULT_HIT_POINTS = 10000f;
    private static final float DEFAULT_ARMOUR = 1000f;

    /** The hull's handling from its table row, in cells for travel and degrees for turning. */
    private record Handling(float maxSpeed, float accel, float decel, float turnRate, float turnAccel) {}

    /** A battleship's handling in atmosphere, used for a hull the table does not list. */
    private static final Handling DEFAULT_HANDLING = new Handling(1.1f,
            1.1f / AtmosphericHandling.SPOOL_SECONDS, 1.1f / AtmosphericHandling.SPOOL_SECONDS,
            4f, 4f / AtmosphericHandling.TURN_SPOOL_SECONDS);

    /** The value a getter reports for a budget that was never set. */
    public static final int UNLIMITED = -1;
    /** The longest a ship takes to leave the map once it has begun to depart before it is removed anyway. */
    public static final float DEPARTURE_LIMIT_SECONDS = 180f;
    /**
     * The outer limit for a hull flying out across its side's edge ({@link #leaveAcrossEdge}): long enough for the
     * slowest hull to cross the widest map, so it is a safety net and never the way such a hull leaves.
     */
    public static final float EDGE_EXIT_LIMIT_SECONDS = 900f;

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
    private final List<WarshipOrder.FireOnArea> allAreas = new ArrayList<>();
    private final List<WarshipOrder.FireOnArea> areasReadOnly = Collections.unmodifiableList(allAreas);
    /** Areas a pilot has laid, one per battery; each stands in for the held areas for its own battery only. */
    private final EnumMap<FireGroup, WarshipOrder.FireOnArea> laid = new EnumMap<>(FireGroup.class);
    private final EnumMap<FireGroup, List<WarshipOrder.FireOnArea>> laidLists = new EnumMap<>(FireGroup.class);
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
    /** The heading wanted on the previous tick, so a helm can lead a heading that is drifting; NaN before the first. */
    float previousDesiredHeading = Float.NaN;
    /** Seconds until another tube on this hull may start a missile volley ({@link MissilePacing}). */
    private float missileGate;

    /** Whether a tube on this hull may start a missile volley now. */
    boolean missileVolleyReady() {
        return missileGate <= 0f;
    }

    /** A tube has started a volley: the others wait {@link MissilePacing#SHIP_STAGGER_SECONDS}. */
    void missileVolleyStarted() {
        missileGate = MissilePacing.SHIP_STAGGER_SECONDS;
    }

    void tickMissileGate(float dt) {
        missileGate = Math.max(0f, missileGate - dt);
    }
    private boolean headingChosen;
    private float hitPoints;
    /** The hull's armour grid, or null for a hull fitted without one. */
    private WarshipPlating plating;
    /** The hull table's {@code tech/manufacturer} column, or empty when it has none. */
    private String techStyle = "";
    /** The ship-support source this hull was called from, or null for any ship not called that way. */
    private ShipSupportSource supportSource;
    /** Seconds of station time left, counted down while the ship is on station; infinite when unbounded. */
    private float stationSecondsRemaining = Float.POSITIVE_INFINITY;
    private boolean departing;
    private float departingSeconds;
    /** Seconds since the ship began dropping through the cloud deck, or negative when it did not enter that way. */
    private float entrySeconds = -1f;
    private boolean departed;
    private boolean physicallyDeparted;
    private float lowHoverWidth;
    private float lowHoverHeight;

    /** Keeps a recovering hull's full authored footprint clear of terrain until climb-out. */
    public void lowHoverFootprint(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f)
            throw new IllegalArgumentException("Low hover needs a finite positive footprint");
        lowHoverWidth = width;
        lowHoverHeight = height;
    }

    /** How many times the shared density this hull's body was fitted at ({@link WarshipScale#HULL_PRESENCE} unless fitted otherwise). */
    private float presence = WarshipScale.HULL_PRESENCE;

    /** How many times the shared density this hull's body is drawn and fitted at. */
    public float presence() { return presence; }

    /** This hull's presence over the standard one: what a body figure read at {@link WarshipScale#hullCells} is multiplied by. */
    public float presenceShare() { return presence / WarshipScale.HULL_PRESENCE; }

    /** Hull-spec pixels to cells at this hull's own presence: {@link WarshipScale#hullCells} for a standard hull. */
    public float hullCells(float hullPixels) { return WarshipScale.cells(hullPixels) * presence; }

    /** Ratio of a berthed body to the same hull in flight; 1 for a ship never put in a berth. */
    private float berthFactor = 1f;
    private boolean berthed;
    /** Seconds since the hull lifted out of its berth; negative while berthed or for a hull never berthed. */
    private float liftSeconds = -1f;
    /** Where a lifting hull's run turns toward; NaN until somebody names it (the system then picks the map centre). */
    private float liftTowardX = Float.NaN, liftTowardY = Float.NaN;
    /** The lift run being flown, or {@code null} before it is planned and once it is through. */
    private float[][] liftRun;
    private int liftIndex;
    private float liftRunLength;
    /** Share of the lift flown so far; it only grows, so a hull pushed back a cell never looks lower again. */
    private float liftDone = 1f;
    /** The station {@link #berth} set, so a lift run that ends with nothing newer does not fly back to the pad. */
    private float berthStationX = Float.NaN, berthStationY = Float.NaN;

    /**
     * Sits the hull in a berth, its drawn body centred on {@code (centreX, centreY)} at its current facing: drawn at
     * true landed scale ({@link LandedScale}), cold and holding its place, until {@link #lift()}. Its fitted geometry
     * (hit shape, slots, shield) stays at flight scale; a berthed hull is not something anything engages.
     */
    public void berth(float centreX, float centreY) {
        if (departing || departed || destroyed()) throw new IllegalStateException("Only a ship present can berth");
        berthed = true;
        berthFactor = LandedScale.berthFactor(presence);
        liftSeconds = -1f;
        liftRun = null;
        liftDone = 0f;
        // The body grows about the centre of gravity, so the sprite's centre sits a scaled pivot away from it.
        double r = Math.toRadians(facingDegrees);
        x = centreX - berthFactor * (pivotX * (float) Math.cos(r) - pivotY * (float) Math.sin(r));
        y = centreY - berthFactor * (pivotX * (float) Math.sin(r) + pivotY * (float) Math.cos(r));
        desiredHeading = facingDegrees;
        stationTo(x, y);
        berthStationX = x;
        berthStationY = y;
    }

    /**
     * Lifts a berthed hull on the ordinary lift run ({@link LandedScale#liftRun}): ahead off the berth and round in a
     * climbing turn toward the middle of the battle, its body easing to flight scale as it flies.
     */
    public void lift() {
        lift(Float.NaN, Float.NaN);
    }

    /** {@link #lift()}, the climbing turn coming round toward {@code (towardX, towardY)}. */
    public void lift(float towardX, float towardY) {
        if (!berthed) return;
        berthed = false;
        liftSeconds = 0f;
        liftTowardX = towardX;
        liftTowardY = towardY;
        liftRun = null;
        liftDone = 0f;
    }

    /** Whether the hull is sitting in a berth and has not lifted. */
    public boolean berthed() { return berthed; }

    /** How far through its lift the hull is: zero berthed, one lifted or never berthed. */
    public float liftProgress() {
        return berthed ? 0f : liftDone;
    }

    /** Seconds since the hull lifted; zero while berthed or never berthed. */
    public float liftSeconds() { return Math.max(0f, liftSeconds); }

    /** Whether the hull is flying its lift run: off the berth and not yet through the turn. */
    public boolean liftRunning() { return liftRun != null; }

    /** The factor the hull's body is drawn at against its flight size: the berth's, easing to one as it lifts. */
    public float bodyScale() {
        if (berthed) return berthFactor;
        return LandedScale.bodyFactor(berthFactor, liftDone);
    }

    /**
     * Advances a lift: plans the run the first tick after {@link #lift()} (turning toward the named point, or toward
     * {@code (defaultX, defaultY)} when none was named), then measures the share flown. A lift still incomplete after
     * {@link LandedScale#LIFT_FALLBACK_SECONDS} is finished by time.
     */
    void addLiftSeconds(float dt, float defaultX, float defaultY) {
        if (berthed || liftSeconds < 0f || liftDone >= 1f) return;
        liftSeconds += dt;
        if (liftRun == null) {
            float tx = Float.isNaN(liftTowardX) ? defaultX : liftTowardX;
            float ty = Float.isNaN(liftTowardY) ? defaultY : liftTowardY;
            liftRun = LandedScale.liftRun(x, y, facingDegrees + 90f, tx, ty, lengthCells * berthFactor);
            liftIndex = 0;
            liftRunLength = Math.max(1f, LandedScale.runLength(x, y, liftRun));
        }
        float flown = LandedScale.runProgress(x, y, liftRun, liftIndex, liftRunLength);
        liftDone = Math.max(liftDone, Math.max(flown, liftSeconds / LandedScale.LIFT_FALLBACK_SECONDS));
        if (liftDone >= 1f) endLiftRun();
    }

    private void endLiftRun() {
        // A hull that was given nowhere else to go holds where its run came out, not back over the empty berth.
        if (liftRun != null && route == null && hasStation && stationX == berthStationX && stationY == berthStationY) {
            float[] end = liftRun[liftRun.length - 1];
            stationTo(end[0], end[1]);
        }
        liftDone = 1f;
        liftRun = null;
    }

    public boolean lowHovering() { return lowHoverWidth > 0f && !departing; }
    public float lowHoverWidth() { return lowHoverWidth; }
    public float lowHoverHeight() { return lowHoverHeight; }
    /** True only when propulsion carried the whole hull beyond the map, never on timeout. */
    public boolean physicallyDeparted() { return physicallyDeparted; }
    void markPhysicallyDeparted() { physicallyDeparted = true; markDeparted(); }
    /** Rounds left in the ship-wide pool, or {@link #UNLIMITED}. */
    private int ordnanceRemaining = UNLIMITED;
    private final List<Mount> mounts;
    private final List<Mount> mountsView;
    private final WarshipFlux flux;
    private final WarshipShield shield;
    private boolean fluxConfigured;
    private HullRefit refit = HullRefit.NONE;
    private MissilePreset missilePreset = MissilePreset.FLAVOURED;

    /**
     * Share of the hull table's flux a directed ship runs on. Capacity and
     * dissipation are halved together, so a brawler's guns surge and rest on a
     * rhythm a player can read (at the hull table's own figures a ground
     * bombardment never nears an Onslaught's capacity). This was once a stand-in
     * for what a shield would take; shields are modelled now ({@link WarshipShield}:
     * upkeep, hits absorbed as hard flux held while raised) and draw on this same
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

    /** How literal this ship's missiles are; {@link MissilePreset#FLAVOURED} unless set. */
    public MissilePreset missilePreset() {
        return missilePreset;
    }

    /** Sets how literal this ship's missiles are. */
    public void setMissilePreset(MissilePreset preset) {
        this.missilePreset = preset == null ? MissilePreset.FLAVOURED : preset;
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

    private int groundShots;

    /** Rounds and lance sweeps this ship has fired at the ground, for evidence. */
    public int groundShots() {
        return groundShots;
    }

    void noteGroundShot() {
        groundShots++;
    }

    /** Whether the automatic shield is locked down by a full pool until it has vented, for evidence and a HUD. */
    public boolean shieldLockedOut() {
        return hasShield() && shield.lockedOut();
    }

    /** The hard share of the flux, as a share of capacity: what absorbed hits put there, held while the shield is up. */
    public float hardFluxFraction() {
        return flux.enabled() ? Math.min(1f, flux.hard() / flux.capacity()) : 0f;
    }

    private int hullHits;

    /** Located hits that reached the hull past any shield, whether or not the plate let anything through; for evidence. */
    public int hullHits() {
        return hullHits;
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

    /**
     * Gives the hull a mission's refit ({@link HullRefit}): its flux pool and shield emitter tuned for one fight. The
     * last refit given stands; {@link HullRefit#NONE} restores the hull as its variant fits it.
     */
    public void refit(HullRefit next) {
        HullRefit r = next == null ? HullRefit.NONE : next;
        flux.refit(r.capacityScale(), r.dissipationScale());
        shield.refit(r.shieldArcBonus(), r.shieldUpkeepScale(), r.shieldEfficiencyScale());
        refit = r;
    }

    /** The refit the hull carries, {@link HullRefit#NONE} unless a mission gave it one. */
    public HullRefit refit() {
        return refit;
    }

    /** Flux dissipation per second now, on the pool's own scale. */
    public float fluxDissipation() {
        return flux.dissipation();
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

    /**
     * Whether the shield takes a round fired from {@code (fromX, fromY)} that came down at {@code (px, py)}: asked
     * of the side the round came from, which is what the arc means. A round with no source (NaN), and every round on
     * a hull with no armour grid (the control), is asked of where it landed, as it always was.
     */
    boolean shieldCoversFrom(float fromX, float fromY, float px, float py) {
        return shieldAbsorbing() && arcCoversFrom(fromX, fromY, px, py);
    }

    /** {@link #arcCovers} asked of the side a round was fired from, by the same rule as {@link #shieldCoversFrom}. */
    boolean arcCoversFrom(float fromX, float fromY, float px, float py) {
        if (plating != null && Float.isFinite(fromX) && Float.isFinite(fromY)) return arcCovers(fromX, fromY);
        return arcCovers(px, py);
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

    /** Every area the ship is firing on now, densest first, then any a pilot laid; empty when it holds fire. */
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
        allAreas.clear();
        allAreas.addAll(areasView);
        allAreas.addAll(laid.values());
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
        for (WarshipOrder.FireOnArea a : laid.values()) if (a.contains(px, py)) return true;
        return false;
    }

    /** Whether {@code (px, py)} lies in an area {@code m} may be dealt: its battery's laid one, else any the ship holds. */
    boolean holdsGround(Mount m, float px, float py) {
        for (WarshipOrder.FireOnArea a : areasFor(m)) if (a.contains(px, py)) return true;
        return false;
    }

    /**
     * The areas {@code m} is dealt from. A pilot's laid area for the mount's battery
     * stands alone for it; a battery with none uses the areas the ship holds.
     */
    List<WarshipOrder.FireOnArea> areasFor(Mount m) {
        if (laid.isEmpty()) return areasView;
        List<WarshipOrder.FireOnArea> own = laidLists.get(m.group());
        return own != null ? own : areasView;
    }

    /**
     * Whether the ship's own limits on directed fire (a posture's range and battery
     * limit) do not apply to what {@code m} is aimed at: the ship was ordered onto
     * the ground by hand, or a pilot laid it.
     */
    boolean exempt(Mount m, float px, float py) {
        if (manual) return true;
        WarshipOrder.FireOnArea own = laid.get(m.group());
        return own != null && own.contains(px, py);
    }

    /** Whether {@code area} is one a pilot laid. */
    boolean isLaid(WarshipOrder.FireOnArea area) {
        for (WarshipOrder.FireOnArea a : laid.values()) if (a == area) return true;
        return false;
    }

    /** The area a pilot has laid for {@code group}, or {@code null}. */
    public WarshipOrder.FireOnArea laidArea(FireGroup group) {
        return laid.get(group);
    }

    /** A laid area moved more than this far asks the hull to choose its heading again. */
    static final float REHEADING_CELLS = 3f;

    /**
     * Lays {@code area} for {@code group}, replacing that battery's earlier one and nothing
     * else. A pilot's area is a hand order for its battery: never refused for a posture's
     * range or a friendly in the blast.
     */
    public void layArea(FireGroup group, WarshipOrder.FireOnArea area) {
        if (group == null || area == null) throw new IllegalArgumentException("lay an area for a battery");
        WarshipOrder.FireOnArea before = laid.put(group, area);
        laidLists.put(group, List.of(area));
        if (before == null || Math.hypot(before.x() - area.x(), before.y() - area.y()) > REHEADING_CELLS) {
            headingChosen = false;
        }
        refreshAreas();
    }

    /** Takes back the area laid for {@code group}; its battery goes back to the areas the ship holds. */
    public void clearLaidArea(FireGroup group) {
        if (laid.remove(group) == null) return;
        laidLists.remove(group);
        refreshAreas();
        headingChosen = false;
        for (Mount mount : mounts) {
            if (mount.pointDefence() || mount.group() != group) continue;
            mount.aimed = false;
            mount.burstRemaining = 0;
        }
    }

    /** Takes back every area a pilot laid. */
    public void clearLaidAreas() {
        for (FireGroup g : FireGroup.values()) clearLaidArea(g);
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

    /** Marks this hull as the one a ship-support call fitted from {@code source}; the battle then records its result. */
    public void calledFrom(ShipSupportSource source) {
        this.supportSource = source;
    }

    /** The source this hull was called from, or null. */
    public ShipSupportSource supportSource() {
        return supportSource;
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

    /**
     * Applies one hit through the armour rule against the hull's full rating and returns the hull points it took. It
     * has no place on the hull, so the plating is untouched: this is for setting a hull up battered, or for damage
     * that is not a round. A destroyed hull takes nothing more.
     */
    public float takeDamage(float damage, float penetration) {
        if (destroyed()) return 0f;
        float taken = Math.min(hitPoints, effectiveDamage(damage, penetration, armour));
        hitPoints -= taken;
        return taken;
    }

    /** Whether hulls fitted from now on carry an armour grid; {@code -Dbattle.warship.platingGrid=false} is the control. */
    public static boolean platingGridEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("battle.warship.platingGrid"));
    }

    /** The hull table's {@code tech/manufacturer} (Low Tech, Midline, High Tech, ...), or empty; for presentation. */
    public String techStyle() {
        return techStyle;
    }

    /** The hull's armour grid, or null for a hull fitted without one (the control, or a hull with no spec). */
    public WarshipPlating plating() {
        return plating;
    }

    /** Gives the hull an armour grid; {@link #fit} does this from the hull's own spec. */
    void plating(WarshipPlating plating) {
        this.plating = plating;
    }

    /**
     * The plating cell a round coming from {@code (fromX, fromY)} toward {@code (toX, toY)} enters, or -1 for a hull
     * with no grid or none of it left. With no source ({@code fromX} NaN, a bomb or a ground blast) the round enters
     * from the side nearest where it landed.
     */
    public int entryCell(float fromX, float fromY, float toX, float toY) {
        if (plating == null) return -1;
        float tu = hullU(toX, toY);
        float tv = hullV(toX, toY);
        float fu;
        float fv;
        if (Float.isFinite(fromX) && Float.isFinite(fromY)) {
            fu = hullU(fromX, fromY);
            fv = hullV(fromX, fromY);
        } else {
            // From outside, along the line from the hull's middle through where it landed; dead on the middle, the nose.
            float du = (tu - 0.5f) * widthCells;
            float dv = (tv - 0.5f) * lengthCells;
            float len = (float) Math.sqrt(du * du + dv * dv);
            if (len < 1e-3f) {
                du = 0f;
                dv = -1f;
                len = 1f;
            }
            float far = 2f * Math.max(widthCells, lengthCells);
            fu = tu + du / len * far / widthCells;
            fv = tv + dv / len * far / lengthCells;
        }
        return plating.entryCell(fu, fv, tu, tv);
    }

    /**
     * Lands one located hit on {@code cell} of the plating and returns the hull points it took: the armour rule read
     * against the plate left there ({@link WarshipPlating#hit}). A hull with no grid, or a hit with no cell left to
     * land on, takes the flat rule ({@link #takeDamage}) or the floor armour respectively.
     */
    public float takeHit(int cell, float damage, float penetration, float nowSeconds) {
        return takeHit(cell, damage, penetration, nowSeconds, 1f);
    }

    /**
     * {@link #takeHit(int, float, float, float)} for a round another hull fired: what the armour rule lets through is
     * divided by {@link WarshipScale#HULL_DURABILITY} on its way to the plate and the hull.
     */
    public float takeHullFightHit(int cell, float damage, float nowSeconds) {
        return takeHit(cell, damage, 1f, nowSeconds, WarshipScale.HULL_DURABILITY);
    }

    private float takeHit(int cell, float damage, float penetration, float nowSeconds, float durability) {
        if (destroyed()) return 0f;
        hullHits++;
        float wear = Math.max(1f, durability);
        if (plating == null) {
            float taken = Math.min(hitPoints, effectiveDamage(damage, penetration, armour) / wear);
            hitPoints -= taken;
            return taken;
        }
        float through;
        if (cell >= 0) {
            through = plating.hit(cell, damage, penetration, nowSeconds, wear);
        } else {
            through = damage * penetration < armour * GLANCE_SHARE ? 0f
                    : effectiveDamage(damage, penetration, armour * WarshipPlating.MIN_ARMOUR_FRACTION) / wear;
        }
        float taken = Math.min(hitPoints, through);
        hitPoints -= taken;
        return taken;
    }

    /** The plating cell a slot sits on, or -1 for a hull with no grid or a slot off the sprite's box. */
    public int slotCell(WarshipSlot slot) {
        if (plating == null) return -1;
        // The slot is placed from the centre of gravity; the grid from the sprite's centre, the pivot away.
        float u = 0.5f + (slot.localX() - pivotX) / widthCells;
        float v = 0.5f - (slot.localY() - pivotY) / lengthCells;
        return plating.cellAt(u, v);
    }

    /**
     * Silences every mount whose slot cell has been breached, abandoning whatever it was doing; returns how many
     * went. The first breach silences a mount for its repair time; a repaired mount whose holed plate is hit again is
     * lost for the rest of the battle.
     */
    public int knockOutBreachedMounts(float now) {
        if (plating == null) return 0;
        int out = 0;
        for (Mount m : mounts) {
            if (m.knockedOut) continue;
            int cell = slotCell(m.slot);
            if (cell < 0 || !plating.breached(cell % plating.cols, cell / plating.cols)) continue;
            if (!Float.isNaN(m.repairedAt)) {
                // A mount already repaired over a holed plate is lost only once that plate is hit again.
                if (plating.lastHitAt(cell % plating.cols, cell / plating.cols) <= m.repairedAt) continue;
                m.lost = true;
            } else {
                m.repairAt = now + MOUNT_REPAIR_SECONDS * WarshipScale.HULL_TEMPO;
            }
            m.knockedOut = true;
            m.burstRemaining = 0;
            m.aimed = false;
            out++;
        }
        return out;
    }

    /**
     * Brings a silenced mount back once its repair is done, and loses a repaired one for good when the holed plate
     * under it is hit again (D2 in {@code warship-tactics.md}).
     */
    void tickMountRepairs(float now) {
        if (plating == null) return;
        for (Mount m : mounts) {
            if (m.lost) continue;
            if (m.knockedOut && !Float.isNaN(m.repairAt) && now >= m.repairAt) {
                m.knockedOut = false;
                m.repairAt = Float.NaN;
                m.repairedAt = now;
            } else if (!m.knockedOut && !Float.isNaN(m.repairedAt)) {
                int cell = slotCell(m.slot);
                if (cell >= 0 && plating.breached(cell % plating.cols, cell / plating.cols)
                        && plating.lastHitAt(cell % plating.cols, cell / plating.cols) > m.repairedAt) {
                    m.knockedOut = true;
                    m.lost = true;
                    m.burstRemaining = 0;
                    m.aimed = false;
                }
            }
        }
    }

    /**
     * The base game's time to bring a disabled weapon back, in seconds, before the hull-fight tempo: a mount silenced
     * by the first breach under it is out this long times {@link WarshipScale#HULL_TEMPO}.
     */
    static final float MOUNT_REPAIR_SECONDS = 10f;

    /**
     * Whether every mount that may fire at a ship is out of action and at least one of them is only silenced under
     * repair, not lost: the ship is disarmed against hulls for now, and will not be once the repair is done.
     */
    public boolean awaitingAntiShipRepair() {
        boolean repairing = false;
        for (Mount m : mounts) {
            if (!WarshipShipRules.mayFireAtHull(m.weapon)) continue;
            if (!m.knockedOut) return false;
            if (!m.lost) repairing = true;
        }
        return repairing;
    }

    /** Mounts lost for good: repaired once over holed plate and hit there again. */
    public int mountsLost() {
        int n = 0;
        for (Mount m : mounts) if (m.lost) n++;
        return n;
    }

    /** Mounts out of action: silenced under repair, or lost with their plating. */
    public int mountsKnockedOut() {
        int n = 0;
        for (Mount m : mounts) if (m.knockedOut) n++;
        return n;
    }

    /** World X of a plating cell's centre. */
    public float cellX(int cell) {
        float lx = (plating.cellU(cell) - 0.5f) * widthCells;
        float ly = (0.5f - plating.cellV(cell)) * lengthCells;
        double r = Math.toRadians(facingDegrees);
        return centerX() + lx * (float) Math.cos(r) - ly * (float) Math.sin(r);
    }

    /** World Y of a plating cell's centre. */
    public float cellY(int cell) {
        float lx = (plating.cellU(cell) - 0.5f) * widthCells;
        float ly = (0.5f - plating.cellV(cell)) * lengthCells;
        double r = Math.toRadians(facingDegrees);
        return centerY() + lx * (float) Math.sin(r) + ly * (float) Math.cos(r);
    }

    /** Normalised across-position of a world point on the hull's sprite box, 0 at the port edge. */
    float hullU(float px, float py) {
        double r = Math.toRadians(facingDegrees);
        float dx = px - centerX();
        float dy = py - centerY();
        float lx = dx * (float) Math.cos(r) + dy * (float) Math.sin(r);
        return 0.5f + lx / widthCells;
    }

    /** Normalised along-position of a world point on the hull's sprite box, 0 at the nose. */
    float hullV(float px, float py) {
        double r = Math.toRadians(facingDegrees);
        float dx = px - centerX();
        float dy = py - centerY();
        float ly = -dx * (float) Math.sin(r) + dy * (float) Math.cos(r);
        return 0.5f - ly / lengthCells;
    }

    /** Where the hull's footprint is centred: the ship's position offset by the sprite's pivot, turned with the heading. */
    public float centerX() {
        double r = Math.toRadians(facingDegrees);
        return x + pivotX * (float) Math.cos(r) - pivotY * (float) Math.sin(r);
    }

    /** See {@link #centerX()}. */
    public float centerY() {
        double r = Math.toRadians(facingDegrees);
        return y + pivotX * (float) Math.sin(r) + pivotY * (float) Math.cos(r);
    }

    /**
     * The gap, in cells, between {@code (px, py)} and the hull's footprint (its
     * sprite rectangle about the sprite's centre); zero inside it.
     */
    public float distanceTo(float px, float py) {
        double r = Math.toRadians(facingDegrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float cx = lowHoverWidth > 0f ? x : centerX();
        float cy = lowHoverWidth > 0f ? y : centerY();
        float dx = px - cx;
        float dy = py - cy;
        float lx = dx * c + dy * s;
        float ly = -dx * s + dy * c;
        float ox = Math.max(0f, Math.abs(lx) - (lowHoverWidth > 0f ? lowHoverHeight : widthCells) * 0.5f);
        float oy = Math.max(0f, Math.abs(ly) - (lowHoverWidth > 0f ? lowHoverWidth : lengthCells) * 0.5f);
        return (float) Math.sqrt(ox * ox + oy * oy);
    }

    boolean headingChosen() {
        return headingChosen;
    }

    void forgetHeadingChoice() {
        headingChosen = false;
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

    /** The player's remote helm intent; targeting and survival remain the ship's. */
    public enum RemoteOrder { NONE, MOVE, HOLD, BUILD }
    private RemoteOrder remoteOrder = RemoteOrder.NONE;
    private record RemoteStation(boolean hasStation, float x, float y, float[][] route,
                                 int index, boolean loops) {}
    private RemoteStation remoteStation;

    public RemoteOrder remoteOrder() { return remoteOrder; }

    /** Captures mission travel once, then replaces travel using ordinary station handling. */
    public void remoteOrder(RemoteOrder order, float x, float y) {
        if (remoteStation == null) remoteStation = new RemoteStation(hasStation, stationX, stationY,
                route == null ? null : route.clone(), routeIndex, routeLoops);
        remoteOrder = order;
        if (order != RemoteOrder.BUILD) {
            flyRoute(null, false);
            stationTo(x, y);
        }
    }

    /** Gives mission travel back without touching station time, weapons or resources. */
    public void remoteAutonomous() {
        if (piloted()) return;
        RemoteStation previous = remoteStation;
        remoteStation = null;
        remoteOrder = RemoteOrder.NONE;
        if (previous == null) return;
        hasStation = previous.hasStation();
        stationX = previous.x();
        stationY = previous.y();
        route = previous.route();
        routeIndex = previous.index();
        routeLoops = previous.loops();
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
        if (liftRun != null) return true;
        return route != null && (routeLoops || routeIndex < route.length - 1);
    }

    /** Index of the waypoint the ship is flying to, or -1 when it has no route. */
    public int routeIndex() {
        return route == null ? -1 : routeIndex;
    }

    /** Moves on to the next waypoint once the ship is within {@code radius} cells of this one. */
    void advanceRoute(float radius) {
        if (liftRun != null) {
            // The lift run is flown first; the mission's own route and station wait untouched behind it.
            float lx = liftRun[liftIndex][0] - x, ly = liftRun[liftIndex][1] - y;
            if (lx * lx + ly * ly > radius * radius) return;
            if (++liftIndex >= liftRun.length) endLiftRun();
            return;
        }
        if (!isPassingThrough()) return;
        float dx = route[routeIndex][0] - x;
        float dy = route[routeIndex][1] - y;
        if (dx * dx + dy * dy > radius * radius) return;
        routeIndex = (routeIndex + 1) % route.length;
        stationTo(route[routeIndex][0], route[routeIndex][1]);
    }

    public boolean hasStation() {
        return hasStation || liftRun != null;
    }

    /** Where the hull is flying to: the next point of its lift run while it flies one, else its station. */
    public float stationX() {
        return liftRun != null ? liftRun[liftIndex][0] : stationX;
    }

    public float stationY() {
        return liftRun != null ? liftRun[liftIndex][1] : stationY;
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

    /** Seconds since departure began; zero before it does. Read by presentation only. */
    public float departingSeconds() {
        return departingSeconds;
    }

    /**
     * Brings the ship in through the cloud deck from where it stands, already pointed at {@code (towardX, towardY)}:
     * its first objective or the hostiles it is coming for. A ship never appears at its station or on the wrong
     * heading; it breaks out of the deck pointed where it is going and closes under its own handling, so the approach
     * is part of the fight. The descent lasts {@link WarshipAtmosphere#DESCENT_SECONDS}, during which it fires nothing
     * and its helm takes no station ({@link #descending}); presentation reads {@link #entrySeconds()} and draws the
     * shadow, glow and descent along the heading.
     */
    public void arriveFromAbove(float towardX, float towardY) {
        float dx = towardX - x;
        float dy = towardY - y;
        arriveFromAboveOnHeading(dx * dx + dy * dy < 1e-6f ? facingDegrees : AirBody.facingToward(dx, dy));
    }

    /** As {@link #arriveFromAbove(float, float)}, on a heading the mission names ({@code facingDegrees}' convention). */
    public void arriveFromAboveOnHeading(float headingDegrees) {
        facingDegrees = headingDegrees;
        desiredHeading = headingDegrees;
        entrySeconds = 0f;
    }

    /** Whether the ship is still coming down through the cloud deck: it fires nothing and takes no duel station yet. */
    public boolean descending() {
        return entrySeconds >= 0f && entrySeconds < WarshipAtmosphere.DESCENT_SECONDS;
    }

    private float visitDwellSeconds = Float.NaN;
    private float visitQuietSeconds;

    /**
     * Makes this ship's stay over the battle one finite visit that belongs to its mission: it leaves through the
     * ordinary departure once no hostile hull has been over the battle for {@code dwellSeconds} on end, and from now
     * on no player command reaches it (remote ship command, direct control, or a power), whatever side it is on.
     * Arriving through the cloud deck is {@link #arriveFromAbove}, which a visit usually starts with.
     */
    public void visit(float dwellSeconds) {
        if (!(dwellSeconds >= 0f)) throw new IllegalArgumentException("a visit's dwell cannot be negative");
        visitDwellSeconds = dwellSeconds;
        visitQuietSeconds = 0f;
    }

    /** Whether the ship is on a mission's visit and so belongs to the mission, not the player. */
    public boolean missionOwned() {
        return !Float.isNaN(visitDwellSeconds);
    }

    /** Seconds on end that no hostile hull has been over the battle, for a ship on a visit; zero otherwise. */
    public float visitQuietSeconds() {
        return visitQuietSeconds;
    }

    /**
     * Counts a visit's quiet: reset while a hostile hull is over the battle, else run up. Returns whether the dwell
     * has been reached, at which point the caller sends the ship away through the ordinary departure.
     */
    boolean tickVisit(float dt, boolean hostileHullOver) {
        if (!missionOwned() || departing) return false;
        visitQuietSeconds = hostileHullOver ? 0f : visitQuietSeconds + dt;
        return !holdsTheField && visitQuietSeconds >= visitDwellSeconds;
    }

    /** Whether the ship arrived through the cloud deck (see {@link #arriveFromAbove()}). */
    public boolean arrivedFromAbove() {
        return entrySeconds >= 0f;
    }

    /** Seconds since the descent began, or a negative number for a ship that did not enter that way. */
    public float entrySeconds() {
        return entrySeconds;
    }

    void addEntrySeconds(float dt) {
        if (entrySeconds >= 0f) entrySeconds += dt;
    }

    void addDepartingSeconds(float dt) {
        departingSeconds += dt;
    }

    /** Counts station time down while on station; returns whether the ship is now due to leave. */
    boolean spendStationTime(float dt) {
        if (departing || stationSecondsRemaining == Float.POSITIVE_INFINITY) return false;
        if (lowPassing()) {
            // A low pass keeps no station: every second of it is charged at the pass rate, and the
            // ship is never sent away mid-run; the departure, if the time is spent, follows the pass.
            stationSecondsRemaining = Math.max(0f, stationSecondsRemaining - LowPass.STATION_COST_FACTOR * dt);
            return false;
        }
        if (!isMoving() || remoteOrder != RemoteOrder.NONE) stationSecondsRemaining = Math.max(0f, stationSecondsRemaining - dt);
        return stationSecondsRemaining <= 0f;
    }

    /** Resumes flight nose-first on the current bearing, using ordinary departure and propulsion. */
    public void departForward(float mapWidth, float mapHeight) {
        if (departing || departed || destroyed()) return;
        beginDeparture();
        desiredHeading = facingDegrees;
        double nose = Math.toRadians(facingDegrees + 90f);
        float reach = (float) Math.hypot(mapWidth, mapHeight) + lengthCells;
        // Two collinear points keep this a through-flight until the entire hull is off map.
        float dx = (float) Math.cos(nose) * reach, dy = (float) Math.sin(nose) * reach;
        flyRoute(new float[][]{{x + dx, y + dy}, {x + 2f * dx, y + 2f * dy}}, false);
    }

    private boolean climbingOut;
    private float climbLength;

    /**
     * Leaves the battle along {@code waypoints} and on over the map edge: a departure (no order, nothing new fired,
     * the lift climbing toward the deck) flown as a through-route at the hull's own speed, the last leg carried on
     * past the edge so the escape is physical ({@link #physicallyDeparted}). The hull stays a hull until it is gone:
     * it can be hit, and its escorts keep it as their lead. The climb is drawn by how much of the way it has flown
     * ({@link #climbSeconds}), so a slow hull is still low when it is still near.
     */
    public void climbOut(float[][] waypoints, float mapWidth, float mapHeight) {
        if (departing || departed || destroyed()) return;
        if (waypoints == null || waypoints.length == 0) throw new IllegalArgumentException("a climb-out needs a route");
        // From a berth, the lift run turns toward the way out before the route takes over.
        lift(waypoints[0][0], waypoints[0][1]);
        beginDeparture();
        float[] last = waypoints[waypoints.length - 1];
        float[] before = waypoints.length > 1 ? waypoints[waypoints.length - 2] : new float[]{x, y};
        float dx = last[0] - before[0];
        float dy = last[1] - before[1];
        float len = (float) Math.hypot(dx, dy);
        float reach = (float) Math.hypot(mapWidth, mapHeight) + lengthCells;
        float[][] route = new float[waypoints.length + 1][];
        for (int i = 0; i < waypoints.length; i++) route[i] = waypoints[i].clone();
        route[waypoints.length] = len > 1e-3f
                ? new float[]{last[0] + dx / len * reach, last[1] + dy / len * reach}
                : new float[]{last[0] + reach, last[1]};
        float total = 0f;
        float px = x;
        float py = y;
        for (float[] p : waypoints) {
            total += (float) Math.hypot(p[0] - px, p[1] - py);
            px = p[0];
            py = p[1];
        }
        climbLength = Math.max(1f, total);
        climbingOut = true;
        flyRoute(route, false);
    }

    private boolean throughDeck;

    /**
     * Leaves the battle straight up through the cloud deck, the reverse of {@link #arriveFromAbove}: a departure with
     * way kept on along the present heading, the hull drawn climbing ({@link #climbSeconds}) and gone from the battle
     * once it is into the deck ({@link WarshipAtmosphere#CLIMB_SECONDS}), not over a map edge. For a hull that has
     * already made its height over the battle, so its escape does not drag the fight to the map's edge. It counts as a
     * climb-out: gone this way it has escaped. Until it is into the deck it can still be hit.
     */
    public void climbThroughDeck(float mapWidth, float mapHeight) {
        if (departing || departed || destroyed()) return;
        departForward(mapWidth, mapHeight);
        climbingOut = true;
        climbLength = 1f;
        throughDeck = true;
    }

    private MapEdge entryEdge;
    private boolean enteringAcrossEdge;
    private float edgeEntrySeconds;
    private float entryMapWidth;
    private float entryMapHeight;
    private MapEdge exitEdge;
    private boolean edgeEscape;
    /** A hull flying in across an edge stops counting as entering after this long, wherever it has got to. */
    static final float EDGE_ENTRY_LIMIT_SECONDS = 90f;

    /**
     * Brings the ship on by flying in across {@code edge}: its hull placed just off the map, {@code along} cells along
     * the edge ({@link MapEdge#point}), already pointed on {@code headingDegrees} and moving at its top speed, with a
     * station ahead on that heading well inside its edge margin. Nothing about it is a descent: it is a ship flying
     * in, seen crossing the edge. Until its hull has cleared its edge margin ({@link WarshipEdgeHelm#margin}) it is
     * {@link #enteringAcrossEdge entering}, and the edge pressure leaves it alone. It can fight as it comes.
     *
     * @throws IllegalArgumentException when the heading does not carry the ship onto the map
     */
    public void enterAcrossEdge(MapEdge edge, float along, float headingDegrees, float mapWidth, float mapHeight) {
        if (departing || departed || destroyed() || berthed) {
            throw new IllegalStateException("Only a ship in flight that is not leaving can fly in");
        }
        double nose = Math.toRadians(headingDegrees + 90f);
        float nx = (float) Math.cos(nose);
        float ny = (float) Math.sin(nose);
        if (nx * edge.outX + ny * edge.outY > -0.25f) {
            throw new IllegalArgumentException("a heading of " + headingDegrees + " does not fly in across " + edge);
        }
        facingDegrees = headingDegrees;
        desiredHeading = headingDegrees;
        previousDesiredHeading = Float.NaN;
        turnVelocity = 0f;
        float[] centre = edge.point(along, 0.5f * lengthCells + 1f, mapWidth, mapHeight);
        x = centre[0] - (centerX() - x);
        y = centre[1] - (centerY() - y);
        velocityX = nx * maxSpeed;
        velocityY = ny * maxSpeed;
        float in = lengthCells + new WarshipEdgeHelm(mapWidth, mapHeight).margin(this) + lengthCells;
        stationTo(x + nx * in, y + ny * in);
        entryEdge = edge;
        entryMapWidth = mapWidth;
        entryMapHeight = mapHeight;
        enteringAcrossEdge = true;
        edgeEntrySeconds = 0f;
    }

    /** Whether the ship is flying in across its edge and has not yet cleared its margin ({@link #enterAcrossEdge}). */
    public boolean enteringAcrossEdge() {
        return enteringAcrossEdge;
    }

    /**
     * Whether the ship is flying in across its edge with its centre still off the map: not yet over the battle,
     * so it neither is fought nor fights a hull. At hull-fight ranges guns reach past the edge, and a hull there would
     * otherwise trade fire before it had crossed into sight.
     */
    public boolean offMapInbound() {
        if (!enteringAcrossEdge) return false;
        float cx = centerX();
        float cy = centerY();
        return cx < 0f || cy < 0f || cx > entryMapWidth || cy > entryMapHeight;
    }

    /** The edge the ship flew in across, or {@code null} for one that did not come on that way. */
    public MapEdge entryEdge() {
        return entryEdge;
    }

    /** Ends an edge entry once the hull has cleared its margin, has begun to leave, or has been at it too long. */
    void tickEdgeEntry(float dt, WarshipEdgeHelm edges) {
        if (!enteringAcrossEdge) return;
        edgeEntrySeconds += dt;
        if (departing || destroyed() || edgeEntrySeconds >= EDGE_ENTRY_LIMIT_SECONDS
                || edges.clearance(this, entryEdge) >= edges.margin(this)) {
            enteringAcrossEdge = false;
        }
    }

    /**
     * Leaves the battle by flying out across {@code edge} as an escape: a departure (no order, nothing new on the
     * ground) flown straight for the nearest point of the edge and on past it, so the hull is gone only once its own
     * propulsion has carried it off the map ({@link #physicallyDeparted}). It is drawn at its hover the whole way,
     * never climbing into the deck. Until it is gone it can be hit, it fires at hostile hulls in reach as it goes, and
     * its escorts keep it as their lead. From a berth, the lift run turns toward the edge first.
     */
    public void leaveAcrossEdge(MapEdge edge, float mapWidth, float mapHeight) {
        leaveAcross(edge, mapWidth, mapHeight, true);
    }

    /** {@link #leaveAcrossEdge}, counted as an escape only when {@code escape}; any other leaving is a departure. */
    void leaveAcross(MapEdge edge, float mapWidth, float mapHeight, boolean escape) {
        if (departing || departed || destroyed()) return;
        float along = edge.along(x, y);
        // One far point straight out past the edge: the hull flies for it at its own speed, keeping clear of other
        // hulls on the way, and is gone once its whole hull is off the map.
        float[] past = edge.point(along, (float) Math.hypot(mapWidth, mapHeight) + lengthCells, mapWidth, mapHeight);
        lift(past[0], past[1]);
        beginDeparture();
        exitEdge = edge;
        edgeEscape = escape;
        enteringAcrossEdge = false;
        flyRoute(new float[][]{past}, false);
    }

    /** Whether the ship is flying out across an edge and has not yet gone ({@link #leaveAcrossEdge}). */
    public boolean leavingAcrossEdge() {
        return exitEdge != null && !departed;
    }

    /** The edge the ship is leaving, or left, across; {@code null} for any other way of leaving. */
    public MapEdge exitEdge() {
        return exitEdge;
    }

    /** Whether the ship is leaving across an edge as an escape rather than an ordinary departure. */
    public boolean escapingAcrossEdge() {
        return exitEdge != null && edgeEscape;
    }

    /** Whether the ship left, or is leaving, up through the cloud deck ({@link #climbThroughDeck}). */
    public boolean throughDeck() {
        return throughDeck;
    }

    /** Whether the ship is leaving along a climb-out route and has not yet gone. */
    public boolean climbingOut() {
        return climbingOut && !departed;
    }

    /**
     * The departure clock presentation draws the climb from: the seconds since departure began, or for a climb-out
     * the share of its route flown, as seconds of {@link WarshipAtmosphere#CLIMB_SECONDS}.
     */
    public float climbSeconds() {
        // Flying out across an edge, the hull is a ship in level flight all the way off the map.
        if (exitEdge != null) return 0f;
        if (!climbingOut || route == null || throughDeck) return departingSeconds;
        float left = 0f;
        float px = x;
        float py = y;
        for (int i = routeIndex; i < route.length - 1; i++) {
            left += (float) Math.hypot(route[i][0] - px, route[i][1] - py);
            px = route[i][0];
            py = route[i][1];
        }
        if (routeIndex >= route.length - 1) left = 0f;
        float flown = 1f - Math.min(1f, left / climbLength);
        return WarshipAtmosphere.CLIMB_SECONDS * flown;
    }

    /** Begins leaving: no order, no director, and every burst in progress is abandoned. */
    void beginDeparture() {
        releaseHelm();
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
        return !departing && hasHullOrdnance(m);
    }

    /**
     * Whether {@code m} has a round to spend at a hull: as {@link #hasOrdnance}, but a departing ship keeps it. A ship
     * leaving a fight (withdrawing, climbing out) still answers a hull that has it in reach as it goes; it neither
     * hunts nor fires on the ground.
     */
    boolean hasHullOrdnance(Mount m) {
        return !m.knockedOut && ordnanceRemaining != 0 && m.ammoRemaining != 0;
    }

    /** Spends one round of {@code m}. */
    void spendOrdnance(Mount m) {
        if (ordnanceRemaining > 0) ordnanceRemaining--;
        if (m.ammoRemaining > 0) m.ammoRemaining--;
    }

    // ---- the helm: what a pilot takes over and what is given back ---------------------------------------

    /** What the ship was doing on its own when a pilot took the helm, to be given back when the pilot lets go. */
    private record Autonomy(boolean directing, float directorRadius, boolean directorBringsGunsToBear,
                            int directorMaxAreas, List<Held> held, boolean manual,
                            boolean hasStation, float stationX, float stationY,
                            float[][] route, int routeIndex, boolean routeLoops,
                            GroundFire groundFire, boolean shieldAuto, boolean shieldUp) {}

    private Autonomy autonomy;
    private long helmGeneration;
    /** Changes at takeover so queued remote input cannot survive a pilot session. */
    public long helmGeneration() { return helmGeneration; }
    private float pilotMoveX;
    private float pilotMoveY;

    /** Whether a pilot has the helm: the hull goes where it is flown and the pilot lays the areas. */
    public boolean piloted() {
        return autonomy != null;
    }

    /**
     * Hands the hull to a pilot. What the ship was doing on its own (its director,
     * the ground it held, its station or route, its fire policy and shield mode)
     * is remembered and given back by {@link #releaseHelm}; meanwhile the doctrine
     * stands still and the station does not pull the hull. The flux, ordnance,
     * shield, armour and point defence stay the ship's own.
     */
    public void takeHelm() {
        if (autonomy != null) return;
        helmGeneration++;
        autonomy = new Autonomy(directing, directorRadius, directorBringsGunsToBear, directorMaxAreas,
                new ArrayList<>(held), manual, hasStation, stationX, stationY,
                route == null ? null : route.clone(), routeIndex, routeLoops,
                groundFire, shield.auto(), shieldUp());
        // The pilot's hands replace the ship's own targeting and station-keeping until the pilot
        // chooses to hand targeting back (the director key) or lets go of the helm.
        route = null;
        hasStation = false;
        directing = false;
        manual = false;
        held.clear();
        refreshAreas();
        pilotMoveX = 0f;
        pilotMoveY = 0f;
        headingChosen = false;
        dropAims();
    }

    /**
     * Gives the ship back to itself as it was when {@link #takeHelm} found it: no area a
     * pilot laid survives, and no hand order the pilot gave either. The hull flies back to
     * its station if it had one. The missile preset is a setting of the ship, not an order,
     * and stays as the pilot left it.
     */
    public void releaseHelm() {
        Autonomy a = autonomy;
        if (a == null) return;
        autonomy = null;
        pilotMoveX = 0f;
        pilotMoveY = 0f;
        laid.clear();
        laidLists.clear();
        held.clear();
        held.addAll(a.held());
        manual = a.manual();
        directing = a.directing();
        directorRadius = a.directorRadius();
        directorBringsGunsToBear = a.directorBringsGunsToBear();
        directorMaxAreas = a.directorMaxAreas();
        retrySeconds = Float.MAX_VALUE;
        route = a.route();
        routeIndex = a.routeIndex();
        routeLoops = a.routeLoops();
        hasStation = a.hasStation();
        stationX = a.stationX();
        stationY = a.stationY();
        refreshAreas();
        headingChosen = false;
        groundFire(a.groundFire());
        if (a.shieldAuto()) shield.auto(true);
        else shield.raise(a.shieldUp());
        dropAims();
    }

    // ---- the low pass: a fast, low run along a line with no station -------------------------------------

    private LowPass lowPass;
    /** The areas a pilot had laid when a pass began, given back when it ends. */
    private final EnumMap<FireGroup, WarshipOrder.FireOnArea> laidBeforePass = new EnumMap<>(FireGroup.class);

    /** The pass the ship is flying or has just flown (its wake still showing), or {@code null}. Read by presentation. */
    public LowPass lowPass() {
        return lowPass;
    }

    /** Whether the ship is flying a low pass now: running in to its line or on it. */
    public boolean lowPassing() {
        return lowPass != null && lowPass.active();
    }

    /**
     * Whether a pass of {@code plan} may begin: a live hull that is not leaving, not already passing, with
     * station time left to pay for it (an unbounded stay always can). The cost is
     * {@link LowPass#STATION_COST_FACTOR} seconds of station time for each second flown, run-in included.
     */
    public boolean canLowPass(LowPass plan) {
        if (plan == null || destroyed() || departing || departed || lowPassing()) return false;
        if (stationSecondsRemaining == Float.POSITIVE_INFINITY) return true;
        return stationSecondsRemaining >= plan.estimatedCost(x, y, maxSpeed, acceleration);
    }

    /**
     * Sends the ship on {@code plan}. Its pilot, director, station and route are not touched: the pass
     * overrides the hull's travel and the guns' ground for as long as it flies, then gives both back
     * (the ship returns to its station or route, or is flown by its pilot again). Returns false when
     * {@link #canLowPass} refuses.
     */
    public boolean beginLowPass(LowPass plan) {
        if (!canLowPass(plan)) return false;
        lowPass = plan;
        laidBeforePass.clear();
        laidBeforePass.putAll(laid);
        silencedPass = false;
        return true;
    }

    private boolean silencedPass;

    /** Stops the pass's guns for the rest of the run (the pilot's hold-fire); the flight carries on. */
    public void silenceLowPass() {
        silencedPass = true;
        if (lowPassing()) restoreLaidAfterPass();
    }

    /** Whether the pass's guns have been held. */
    boolean lowPassSilenced() {
        return silencedPass;
    }

    /** Keeps the pass's firing circle on {@code (cx, cy)}, for every battery the ship has. */
    void layPassFire(float cx, float cy) {
        for (FireGroup g : FireGroup.values()) {
            if (!hasBattery(g)) continue;
            WarshipOrder.FireOnArea have = laid.get(g);
            if (have != null && Math.abs(have.x() - cx) < 0.5f && Math.abs(have.y() - cy) < 0.5f
                    && have.radius() == LowPass.CORRIDOR_HALF_WIDTH_CELLS) continue;
            layArea(g, new WarshipOrder.FireOnArea(cx, cy, LowPass.CORRIDOR_HALF_WIDTH_CELLS, false));
        }
    }

    /** Ends the pass's flight: its guns' ground is given back as it was. The wake stays for the presentation to fade. */
    void finishLowPass() {
        if (!silencedPass) restoreLaidAfterPass();
    }

    private void restoreLaidAfterPass() {
        clearLaidAreas();
        for (Map.Entry<FireGroup, WarshipOrder.FireOnArea> e : laidBeforePass.entrySet()) layArea(e.getKey(), e.getValue());
        laidBeforePass.clear();
        headingChosen = false;
    }

    /** Drops the pass once it has nothing left to show. */
    void clearLowPass() {
        lowPass = null;
    }

    private void dropAims() {
        for (Mount m : mounts) {
            if (m.pointDefence()) continue;
            m.aimed = false;
            m.burstRemaining = 0;
        }
    }

    /** Whether the ship carries any area-fire mount of {@code group}; a pilot has nothing to lay for a battery it lacks. */
    public boolean hasBattery(FireGroup group) {
        for (Mount m : mounts) if (!m.pointDefence() && m.group() == group) return true;
        return false;
    }

    /** The pilot's drive demand: world-axis travel in [-1, 1]; a longer vector is brought back to unit length. */
    public void pilotMove(float moveX, float moveY) {
        float x = Float.isFinite(moveX) ? moveX : 0f;
        float y = Float.isFinite(moveY) ? moveY : 0f;
        float len = (float) Math.sqrt(x * x + y * y);
        if (len > 1f) {
            x /= len;
            y /= len;
        }
        pilotMoveX = x;
        pilotMoveY = y;
    }

    float pilotMoveX() {
        return pilotMoveX;
    }

    float pilotMoveY() {
        return pilotMoveY;
    }

    /** Whether the pilot is asking the hull to travel. */
    public boolean pilotUnderWay() {
        return pilotMoveX != 0f || pilotMoveY != 0f;
    }

    /** The hull's velocity in cells per second, for the pilot's readout and for evidence. */
    public float speed() {
        return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
    }

    /**
     * Turns the ship's own targeting on or off for a pilot. On, the director picks
     * ground for every battery that has no area of the pilot's laid; off, the director
     * stops and the areas it chose are dropped.
     */
    public void pilotDirector(boolean on) {
        if (on) {
            if (!directing) direct(directorRadius > 0f ? directorRadius : PILOT_DIRECTOR_RADIUS, true, directorMaxAreas);
            return;
        }
        directing = false;
        if (!manual) {
            held.clear();
            refreshAreas();
            dropAims();
        }
    }

    /** The area size the director uses when a pilot switches it on for a ship that never had one, in cells. */
    public static final float PILOT_DIRECTOR_RADIUS = 10f;

    /** Steps the ship's ground-fire policy: all batteries, then heavy only, then none, then all again. */
    public void cycleGroundFire() {
        GroundFire[] all = GroundFire.values();
        groundFire(all[(groundFire.ordinal() + 2) % all.length]);
    }

    /** Which batteries may fire at ground. */
    public GroundFire groundFire() {
        return groundFire;
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
    private ShipClass shipClass = ShipClass.DEFAULT;
    /** Whether the hull's own {@code hullSize} is a fighter's: a craft that may overlap a ship, not a ship. */
    private boolean fighterSize;
    private float declaredThreatSeconds;
    private final List<float[]> focusPoints = new ArrayList<>();
    private final List<float[]> protectedGround = new ArrayList<>();
    private int friendlyRefusals;
    private boolean holdingForFriendlies;
    private float originalStationX;
    private float originalStationY;
    private boolean standoffApplied;
    private boolean originalHadStation;
    private Warship escortLead;
    private boolean escortExplicit;
    private float escortAngleDegrees;
    private boolean escortAngleSet;
    private boolean escortApplied;
    private boolean escortOriginalHadStation;
    private float escortOriginalX;
    private float escortOriginalY;

    /**
     * Makes this ship the escort of {@code lead}: an explicit relation for a mission or a script, which stays until
     * the lead is lost or {@link #releaseEscort} is called. The ship takes a doctrine (an escort needs its posture
     * to turn to the air fight) and keeps station near the lead; see {@link WarshipEscort}. Its station before
     * the relation is remembered and restored when the escorting ends.
     */
    public void escort(Warship lead) {
        if (lead == null || lead == this) throw new IllegalArgumentException("an escort needs another ship to follow");
        useDoctrine();
        setEscortLead(lead, true);
    }

    /** Ends the escort relation now (explicit or automatic); the ship goes back to its own station and posture. */
    public void releaseEscort() {
        escortLead = null;
        escortExplicit = false;
        endEscort();
    }

    /** The ship this one escorts, or {@code null}. May name a lead that has since been destroyed or left; the system clears it. */
    public Warship escortLead() {
        return escortLead;
    }

    /** Whether the ship is escorting a ship that is still over the battle. */
    public boolean escorting() {
        return WarshipEscort.liveLead(this) != null;
    }

    boolean escortExplicit() {
        return escortExplicit;
    }

    void setEscortLead(Warship lead, boolean explicit) {
        if (escortLead != lead) escortAngleSet = false;
        escortLead = lead;
        escortExplicit = explicit;
    }

    /** The bearing, in degrees as {@code atan2(dy, dx)}, from the lead at which this escort last chose to sit; NaN until chosen. */
    float escortAngle() {
        return escortAngleSet ? escortAngleDegrees : Float.NaN;
    }

    void escortAngle(float degrees) {
        escortAngleDegrees = degrees;
        escortAngleSet = true;
    }

    /** Remembers the station the mission gave, and moves to {@code (sx, sy)} beside the lead. */
    void escortTo(float sx, float sy) {
        if (!escortApplied) {
            escortOriginalX = stationX;
            escortOriginalY = stationY;
            escortOriginalHadStation = hasStation;
            escortApplied = true;
        }
        stationTo(sx, sy);
    }

    /** Goes back to the station the mission gave once the escorting is over. */
    void endEscort() {
        escortAngleSet = false;
        if (!escortApplied) return;
        escortApplied = false;
        if (escortOriginalHadStation) {
            stationTo(escortOriginalX, escortOriginalY);
        } else {
            hasStation = false;
        }
    }

    /** Seconds since the last doctrine pass; the system staggers the first one per ship. */
    float doctrineClock = 1f;

    /**
     * Puts the ship's posture in the hands of a {@link WarshipDoctrine}, which
     * re-assesses once a second. A ship without one behaves as it always did
     * (BOMBARD). Pinning a posture ({@link #pinPosture}) stops the doctrine.
     */
    public void useDoctrine() {
        if (doctrine == null) doctrine = new WarshipDoctrine();
        if (holdsTheField) doctrine.holdTheField();
    }

    private boolean holdsTheField;

    /**
     * The mission keeps this ship over the battle until it is destroyed: its doctrine (now or later given) never
     * withdraws it, and a visit it is on never ends, so it stays the mission's without leaving. Nothing else about how
     * it fights changes, and a mission that sends it away itself (an edge exit, spent station time) still can.
     */
    public void holdTheField() {
        holdsTheField = true;
        if (doctrine != null) doctrine.holdTheField();
    }

    /** Whether the mission keeps this ship over the battle until it is destroyed. */
    public boolean holdsTheField() {
        return holdsTheField;
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

    /** Re-derives the current profile once the class is known (fit time), so a carrier's bias holds from the start. */
    private void applyClassToProfile() {
        profile = PostureProfile.of(posture, shipClass);
        flux.thresholds(profile.softLimit(), profile.resumeLevel());
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

    /**
     * Sets the flux pair for what the ship is doing: fighting a hull it spends its pool as the hull table allows
     * ({@link WarshipFlux#SOFT_LIMIT} and {@link WarshipFlux#RESUME_LEVEL}), whatever its posture would keep back for
     * the air or the ground; otherwise its posture's pair. A duel between warships fought on half a pool is a duel of
     * shields that never give.
     */
    void fluxFooting(boolean fightingHull) {
        if (fightingHull && HULL_FIGHT_FOOTING) flux.thresholds(WarshipFlux.SOFT_LIMIT, WarshipFlux.RESUME_LEVEL);
        else flux.thresholds(profile.softLimit(), profile.resumeLevel());
    }

    /** {@code -Dbattle.warship.hullFightFooting=false} is the control: a hull fight keeps the posture's flux pair. */
    static final boolean HULL_FIGHT_FOOTING =
            !"false".equalsIgnoreCase(System.getProperty("battle.warship.hullFightFooting"));

    /** Takes {@code next}, with the sentence that explains it. A change restarts the clock and counts as a transition. */
    void setPosture(WarshipPosture next, String reason) {
        postureReason = reason;
        if (next == posture) return;
        posture = next;
        profile = PostureProfile.of(next, shipClass);
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

    private ShipFireOrder shipFire = ShipFireOrder.AUTO;
    /** The hostile hull this ship is fighting, as last chosen by {@link WarshipShipFire}; {@code null} when none. */
    private Warship hullTarget;
    private boolean duelFooting;

    /**
     * Whether the ship's weapons may fire on hostile hulls; see {@link ShipFireOrder}. {@code AUTO} is the default.
     * {@code ALL} puts the ship on its duel footing at once, {@code OFF} takes it off; under {@code AUTO} the ship
     * takes it the first time it turns on a hull.
     */
    public void shipFire(ShipFireOrder order) {
        if (order == null) throw new IllegalArgumentException("a ship-fire order is needed");
        this.shipFire = order;
        if (order == ShipFireOrder.OFF) {
            hullTarget = null;
            duelFooting = false;
            flux.applyFittings(false);
        } else if (order == ShipFireOrder.ALL) {
            duelFooting();
        }
    }

    /**
     * Puts the ship on the base game's footing for a fight between hulls, once: ship against ship keeps the base
     * game's balance, so it carries its variant's capacitors and vents (a ship that has only bombarded keeps the hull
     * table's rhythm its postures were tuned to), and its missiles carry the base game's magazines, since a duel is
     * not fought with a bottomless Sabot rack.
     */
    void duelFooting() {
        if (duelFooting) return;
        duelFooting = true;
        flux.applyFittings(true);
        for (Mount m : mounts) {
            if (!m.weapon.missile() || m.ammoRemaining >= 0) continue;
            int ammo = VanillaWeapons.ammo(m.weapon.id());
            if (ammo <= 0) continue;
            m.ammoRemaining = ammo;
            m.ammoMax = ammo;
            m.reloadPerSecond = VanillaWeapons.ammoPerSecond(m.weapon.id());
            m.reloadSize = VanillaWeapons.reloadSize(m.weapon.id());
        }
    }

    /**
     * Refills the magazines that regain rounds in the base game (a Pilum's, a Swarmer fighter's), at the table's
     * {@code ammo/sec} on the clock {@code dt} is measured on (the hull fight's, so a reload takes the base game's
     * seconds against the hull fight's tempo), in the table's reload size, never past the magazine. A full magazine
     * earns nothing toward the next reload, as in the base game.
     */
    void tickReloads(float dt) {
        if (!duelFooting) return;
        for (Mount m : mounts) {
            if (m.ammoMax <= 0 || m.reloadPerSecond <= 0f || m.lost) continue;
            if (m.ammoRemaining >= m.ammoMax) {
                m.reloadProgress = 0f;
                continue;
            }
            m.reloadProgress += m.reloadPerSecond * dt;
            while (m.reloadProgress >= m.reloadSize && m.ammoRemaining < m.ammoMax) {
                m.reloadProgress -= m.reloadSize;
                int back = Math.min(m.reloadSize, m.ammoMax - m.ammoRemaining);
                m.ammoRemaining += back;
                m.reloaded += back;
            }
        }
    }

    /** Whether the ship is on its duel footing ({@link #duelFooting()}). */
    public boolean onDuelFooting() {
        return duelFooting;
    }

    public ShipFireOrder shipFire() {
        return shipFire;
    }

    /** The hostile hull this ship is turning its guns on, or {@code null}. */
    public Warship hullTarget() {
        return hullTarget;
    }

    void hullTarget(Warship target) {
        this.hullTarget = target;
    }

    private boolean duelHelm = true;
    /** The station the mission gave the ship before it began manoeuvring against a hull; restored when the fight ends. */
    private float[] duelHome;

    /**
     * Whether a ship fighting a hull manoeuvres for it ({@link WarshipDuelHelm}); on by default, so a ship ordered to
     * fight hulls fights them. Off holds the mission's station whatever happens.
     */
    public void duelHelm(boolean on) {
        this.duelHelm = on;
    }

    public boolean duelHelm() {
        return duelHelm;
    }

    /** Whether the helm is flying this ship against a hull now. */
    public boolean manoeuvring() {
        return duelHome != null;
    }

    float[] duelHome() {
        return duelHome;
    }

    void duelHome(float[] station) {
        this.duelHome = station;
    }

    /** What the ship is doing in its group's hull fight: its role, target, side and current manoeuvre. */
    private final WarshipManoeuvre manoeuvre = new WarshipManoeuvre();

    /**
     * Whether this hull is fighter-sized by its own {@code .ship} ({@code hullSize} FIGHTER): such a craft may pass over
     * and under a ship, and hull contact ({@link WarshipSpacing}) is only between hulls of frigate size and up.
     */
    public boolean fighterSize() {
        return fighterSize;
    }

    /** The ship's place in its group's hull fight ({@link WarshipTactics}); readable for a HUD and for evidence. */
    public WarshipManoeuvre manoeuvre() {
        return manoeuvre;
    }

    /** The order this hull was added to its battle in, a stable identity for hashes; -1 until it is added. */
    private int serial = -1;

    int serial() {
        return serial;
    }

    void serial(int value) {
        this.serial = value;
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

    /** What kind of hull this is, read at fit time; a combat capital when the hull had no data to read. */
    public ShipClass shipClass() {
        return shipClass;
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

    /** The station the mission gave, as {@code {x, y}}: the remembered one while a standoff has moved the ship, else the current one. */
    float[] missionStation() {
        if (standoffApplied) return originalHadStation ? new float[]{originalStationX, originalStationY} : new float[]{x, y};
        return hasStation ? new float[]{stationX, stationY} : new float[]{x, y};
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

    /** Whether the ship's own director is choosing ground for it. */
    public boolean isDirected() {
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
        return fit(loadout, faction, x, y, facingDegrees, WarshipScale.HULL_PRESENCE);
    }

    /**
     * As above, with the body at {@code presence} times the shared density rather than
     * {@link WarshipScale#HULL_PRESENCE}: its length, beam, pivot, slots and shield, and so how far its mounts sit from
     * the ground they reach. Gun reach, ground and hull alike, does not change with it.
     */
    public static Warship fit(WarshipLoadout loadout, Faction faction,
                              float x, float y, float facingDegrees, float presence) throws Exception {
        if (!(presence > 0f)) throw new IllegalArgumentException("a hull's presence must be positive");
        // A skin (a Wolf (H), a Hound (P)) is its base hull with its own sprite: the spec, table row and engines are
        // the base's, the picture is the skin's.
        WarshipSkins.Skin skin = WarshipSkins.resolve(loadout.hullId());
        String hullId = skin.baseHullId();
        JSONObject hull = VanillaData.json("data/hulls/" + WarshipSkins.hullFile(hullId) + ".ship");
        List<Mount> mounts = new ArrayList<>();
        float k = WarshipScale.cells(1f) * presence;
        for (WarshipSlot slot : WarshipSlot.parse(hull, k)) {
            VanillaWeapon weapon = VanillaWeapons.get(loadout.weaponInSlot(slot.id()));
            if (weapon == null) continue;
            Mount mount = new Mount(slot, weapon);
            mount.headingDegrees = facingDegrees + slot.angleDegrees();
            mounts.add(mount);
        }
        float w = (float) hull.optDouble("width", 0.0);
        float h = (float) hull.optDouble("height", 0.0);
        JSONArray c = hull.optJSONArray("center");
        // The same pivot every air hull uses: the sprite's centre, less the
        // authored centre of gravity, so the CoG stays fixed at the body.
        float pivotX = c == null ? 0f : (w / 2f - (float) c.optDouble(0)) * k;
        float pivotY = c == null ? 0f : (h / 2f - (float) c.optDouble(1)) * k;
        Handling handling = handling(hullId);
        float[] toughness = toughness(hullId);
        Map<String, String> row = hullRow(hullId);
        String sprite = skin.spriteName() != null ? skin.spriteName() : hull.optString("spriteName");
        Warship ship = new Warship(hullId, sprite, h * k, pivotX, pivotY,
                handling, w * k, toughness[0], toughness[1], faction, x, y, facingDegrees, mounts,
                fluxFigures(hullId), WarshipShield.read(hull, row, k));
        ship.presence = presence;
        if (platingGridEnabled() && w > 0f && h > 0f) {
            JSONArray b = hull.optJSONArray("bounds");
            float[] bounds = null;
            if (b != null) {
                bounds = new float[b.length()];
                for (int i = 0; i < bounds.length; i++) bounds[i] = (float) b.optDouble(i);
            }
            float cx = c == null ? w / 2f : (float) c.optDouble(0);
            float cy = c == null ? h / 2f : (float) c.optDouble(1);
            ship.plating = WarshipPlating.fromHull(bounds, w, h, cx, cy, toughness[1], toughness[0]);
        }
        ship.fighterSize = "FIGHTER".equalsIgnoreCase(hull.optString("hullSize", ""));
        ship.flux.fittings(FLUX_PER_CAPACITOR * Math.max(0, loadout.fluxCapacitors()),
                DISSIPATION_PER_VENT * Math.max(0, loadout.fluxVents()));
        if (row != null) {
            String tech = row.get("tech/manufacturer");
            ship.techStyle = tech == null ? "" : tech.trim();
            ship.shipClass = ShipClass.classify(hull.optString("hullSize", null), row.get("designation"),
                    row.get("hints"), row.get("fighter bays"), row.get("shield type"),
                    row.get("shield arc"), row.get("shield efficiency"));
            ship.applyClassToProfile();
        }
        return ship;
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

    /** Flux capacity one fitted capacitor adds, the base game's figure for every hull size. */
    public static final float FLUX_PER_CAPACITOR = 200f;
    /** Flux dissipation, per second, one fitted vent adds, the base game's figure for every hull size. */
    public static final float DISSIPATION_PER_VENT = 10f;

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

    /** The hull's handling from the table, made heavy for atmosphere ({@link AtmosphericHandling}), or a battleship's. */
    private static Handling handling(String hullId) {
        try {
            for (Map<String, String> row : VanillaData.csv("data/hulls/ship_data.csv")) {
                if (!hullId.equals(row.get("id"))) continue;
                AtmosphericHandling.Figures f = AtmosphericHandling.of(
                        positive(row.get("max speed"), DEFAULT_HANDLING.maxSpeed() / WarshipScale.cells(1f)),
                        positive(row.get("acceleration"), DEFAULT_HANDLING.accel() / WarshipScale.cells(1f)),
                        positive(row.get("deceleration"), DEFAULT_HANDLING.decel() / WarshipScale.cells(1f)),
                        positive(row.get("max turn rate"), DEFAULT_HANDLING.turnRate()),
                        positive(row.get("turn acceleration"), DEFAULT_HANDLING.turnAccel()));
                return new Handling(WarshipScale.cells(f.maxSpeed()), WarshipScale.cells(f.accel()),
                        WarshipScale.cells(f.decel()), f.turnRate(), f.turnAccel());
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

    /** World X of any slot of the hull (a launch bay, say) at the hull's current facing. */
    public float slotX(WarshipSlot slot) {
        return mountX(x, slot, facingDegrees);
    }

    /** World Y of any slot of the hull at the hull's current facing. */
    public float slotY(WarshipSlot slot) {
        return mountY(y, slot, facingDegrees);
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
