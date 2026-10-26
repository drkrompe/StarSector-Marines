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
 * <p>A price is credits only: the metals, machinery, transplutonics and organics
 * tiers once took from cargo were folded in at their base prices (`FabricationCost`).
 * The strongest tiers are also gated on AI cores, the base game's own measure of
 * something powerful, which no price stands in for: a gamma core runs the processing
 * cluster, a beta core the tactical core, and an alpha core the military flux grid,
 * the tier that starts making her more than a frigate.
 */
public enum RefitOrder {
    POWER_RESTORED(RefitTrack.POWER, RefitBand.RESTORED, 3_000, "POWER"),
    POWER_OVERHAUL(RefitTrack.POWER, RefitBand.IMPROVED_I, 14_100,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
    POWER_AUXILIARY(RefitTrack.POWER, RefitBand.IMPROVED_II, 35_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    LIFE_SUPPORT_RESTORED(RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, 4_100,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    STRUCTURE_RESTORED(RefitTrack.STRUCTURE, RefitBand.RESTORED, 5_200),
    COMPUTE_RESTORED(RefitTrack.COMPUTE, RefitBand.RESTORED, 2_500,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    COMPUTE_CLUSTER(RefitTrack.COMPUTE, RefitBand.IMPROVED_I, 14_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    COMPUTE_TACTICAL(RefitTrack.COMPUTE, RefitBand.IMPROVED_II, 37_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    MESS_RESTORED(RefitTrack.MESS, RefitBand.RESTORED, 2_500, "MESS_HALL"),
    MESS_GALLEY(RefitTrack.MESS, RefitBand.IMPROVED_I, 8_600,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    MESS_COLD_STORE(RefitTrack.MESS, RefitBand.IMPROVED_II, 18_900,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.RESTORED),
    RANGE_OPEN(RefitTrack.RANGE, RefitBand.RESTORED, 8_700,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.RESTORED),
    GYM_OPEN(RefitTrack.GYM, RefitBand.RESTORED, 6_600,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    SICK_BAY_OPEN(RefitTrack.SICK_BAY, RefitBand.RESTORED, 8_750,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.RESTORED),
    REC_LOUNGE_OPEN(RefitTrack.REC_LOUNGE, RefitBand.RESTORED, 4_300,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    BRIG_OPEN(RefitTrack.BRIG, RefitBand.RESTORED, 3_950,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    // The reserved facilities' improvements. Each names cores the room's opening did
    // not: the workshop that cuts its fittings, the overhauled plant that runs its
    // machinery, the cluster that drives its screens. The brig has none yet: no
    // battle takes a prisoner for it to question.
    /** Pop-up targets and traps cut in the workshop, run off the overhauled plant. */
    RANGE_MACHINERY(RefitTrack.RANGE, RefitBand.IMPROVED_I, 15_500,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I),
    /** Simulated targets need the processing cluster, the plant behind it and tooled projector mounts. */
    RANGE_SIMULATOR(RefitTrack.RANGE, RefitBand.IMPROVED_II, 35_200,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    /** Press, rack and powered cardio built in the workshop and wired to restored distribution. */
    GYM_CONDITIONING(RefitTrack.GYM, RefitBand.IMPROVED_I, 13_050,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    /** A timed loop: lap boards on the compute net, the overhauled plant, sprung decking from the workshop. */
    GYM_TRACK(RefitTrack.GYM, RefitBand.IMPROVED_II, 29_800,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    /** A theatre needs the overhauled plant's steady power and instruments made in the workshop. */
    SICK_BAY_SURGERY(RefitTrack.SICK_BAY, RefitBand.IMPROVED_I, 17_750,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.RESTORED),
    /** Monitored beds on the processing cluster, an isolation loop on life support, the plant behind both. */
    SICK_BAY_TRAUMA(RefitTrack.SICK_BAY, RefitBand.IMPROVED_II, 39_200,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I),
    /** Screens and a library on the compute net, powered from restored distribution. */
    REC_LOUNGE_CENTRE(RefitTrack.REC_LOUNGE, RefitBand.IMPROVED_I, 10_750,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    /** A shore-leave deck of projected views: the cluster, the overhauled plant, and the workshop's frames. */
    REC_LOUNGE_SHORE_LEAVE(RefitTrack.REC_LOUNGE, RefitBand.IMPROVED_II, 27_750,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.RESTORED),
    MECH_CALIBRATION_RESTORED(RefitTrack.MECH_CALIBRATION, RefitBand.RESTORED, 3_300,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    MECH_CALIBRATION_I(RefitTrack.MECH_CALIBRATION, RefitBand.IMPROVED_I, 13_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.RESTORED),
    MECH_CALIBRATION_II(RefitTrack.MECH_CALIBRATION, RefitBand.IMPROVED_II, 30_600,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_II, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.FABRICATION, RefitBand.IMPROVED_I),
    MECH_COOLANT_RESTORED(RefitTrack.MECH_COOLANT, RefitBand.RESTORED, 3_450,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    MECH_COOLANT_I(RefitTrack.MECH_COOLANT, RefitBand.IMPROVED_I, 13_200,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    MECH_COOLANT_II(RefitTrack.MECH_COOLANT, RefitBand.IMPROVED_II, 29_100,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    MECH_ARMOR_RESTORED(RefitTrack.MECH_ARMOR, RefitBand.RESTORED, 4_100,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    MECH_ARMOR_I(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_I, 13_200,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.RESTORED),
    MECH_ARMOR_II(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_II, 28_800,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I),
    MECH_REPAIR_RESTORED(RefitTrack.MECH_REPAIR, RefitBand.RESTORED, 2_800,
            RefitTrack.FABRICATION, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    MECH_REPAIR_I(RefitTrack.MECH_REPAIR, RefitBand.IMPROVED_I, 10_600,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.RESTORED),
    MECH_REPAIR_II(RefitTrack.MECH_REPAIR, RefitBand.IMPROVED_II, 21_800,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    HYDROPONICS_RESTORED(RefitTrack.HYDROPONICS, RefitBand.RESTORED, 4_600,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HYDROPONICS_I(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_I, 13_200,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    HYDROPONICS_II(RefitTrack.HYDROPONICS, RefitBand.IMPROVED_II, 28_400,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    // The wardroom and the briefing room are the two ends of the mess, so the mess must be in service.
    WARDROOM_RESTORED(RefitTrack.WARDROOM, RefitBand.RESTORED, 3_800,
            RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED,
            RefitTrack.MESS, RefitBand.RESTORED),
    WARDROOM_I(RefitTrack.WARDROOM, RefitBand.IMPROVED_I, 12_700,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    WARDROOM_II(RefitTrack.WARDROOM, RefitBand.IMPROVED_II, 29_800,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    BRIEFING_RESTORED(RefitTrack.BRIEFING, RefitBand.RESTORED, 3_150,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED,
            RefitTrack.MESS, RefitBand.RESTORED),
    /** The plot table: the holo-tank's mount. Its own battle passive has no hook yet, so it changes nothing in battle. */
    BRIEFING_I(RefitTrack.BRIEFING, RefitBand.IMPROVED_I, 8_450,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    /** The holo-tank, run off the processing cluster: the briefing shows the garrison the operation faces. */
    BRIEFING_II(RefitTrack.BRIEFING, RefitBand.IMPROVED_II, 21_500,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED),
    HOLDS_RESTORED(RefitTrack.HOLDS, RefitBand.RESTORED, 3_600,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.POWER, RefitBand.RESTORED),
    HOLDS_I(RefitTrack.HOLDS, RefitBand.IMPROVED_I, 11_200,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
    HOLDS_II(RefitTrack.HOLDS, RefitBand.IMPROVED_II, 26_700,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I),
    HULL_RESTORED(RefitTrack.HULL, RefitBand.RESTORED, 10_800,
            RefitTrack.STRUCTURE, RefitBand.RESTORED, RefitTrack.FABRICATION, RefitBand.RESTORED),
    HULL_I(RefitTrack.HULL, RefitBand.IMPROVED_I, 25_000,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I),
    HULL_II(RefitTrack.HULL, RefitBand.IMPROVED_II, 49_500,
            RefitTrack.FABRICATION, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    FLUX_RESTORED(RefitTrack.FLUX, RefitBand.RESTORED, 12_000,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    FLUX_I(RefitTrack.FLUX, RefitBand.IMPROVED_I, 26_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.COMPUTE, RefitBand.IMPROVED_I),
    FLUX_II(RefitTrack.FLUX, RefitBand.IMPROVED_II, 51_000,
            RefitTrack.POWER, RefitBand.IMPROVED_II, RefitTrack.COMPUTE, RefitBand.IMPROVED_II),
    SENSORS_RESTORED(RefitTrack.SENSORS, RefitBand.RESTORED, 7_000,
            RefitTrack.COMPUTE, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    SENSORS_I(RefitTrack.SENSORS, RefitBand.IMPROVED_I, 16_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_I, RefitTrack.POWER, RefitBand.IMPROVED_I),
    SENSORS_II(RefitTrack.SENSORS, RefitBand.IMPROVED_II, 34_000,
            RefitTrack.COMPUTE, RefitBand.IMPROVED_II, RefitTrack.POWER, RefitBand.IMPROVED_II),
    DRIVE_RESTORED(RefitTrack.DRIVE, RefitBand.RESTORED, 9_500,
            RefitTrack.POWER, RefitBand.RESTORED, RefitTrack.STRUCTURE, RefitBand.RESTORED),
    DRIVE_I(RefitTrack.DRIVE, RefitBand.IMPROVED_I, 18_250,
            RefitTrack.POWER, RefitBand.IMPROVED_I, RefitTrack.COMPUTE, RefitBand.RESTORED),
    DRIVE_II(RefitTrack.DRIVE, RefitBand.IMPROVED_II, 38_000,
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

    private static final Map<RefitOrder, Map<String, Integer>> CORES = new EnumMap<>(RefitOrder.class);
    static {
        bill(COMPUTE_CLUSTER, Commodities.GAMMA_CORE, 1);
        bill(COMPUTE_TACTICAL, Commodities.BETA_CORE, 1);
        bill(RANGE_SIMULATOR, Commodities.GAMMA_CORE, 1);
        bill(SICK_BAY_TRAUMA, Commodities.GAMMA_CORE, 1);
        bill(MECH_CALIBRATION_II, Commodities.BETA_CORE, 1);
        bill(BRIEFING_II, Commodities.GAMMA_CORE, 1);
        bill(FLUX_II, Commodities.ALPHA_CORE, 1);
    }

    private static void bill(RefitOrder order, Object... pairs) {
        var cores = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < pairs.length; i += 2) cores.put((String) pairs[i], (Integer) pairs[i + 1]);
        CORES.put(order, Collections.unmodifiableMap(cores));
    }

    /** The AI cores taken from fleet cargo besides the credits; empty for a credits-only tier. */
    public Map<String, Integer> cores() { return CORES.getOrDefault(this, Map.of()); }

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
