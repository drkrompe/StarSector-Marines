package com.dillon.starsectormarines.battle.warship;

import java.util.Map;

/**
 * The readings every hull-fight scene reports about who rounds between hulls actually met
 * ({@link WarshipRoundSweep}): hits on hulls, how many were met on the way, rounds stopped by a hull they were not
 * fired at, friendly fire, hits on escorts and on escorts screening their charge, and how long mounts held for a
 * friend in the line.
 */
public final class HullContactReadings {

    private HullContactReadings() {}

    public static void put(Map<String, Number> metrics, WarshipShipFire fire) {
        int struck = fire.hullsStruck();
        metrics.put("hullsStruck", struck);
        metrics.put("hullsStruckInFlight", fire.hullsStruckInFlight());
        metrics.put("unintendedStops", fire.unintendedStops());
        metrics.put("friendlyHits", fire.friendlyHits());
        metrics.put("friendlyFirePercent", struck == 0 ? 0f : Math.round(1000f * fire.friendlyHits() / struck) / 10f);
        metrics.put("escortHits", fire.escortHits());
        metrics.put("screenHits", fire.screenHits());
        metrics.put("lineHeldMountSeconds", Math.round(fire.lineHeldSeconds() * 10f) / 10f);
        metrics.put("lineRefusals", fire.lineRefusals());
        fire.lineHeldBySide().forEach((side, seconds) -> metrics.put("lineHeldMountSeconds" + side.name(),
                Math.round(seconds * 10f) / 10f));
    }
}
