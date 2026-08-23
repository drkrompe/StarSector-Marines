package com.dillon.starsectormarines.battle.mech;

/**
 * Installed onboard subsystem that controls how quickly finite missile mounts
 * fabricate and load replacement trigger packs. The component is independent
 * of chassis and weapon hardware so a future mech-upgrade item can replace it
 * without rewriting either authority.
 */
public record MissileReplenisherComponent(
        String id,
        String displayName,
        float srmReplenishmentSeconds,
        float lrmReplenishmentSeconds) {

    public static final MissileReplenisherComponent STANDARD =
            new MissileReplenisherComponent(
                    "standard_replenisher", "Standard missile replenisher",
                    12f, 18f);

    public MissileReplenisherComponent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Replenisher id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Replenisher display name is required");
        }
        if (!(srmReplenishmentSeconds > 0f)
                || !Float.isFinite(srmReplenishmentSeconds)) {
            throw new IllegalArgumentException("SRM replenishment cadence must be finite and positive");
        }
        if (!(lrmReplenishmentSeconds > 0f)
                || !Float.isFinite(lrmReplenishmentSeconds)) {
            throw new IllegalArgumentException("LRM replenishment cadence must be finite and positive");
        }
    }

    /** Seconds required to restore one trigger pack for {@code weapon}. */
    public float replenishmentSeconds(MechWeapon weapon) {
        if (weapon == MechWeapon.SRM_POD) return srmReplenishmentSeconds;
        if (weapon == MechWeapon.LRM_ARTILLERY) return lrmReplenishmentSeconds;
        return Float.POSITIVE_INFINITY;
    }
}
