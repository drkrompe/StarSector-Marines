package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.weapon.LoaderPace;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.List;

/**
 * Installed onboard subsystem, the mech's loader: how quickly finite missile mounts are recharged, and how fast every
 * rotary gun's hopper refills its belt ({@link #loaderFactor}, {@link LoaderPace}). A rack's recharge is one time for
 * the whole rack ({@link MechWeaponMount#advanceReplenishment}): an SRM rack, five-salvo or fifteen, is full again
 * {@link #srmReplenishmentSeconds} after its last salvo, an LRM rack {@link #lrmReplenishmentSeconds} after.
 * The component is independent of chassis and weapon hardware. In the campaign it is a loader part in the
 * auxiliary compartment ({@code MechInternal.STANDARD_LOADER} and its kin);
 * a mech frozen without one carries {@link #NONE}, whose missile cadence never completes.
 */
public record MissileReplenisherComponent(
        String id,
        String displayName,
        float srmReplenishmentSeconds,
        float lrmReplenishmentSeconds) {

    public String displayName() { return Strings.resolve(displayName); }

    public static final MissileReplenisherComponent STANDARD =
            new MissileReplenisherComponent(
                    "standard_replenisher", "@gearReplenisherStandard",
                    32f, 54f);
    /** A pack in three quarters of the standard loader's time ({@link LoaderPace#ACCELERATED_TIME_SHARE}). */
    public static final MissileReplenisherComponent ACCELERATED_FEED =
            new MissileReplenisherComponent(
                    "accelerated_feed", "@gearReplenisherAccelerated",
                    STANDARD.srmReplenishmentSeconds * LoaderPace.ACCELERATED_TIME_SHARE,
                    STANDARD.lrmReplenishmentSeconds * LoaderPace.ACCELERATED_TIME_SHARE);

    /** No loader fitted: racks keep what they launched with; belts keep their gun's own hopper. */
    public static final MissileReplenisherComponent NONE =
            new MissileReplenisherComponent(
                    "no_loader", "@gearReplenisherNone",
                    Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

    private static final List<MissileReplenisherComponent> CATALOG =
            List.of(STANDARD, ACCELERATED_FEED, NONE);

    public MissileReplenisherComponent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Replenisher id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Replenisher display name is required");
        }
        // Infinity is allowed and means the pack never comes back.
        if (!(srmReplenishmentSeconds > 0f)) {
            throw new IllegalArgumentException("SRM replenishment cadence must be positive");
        }
        if (!(lrmReplenishmentSeconds > 0f)) {
            throw new IllegalArgumentException("LRM replenishment cadence must be positive");
        }
    }

    /**
     * Seconds required to recharge a rack of {@code weapon}: the whole rack at once, from its last salvo
     * ({@link MechWeaponMount#advanceReplenishment}). The SRM-3 (QS) recharges as an SRM rack does.
     */
    public float replenishmentSeconds(String weaponId) {
        if (WeaponRegistry.isMechSrm(weaponId) || WeaponRegistry.MECH_SRM_3_QS_ID.equals(weaponId)) {
            return srmReplenishmentSeconds;
        }
        if (WeaponRegistry.isMechLrm(weaponId)) return lrmReplenishmentSeconds;
        return Float.POSITIVE_INFINITY;
    }

    /**
     * What this loader multiplies a belt-fed gun's hopper by ({@link LoaderPace}): the standard loader is the gun's own
     * rate, a faster loader is faster by as much as it is on missile packs. With no loader a hopper keeps the gun's own
     * rate, since the hopper is the gun's; only the missile packs, which the loader fabricates, stop.
     */
    public float loaderFactor() {
        if (!Float.isFinite(srmReplenishmentSeconds)) return LoaderPace.STANDARD;
        return LoaderPace.ofTimeShare(srmReplenishmentSeconds / STANDARD.srmReplenishmentSeconds);
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
