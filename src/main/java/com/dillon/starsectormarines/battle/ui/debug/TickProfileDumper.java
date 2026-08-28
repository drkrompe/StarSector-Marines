package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickProfile;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * One-shot JSON dump of the live per-phase tick profile. Records per-phase
 * average + worst-tick microseconds, percent share of the average tick,
 * sample count, plus a few sim aggregates (unit count, simTickIndex) that
 * frame the numbers. Written to
 * {@code saves/common/starsector_marines/debug/tick_profile_<tickIndex>.json}
 * — same common-folder route the squad dumper uses (the only file I/O
 * available to mod code, per the Starsector script sandbox).
 *
 * <p>An embedded battle fixture can push the dump past the SettingsAPI
 * common-folder size cap, so the fixture spills to a sibling
 * {@code *.fixture.json} when the combined document would not fit;
 * {@code BattleFixtureJson.fromJson} reads that spill file directly, so a
 * spilled fixture stays replayable through {@code -Pfixture=}.
 *
 * <p>Triggered from the {@code TickProfileDebugPanel} DUMP button. The dump
 * captures whatever the profile's display buffer is currently exposing —
 * the last completed averaging window, not the in-progress one. If the user
 * mashes DUMP at a moment of interest, they get the averages preceding it.
 */
@DebugOnly
public final class TickProfileDumper {

    private static final Logger LOG = Logger.getLogger(TickProfileDumper.class);
    /** Bumped when the dump shape changes — v8 adds commander and influence subphase rows. */
    private static final int SCHEMA_VERSION = 8;
    /**
     * SettingsAPI rejects any common-folder text write longer than this many
     * characters. It throws from its own writer thread when routed through
     * {@code writeJSONToCommon}, where mod code cannot catch it, so this class
     * serializes up front and checks the length itself.
     */
    static final int MAX_COMMON_FILE_CHARS = 1_048_576;
    /** Two-space indent keeps the dump readable at roughly half the bulk of the game's own indent. */
    private static final int JSON_INDENT = 2;

    private TickProfileDumper() {}

    /** Manual dump — caller is the DUMP button. */
    public static String dump(BattleSimulation sim) {
        return dump(sim, null, null);
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
        return dump(sim, null, spike);
    }

    /** Dump with optional replayable tick-zero construction metadata. */
    public static String dump(BattleSimulation sim, BattleFixture fixture,
                              TickProfile.Spike spike) {
        if (sim == null) return null;
        try {
            TickProfile profile = sim.getTickProfile();
            JSONObject root = new JSONObject();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("simTickIndex", sim.simTickIndex);
            root.put("windowSamples", profile.sampleCount());
            root.put("unitCount", sim.liveUnitCount());
            root.put("squadCount", sim.getSquads().size());
            root.put("triggerSource", spike != null ? "auto-spike" : "manual");
            // Construction inputs only: replay rebuilds tick zero through
            // BattleSetup; this is deliberately not a live-state snapshot.
            // Attached last, once the size of everything else is known.
            JSONObject fixtureJson =
                    fixture != null ? BattleFixtureJson.toJson(fixture) : null;

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

            // Inner profile — per-behavior + per-primitive sub-step breakdown
            // for THIS tick (the spike tick for auto-dumps, the in-progress
            // tick for manual). For spike dumps we use the snapshot captured
            // when the spike fired so the numbers don't get overwritten by
            // the time we serialize them; for manual dumps we read the live
            // counters directly. Either way the JSON shape is identical.
            JSONArray innerArr = new JSONArray();
            root.put("innerTimingSemantics",
                    "aggregate worker time; nested primitive and influence buckets overlap enclosing behavior and commander buckets");
            TickInnerProfile.Snapshot innerSnap = (spike != null) ? spike.innerSnapshot : null;
            TickInnerProfile liveInner = sim.getTickInnerProfile();
            for (TickInnerProfile.Bucket b : TickInnerProfile.Bucket.VALUES) {
                long ns;
                int cnt;
                if (innerSnap != null) {
                    ns = innerSnap.nanosOf(b);
                    cnt = innerSnap.countOf(b);
                } else {
                    ns = liveInner.nanosOf(b);
                    cnt = liveInner.countOf(b);
                }
                JSONObject bo = new JSONObject();
                bo.put("name", b.name());
                bo.put("nanos", ns);
                bo.put("us", ns / 1_000.0);
                bo.put("count", cnt);
                bo.put("avgUsPerCall", cnt > 0 ? (ns / 1_000.0) / cnt : 0.0);
                innerArr.put(bo);
            }
            root.put("inner", innerArr);

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
            return write(path, root, fixtureJson);
        } catch (Exception ex) {
            LOG.warn("TickProfileDumper: dump failed", ex);
            return null;
        }
    }

    /**
     * Attaches the fixture, then writes the dump under the common-folder size
     * cap. The fixture is embedded when the whole document fits; otherwise it
     * spills to a sibling file that the dump names, and is dropped outright
     * only when it cannot fit on its own either. Returns the written profile
     * path, or {@code null} when even the fixture-free profile is too large.
     *
     * <p>Paths here are the logical common-folder names; SettingsAPI appends
     * {@code .data} to each of them on disk.
     */
    static String write(String path, JSONObject root, JSONObject fixtureJson)
            throws IOException, JSONException {
        String text = null;
        if (fixtureJson != null) {
            root.put("battleFixture", fixtureJson);
            text = root.toString(JSON_INDENT);
            if (text.length() > MAX_COMMON_FILE_CHARS) {
                root.remove("battleFixture");
                // Compact: the spill file is read back by BattleFixtureJson,
                // not by eye, and every saved character is headroom.
                String fixtureText = fixtureJson.toString();
                String fixturePath = fixturePathFor(path);
                if (fixtureText.length() > MAX_COMMON_FILE_CHARS) {
                    root.put("battleFixtureOmitted", fixtureText.length()
                            + " chars exceeds the " + MAX_COMMON_FILE_CHARS
                            + "-char common-folder cap");
                    LOG.warn("TickProfileDumper: battle fixture omitted; "
                            + fixtureText.length() + " chars exceeds the "
                            + MAX_COMMON_FILE_CHARS + "-char cap");
                } else {
                    Global.getSettings().writeTextFileToCommon(fixturePath, fixtureText);
                    root.put("battleFixtureFile", fixturePath);
                    LOG.info("TickProfileDumper: battle fixture spilled to saves/common/"
                            + fixturePath);
                }
                text = null;
            }
        }
        if (text == null) text = root.toString(JSON_INDENT);
        if (text.length() > MAX_COMMON_FILE_CHARS) {
            LOG.warn("TickProfileDumper: dump skipped; " + text.length()
                    + " chars exceeds the " + MAX_COMMON_FILE_CHARS
                    + "-char common-folder cap");
            return null;
        }
        Global.getSettings().writeTextFileToCommon(path, text);
        LOG.info("TickProfileDumper: wrote tick profile to saves/common/" + path);
        return path;
    }

    private static String fixturePathFor(String path) {
        int dot = path.lastIndexOf('.');
        return (dot < 0 ? path : path.substring(0, dot)) + ".fixture.json";
    }

    private static String pathFor(int tickIndex, boolean isSpike) {
        String prefix = isSpike ? "tick_profile_spike_" : "tick_profile_";
        return StarsectorMarinesModPlugin.MOD_ID + "/debug/" + prefix + tickIndex + ".json";
    }
}
