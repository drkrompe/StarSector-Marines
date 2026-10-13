package com.dillon.starsectormarines.battle.world.model;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Authored environmental lighting. Power changes presentation, never sight or navigation. */
public record NightLighting(float darkness, List<Lamp> lamps, Set<String> poweredCircuits) {
    public record Lamp(float x, float y, float radius, float red, float green, float blue,
                       String circuit) {
        public Lamp {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(radius) || radius <= 0
                    || circuit == null || circuit.isBlank()) throw new IllegalArgumentException("Invalid yard light");
        }
        public float pool(float px, float py) {
            float dx = px - x, dy = py - y;
            float falloff = Math.max(0f, 1f - (dx * dx + dy * dy) / (radius * radius));
            return falloff * falloff;
        }
    }

    public NightLighting {
        if (!Float.isFinite(darkness) || darkness < 0 || darkness > .9f)
            throw new IllegalArgumentException("Night must retain readable ground");
        lamps = List.copyOf(lamps);
        poweredCircuits = Set.copyOf(poweredCircuits);
    }

    public boolean powered(Lamp lamp) { return poweredCircuits.contains(lamp.circuit()); }

    public NightLighting withPower(String circuit) {
        var circuits = new HashSet<>(poweredCircuits);
        circuits.add(circuit);
        return new NightLighting(darkness, lamps, circuits);
    }
}
