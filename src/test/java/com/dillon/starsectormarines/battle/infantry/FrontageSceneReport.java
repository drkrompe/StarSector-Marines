package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Crowding;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Sample;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Scene;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.SquadSample;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Machine-readable summary of one played {@link FrontageScene}.
 *
 * <p>The console timeline is for a person watching a run go by; this is for
 * whoever comes back to the scene later — a session asking whether a change
 * moved the numbers, a reviewer checking a claim, a future harness diffing two
 * runs. It answers the questions the scene exists to answer without anyone
 * re-deriving them from prose: what the frontage was, which layer each garrison
 * held, whether posts ever overlapped between squads, how tightly bodies
 * packed, and when the run crossed each of its interesting thresholds.
 *
 * <p>Every field is a measurement of the run. Nothing here is a verdict: the
 * assertions live in the test, and a report that quietly decided what "good"
 * meant would let a threshold drift without anything failing.
 */
final class FrontageSceneReport {

    /** Where runs are written. One file per scenario label, replaced each run. */
    static final Path REPORT_ROOT = Path.of("build", "reports", "frontage-scene");

    private FrontageSceneReport() {}

    /**
     * Write {@code label}.json for a completed run and return the path.
     * Deterministic for a given seed and configuration, so two runs of an
     * unchanged scene produce identical bytes and a diff means something moved.
     */
    static Path write(String label, Scene scene, List<Sample> samples)
            throws IOException, JSONException {
        JSONObject root = new JSONObject();
        root.put("label", label);
        root.put("approach", scene.approach().name());
        root.put("edge", scene.approach().renderedEdge());
        root.put("map", new JSONObject()
                .put("width", FrontageScene.WIDTH)
                .put("height", FrontageScene.HEIGHT));
        root.put("force", force(scene));
        root.put("frontage", frontage(scene));
        root.put("garrisons", garrisons(scene, samples));
        root.put("crowding", crowding(samples));
        root.put("timeline", timeline(samples));
        root.put("events", events(samples));

        Path output = REPORT_ROOT.resolve(label + ".json").toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, root.toString(2) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        return output;
    }

    private static JSONObject force(Scene scene) throws JSONException {
        return new JSONObject()
                .put("garrisonSquads", scene.garrisons().size())
                .put("assaultSquads", scene.assaults().size())
                .put("garrisonMembers", scene.garrisons().stream()
                        .mapToInt(s -> s.originalSize).sum())
                .put("assaultMembers", scene.assaults().stream()
                        .mapToInt(s -> s.originalSize).sum());
    }

    private static JSONObject frontage(Scene scene) throws JSONException {
        List<DefenseFrontage.Aperture> apertures =
                DefenseFrontage.forCompound(scene.primary(), scene.sim());
        Set<DefenseFrontage.Facing> facings = new LinkedHashSet<>();
        int windows = 0;
        for (DefenseFrontage.Aperture aperture : apertures) {
            facings.add(aperture.facing());
            if (aperture.kind() == DefenseFrontage.Kind.WINDOW) windows++;
        }
        return new JSONObject()
                .put("apertures", apertures.size())
                .put("windows", windows)
                .put("entrances", apertures.size() - windows)
                .put("facings", facings.size());
    }

    /** Per-squad rows: which layer it held, and the most it ever manned. */
    private static JSONArray garrisons(Scene scene, List<Sample> samples) throws JSONException {
        JSONArray rows = new JSONArray();
        for (int index = 0; index < scene.garrisons().size(); index++) {
            int squadId = scene.garrisons().get(index).id;
            String scope = "none";
            int peakApertures = 0;
            int peakOnPost = 0;
            int standToSamples = 0;
            for (Sample sample : samples) {
                for (SquadSample row : sample.garrisons()) {
                    if (row.squadId() != squadId) continue;
                    if (row.aperturePosts() > 0) scope = row.scope();
                    peakApertures = Math.max(peakApertures, row.aperturePosts());
                    peakOnPost = Math.max(peakOnPost, row.membersOnPost());
                    if (row.frontageRelevant()) standToSamples++;
                }
            }
            rows.put(new JSONObject()
                    .put("squadId", squadId)
                    .put("node", scene.nodes().isEmpty() ? "none"
                            : nodeLabel(scene, index))
                    .put("scope", scope)
                    .put("peakAperturePosts", peakApertures)
                    .put("peakMembersOnPost", peakOnPost)
                    .put("standToSamples", standToSamples));
        }
        return rows;
    }

