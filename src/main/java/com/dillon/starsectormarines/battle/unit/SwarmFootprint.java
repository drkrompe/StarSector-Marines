package com.dillon.starsectormarines.battle.unit;

/**
 * How much ground a Rav Bloom body covers as it is drawn, seen from above: how far it reaches
 * ahead of its sim position and behind, and how wide it is, at the adult stage. Its growth stage
 * scales all three ({@link SwarmStage#scale(UnitType)}). It is what the body's shadow is cast
 * from: one ellipse laid along the body's facing.
 *
 * <p>Not a collision shape: the Runner's {@link BodyCapsule} is its crowding shape, measured off
 * the same skeleton, and the other strains crowd as circles. This is the drawn mass only.
 */
public final class SwarmFootprint {

    /**
     * Cells ahead of the sim position, behind it (a thin tail left out), and across, as adults:
     * the body's mass as drawn at play zoom, legs, spines and crown left out.
     */
    private static final float RUNNER_AHEAD = 0.85f, RUNNER_BEHIND = 0.65f, RUNNER_ACROSS = 0.46f;
    private static final float BRUTE_AHEAD = 0.94f, BRUTE_BEHIND = 0.57f, BRUTE_ACROSS = 0.92f;
    private static final float MATRIARCH_AHEAD = 2.60f, MATRIARCH_BEHIND = 1.80f, MATRIARCH_ACROSS = 1.90f;
    private static final float BOMBARD_AHEAD = 0.86f, BOMBARD_BEHIND = 0.82f, BOMBARD_ACROSS = 0.95f;

    private SwarmFootprint() {
    }

    /** Whether {@code type} has a drawn footprint here: the long-bodied Rav strains. */
    public static boolean has(UnitType type) {
        return type == UnitType.SWARM_RUNNER || type == UnitType.SWARM_BRUTE || type == UnitType.SWARM_MATRIARCH
                || type == UnitType.SWARM_BOMBARD;
    }

    /** Nose to rump, cells. */
    public static float length(UnitType type, SwarmStage stage) {
        return (ahead(type) + behind(type)) * SwarmStage.of(stage).scale(type);
    }

    /** Flank to flank, cells. */
    public static float width(UnitType type, SwarmStage stage) {
        return across(type) * SwarmStage.of(stage).scale(type);
    }

    /** How far ahead of the sim position the footprint's middle lies, cells. */
    public static float forwardOffset(UnitType type, SwarmStage stage) {
        return (ahead(type) - behind(type)) * 0.5f * SwarmStage.of(stage).scale(type);
    }

    private static float ahead(UnitType type) {
        return switch (type) {
            case SWARM_RUNNER -> RUNNER_AHEAD;
            case SWARM_BRUTE -> BRUTE_AHEAD;
            case SWARM_MATRIARCH -> MATRIARCH_AHEAD;
            case SWARM_BOMBARD -> BOMBARD_AHEAD;
            default -> 0f;
        };
    }

    private static float behind(UnitType type) {
        return switch (type) {
            case SWARM_RUNNER -> RUNNER_BEHIND;
            case SWARM_BRUTE -> BRUTE_BEHIND;
            case SWARM_MATRIARCH -> MATRIARCH_BEHIND;
            case SWARM_BOMBARD -> BOMBARD_BEHIND;
            default -> 0f;
        };
    }

    private static float across(UnitType type) {
        return switch (type) {
            case SWARM_RUNNER -> RUNNER_ACROSS;
            case SWARM_BRUTE -> BRUTE_ACROSS;
            case SWARM_MATRIARCH -> MATRIARCH_ACROSS;
            case SWARM_BOMBARD -> BOMBARD_ACROSS;
            default -> 0f;
        };
    }
}
