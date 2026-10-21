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
     * at once, the distinct places manned, and machine guards attached, stood down and at most at once; the flow tier's
     * stage and forward orders, releases and most staged at once; manning orders drawn from staged squads; and manning
     * orders resolved as arriving before the attack reached the place, after it, or neither; by name.
     */
    Map<String, Integer> reactiveDefenceFigures();

    /** The figures of one planner, in a stable order. */
    static Map<String, Integer> figures(MissionGarrisons garrisons) {
        MissionGarrisons.Stats stats = garrisons.stats();
        PlaceFlow flow = garrisons.flow();
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
        out.put("stageOrders", flow.stageOrders);
        out.put("forwardOrders", flow.forwardOrders);
        out.put("stageReleases", flow.releases);
        out.put("peakStaged", flow.peakStaged);
        out.put("manFromStage", stats.manFromStage);
        out.put("arrivedFirst", stats.arrivedFirst);
        out.put("contactFirst", stats.contactFirst);
        out.put("arrivalUnresolved", stats.arrivalUnresolved);
        out.put("dutyShareWithheld", stats.dutyShareWithheld);
        return out;
    }
}
