package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.Verdict;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How well a hull's turrets stay laid on the hull they are fighting: for every turret, the ticks the ship's chosen hull
 * lay in its reach and arc, and of those the ticks its barrel was within {@link WarshipShipFire#ALIGNED_DEGREES} of
 * where it would aim. A turret that swings back to rest between rounds spends most of a fight re-laying, so the share
 * reads that directly; rounds fired are read beside it, since a barrel laid but silent is a different fault.
 */
public final class MountLayReadings {

    /** Ticks a turret must have had a hull in reach and arc before its share is judged: ten seconds. */
    public static final int MIN_ELIGIBLE_TICKS = 300;

    /** Per turret, {@code {eligible ticks, on-target ticks, rounds}}, keyed {@code who_slot_weapon}. */
    private final Map<String, int[]> ticks = new LinkedHashMap<>();

    /** Samples every turret of {@code ship} on this tick, under the name {@code who}. */
    public void observe(String who, Warship ship) {
        if (ship.destroyed() || ship.departed()) return;
        for (Warship.Mount m : ship.mounts()) {
            float error = WarshipShipFire.layError(ship, m);
            String key = who + "_" + m.slot.id() + "_" + m.weapon.id();
            int[] t = ticks.get(key);
            if (t == null && Float.isNaN(error)) continue;
            if (t == null) ticks.put(key, t = new int[3]);
            t[2] = m.shotsFired;
            if (Float.isNaN(error)) continue;
            t[0]++;
            if (error <= WarshipShipFire.ALIGNED_DEGREES) t[1]++;
        }
    }

    /** The share, in percent, of its in-reach ticks turret {@code key} was laid; -1 when it never had a hull in reach. */
    public int percent(String key) {
        int[] t = ticks.get(key);
        return t == null || t[0] == 0 ? -1 : Math.round(100f * t[1] / t[0]);
    }

    public void put(Map<String, Number> metrics) {
        for (Map.Entry<String, int[]> e : ticks.entrySet()) {
            int[] t = e.getValue();
            metrics.put("laid_" + e.getKey() + "_percent", percent(e.getKey()));
            metrics.put("laid_" + e.getKey() + "_inReachSeconds", Math.round(t[0] / 3f) / 10f);
            metrics.put("laid_" + e.getKey() + "_rounds", t[2]);
        }
    }

    /**
     * Every turret that had a hull in reach and arc for at least {@link #MIN_ELIGIBLE_TICKS} was laid on it for at least
     * {@code minShare} of that time.
     */
    public Verdict verdict(String name, float minShare) {
        String worst = null;
        float worstShare = 2f;
        int judged = 0;
        for (Map.Entry<String, int[]> e : ticks.entrySet()) {
            int[] t = e.getValue();
            if (t[0] < MIN_ELIGIBLE_TICKS) continue;
            judged++;
            float share = (float) t[1] / t[0];
            if (share < worstShare) {
                worstShare = share;
                worst = e.getKey();
            }
        }
        if (judged == 0) return Verdict.pass(name, "no turret had a hull in reach and arc for ten seconds");
        return Verdict.of(name, worstShare >= minShare, String.format(Locale.ROOT,
                "%d turrets judged; least laid %s at %.0f%% of its in-reach time (bar %.0f%%)",
                judged, worst, 100f * worstShare, 100f * minShare));
    }
}
