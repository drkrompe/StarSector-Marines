package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.evacuation.HeartSequence;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.evacuation.SwarmBores;
import com.dillon.starsectormarines.battle.evacuation.SwarmPressureSnapshot;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BloomHeartBattleFixture;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveResult;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The heart's evidence (`heart-nouns.md`): both variants play forward with no
 * input and report how the company fared against the heart, the beats they
 * published and every bore's life.
 *
 * <p>Asserts mechanics, not outcomes: every beat is the heart's; the company
 * reached the ground; no mother surged while the heart slept (before the wake
 * on Broad Front, before she was hurt on The Burn, or before a mother fell); on
 * Broad Front the first satellite's collapse woke the heart on that tick; the
 * heart was down exactly when every mother had collapsed. Who won, the ticks and
 * the losses are readings.
 */
@Tag("heart-command-evidence")
class HeartEvidenceTest {

    private static final List<String> MATRIX = List.of(
            "/battle-fixtures/heart-front-v1.json",
            "/battle-fixtures/heart-burn-v1.json");
    private static final int DEFAULT_MAX_TICKS = 27_000;
    private static final String PREFIX = "heart.command.evidence.";

    @Test
    void writesTheHeartsBeatsAndBores() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        int maxTicks = configuredMaxTicks();
        Path output = Path.of(System.getProperty(PREFIX + "outputDir", "build/reports/commander/heart"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output);
        List<Callable<JSONObject>> jobs = new ArrayList<>();
        for (Selected selected : selectedFixtures()) {
            jobs.add(() -> runFixture(selected.fixture(), selected.id(), selected.source(), output, maxTicks));
        }
        List<JSONObject> summaries = EvidenceFanOut.run(jobs);

        JSONArray matrix = new JSONArray();
        StringBuilder md = new StringBuilder("# The heart evidence\n\n")
                .append("Each fixture ").append(EvidenceFanOut.replayWording(EvidenceFanOut.repeat()))
                .append(". Without `-Pfixture` every seed of each form's story-map pool is played (`story-map-pools.md`). ")
                .append("Each fixture's report is in its own folder.\n\n")
                .append("| fixture | form | end | winner | woken | mothers down | satellites down | marines left | swarm left |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        for (JSONObject s : summaries) {
            matrix.put(s);
            md.append("| `").append(s.getString("fixtureId")).append("` | ").append(s.getString("form"))
                    .append(" | ").append(s.getString("termination")).append(" @").append(s.getInt("ticks"))
                    .append(" | ").append(s.getString("winner")).append(" | ").append(s.getBoolean("woken"))
                    .append(" | ").append(s.getInt("mothersCollapsed")).append('/').append(s.getInt("mothers"))
                    .append(" | ").append(s.getInt("satellitesCollapsed")).append('/').append(s.getInt("satellites"))
                    .append(" | ").append(s.getInt("marinesAlive")).append(" | ").append(s.getInt("liveSwarm")).append(" |\n");
        }
        Files.writeString(output.resolve("summary.json"), new JSONObject().put("schemaVersion", 1)
                .put("fixtures", matrix).toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println("[heart-evidence] report " + output.resolve("summary.md"));
    }

    private static JSONObject runFixture(BattleFixture fixture, String fixtureId, String selected, Path root,
                                         int maxTicks) throws Exception {
        Form form = ((BloomHeartBattleFixture) fixture).form();
        Path output = root.resolve(fixtureId);
        Files.createDirectories(output);
        String firstTrace = null;
        JSONObject summary = null;
        for (int run = 0; run < EvidenceFanOut.repeat(); run++) {
            try (BattleSimulation sim = fixture.build();
                 CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(run == 0 ? root : null, fixtureId, sim)) {
                assertNotNull(sim.getHeart(), "the heart must install its sequence");
                sim.setCommandTraceEnabled(true, fixture.kind());
                int landfallTick = -1;
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    capture.afterAdvance();
                    if (landfallTick < 0 && marinesAshore(sim)) landfallTick = sim.getSimTickIndex();
                }
                if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
                // The campaign hand-off ends the board; evidence ends it here to read how each beat came out.
                ObjectiveBoard board = sim.getOptionalObjectives();
                if (board != null) board.end(sim.getSimTickIndex());
                String trace = sim.getCommandTraceJsonLines();
                if (firstTrace == null) {
                    firstTrace = trace;
                    summary = summarize(sim, form, maxTicks, landfallTick);
                    summary.put("fixtureId", fixtureId).put("fixture", selected);
                    assertHeartLaws(sim, form);
                } else {
                    assertEquals(firstTrace, trace, "the heart must replay byte-identically");
                }
            }
        }
        Files.writeString(output.resolve("heart.jsonl"), firstTrace, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.json"), summary.toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"), markdown(summary), StandardCharsets.UTF_8);
        return summary;
    }

    /** The mechanics the heart owns, read off the bores' event log and the beats. */
    private static void assertHeartLaws(BattleSimulation sim, Form form) {
        Map<String, Integer> beatTick = new HashMap<>();
        for (StoryBeatLog.Event beat : sim.getStoryBeats()) {
            assertTrue(beat.id().startsWith(HeartSequence.BEAT_PREFIX), "a foreign beat: " + beat.id());
            beatTick.putIfAbsent(beat.id().substring(HeartSequence.BEAT_PREFIX.length()), beat.tick());
        }
        assertTrue(beatTick.containsKey("start"), "the heart never started: " + beatTick.keySet());
        int firstSatelliteDown = Integer.MAX_VALUE, firstMotherDown = Integer.MAX_VALUE;
        for (SwarmBores.Event event : sim.swarmBores().events()) {
            if (event.kind() != SwarmBores.EventKind.COLLAPSED) continue;
            if (event.bore().mother()) firstMotherDown = Math.min(firstMotherDown, event.tick());
            else firstSatelliteDown = Math.min(firstSatelliteDown, event.tick());
        }
        if (form == Form.BROAD_FRONT && firstSatelliteDown != Integer.MAX_VALUE) {
            assertEquals(firstSatelliteDown, beatTick.getOrDefault("heart_surge", -1),
                    "the first satellite's collapse must wake the heart on its tick");
        }
        for (SwarmBores.Event event : sim.swarmBores().events()) {
            if (event.kind() != SwarmBores.EventKind.SURGE || !event.bore().mother()) continue;
            int wake = Math.min(firstMotherDown, form == Form.BROAD_FRONT
                    ? beatTick.getOrDefault("heart_surge", Integer.MAX_VALUE)
                    : hurtTick(sim, event.bore()));
            assertTrue(event.tick() >= wake, "mother " + event.bore().beatKey() + " surged at " + event.tick()
                    + " while the heart slept (woke at " + wake + ")");
        }
        boolean heartDown = beatTick.containsKey("heart_down");
        boolean allMothers = sim.swarmBores().bores().stream().filter(SwarmBore::mother).allMatch(SwarmBore::collapsed);
        assertEquals(allMothers && sim.getWinner() == Faction.MARINE, heartDown,
                "the heart is down exactly when every mother has collapsed");
    }

    /** The tick a mother was first hurt into a breakout, or never. */
    private static int hurtTick(BattleSimulation sim, SwarmBore bore) {
        String key = HeartSequence.BEAT_PREFIX + "breakout_" + bore.beatKey();
        for (StoryBeatLog.Event beat : sim.getStoryBeats()) if (beat.id().equals(key)) return beat.tick();
        return Integer.MAX_VALUE;
    }

    private static JSONObject summarize(BattleSimulation sim, Form form, int maxTicks, int landfallTick) throws Exception {
        JSONArray bores = new JSONArray();
        int mothers = 0, mothersDown = 0, satellites = 0, satellitesDown = 0;
        SwarmPressureSnapshot director = sim.getSwarmPressureSnapshot();
        Map<String, SwarmPressureSnapshot.BoreState> states = new HashMap<>();
        if (director != null) for (SwarmPressureSnapshot.BoreState state : director.bores()) states.put(state.key(), state);
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.mother()) { mothers++; if (bore.collapsed()) mothersDown++; }
            else { satellites++; if (bore.collapsed()) satellitesDown++; }
            SwarmPressureSnapshot.BoreState state = states.get(bore.beatKey());
            bores.put(new JSONObject().put("bore", bore.beatKey()).put("mother", bore.mother())
                    .put("x", bore.x()).put("y", bore.y()).put("tier", bore.tier().name())
                    .put("integrity", state == null ? 0f : state.integrity())
                    .put("surges", bore.surges()).put("bodiesSpawned", bore.bodiesSpawned())
                    .put("matriarchReleased", bore.matriarchReleased()).put("collapsedTick", bore.collapsedTick()));
        }
        JSONArray beats = new JSONArray();
        for (StoryBeatLog.Event beat : sim.getStoryBeats()) {
            beats.put(new JSONObject().put("tick", beat.tick()).put("id", beat.id()).put("audience", beat.audience().name()));
        }
        int marines = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE && !sim.identity().type(unit).isTurret()) marines++;
        }
        return new JSONObject()
                .put("form", form.name())
                .put("termination", sim.isComplete() ? "COMPLETE" : "TIMEOUT")
                .put("winner", sim.getWinner() == null ? "NONE" : sim.getWinner().name())
                .put("ticks", sim.getSimTickIndex()).put("maxTicks", maxTicks)
                .put("landfallTick", landfallTick)
                .put("woken", sim.getHeart().woken())
                .put("mothers", mothers).put("mothersCollapsed", mothersDown)
                .put("satellites", satellites).put("satellitesCollapsed", satellitesDown)
                .put("marinesAlive", marines)
                .put("directorReason", director == null ? "" : director.pressureReason())
                .put("liveSwarm", director == null ? 0 : director.liveRunners())
                .put("bores", bores).put("beats", beats).put("objectives", objectives(sim));
    }

    /** How each optional objective came out, by key. */
    private static JSONObject objectives(BattleSimulation sim) throws Exception {
        JSONObject out = new JSONObject();
        ObjectiveBoard board = sim.getOptionalObjectives();
        if (board != null) {
            for (ObjectiveResult result : board.results()) {
                out.put(result.key(), result.status().name().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String markdown(JSONObject s) throws Exception {
        StringBuilder md = new StringBuilder("# ").append(s.getString("fixtureId")).append("\n\n")
                .append(s.getString("form")).append(": ").append(s.getString("termination")).append(" at tick ")
                .append(s.getInt("ticks")).append(", winner ").append(s.getString("winner")).append(", ")
                .append(s.getInt("marinesAlive")).append(" marines and ").append(s.getInt("liveSwarm"))
                .append(" swarm left. Landfall at ").append(s.getInt("landfallTick")).append(".\n\n")
                .append("| bore | cell | tier | integrity | surges | bodies | matriarch | collapsed |\n|---|---|---|---|---|---|---|---|\n");
        JSONArray bores = s.getJSONArray("bores");
        for (int i = 0; i < bores.length(); i++) {
            JSONObject b = bores.getJSONObject(i);
            md.append("| ").append(b.getString("bore")).append(" | ").append(b.getInt("x")).append(',').append(b.getInt("y"))
                    .append(" | ").append(b.getString("tier")).append(" | ")
                    .append(String.format(Locale.ROOT, "%.2f", b.getDouble("integrity")))
                    .append(" | ").append(b.getInt("surges")).append(" | ").append(b.getInt("bodiesSpawned"))
                    .append(" | ").append(b.getBoolean("matriarchReleased")).append(" | ").append(b.getInt("collapsedTick")).append(" |\n");
        }
        JSONObject objectives = s.getJSONObject("objectives");
        if (objectives.length() > 0) {
            md.append("\n## Objectives\n\n");
            List<String> keys = new ArrayList<>();
            for (var it = objectives.keys(); it.hasNext(); ) keys.add(String.valueOf(it.next()));
            keys.sort(null);
            for (String key : keys) md.append("- `").append(key).append("` ").append(objectives.getString(key)).append('\n');
        }
        md.append("\n## Beats\n\n");
        JSONArray beats = s.getJSONArray("beats");
        for (int i = 0; i < beats.length(); i++) {
            JSONObject b = beats.getJSONObject(i);
            md.append("- ").append(b.getInt("tick")).append(" `").append(b.getString("id")).append("`\n");
        }
        return md.toString();
    }

    private static boolean marinesAshore(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE && !sim.identity().type(unit).isTurret()) return true;
        }
        return false;
    }

    private static BattleFixture loadFixture(String selected) throws Exception {
        String json;
        if (selected.startsWith("/")) {
            try (InputStream stream = HeartEvidenceTest.class.getResourceAsStream(selected)) {
                if (stream == null) throw new IllegalStateException("Missing heart fixture: " + selected);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(selected).toAbsolutePath().normalize());
        }
        return BattleFixtureJson.fromJson(new JSONObject(json));
    }

    private record Selected(BattleFixture fixture, String id, String source) { }

    /**
     * The fixtures named by {@code -Pfixture} (several separated by ';' play side
     * by side), or else every seed of each form's story-map pool, the matrix
     * fixture standing in for everything but the seed.
     */
    private static List<Selected> selectedFixtures() throws Exception {
        String selected = System.getProperty(PREFIX + "fixture.path", "").trim();
        List<Selected> out = new ArrayList<>();
        if (!selected.isBlank()) {
            for (String path : selected.split(";")) out.add(new Selected(loadFixture(path), fixtureId(path), path));
            return out;
        }
        for (String template : MATRIX) {
            BloomHeartBattleFixture heart = (BloomHeartBattleFixture) loadFixture(template);
            for (Density density : densities()) {
                String d = density == Density.RURAL ? "" : "-" + density.name().toLowerCase(Locale.ROOT);
                for (SurfacePalette biome : SurfacePalette.values()) {
                    String b = biome.name().toLowerCase(Locale.ROOT);
                    for (StoryMapPool.Entry entry : StoryMapPools.pool(BloomHeartSetup.storyMap(heart.form(), density)).entries(biome)) {
                        out.add(new Selected(new BloomHeartBattleFixture(entry.seed(), heart.form(), biome, density, heart.manifest()),
                                fixtureId(template) + d + "-" + b + "-seed" + entry.seed(),
                                template + " at pooled " + density.name().toLowerCase(Locale.ROOT) + " " + b + " seed " + entry.seed()));
                    }
                }
            }
        }
        return out;
    }

    /** {@code -Pdensities=town,city} plays only those densities' pools; every density by default. */
    private static List<Density> densities() {
        String named = System.getProperty(PREFIX + "densities", "").trim();
        if (named.isBlank()) return List.of(Density.values());
        List<Density> out = new ArrayList<>();
        for (String d : named.split(",")) {
            String n = d.trim().toUpperCase(Locale.ROOT);
            out.add(Density.valueOf(n.equals("URBAN") ? "CITY" : n));
        }
        return out;
    }

    private static String fixtureId(String selected) {
        String name = Path.of(selected.startsWith("/") ? selected.substring(1) : selected).getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static int configuredMaxTicks() {
        String configured = System.getProperty(PREFIX + "maxTicks", "").trim();
        int result = configured.isBlank() ? DEFAULT_MAX_TICKS : Integer.parseInt(configured);
        if (result < 1) throw new IllegalArgumentException(PREFIX + "maxTicks must be positive");
        return result;
    }
}
