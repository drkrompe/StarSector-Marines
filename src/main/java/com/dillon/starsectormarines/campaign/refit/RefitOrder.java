package com.dillon.starsectormarines.campaign.refit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Every purchasable tier of the refit tree: the band it brings its track to,
 * the yard price and the tiers of other tracks it needs first. A track's own
 * preceding tier is always required and is not listed. Prices are placeholders
 * until they are anchored to measured contract income.
 *
 * <p>{@link #POWER_RESTORED} and {@link #MESS_RESTORED} are the original
 * distribution and mess orders; they keep those orders' prices and saved
 * receipts. Fabrication has no orders here: the workshop chain sells it.
 */
public enum RefitOrder {
    POWER_RESTORED(RefitTrack.POWER, RefitBand.RESTORED, 3_000, "POWER"),
    POWER_OVERHAUL(RefitTrack.POWER, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED),
    POWER_AUXILIARY(RefitTrack.POWER, RefitBand.IMPROVED_II, 30_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    LIFE_SUPPORT_RESTORED(RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, 3_500,
            RefitTrack.POWER, RefitBand.RESTORED),
    STRUCTURE_RESTORED(RefitTrack.STRUCTURE, RefitBand.RESTORED, 4_000),
    COMPUTE_RESTORED(RefitTrack.COMPUTE, RefitBand.RESTORED, 2_500,
            RefitTrack.POWER, RefitBand.RESTORED),
    COMPUTE_CLUSTER(RefitTrack.COMPUTE, RefitBand.IMPROVED_I, 14_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I),
    MESS_RESTORED(RefitTrack.MESS, RefitBand.RESTORED, 2_500, "MESS_HALL"),
    MESS_GALLEY(RefitTrack.MESS, RefitBand.IMPROVED_I, 8_000,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    MESS_COLD_STORE(RefitTrack.MESS, RefitBand.IMPROVED_II, 18_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I),
    HULL_RESTORED(RefitTrack.HULL, RefitBand.RESTORED, 9_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED),
    HULL_I(RefitTrack.HULL, RefitBand.IMPROVED_I, 20_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    HULL_II(RefitTrack.HULL, RefitBand.IMPROVED_II, 40_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II),
    FLUX_RESTORED(RefitTrack.FLUX, RefitBand.RESTORED, 10_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I),
    FLUX_I(RefitTrack.FLUX, RefitBand.IMPROVED_I, 22_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II),
    FLUX_II(RefitTrack.FLUX, RefitBand.IMPROVED_II, 45_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    SENSORS_RESTORED(RefitTrack.SENSORS, RefitBand.RESTORED, 6_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED),
    SENSORS_I(RefitTrack.SENSORS, RefitBand.IMPROVED_I, 14_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    SENSORS_II(RefitTrack.SENSORS, RefitBand.IMPROVED_II, 30_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_II),
    DRIVE_RESTORED(RefitTrack.DRIVE, RefitBand.RESTORED, 8_000,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    DRIVE_I(RefitTrack.DRIVE, RefitBand.IMPROVED_I, 16_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I),
    DRIVE_II(RefitTrack.DRIVE, RefitBand.IMPROVED_II, 32_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II);

    public final RefitTrack track;
    public final RefitBand band;
    public final int price;
    /** Other tracks' tiers this order needs, in the order a refusal names them. */
    public final Map<RefitTrack, RefitBand> requires;
    /** The receipt suffix this order is saved under. */
    public final String receipt;

    RefitOrder(RefitTrack track, RefitBand band, int price, Object... requires) {
        this.track = track;
        this.band = band;
        this.price = price;
        String receipt = name();
        var needs = new EnumMap<RefitTrack, RefitBand>(RefitTrack.class);
        int i = 0;
        if (requires.length % 2 == 1) receipt = (String) requires[i++];
        for (; i < requires.length; i += 2) needs.put((RefitTrack) requires[i], (RefitBand) requires[i + 1]);
        this.receipt = receipt;
        this.requires = Collections.unmodifiableMap(needs);
    }

    /** The order that brings {@code track} from {@code band} to the next tier, or null. */
    public static RefitOrder after(RefitTrack track, RefitBand band) {
        RefitBand next = band.next();
        for (RefitOrder order : values()) if (order.track == track && order.band == next) return order;
        return null;
    }

    /** The highest band {@code track} can reach in this catalog. */
    public static RefitBand ceiling(RefitTrack track) {
        // The workshop chain's last stage, tooling, is fabrication's first improvement.
        if (track == RefitTrack.FABRICATION) return RefitBand.IMPROVED_I;
        RefitBand top = track.start();
        for (RefitOrder order : values()) if (order.track == track && order.band.atLeast(top)) top = order.band;
        return top;
    }
}
