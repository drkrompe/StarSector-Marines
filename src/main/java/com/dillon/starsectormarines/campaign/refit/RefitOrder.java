package com.dillon.starsectormarines.campaign.refit;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
 *
 * <p>Beyond credits, most tiers take materials out of fleet cargo: metals for
 * structure and plating, heavy machinery for plant and drive, transplutonics for
 * flux and sensor work. The strongest tiers are gated on AI cores, the base
 * game's own measure of something powerful: a gamma core runs the processing
 * cluster, a beta core the tactical core, and an alpha core the military flux
 * grid, the tier that starts making her more than a frigate.
 */
public enum RefitOrder {
    POWER_RESTORED(RefitTrack.POWER, RefitBand.RESTORED, 3_000, "POWER"),
    POWER_OVERHAUL(RefitTrack.POWER, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
    POWER_AUXILIARY(RefitTrack.POWER, RefitBand.IMPROVED_II, 30_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    LIFE_SUPPORT_RESTORED(RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, 3_500,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    STRUCTURE_RESTORED(RefitTrack.STRUCTURE, RefitBand.RESTORED, 4_000),
    COMPUTE_RESTORED(RefitTrack.COMPUTE, RefitBand.RESTORED, 2_500,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    COMPUTE_CLUSTER(RefitTrack.COMPUTE, RefitBand.IMPROVED_I, 14_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    COMPUTE_TACTICAL(RefitTrack.COMPUTE, RefitBand.IMPROVED_II, 35_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    MESS_RESTORED(RefitTrack.MESS, RefitBand.RESTORED, 2_500, "MESS_HALL"),
    MESS_GALLEY(RefitTrack.MESS, RefitBand.IMPROVED_I, 8_000,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    MESS_COLD_STORE(RefitTrack.MESS, RefitBand.IMPROVED_II, 18_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.RESTORED),
    RANGE_OPEN(RefitTrack.RANGE, RefitBand.RESTORED, 7_500,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.RESTORED),
    GYM_OPEN(RefitTrack.GYM, RefitBand.RESTORED, 6_000,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    SICK_BAY_OPEN(RefitTrack.SICK_BAY, RefitBand.RESTORED, 8_000,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.RESTORED),
    REC_LOUNGE_OPEN(RefitTrack.REC_LOUNGE, RefitBand.RESTORED, 4_000,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    BRIG_OPEN(RefitTrack.BRIG, RefitBand.RESTORED, 3_500,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HYDROPONICS_RESTORED(RefitTrack.HYDROPONICS, RefitBand.RESTORED, 4_000,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HYDROPONICS_I(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    HYDROPONICS_II(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_II, 26_000,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    WARDROOM_RESTORED(RefitTrack.WARDROOM, RefitBand.RESTORED, 3_500,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    WARDROOM_I(RefitTrack.WARDROOM, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    WARDROOM_II(RefitTrack.WARDROOM, RefitBand.IMPROVED_II, 28_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    /** The plot table and the holo-tank above it are not offered until their passives have a hook. */
    BRIEFING_RESTORED(RefitTrack.BRIEFING, RefitBand.RESTORED, 3_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HOLDS_RESTORED(RefitTrack.HOLDS, RefitBand.RESTORED, 3_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HOLDS_I(RefitTrack.HOLDS, RefitBand.IMPROVED_I, 10_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
    HOLDS_II(RefitTrack.HOLDS, RefitBand.IMPROVED_II, 24_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I),
    HULL_RESTORED(RefitTrack.HULL, RefitBand.RESTORED, 9_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
    HULL_I(RefitTrack.HULL, RefitBand.IMPROVED_I, 20_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I),
    HULL_II(RefitTrack.HULL, RefitBand.IMPROVED_II, 40_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    FLUX_RESTORED(RefitTrack.FLUX, RefitBand.RESTORED, 10_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    FLUX_I(RefitTrack.FLUX, RefitBand.IMPROVED_I, 22_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    FLUX_II(RefitTrack.FLUX, RefitBand.IMPROVED_II, 45_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.COMPUTE, RefitBand.IMPROVED_II),
    SENSORS_RESTORED(RefitTrack.SENSORS, RefitBand.RESTORED, 6_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    SENSORS_I(RefitTrack.SENSORS, RefitBand.IMPROVED_I, 14_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I),
    SENSORS_II(RefitTrack.SENSORS, RefitBand.IMPROVED_II, 30_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_II, RefitTrack.POWER, RefitBand.IMPROVED_II),
    DRIVE_RESTORED(RefitTrack.DRIVE, RefitBand.RESTORED, 8_000,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    DRIVE_I(RefitTrack.DRIVE, RefitBand.IMPROVED_I, 16_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.RESTORED),
    DRIVE_II(RefitTrack.DRIVE, RefitBand.IMPROVED_II, 32_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.IMPROVED_I);

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

    private static final Map<RefitOrder, Map<String, Integer>> MATERIALS = new EnumMap<>(RefitOrder.class);
    static {
        bill(POWER_OVERHAUL, Commodities.HEAVY_MACHINERY, 10, Commodities.METALS, 20);
        bill(POWER_AUXILIARY, Commodities.HEAVY_MACHINERY, 20, Commodities.RARE_METALS, 10);
        bill(LIFE_SUPPORT_RESTORED, Commodities.HEAVY_MACHINERY, 4);
        bill(STRUCTURE_RESTORED, Commodities.METALS, 40);
        bill(COMPUTE_CLUSTER, Commodities.GAMMA_CORE, 1);
        bill(COMPUTE_TACTICAL, Commodities.BETA_CORE, 1, Commodities.RARE_METALS, 10);
        bill(MESS_GALLEY, Commodities.HEAVY_MACHINERY, 4);
        bill(MESS_COLD_STORE, Commodities.HEAVY_MACHINERY, 6);
        bill(RANGE_OPEN, Commodities.METALS, 30, Commodities.HEAVY_MACHINERY, 2);
        bill(GYM_OPEN, Commodities.METALS, 20);
        bill(SICK_BAY_OPEN, Commodities.HEAVY_MACHINERY, 3, Commodities.METALS, 10);
        bill(REC_LOUNGE_OPEN, Commodities.METALS, 10);
        bill(BRIG_OPEN, Commodities.METALS, 15);
        bill(HYDROPONICS_RESTORED, Commodities.METALS, 10, Commodities.ORGANICS, 10);
        bill(HYDROPONICS_I, Commodities.HEAVY_MACHINERY, 4, Commodities.ORGANICS, 20);
        bill(HYDROPONICS_II, Commodities.HEAVY_MACHINERY, 8, Commodities.ORGANICS, 40);
        bill(WARDROOM_RESTORED, Commodities.METALS, 10);
        bill(WARDROOM_I, Commodities.HEAVY_MACHINERY, 2, Commodities.RARE_METALS, 2);
        bill(WARDROOM_II, Commodities.HEAVY_MACHINERY, 4, Commodities.RARE_METALS, 6);
        bill(BRIEFING_RESTORED, Commodities.METALS, 5);
        bill(HOLDS_RESTORED, Commodities.METALS, 20);
        bill(HOLDS_I, Commodities.METALS, 40);
        bill(HOLDS_II, Commodities.METALS, 60, Commodities.HEAVY_MACHINERY, 6);
        bill(HULL_RESTORED, Commodities.METALS, 60);
        bill(HULL_I, Commodities.METALS, 100, Commodities.RARE_METALS, 10);
        bill(HULL_II, Commodities.METALS, 150, Commodities.RARE_METALS, 25);
        bill(FLUX_RESTORED, Commodities.RARE_METALS, 10);
        bill(FLUX_I, Commodities.RARE_METALS, 20);
        bill(FLUX_II, Commodities.ALPHA_CORE, 1, Commodities.RARE_METALS, 30);
        bill(SENSORS_RESTORED, Commodities.RARE_METALS, 5);
        bill(SENSORS_I, Commodities.RARE_METALS, 10);
        bill(SENSORS_II, Commodities.RARE_METALS, 20);
        bill(DRIVE_RESTORED, Commodities.HEAVY_MACHINERY, 10);
        bill(DRIVE_I, Commodities.HEAVY_MACHINERY, 15);
        bill(DRIVE_II, Commodities.HEAVY_MACHINERY, 20, Commodities.RARE_METALS, 15);
    }

    private static void bill(RefitOrder order, Object... pairs) {
        var materials = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < pairs.length; i += 2) materials.put((String) pairs[i], (Integer) pairs[i + 1]);
        MATERIALS.put(order, Collections.unmodifiableMap(materials));
    }

    /** Commodities taken from fleet cargo besides the credits, in bill order; empty for a credits-only tier. */
    public Map<String, Integer> materials() { return MATERIALS.getOrDefault(this, Map.of()); }

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
