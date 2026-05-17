package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.TurretKind;
import com.dillon.starsectormarines.battle.TurretRole;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Static config for each shuttle variant — sprite, capacity, handling profile,
 * combat capability (hardpoints + HP + fire-support window), and the vanilla
 * hull IDs that map to this type when scanning the player's fleet. Each
 * entry's sprite resolves against the vanilla install, so we don't ship any
 * of these textures.
 *
 * <p>Also acts as the per-type {@link AirHandling} profile. Three rough
 * handling tiers:
 *
 * <ul>
 *   <li><b>Nimble</b> (Aeroshuttle, Mudskipper, Kite, Hermes) — small drop
 *       craft. High turn rate, crisp accel/brake, low lateral damping.</li>
 *   <li><b>Medium</b> (Wayfarer, Shepherd) — utility hulls.</li>
 *   <li><b>Bus</b> (Buffalo, Tarsus, Mule, Nebula, Valkyrie) — heavy
 *       freighters / dedicated transports. Low turn rate, sluggish accel,
 *       high lateral damping so they pendulum into headings.</li>
 * </ul>
 *
 * <p>Combat capability is orthogonal to handling: Valkyrie is a heavy bus
 * AND fully armed (4 hardpoints, 150 HP, 60s loiter), while a Mudskipper is
 * nimble with a single hardpoint and 25s of fire-support fuel.
 * {@link #kitFor} maps {@code (role, hardpoints)} to a concrete mount layout
 * — the default A2G role expands to a mix of Arbalests + a Hephaestus on
 * larger hulls, and a single Heavy Mortar on a one-hardpoint tugboat.
 */
public enum ShuttleType implements AirHandling {

    // Employer's default — Aeroshuttle isn't normally a player fleet member
    // (it's a drone wing in vanilla), so leaving its match list empty here
    // means no player ship ever surfaces as "Aeroshuttle". The matching Kite
    // (which actually uses the same sprite in its hull spec) is handled by
    // {@link #KITE} so the player's roster shows the right name.
    AEROSHUTTLE(
            "graphics/ships/aeroshuttle/aeroshuttle_base.png",
            4, 3.0f, 10f, 0.6f,
            Profiles.NIMBLE, 1, 25f, 60f),

    KITE(
            // Kite's hull spec points at aeroshuttle_base.png in vanilla —
            // visually identical to AEROSHUTTLE, but tagged as Kite in the
            // briefing so the player sees what's actually in their fleet.
            "graphics/ships/aeroshuttle/aeroshuttle_base.png",
            4, 3.0f, 9f, 0.6f,
            Profiles.NIMBLE, 1, 25f, 60f,
            "kite", "kite_original"),

    HERMES(
            "graphics/ships/hermes/hermes_base.png",
            3, 2.8f, 11f, 0.5f,
            Profiles.NIMBLE, 1, 25f, 55f,
            "hermes"),

    MUDSKIPPER(
            "graphics/ships/mudskipper/mudskipper.png",
            4, 3.2f, 12f, 0.55f,
            Profiles.NIMBLE, 1, 25f, 60f,
            "mudskipper", "mudskipper_mk2"),

    SHEPHERD(
            "graphics/ships/drone_tender.png",
            4, 3.5f, 7f, 0.7f,
            Profiles.MEDIUM, 0, 0f, 80f,
            "shepherd"),

    WAYFARER(
            "graphics/ships/wayfarer/wayfarer.png",
            4, 3.5f, 8f, 0.65f,
            Profiles.MEDIUM, 0, 0f, 80f,
            "wayfarer"),

    BUFFALO(
            "graphics/ships/buffalo/buffalo_base.png",
            6, 4.0f, 6f, 0.9f,
            Profiles.BUS, 0, 0f, 100f,
            "buffalo"),

    TARSUS(
            "graphics/ships/tarsus/tarsus_base.png",
            5, 4.5f, 6f, 0.85f,
            Profiles.BUS, 0, 0f, 90f,
            "tarsus"),

    MULE(
            "graphics/ships/mule/mule_base.png",
            6, 4.5f, 7f, 0.75f,
            Profiles.BUS, 0, 0f, 100f,
            "mule"),

    NEBULA(
            "graphics/ships/nebula/nebula.png",
            7, 5.0f, 5f, 0.85f,
            Profiles.BUS, 0, 0f, 120f,
            "nebula"),

    VALKYRIE(
            "graphics/ships/valkyrie/valkyrie_ap.png",
            8, 5.0f, 7f, 0.8f,
            Profiles.BUS, 4, 60f, 150f,
            "valkyrie");

    public final String spritePath;
    public final int capacity;
    public final float visualLengthCells;
    /** Cruise / max forward velocity, cells/sec. Used as the AirHandling#maxSpeed cap. */
    public final float maxSpeed;
    public final float deboardInterval;
    public final HandlingProfile handling;
    /** Number of turret hardpoints. 0 means this type is a pure transport — no fire support, no hover loiter. */
    public final int hardpoints;
    /** Sim-seconds the shuttle will loiter in HOVER_STATION before turning for home. Capped by ammo-dry / HP-threshold exits. 0 when {@link #hardpoints} is 0. */
    public final float fireSupportSec;
    /** Maximum HP for the shuttle as a whole. Drives the pressure-to-leave exit during hover; no damage source exists yet, so the field is wired forward for future anti-air work. */
    public final float maxHp;
    /** Vanilla hull IDs that map to this type when scanning the player's fleet. */
    public final List<String> matchingHullIds;

    ShuttleType(String spritePath, int capacity, float visualLengthCells,
                float maxSpeed, float deboardInterval,
                HandlingProfile handling,
                int hardpoints, float fireSupportSec, float maxHp,
                String... matchingHullIds) {
        this.spritePath = spritePath;
        this.capacity = capacity;
        this.visualLengthCells = visualLengthCells;
        this.maxSpeed = maxSpeed;
        this.deboardInterval = deboardInterval;
        this.handling = handling;
        this.hardpoints = hardpoints;
        this.fireSupportSec = fireSupportSec;
        this.maxHp = maxHp;
        this.matchingHullIds = Collections.unmodifiableList(Arrays.asList(matchingHullIds));
    }

    @Override public float maxSpeed()                 { return maxSpeed; }
    @Override public float accel()                    { return handling.accel; }
    @Override public float brakingAccel()             { return handling.brakingAccel; }
    @Override public float maxTurnRateDegPerSec()     { return handling.maxTurnRateDegPerSec; }
    @Override public float lateralDriftDamping()      { return handling.lateralDriftDamping; }
    @Override public float stationDamping()           { return handling.stationDamping; }

    /**
     * Returns the {@code ShuttleType} that handles a given vanilla hull id, or
     * {@code null} if the hull doesn't qualify as a marine transport in this
     * mod's whitelist. Tightens the gating beyond "any civilian ship" — Atlas /
     * Prometheus / Shepherd / Hermes etc. all return null here.
     */
    public static ShuttleType forHullId(String hullId) {
        if (hullId == null) return null;
        for (ShuttleType t : values()) {
            if (t.matchingHullIds.contains(hullId)) return t;
        }
        return null;
    }

    /**
     * Expands a {@link TurretRole} into the concrete {@link TurretMount}
     * layout for a hull of {@code hardpoints} mount points. {@code null} role
     * or zero hardpoints returns an empty array — these shuttles skip
     * HOVER_STATION entirely and depart as pure transports.
     *
     * <p>Today only A2G has a populated kit. The AA / POINT_DEFENSE branches
     * return empty until anti-air entities and in-flight missile targeting
     * are surfaced as real targets — the role itself is honored, but the
     * mount loadout has nothing meaningful to mount.
     */
    public static TurretMount[] kitFor(TurretRole role, int hardpoints) {
        if (role == null || hardpoints <= 0) return new TurretMount[0];
        switch (role) {
            case A2G:           return a2gKit(hardpoints);
            case AA:
            case POINT_DEFENSE:
            default:            return new TurretMount[0];
        }
    }

    /**
     * Default A2G fits — small craft get a single Heavy Mortar (heavy hitter
     * with slow rotation suits a stationary hover); larger hulls fan out
     * Arbalests plus a Hephaestus for sustained DPS. Mount offsets are in
     * the shuttle's local frame: +Y is toward the nose, +X is the right side
     * of the hull, in cells.
     */
    private static TurretMount[] a2gKit(int hardpoints) {
        switch (hardpoints) {
            case 1: return new TurretMount[]{
                    new TurretMount(TurretKind.HEAVY_MORTAR, 0f, 0f),
            };
            case 2: return new TurretMount[]{
                    new TurretMount(TurretKind.ARBALEST, -0.6f, +0.6f),
                    new TurretMount(TurretKind.ARBALEST, +0.6f, +0.6f),
            };
            case 3: return new TurretMount[]{
                    new TurretMount(TurretKind.ARBALEST, -0.6f, +0.6f),
                    new TurretMount(TurretKind.ARBALEST, +0.6f, +0.6f),
                    new TurretMount(TurretKind.HEPHAESTUS, 0f, -0.8f),
            };
            case 4:
            default: return new TurretMount[]{
                    new TurretMount(TurretKind.ARBALEST, -0.7f, +0.8f),
                    new TurretMount(TurretKind.ARBALEST, +0.7f, +0.8f),
                    new TurretMount(TurretKind.ARBALEST, 0f, +0.3f),
                    new TurretMount(TurretKind.HEPHAESTUS, 0f, -0.9f),
            };
        }
    }

    /**
     * Bundle of the per-tier handling tunables. Lives as a tier-shared record
     * so per-type lines stay readable — only sprite, capacity, length, speed,
     * and the tier tag vary case-to-case. Re-tune by editing the three
     * constants below.
     */
    public static final class HandlingProfile {
        public final float accel;
        public final float brakingAccel;
        public final float maxTurnRateDegPerSec;
        public final float lateralDriftDamping;
        public final float stationDamping;

        public HandlingProfile(float accel, float brakingAccel, float maxTurnRateDegPerSec,
                               float lateralDriftDamping, float stationDamping) {
            this.accel = accel;
            this.brakingAccel = brakingAccel;
            this.maxTurnRateDegPerSec = maxTurnRateDegPerSec;
            this.lateralDriftDamping = lateralDriftDamping;
            this.stationDamping = stationDamping;
        }
    }

    /**
     * Tier presets. In a nested class because enum constant initializers run
     * BEFORE the enclosing enum's static fields are assigned — Java rejects
     * the forward reference at compile time. Pulling them into a separate
     * type sidesteps that ordering trap and keeps the per-tier numbers
     * grouped for quick re-tuning.
     */
    public static final class Profiles {
        /** Small drop craft — twitchy. */
        public static final HandlingProfile NIMBLE = new HandlingProfile(10f, 12f, 130f, 3.0f, 5.0f);
        /** Utility hulls — middle of the road. */
        public static final HandlingProfile MEDIUM = new HandlingProfile( 6f,  7f,  75f, 4.0f, 6.0f);
        /** Heavy freighters / dedicated transports — hovering bus. */
        public static final HandlingProfile BUS    = new HandlingProfile( 3f,  4f,  40f, 6.0f, 8.0f);

        private Profiles() {}
    }
}
