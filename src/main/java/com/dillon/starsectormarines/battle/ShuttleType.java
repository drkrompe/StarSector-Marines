package com.dillon.starsectormarines.battle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Static config for each shuttle variant — sprite path, capacity, flight feel,
 * and the vanilla hull IDs that map to this type when scanning the player's
 * fleet for available transports. Each entry's sprite resolves against the
 * vanilla install, so we don't ship any of these textures.
 *
 * <p>Character per type (current pass):
 * <ul>
 *   <li>{@link #AEROSHUTTLE} — the small VTOL dropship, employer-provided default.
 *       Quick in/out, light troop capacity.</li>
 *   <li>{@link #MUDSKIPPER} — small civilian landing craft. Same capacity as
 *       Aeroshuttle, faster.</li>
 *   <li>{@link #BUFFALO} — civilian freighter pressed into service. Heavier,
 *       slower, bigger troop drop.</li>
 *   <li>{@link #VALKYRIE} — dedicated personnel transport. Biggest drop, slowest
 *       cycle. Future: loiter on station providing fire support after deboard.</li>
 * </ul>
 *
 * <p>Future work — Valkyrie's character is meant to include cover fire and a
 * post-deboard loiter window. That requires extending the shuttle state machine
 * with a HOVERING phase and adding a weapon model; this enum is the surface
 * where those flags would live (e.g. {@code loiterSec}, {@code hasFireSupport}).
 */
public enum ShuttleType {

    // Employer's default — Aeroshuttle isn't normally a player fleet member
    // (it's a drone wing in vanilla), so leaving its match list empty here
    // means no player ship ever surfaces as "Aeroshuttle". The matching Kite
    // (which actually uses the same sprite in its hull spec) is handled by
    // {@link #KITE} so the player's roster shows the right name.
    AEROSHUTTLE(
            "graphics/ships/aeroshuttle/aeroshuttle_base.png",
            4, 3.0f, 10f, 0.6f),

    KITE(
            // Kite's hull spec points at aeroshuttle_base.png in vanilla —
            // visually identical to AEROSHUTTLE, but tagged as Kite in the
            // briefing so the player sees what's actually in their fleet.
            "graphics/ships/aeroshuttle/aeroshuttle_base.png",
            4, 3.0f, 9f, 0.6f,
            "kite", "kite_original"),

    HERMES(
            "graphics/ships/hermes/hermes_base.png",
            3, 2.8f, 11f, 0.5f,
            "hermes"),

    MUDSKIPPER(
            "graphics/ships/mudskipper/mudskipper.png",
            4, 3.2f, 12f, 0.55f,
            "mudskipper", "mudskipper_mk2"),

    SHEPHERD(
            "graphics/ships/drone_tender.png",
            4, 3.5f, 7f, 0.7f,
            "shepherd"),

    WAYFARER(
            "graphics/ships/wayfarer/wayfarer.png",
            4, 3.5f, 8f, 0.65f,
            "wayfarer"),

    BUFFALO(
            "graphics/ships/buffalo/buffalo_base.png",
            6, 4.0f, 6f, 0.9f,
            "buffalo"),

    TARSUS(
            "graphics/ships/tarsus/tarsus_base.png",
            5, 4.5f, 6f, 0.85f,
            "tarsus"),

    MULE(
            "graphics/ships/mule/mule_base.png",
            6, 4.5f, 7f, 0.75f,
            "mule"),

    NEBULA(
            "graphics/ships/nebula/nebula.png",
            7, 5.0f, 5f, 0.85f,
            "nebula"),

    VALKYRIE(
            "graphics/ships/valkyrie/valkyrie_ap.png",
            8, 5.0f, 7f, 0.8f,
            "valkyrie");

    public final String spritePath;
    public final int capacity;
    public final float visualLengthCells;
    public final float flightSpeed;
    public final float deboardInterval;
    /** Vanilla hull IDs that map to this type when scanning the player's fleet. */
    public final List<String> matchingHullIds;

    ShuttleType(String spritePath, int capacity, float visualLengthCells,
                float flightSpeed, float deboardInterval, String... matchingHullIds) {
        this.spritePath = spritePath;
        this.capacity = capacity;
        this.visualLengthCells = visualLengthCells;
        this.flightSpeed = flightSpeed;
        this.deboardInterval = deboardInterval;
        this.matchingHullIds = Collections.unmodifiableList(Arrays.asList(matchingHullIds));
    }

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
}