    private static String nodeLabel(Scene scene, int index) {
        var node = scene.garrisons().get(index).assignedNode;
        return node == null ? "none" : node.kind + "@" + node.anchorX + "," + node.anchorY;
    }

    /** The crowding question, reduced to the few numbers that answer it. */
    private static JSONObject crowding(List<Sample> samples) throws JSONException {
        int maxCollisions = 0;
        int samplesWithCollision = 0;
        int maxUnitsInCell = 0;
        int maxCrowdedPairs = 0;
        double maxPacking = 0d;
        for (Sample sample : samples) {
            Crowding crowding = sample.crowding();
            maxCollisions = Math.max(maxCollisions, crowding.postCollisions());
            if (crowding.postCollisions() > 0) samplesWithCollision++;
            maxUnitsInCell = Math.max(maxUnitsInCell, crowding.maxUnitsInCell());
            maxCrowdedPairs = Math.max(maxCrowdedPairs, crowding.crowdedPairs());
            maxPacking = Math.max(maxPacking, crowding.packing());
        }
        return new JSONObject()
                .put("maxPostCollisions", maxCollisions)
                .put("samplesWithPostCollision", samplesWithCollision)
                .put("maxUnitsInOneCell", maxUnitsInCell)
                .put("maxCrowdedPairs", maxCrowdedPairs)
                .put("maxUnitsPerOccupiedCell", round(maxPacking));
    }

    /** Ticks at which the run first crossed each threshold it exists to demonstrate. */
    private static JSONObject events(List<Sample> samples) throws JSONException {
        JSONObject events = new JSONObject();
        events.put("firstStandTo", firstTick(samples, Sample::frontageRelevant));
        events.put("firstMemberOnPost", firstTick(samples, s -> s.membersOnPost() > 0));
        events.put("firstBreach", firstTick(samples, Sample::enemyInside));
        events.put("standToWhileBreached", samples.stream()
                .anyMatch(s -> s.enemyInside() && s.frontageRelevant()));
        return events;
    }

    private static Object firstTick(List<Sample> samples,
                                    java.util.function.Predicate<Sample> test) {
        for (Sample sample : samples) if (test.test(sample)) return sample.tick();
        return JSONObject.NULL;
    }

    /** One compact row per sample — enough to plot or diff, not the whole world state. */
    private static JSONArray timeline(List<Sample> samples) throws JSONException {
        JSONArray rows = new JSONArray();
        for (Sample sample : samples) {
            rows.put(new JSONObject()
                    .put("t", sample.tick())
                    .put("goal", sample.goal())
                    .put("aperturePosts", sample.aperturePosts())
                    .put("reservePosts", sample.reservePosts())
                    .put("postsFacingThreat", sample.postsFacingThreat())
                    .put("membersOnPost", sample.membersOnPost())
                    .put("standToLegal", sample.frontageRelevant())
                    .put("breached", sample.enemyInside())
                    .put("pressure", round(sample.believedPressure()))
                    .put("liveMarines", sample.liveMarines())
                    .put("postCollisions", sample.crowding().postCollisions())
                    .put("maxUnitsInCell", sample.crowding().maxUnitsInCell())
                    .put("crowdedPairs", sample.crowding().crowdedPairs()));
        }
        return rows;
    }

    /** Three decimals, so an unchanged run diffs clean instead of churning on float noise. */
    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
