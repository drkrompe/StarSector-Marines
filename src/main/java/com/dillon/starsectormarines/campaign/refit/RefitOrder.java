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
    // The reserved facilities' improvements. Each names cores the room's opening did
    // not: the workshop that cuts its fittings, the overhauled plant that runs its
    // machinery, the cluster that drives its screens. The brig has none yet: no
    // battle takes a prisoner for it to question.
    /** Pop-up targets and traps cut in the workshop, run off the overhauled plant. */
    RANGE_MACHINERY(RefitTrack.RANGE, RefitBand.IMPROVED_I, 14_000,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I),
    /** Simulated targets need the processing cluster, the plant behind it and tooled projector mounts. */
    RANGE_SIMULATOR(RefitTrack.RANGE, RefitBand.IMPROVED_II, 32_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    /** Press, rack and powered cardio built in the workshop and wired to restored distribution. */
    GYM_CONDITIONING(RefitTrack.GYM, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    /** A timed loop: lap boards on the compute net, the overhauled plant, sprung decking from the workshop. */
    GYM_TRACK(RefitTrack.GYM, RefitBand.IMPROVED_II, 28_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    /** A theatre needs the overhauled plant's steady power and instruments made in the workshop. */
    SICK_BAY_SURGERY(RefitTrack.SICK_BAY, RefitBand.IMPROVED_I, 16_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.RESTORED),
    /** Monitored beds on the processing cluster, an isolation loop on life support, the plant behind both. */
    SICK_BAY_TRAUMA(RefitTrack.SICK_BAY, RefitBand.IMPROVED_II, 36_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I),
    /** Screens and a library on the compute net, powered from restored distribution. */
    REC_LOUNGE_CENTRE(RefitTrack.REC_LOUNGE, RefitBand.IMPROVED_I, 10_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    /** A shore-leave deck of projected views: the cluster, the overhauled plant, and the workshop's frames. */
    REC_LOUNGE_SHORE_LEAVE(RefitTrack.REC_LOUNGE, RefitBand.IMPROVED_II, 26_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.RESTORED),
    MECH_CALIBRATION_RESTORED(RefitTrack.MECH_CALIBRATION, RefitBand.RESTORED, 3_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    MECH_CALIBRATION_I(RefitTrack.MECH_CALIBRATION, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.RESTORED),
    MECH_CALIBRATION_II(RefitTrack.MECH_CALIBRATION, RefitBand.IMPROVED_II, 28_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_II, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    MECH_COOLANT_RESTORED(RefitTrack.MECH_COOLANT, RefitBand.RESTORED, 3_000,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    MECH_COOLANT_I(RefitTrack.MECH_COOLANT, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    MECH_COOLANT_II(RefitTrack.MECH_COOLANT, RefitBand.IMPROVED_II, 26_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    MECH_ARMOR_RESTORED(RefitTrack.MECH_ARMOR, RefitBand.RESTORED, 3_500,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    MECH_ARMOR_I(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.RESTORED),
    MECH_ARMOR_II(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_II, 25_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I),
    MECH_REPAIR_RESTORED(RefitTrack.MECH_REPAIR, RefitBand.RESTORED, 2_500,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    MECH_REPAIR_I(RefitTrack.MECH_REPAIR, RefitBand.IMPROVED_I, 10_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.RESTORED),
    MECH_REPAIR_II(RefitTrack.MECH_REPAIR, RefitBand.IMPROVED_II, 20_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    HYDROPONICS_RESTORED(RefitTrack.HYDROPONICS, RefitBand.RESTORED, 4_000,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HYDROPONICS_I(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    HYDROPONICS_II(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_II, 26_000,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    // The wardroom and the briefing room are the two ends of the mess, so the mess must be in service.
    WARDROOM_RESTORED(RefitTrack.WARDROOM, RefitBand.RESTORED, 3_500,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED,
            RefitTrack.MESS, RefitBand.RESTORED),
    WARDROOM_I(RefitTrack.WARDROOM, RefitBand.IMPROVED_I, 12_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    WARDROOM_II(RefitTrack.WARDROOM, RefitBand.IMPROVED_II, 28_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    BRIEFING_RESTORED(RefitTrack.BRIEFING, RefitBand.RESTORED, 3_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED,
            RefitTrack.MESS, RefitBand.RESTORED),
    /** The plot table: the holo-tank's mount. Its own battle passive has no hook yet, so it changes nothing in battle. */
    BRIEFING_I(RefitTrack.BRIEFING, RefitBand.IMPROVED_I, 8_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    /** The holo-tank, run off the processing cluster: the briefing shows the garrison the operation faces. */
    BRIEFING_II(RefitTrack.BRIEFING, RefitBand.IMPROVED_II, 20_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    HOLDS_RESTORED(RefitTrack.HOLDS, RefitBand.RESTORED, 3_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HOLDS_I(RefitTrack.HOLDS, RefitBand.IMPROVED_I, 10_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
    HOLDS_II(RefitTrack.HOLDS, RefitBand.IMPROVED_II, 24_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I),
    /** Two machines set aside whole, off their berths and gantries. */
    RESERVE_HANGAR_RESTORED(RefitTrack.RESERVE_HANGAR, RefitBand.RESTORED, 12_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    /** Four, racked two high on cradles the machine shop cuts. */
    RESERVE_HANGAR_I(RefitTrack.RESERVE_HANGAR, RefitBand.IMPROVED_I, 20_000,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
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
        bill(RANGE_MACHINERY, Commodities.HEAVY_MACHINERY, 6, Commodities.METALS, 20);
        bill(RANGE_SIMULATOR, Commodities.GAMMA_CORE, 1, Commodities.RARE_METALS, 10, Commodities.HEAVY_MACHINERY, 8);
        bill(GYM_CONDITIONING, Commodities.HEAVY_MACHINERY, 4, Commodities.METALS, 15);
        bill(GYM_TRACK, Commodities.METALS, 30, Commodities.HEAVY_MACHINERY, 6);
        bill(SICK_BAY_SURGERY, Commodities.HEAVY_MACHINERY, 5, Commodities.RARE_METALS, 5);
        bill(SICK_BAY_TRAUMA, Commodities.GAMMA_CORE, 1, Commodities.HEAVY_MACHINERY, 8, Commodities.RARE_METALS, 10);
        bill(REC_LOUNGE_CENTRE, Commodities.METALS, 15, Commodities.HEAVY_MACHINERY, 2);
        bill(REC_LOUNGE_SHORE_LEAVE, Commodities.METALS, 25, Commodities.RARE_METALS, 5);
        bill(MECH_CALIBRATION_RESTORED, Commodities.HEAVY_MACHINERY, 2);
        bill(MECH_CALIBRATION_I, Commodities.RARE_METALS, 5);
        bill(MECH_CALIBRATION_II, Commodities.BETA_CORE, 1, Commodities.RARE_METALS, 10, Commodities.HEAVY_MACHINERY, 4);
        bill(MECH_COOLANT_RESTORED, Commodities.HEAVY_MACHINERY, 3);
        bill(MECH_COOLANT_I, Commodities.HEAVY_MACHINERY, 6, Commodities.METALS, 10);
        bill(MECH_COOLANT_II, Commodities.HEAVY_MACHINERY, 10, Commodities.RARE_METALS, 8);
        bill(MECH_ARMOR_RESTORED, Commodities.METALS, 20);
        bill(MECH_ARMOR_I, Commodities.METALS, 40);
        bill(MECH_ARMOR_II, Commodities.METALS, 60, Commodities.RARE_METALS, 10);
        bill(MECH_REPAIR_RESTORED, Commodities.METALS, 10);
        bill(MECH_REPAIR_I, Commodities.HEAVY_MACHINERY, 4);
        bill(MECH_REPAIR_II, Commodities.METALS, 30, Commodities.HEAVY_MACHINERY, 6);
        bill(HYDROPONICS_RESTORED, Commodities.METALS, 10, Commodities.ORGANICS, 10);
        bill(HYDROPONICS_I, Commodities.HEAVY_MACHINERY, 4, Commodities.ORGANICS, 20);
        bill(HYDROPONICS_II, Commodities.HEAVY_MACHINERY, 8, Commodities.ORGANICS, 40);
        bill(WARDROOM_RESTORED, Commodities.METALS, 10);
        bill(WARDROOM_I, Commodities.HEAVY_MACHINERY, 2, Commodities.RARE_METALS, 2);
        bill(WARDROOM_II, Commodities.HEAVY_MACHINERY, 4, Commodities.RARE_METALS, 6);
        bill(BRIEFING_RESTORED, Commodities.METALS, 5);
        bill(BRIEFING_I, Commodities.METALS, 15);
        bill(BRIEFING_II, Commodities.GAMMA_CORE, 1, Commodities.HEAVY_MACHINERY, 2, Commodities.RARE_METALS, 6);
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
        // The workshop chain's last two stages, tooling and the reclamation line, are fabrication's improvements.
        if (track == RefitTrack.FABRICATION) return RefitBand.IMPROVED_II;
        RefitBand top = track.start();
        for (RefitOrder order : values()) if (order.track == track && order.band.atLeast(top)) top = order.band;
        return top;
    }
}
