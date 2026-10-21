package com.dillon.starsectormarines.battle.command;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A mission defender commander that mans garrisonables reactively (`garrisonables.md`, mission defenders), as evidence
 * reads it: running totals over the battle so far.
 */
public interface ReactiveDefenceSource {

    /**
     * Pulses planned, manning orders, releases, fall backs, rooms holds, shifts, holder-pulses, the most squads holding
     * at once, the distinct places manned, and machine guards attached, stood down and at most at once, by name.
     */
    Map<String, Integer> reactiveDefenceFigures();

    /** The figures of one planner, in a stable order. */
    static Map<String, Integer> figures(MissionGarrisons.Stats stats) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("pulses", stats.pulses);
        out.put("manOrders", stats.manOrders);
        out.put("releases", stats.releases);
        out.put("fallBacks", stats.fallBacks);
        out.put("roomsHolds", stats.roomsHolds);
        out.put("shifts", stats.shifts);
        out.put("holderPulses", stats.holderPulses);
        out.put("peakHolders", stats.peakHolders);
        out.put("placesManned", stats.distinctPlaces());
        out.put("guardAttaches", stats.guardAttaches);
        out.put("guardStandDowns", stats.guardStandDowns);
        out.put("peakGuards", stats.peakGuards);
        return out;
    }
}
