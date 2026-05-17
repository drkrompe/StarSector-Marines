package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.air.AirHandling;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Static config for each shuttle variant — sprite path, capacity, and the
 * vanilla hull IDs that map to this type when scanning the player's fleet
 * for available transports. Each entry's sprite resolves against the vanilla
 * install, so we don't ship any of these textures.
 *
 * <p>Also acts as the per-type {@link AirHandling} profile. Three rough
 * tiers shape the per-tier numbers:
 *
 * <ul>
 *   <li><b>Nimble</b> (Aeroshuttle, Mudskipper, Kite, Hermes) — small drop
 *       craft. High turn rate, crisp accel/brake, low lateral damping. Reads
 *       as twitchy.</li>
 *   <li><b>Medium</b> (Wayfarer, Shepherd) — utility hulls. Middling on every
 *       axis.</li>
 *   <li><b>Bus</b> (Buffalo, Tarsus, Mule, Nebula, Valkyrie) — heavy
 *       freighters / dedicated transports. Low turn rate, sluggish accel,
 *       high lateral damping so they pendulum into headings instead of
 *       drifting sideways. Reads as "hovering bus."</li>
 * </ul>
 *
 * <p>Character per type for combined-arms work (future): Valkyrie is the
 * type that gets fire-support turrets and a post-deboard loiter; this enum
 * is the surface where those flags would live.
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
            Profiles.NIMBLE),

    KITE(
            // Kite's hull spec points at aeroshuttle_base.png in vanilla —
            // visually identical to AEROSHUTTLE, but tagged as Kite in the
            // briefing so the player sees what's actually in their fleet.
            "graphics/ships/aeroshuttle/aeroshuttle_base.png",
            4, 3.0f, 9f, 0.6f,
            Profiles.NIMBLE,
            "kite", "kite_original"),

    HERMES(
            "graphics/ships/hermes/hermes_base.png",
            3, 2.8f, 11f, 0.5f,
            Profiles.NIMBLE,
            "hermes"),

    MUDSKIPPER(
            "graphics/ships/mudskipper/mudskipper.png",
            4, 3.2f, 12f, 0.55f,
            Profiles.NIMBLE,
            "mudskipper", "mudskipper_mk2"),

    SHEPHERD(
            "graphics/ships/drone_tender.png",
            4, 3.5f, 7f, 0.7f,
            Profiles.MEDIUM,
            "shepherd"),

    WAYFARER(
            "graphics/ships/wayfarer/wayfarer.png",
            4, 3.5f, 8f, 0.65f,
            Profiles.MEDIUM,
            "wayfarer"),

    BUFFALO(
            "graphics/ships/buffalo/buffalo_base.png",
            6, 4.0f, 6f, 0.9f,
            Profiles.BUS,
            "buffalo"),

    TARSUS(
            "graphics/ships/tarsus/tarsus_base.png",
            5, 4.5f, 6f, 0.85f,
            Profiles.BUS,
            "tarsus"),

    MULE(
            "graphics/ships/mule/mule_base.png",
            6, 4.5f, 7f, 0.75f,
            Profiles.BUS,
            "mule"),

    NEBULA(
            "graphics/ships/nebula/nebula.png",
            7, 5.0f, 5f, 0.85f,
            Profiles.BUS,
            "nebula"),

    VALKYRIE(
            "graphics/ships/valkyrie/valkyrie_ap.png",
            8, 5.0f, 7f, 0.8f,
            Profiles.BUS,
            "valkyrie");

    public final String spritePath;
    public final int capacity;
    public final float visualLengthCells;
    /** Cruise / max forward velocity, cells/sec. Used as the AirHandling#maxSpeed cap. */
    public final float maxSpeed;
    public final float deboardInterval;
    public final HandlingProfile handling;
    /** Vanilla hull IDs that map to this type when scanning the player's fleet. */
    public final List<String> matchingHullIds;

    ShuttleType(String spritePath, int capacity, float visualLengthCells,
                float maxSpeed, float deboardInterval,
                HandlingProfile handling,
                String... matchingHullIds) {
        this.spritePath = spritePath;
        this.capacity = capacity;
        this.visualLengthCells = visualLengthCells;
        this.maxSpeed = maxSpeed;
        this.deboardInterval = deboardInterval;
        this.handling = handling;
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
