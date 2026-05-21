package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.profile.TickProfile;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One-shot JSON dump of the live per-phase tick profile. Records per-phase
 * average + worst-tick microseconds, percent share of the average tick,
 * sample count, plus a few sim aggregates (unit count, simTickIndex) that
 * frame the numbers. Written to
 * {@code saves/common/starsector_marines/debug/tick_profile_<tickIndex>.json}
 * — same common-folder route the squad dumper uses (the only file I/O
 * available to mod code, per the Starsector script sandbox).
 *
 * <p>Triggered from the {@code TickProfileDebugPanel} DUMP button. The dump
 * captures whatever the profile's display buffer is currently exposing —
 * the last completed averaging window, not the in-progress one. If the user
 * mashes DUMP at a moment of interest, they get the averages preceding it.
 */
public final class TickProfileDumper {

    private static final Logger LOG = Logger.getLogger(TickProfileDumper.class);
    /** Bumped when the dump shape changes — lets offline tools recognize older dumps. */
    private static final int SCHEMA_VERSION = 1;

    private TickProfileDumper() {}

    /** Manual dump — caller is the DUMP button. */
    public static String dump(BattleSimulation sim) {
        return dump(sim, null);
    }

    /**
     * Writes the dump and returns the common-folder-relative path on success,
     * or {@code null} on any error (the call site shows a brief status either
     * way; details land in the game log).
     *
     * <p>{@code spike} is {@code null} for manual dumps (DUMP button). For
     * auto-spike dumps, pass the latched spike — the per-tick value gets
     * recorded alongside the per-window averages so the JSON tells the full
     * story of "this is what spiked vs what the steady state looked like",
     * and the filename gets a {@code _spike_} marker so it's easy to grep.
     */
    public static String dump(BattleSimulation sim, TickProfile.Spike spike) {
        if (sim == null) return null;
        try {
            TickProfile profile = sim.getTickProfile();
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("simTickIndex", sim.simTickIndex);
            root.put("windowSamples", profile.sampleCount());
            root.put("unitCount", sim.getUnits().size());
            root.put("squadCount", sim.getSquads().size());
            root.put("triggerSource", spike != null ? "auto-spike" : "manual");

            long totalAvgNs = profile.totalAvgNanos();
            root.put("totalAvgUs", totalAvgNs / 1_000.0);
            root.put("totalAvgMs", totalAvgNs / 1_000_000.0);

            if (spike != null) {
                JSONObject so = new JSONObject();
                so.put("tickIndex", spike.tickIndex);
                so.put("totalUs", spike.totalNanos / 1_000.0);
                so.put("totalMs", spike.totalNanos / 1_000_000.0);
                so.put("baselineUs", spike.baselineNanos / 1_000.0);
                so.put("ratioOverBaseline", spike.ratio());
                root.put("spike", so);
            }

            JSONArray phases = new JSONArray();
            for (TickProfile.Phase p : TickProfile.Phase.VALUES) {
                long avgNs = profile.avgNanos(p);
                long maxNs = profile.maxNanos(p);
                // Last-tick per-phase numbers matter most for spike dumps —
                // they pinpoint which phase blew up on this specific tick.
                // Included on manual dumps too for parity (cheap to write).
                long lastNs = profile.lastTickNanos(p);
                JSONObject po = new JSONObject();
                po.put("name", p.name());
                po.put("avgUs", avgNs / 1_000.0);
                po.put("maxUs", maxNs / 1_000.0);
                po.put("lastTickUs", lastNs / 1_000.0);
                po.put("shareOfTotal", totalAvgNs > 0 ? (double) avgNs / totalAvgNs : 0.0);
                phases.put(po);
            }
            root.put("phases", phases);

            String path = pathFor(sim.simTickIndex, spike != null);
            Global.getSettings().writeJSONToCommon(path, root, true);
            LOG.info("TickProfileDumper: wrote tick profile to saves/common/" + path);
            return path;
        } catch (Exception ex) {
            LOG.warn("TickProfileDumper: dump failed", ex);
            return null;
        }
    }

    private static String pathFor(int tickIndex, boolean isSpike) {
        String prefix = isSpike ? "tick_profile_spike_" : "tick_profile_";
        return StarsectorMarinesModPlugin.MOD_ID + "/debug/" + prefix + tickIndex + ".json";
    }
}
