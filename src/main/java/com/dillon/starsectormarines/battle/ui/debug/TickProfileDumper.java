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

    /**
     * Writes the dump and returns the common-folder-relative path on success,
     * or {@code null} on any error (the call site shows a brief status either
     * way; details land in the game log).
     */
    public static String dump(BattleSimulation sim) {
        if (sim == null) return null;
        try {
            TickProfile profile = sim.getTickProfile();
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("simTickIndex", sim.simTickIndex);
            root.put("windowSamples", profile.sampleCount());
            root.put("unitCount", sim.getUnits().size());
            root.put("squadCount", sim.getSquads().size());

            long totalAvgNs = profile.totalAvgNanos();
            root.put("totalAvgUs", totalAvgNs / 1_000.0);
            root.put("totalAvgMs", totalAvgNs / 1_000_000.0);

            JSONArray phases = new JSONArray();
            for (TickProfile.Phase p : TickProfile.Phase.VALUES) {
                long avgNs = profile.avgNanos(p);
                long maxNs = profile.maxNanos(p);
                JSONObject po = new JSONObject();
                po.put("name", p.name());
                po.put("avgUs", avgNs / 1_000.0);
                po.put("maxUs", maxNs / 1_000.0);
                po.put("shareOfTotal", totalAvgNs > 0 ? (double) avgNs / totalAvgNs : 0.0);
                phases.put(po);
            }
            root.put("phases", phases);

            String path = pathFor(sim.simTickIndex);
            Global.getSettings().writeJSONToCommon(path, root, true);
            LOG.info("TickProfileDumper: wrote tick profile to saves/common/" + path);
            return path;
        } catch (Exception ex) {
            LOG.warn("TickProfileDumper: dump failed", ex);
            return null;
        }
    }

    private static String pathFor(int tickIndex) {
        return StarsectorMarinesModPlugin.MOD_ID + "/debug/tick_profile_" + tickIndex + ".json";
    }
}
