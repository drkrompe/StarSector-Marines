package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.List;

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
    public static final MissileReplenisherComponent ACCELERATED_FEED =
            new MissileReplenisherComponent(
                    "accelerated_feed", "Accelerated feed system",
                    9f, 13.5f);

    private static final List<MissileReplenisherComponent> CATALOG =
            List.of(STANDARD, ACCELERATED_FEED);

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
    public float replenishmentSeconds(String weaponId) {
        if (WeaponRegistry.MECH_SRM_POD_ID.equals(weaponId)) return srmReplenishmentSeconds;
        if (WeaponRegistry.MECH_LRM_ARTILLERY_ID.equals(weaponId)) return lrmReplenishmentSeconds;
        return Float.POSITIVE_INFINITY;
    }

    /** Stable item catalog shared by campaign inventory and battle loadouts. */
    public static List<MissileReplenisherComponent> catalog() {
        return CATALOG;
    }

    /** Returns null for an unknown persisted item id. */
    public static MissileReplenisherComponent findById(String id) {
        if (id == null) return null;
        for (MissileReplenisherComponent component : CATALOG) {
            if (component.id().equals(id)) return component;
        }
        return null;
    }

    /** Resolves a persisted item id, rejecting unknown fixture vocabulary. */
    public static MissileReplenisherComponent requireById(String id) {
        MissileReplenisherComponent component = findById(id);
        if (component == null) {
            throw new IllegalArgumentException(
                    "Unknown missile replenisher id '" + id + "'");
        }
        return component;
    }

    /** Legacy-safe resolution for a live loadout. */
    public static MissileReplenisherComponent resolve(String id) {
        MissileReplenisherComponent component = findById(id);
        return component != null ? component : STANDARD;
    }
}
