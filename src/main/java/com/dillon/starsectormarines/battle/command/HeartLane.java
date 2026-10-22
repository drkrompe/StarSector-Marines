package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;

import java.util.List;

/**
 * One lane into the heart (`heart-nouns.md`, Lane): the band of ground within
 * {@code halfWidth} cells either side of the line a force advances along. A
 * lane is ground, not a corridor. Its force goes for the satellites standing in
 * its band first, then the nearest living mother, until none is left.
 *
 * @param advancesAlongY whether the attack runs along the map's y axis
 * @param across         the lane's line, as the cell coordinate across the advance
 * @param halfWidth      cells either side of the line a satellite may stand and still be the lane's
 */
public record HeartLane(boolean advancesAlongY, int across, int halfWidth) {

    /** The bore this lane's force goes for from {@code (x, y)}, or {@code null} when none is left. */
    public KnownBore target(List<KnownBore> bores, float x, float y) {
        KnownBore best = nearest(bores, x, y, false);
        return best != null ? best : nearest(bores, x, y, true);
    }

    private KnownBore nearest(List<KnownBore> bores, float x, float y, boolean mothers) {
        KnownBore best = null;
        float bestDistance = Float.MAX_VALUE;
        for (KnownBore bore : bores) {
            if (!bore.living() || bore.mother() != mothers) continue;
            if (!mothers && Math.abs((advancesAlongY ? bore.x() : bore.y()) - across) > halfWidth) continue;
            float dx = bore.x() - x, dy = bore.y() - y, distance = dx * dx + dy * dy;
            if (distance < bestDistance) { bestDistance = distance; best = bore; }
        }
        return best;
    }
}
