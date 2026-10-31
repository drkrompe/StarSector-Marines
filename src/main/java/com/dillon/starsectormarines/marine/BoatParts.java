package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The boat parts a company can learn: every {@link BoatFitting} and {@link BoatGun} by id, with
 * which the yard already knows, which a store may sell, and how scarce each is.
 *
 * <p>A part is <em>starter</em> when a new boat is built with it: the standard fitting of every
 * slot and the yard's own gun on every hardpoint of every pattern. Those are known to every
 * company and never sold, because a boat the yard builds must always be buildable. The medium and
 * heavy lift fittings are starter issue too ({@link #COMPANY_ISSUE}). Everything else is learned
 * ({@link BoatDeck#learnPart}) before the workshop will build another.
 */
public final class BoatParts {

    private BoatParts() {}

    /**
     * Parts every company knows from the start though no new boat is built with them: the medium and heavy lift, so a
     * company can carry its heavier machines without waiting on a licence (owner, 2026-10-31, for v0.1).
     */
    static final List<BoatFitting> COMPANY_ISSUE = List.of(BoatFitting.HEAVY_CRADLE, BoatFitting.HEAVY_LIFT_FRAME);

    /** Every part a new boat of any pattern is built with, and the company issue. */
    public static Set<String> starterIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (BoatFitting fitting : COMPANY_ISSUE) ids.add(fitting.id());
        for (BoatFitting fitting : BoatFitting.catalog()) {
            if (fitting.standard()) ids.add(fitting.id());
        }
        for (ShuttleType pattern : ShuttleType.values()) {
            for (int hardpoint = 0; hardpoint < pattern.hardpoints(); hardpoint++) {
                BoatGun gun = BoatGun.standardFor(pattern, hardpoint);
                if (gun != null) ids.add(gun.id());
            }
        }
        return ids;
    }

    /** Whether {@code partId} names a fitting or a gun of this build. */
    public static boolean exists(String partId) {
        return BoatFitting.findById(partId) != null || BoatGun.findById(partId) != null;
    }

    public static boolean starter(String partId) {
        return starterIds().contains(partId);
    }

    /** Every part a store may sell: all but the starter set, fittings in slot order then guns. */
    public static List<String> offerable() {
        Set<String> starter = starterIds();
        List<String> ids = new ArrayList<>();
        for (BoatFitting fitting : BoatFitting.catalog()) {
            if (!starter.contains(fitting.id())) ids.add(fitting.id());
        }
        for (BoatGun gun : BoatGun.catalog()) {
            if (!starter.contains(gun.id())) ids.add(gun.id());
        }
        return List.copyOf(ids);
    }

    /**
     * How scarce a part is, as every other channel's gate reads it: the yard's second tier is
     * ordinary kit, the third is advanced, a fourth is prestige. Null for an unknown part.
     */
    public static EquipmentAccessTier accessTier(String partId) {
        BoatFitting fitting = BoatFitting.findById(partId);
        BoatGun gun = fitting == null ? BoatGun.findById(partId) : null;
        if (fitting == null && gun == null) return null;
        int tier = fitting != null ? fitting.tier() : gun.tier();
        return tier <= 2 ? EquipmentAccessTier.COMMON
                : tier == 3 ? EquipmentAccessTier.ADVANCED : EquipmentAccessTier.PRESTIGE;
    }

    /** The slot a fitting goes in, or null for a gun or an unknown part. */
    public static BoatFittingSlot slot(String partId) {
        BoatFitting fitting = BoatFitting.findById(partId);
        return fitting == null ? null : fitting.slot();
    }

    public static String displayName(String partId) {
        BoatFitting fitting = BoatFitting.findById(partId);
        if (fitting != null) return fitting.displayName();
        BoatGun gun = BoatGun.findById(partId);
        return gun != null ? gun.displayName() : partId;
    }
}
