package com.dillon.starsectormarines.battle.squad;

/**
 * What the company's home is worth to one squad in this battle: the mess's morale
 * recovery, the range's drill on primary accuracy, and the gym's conditioning on move
 * speed and on the morale a hit or near miss drains. Frozen with the squad at launch
 * ({@code second-wind-refit-tree.md}); the battle never reads the live ship.
 *
 * <p>Every figure is a multiplier and {@link #NEUTRAL} is one on all of them, which is
 * what allies, enemies, fixtures and every hull but the founding one carry.
 */
public record HullWelfare(float moraleRecovery, float accuracy, float moveSpeed, float moraleDrain) {
    public static final HullWelfare NEUTRAL = new HullWelfare(1f, 1f, 1f, 1f);

    public HullWelfare {
        requirePositive(moraleRecovery, "moraleRecovery");
        requirePositive(accuracy, "accuracy");
        requirePositive(moveSpeed, "moveSpeed");
        requirePositive(moraleDrain, "moraleDrain");
    }

    /** Only the mess: what a commitment saved before the facilities carries. */
    public static HullWelfare mess(float moraleRecovery) {
        return new HullWelfare(moraleRecovery, 1f, 1f, 1f);
    }

    /** Whether any figure the {@link HullWelfareModifier} owns differs from neutral. */
    public boolean changesUnitStats() {
        return accuracy != 1f || moveSpeed != 1f || moraleDrain != 1f;
    }

    private static void requirePositive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f)
            throw new IllegalArgumentException(name + " must be finite and positive");
    }
}
