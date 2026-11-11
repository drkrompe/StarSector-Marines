package com.dillon.starsectormarines.battle.world.model;

import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Authored environmental lighting. Power changes presentation, never sight or navigation.
 *
 * <p>{@code darkness} falls on open ground and {@code roofedDarkness} on ground under an intact roof
 * ({@link CellTopology#isRoofIntact}): equal on a night map, apart in a sealed ruin whose plant is black under its roof
 * while daylight falls through its light wells and broken roof ({@code ruins-expedition.md}, Light).
 *
 * <p>{@code poweredAt} holds the battle seconds at which a circuit came on, for those that came on during the battle:
 * their lamps warm up from that moment, so a frame drawn at any later time shows them as they would be then. A powered
 * circuit with no time is lit as laid.
 */
public record NightLighting(float darkness, float roofedDarkness, List<Lamp> lamps, Set<String> poweredCircuits,
                            Map<String, Float> poweredAt) {
    public enum Fixture {
        STRIP, TRIPOD_FLOODLIGHT,
        /** No fixture drawn: the light belongs to its own art, as a hydroponic shed's lit roof does; only its pool. */
        GLOW,
        /** A ruin's caged ceiling strip, drawn from its painted art lit or dark; its bearing lays its long axis. */
        CEILING_STRIP,
        /** A ruin's round high-bay lamp over a hall. */
        HIGH_BAY,
        /** A ruin's emergency strip, on its own circuit when all else is dark. */
        EMERGENCY_STRIP,
        /** A ruin's alarm beacon: the watch's lamps and its bays'. */
        BEACON
    }

    /**
     * One light. {@code strength} scales its pool, so a residual strip lights its floor only dimly; {@code tint} scales
     * how hard its colour stains what it lights, so a dim red emergency strip reads red rather than as dim daylight.
     */
    public record Lamp(float x, float y, float radius, float red, float green, float blue,
                       String circuit, Fixture fixture, float bearingDegrees, float strength, float tint) {
        /** A lamp at full strength and the ordinary tint. */
        public Lamp(float x, float y, float radius, float red, float green, float blue,
                    String circuit, Fixture fixture, float bearingDegrees) {
            this(x, y, radius, red, green, blue, circuit, fixture, bearingDegrees, 1f, 1f);
        }
        /** Existing ceiling/wall strips retain their appearance and symmetric pool. */
        public Lamp(float x, float y, float radius, float red, float green, float blue, String circuit) {
            this(x, y, radius, red, green, blue, circuit, Fixture.STRIP, 0);
        }
        public Lamp {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(radius) || radius <= 0
                    || circuit == null || circuit.isBlank() || fixture == null
                    || !Float.isFinite(bearingDegrees) || !(strength > 0 && strength <= 1)
                    || !(tint >= 1 && tint <= 8)) throw new IllegalArgumentException("Invalid yard light");
        }
        public float pool(float px, float py) {
            float dx = px - x, dy = py - y;
            float falloff = Math.max(0f, 1f - (dx * dx + dy * dy) / (radius * radius));
            return falloff * falloff * strength;
        }
    }

    /** A night as dark under a roof as in the open. */
    public NightLighting(float darkness, List<Lamp> lamps, Set<String> poweredCircuits) {
        this(darkness, darkness, lamps, poweredCircuits);
    }

    /** A night whose powered circuits are all lit as laid. */
    public NightLighting(float darkness, float roofedDarkness, List<Lamp> lamps, Set<String> poweredCircuits) {
        this(darkness, roofedDarkness, lamps, poweredCircuits, Map.of());
    }

    public NightLighting {
        if (!readable(darkness) || !readable(roofedDarkness))
            throw new IllegalArgumentException("Night must retain readable ground");
        lamps = List.copyOf(lamps);
        poweredCircuits = Set.copyOf(poweredCircuits);
        poweredAt = Map.copyOf(poweredAt);
    }

    private static boolean readable(float darkness) {
        return Float.isFinite(darkness) && darkness >= 0 && darkness <= .9f;
    }

    /**
     * Whether a cell takes the roofed darkness: it is under an intact roof, or it is a wall standing against such a
     * cell, so a dark room's walls are dark with it rather than outlined in daylight.
     */
    public static boolean roofed(CellTopology ground, int x, int y) {
        if (ground.isRoofIntact(x, y)) return true;
        if (!ground.isWall(x, y)) return false;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if ((dx != 0 || dy != 0) && ground.isRoofIntact(x + dx, y + dy)) return true;
        return false;
    }

    /** The darkness over a cell: under an intact roof, or in the open. */
    public float darknessAt(boolean roofed) { return roofed ? roofedDarkness : darkness; }

    public boolean powered(Lamp lamp) { return poweredCircuits.contains(lamp.circuit()); }

    /** The battle seconds at which a lamp's circuit came on, or NaN when it is lit as laid or unpowered. */
    public float poweredAt(Lamp lamp) {
        Float at = poweredAt.get(lamp.circuit());
        return at == null ? Float.NaN : at;
    }

    /** The same night and lamps with exactly {@code circuits} powered, all lit as laid; this one when they already are. */
    public NightLighting withPowered(Set<String> circuits) {
        if (poweredCircuits.equals(circuits)) return this;
        return new NightLighting(darkness, roofedDarkness, lamps, circuits, keep(circuits));
    }

    /**
     * The same night and lamps with exactly {@code circuits} powered, those newly on coming on at battle second
     * {@code seconds}; this one when they already are.
     */
    public NightLighting withPowered(Set<String> circuits, float seconds) {
        if (poweredCircuits.equals(circuits)) return this;
        Map<String, Float> at = keep(circuits);
        for (String circuit : circuits) if (!poweredCircuits.contains(circuit)) at.put(circuit, seconds);
        return new NightLighting(darkness, roofedDarkness, lamps, circuits, at);
    }

    /** When the circuits still powered among {@code circuits} came on. */
    private Map<String, Float> keep(Set<String> circuits) {
        Map<String, Float> at = new HashMap<>();
        for (var entry : poweredAt.entrySet()) if (circuits.contains(entry.getKey())) at.put(entry.getKey(), entry.getValue());
        return at;
    }

    /** The same night with {@code lamps} in place of its own. */
    public NightLighting withLamps(List<Lamp> lamps) {
        return new NightLighting(darkness, roofedDarkness, lamps, poweredCircuits, poweredAt);
    }

    public NightLighting withPower(String circuit) {
        var circuits = new HashSet<>(poweredCircuits);
        circuits.add(circuit);
        return new NightLighting(darkness, roofedDarkness, lamps, circuits, keep(circuits));
    }
}
