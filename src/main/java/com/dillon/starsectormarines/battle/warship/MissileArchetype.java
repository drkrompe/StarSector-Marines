package com.dillon.starsectormarines.battle.warship;

import java.util.Locale;

/**
 * The feel of a missile line: a small authored table keyed by archetype and
 * overridden by weapon id, with everything else derived from the spec so a
 * modded launcher arrives with a sensible shape.
 *
 * <ul>
 *   <li>{@link #ROCKET_RACK}: Annihilator, Hammer rack, Squall; a ripple that walks
 *       across the ground, near to far.</li>
 *   <li>{@link #SWARM_LIGHT}: Swarmer, Locust; a tight, random mass with no order.</li>
 *   <li>{@link #MIRV_BLOOM}: Hurricane, Hydra, Sabot, Pilum; a carrier that splits.</li>
 *   <li>{@link #HEAVY}: the torpedoes; one deliberate body, never scattered.</li>
 * </ul>
 */
public enum MissileArchetype {

    /** Rocket racks and pods: bodies ripple across the footprint, nearest first. */
    ROCKET_RACK(0.7f, 1f, 0.32f),
    /** Anti-fighter swarm missiles: a random mass, a wide ragged cover. */
    SWARM_LIGHT(0.1f, 1.1f, 0.25f),
    /** Carriers that split into warheads. */
    MIRV_BLOOM(0.4f, 1f, 0.45f),
    /** A single heavy body. */
    HEAVY(0f, 0f, 0f);

    private static final String[] HEAVY_IDS = {"reaper", "typhoon", "cyclone", "atropos", "harpoon", "dragon"};
    private static final String[] SWARM_IDS = {"swarmer", "locust"};
    private static final String[] RACK_IDS = {"annihilator", "hammerrack", "jackhammer", "squall"};
    private static final String[] SPLITTABLE_IDS = {"hammer", "jackhammer"};

    private final float ripple;
    private final float scatterFactor;
    private final float bloomLoop;

    MissileArchetype(float ripple, float scatterFactor, float bloomLoop) {
        this.ripple = ripple;
        this.scatterFactor = scatterFactor;
        this.bloomLoop = bloomLoop;
    }

    /** How strongly arrivals follow distance from the shooter, 0 (random) to 1 (nearest first). */
    public float ripple() {
        return ripple;
    }

    /** A multiplier on the footprint a volley is scattered over. */
    public float scatterFactor() {
        return scatterFactor;
    }

    /** How far out of the split a warhead's flight bulges, as a share of its chord. */
    public float bloomLoop() {
        return bloomLoop;
    }

    /** The archetype of a weapon: its id's entry if it has one, else what its spec shows. */
    public static MissileArchetype of(VanillaWeapon weapon) {
        MissileFlightSpec flight = weapon.flight();
        if (flight != null && flight.mirv() != null) return MIRV_BLOOM;
        if (!weapon.missile() || flight == null) return HEAVY;
        String id = weapon.id() == null ? "" : weapon.id().toLowerCase(Locale.ROOT);
        if (startsWithAny(id, HEAVY_IDS)) return HEAVY;
        if (startsWithAny(id, SWARM_IDS)) return SWARM_LIGHT;
        if (startsWithAny(id, RACK_IDS)) return ROCKET_RACK;
        if (flight.guidance() == MissileFlightSpec.Guidance.HOMING
                && flight.turnRateDegPerSec() >= 200f && weapon.burstSize() >= 4) {
            return SWARM_LIGHT;
        }
        if (weapon.burstSize() >= 3 || (flight.guidance() == MissileFlightSpec.Guidance.UNGUIDED
                && weapon.damagePerShot() <= 250f)) {
            return ROCKET_RACK;
        }
        return HEAVY;
    }

    /**
     * Whether a weapon the spec does not split may be split by the cinematic
     * preset: the Hammer family, "quantity over finesse", and not the torpedoes
     * whose lore is a single warhead.
     */
    public static boolean splittable(VanillaWeapon weapon) {
        if (weapon.flight() == null || weapon.flight().mirv() != null || weapon.burstSize() > 1) return false;
        String id = weapon.id() == null ? "" : weapon.id().toLowerCase(Locale.ROOT);
        return startsWithAny(id, SPLITTABLE_IDS) && !id.startsWith("hammerrack");
    }

    private static boolean startsWithAny(String id, String[] prefixes) {
        for (String p : prefixes) if (id.startsWith(p)) return true;
        return false;
    }
}
