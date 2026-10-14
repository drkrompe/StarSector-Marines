package com.dillon.starsectormarines.ops.battleview;

/**
 * The particles a treatment gives off at the patient ({@code squad-medic.md}):
 * hands-on work rather than a spell, so nothing glows and nothing is additive.
 *
 * <p>Restoring health throws a pale dressing mist that drifts up and away off
 * the patient from the medic's side. Re-plating armor lays cool grey-blue
 * sealant flecks that settle inward onto the suit and shrink as they set. When
 * both are being restored the slots alternate between the two.
 *
 * <p>Stateless: every particle is derived from how long the treatment has run,
 * its slot, and the patient's identity, so the same moment of the same
 * treatment always draws the same picture and nothing is stored between frames.
 * Positions are in cells relative to the patient's centre, along the same axes
 * as the patient's world position.
 */
final class TreatmentParticles {

    enum Kind { DRESSING, SEALANT }

    /** One particle at one moment: offset from the patient in cells, diameter in cells, and opacity. */
    interface Sink {
        void particle(Kind kind, float dx, float dy, float size, float alpha);
    }

    static final int SLOTS = 12;
    /** Seconds one particle lives before its slot respawns elsewhere. */
    static final float LIFETIME = 0.9f;

    private TreatmentParticles() { }

    /**
     * Emits every live particle for a treatment {@code elapsed} seconds in.
     *
     * @param towardX    x of the unit vector from the patient toward the medic
     * @param towardY    y of that vector
     * @param upY        +1 or -1: which way along y the dressing mist rises on screen
     * @param bodyRadius the patient's body radius in cells
     */
    static void emit(float elapsed, long seed, boolean health, boolean armor,
                     float towardX, float towardY, float upY, float bodyRadius, Sink sink) {
        if (!health && !armor) return;
        for (int slot = 0; slot < SLOTS; slot++) {
            float shifted = elapsed + LIFETIME * slot / SLOTS;
            int cycle = (int) Math.floor(shifted / LIFETIME);
            float age = shifted / LIFETIME - cycle;
            Kind kind = health && armor ? ((slot & 1) == 0 ? Kind.DRESSING : Kind.SEALANT)
                    : health ? Kind.DRESSING : Kind.SEALANT;
            long h = mix(seed, slot, cycle);
            float r1 = unit(h);
            float r2 = unit(h >>> 21);
            float r3 = unit(h >>> 42);
            // Work happens where the hands are: on the medic's side of the body.
            double angle = Math.atan2(towardY, towardX) + (r1 - 0.5f) * Math.PI * 0.75f;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            if (kind == Kind.DRESSING) {
                float start = bodyRadius * (0.35f + 0.5f * r2);
                float drift = bodyRadius * (0.35f + 0.35f * r3) * age;
                float out = start + drift * 0.6f;
                float size = bodyRadius * (0.38f + 0.2f * r3) * (0.7f + 0.6f * age);
                sink.particle(kind, cos * out, sin * out + upY * drift, size,
                        envelope(age) * 0.55f);
            } else {
                float reach = bodyRadius * (1.3f - 0.9f * age) * (0.8f + 0.3f * r2);
                float size = bodyRadius * (0.24f + 0.1f * r3) * (1f - 0.5f * age);
                sink.particle(kind, cos * reach, sin * reach, size, envelope(age) * 0.8f);
            }
        }
    }

    /** Fades in over the first fifth of a life and out over the last half. */
    static float envelope(float age) {
        if (age < 0.2f) return age / 0.2f;
        if (age > 0.5f) return Math.max(0f, (1f - age) / 0.5f);
        return 1f;
    }

    private static long mix(long seed, int slot, int cycle) {
        long z = seed * 0x9E3779B97F4A7C15L + slot * 0xBF58476D1CE4E5B9L + cycle * 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long bits) {
        return (bits & 0x1FFFFF) / (float) 0x200000;
    }
}
