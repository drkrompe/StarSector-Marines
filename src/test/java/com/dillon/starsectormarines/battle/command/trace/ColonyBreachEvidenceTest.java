package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.evacuation.ColonyBreachSequence;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.evacuation.ColonyPopulation;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Colony Breach evidence: the canonical colony plays forward with no input and
 * reports its breach — where the colony broke open, when, and the beats it
 * published — beside the corridor and director traces the rescue already
 * writes.
 *
 * <p>Asserts mechanics, not outcomes: the plan is complete and the same on a
 * second construction, no site stands in a protected zone or on a landing,
 * and the beats that fire come in the order the noun doc's flow gives them.
 * Whether the cohort gets out is a reading in the summary.
 */
@Tag("colony-breach-command-evidence")
class ColonyBreachEvidenceTest {
    private static final String DEFAULT_FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int DEFAULT_MAX_TICKS = 18_000;
    private static final String PREFIX = "colony.breach.command.evidence.";

    /** The flow's order. A beat that fires never precedes one listed before it. */
    private static final List<String> FLOW = List.of(
            "landfall", "shelter_reached", "tremor",
            "bore_erupted_1", "bore_erupted_2", "bore_erupted_3", "bore_erupted_4",
            "mother_bore_erupted");

    @Test
    void writesTheBreachScheduleBeatsAndRescueTraces() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        int maxTicks = configuredMaxTicks();
        BattleFixture fixture = loadFixture();
        Path output = Path.of(System.getProperty(PREFIX + "outputDir",
                "build/reports/commander/colony-breach")).toAbsolutePath().normalize();
        Files.createDirectories(output);

        List<ColonyEruptionPlan.Site> replayedSites;
        try (BattleSimulation again = fixture.build()) {
            assertNotNull(again.getColonyBreach(), "the rescue must install the breach");
            replayedSites = again.getColonyBreach().plan().sites();
        }

        int repeat = Integer.getInteger("commander.evidence.repeat", 1);
        String firstTrace = null;
        JSONObject summary = null;
        JSONArray shuttleTouchdowns = null;
        JSONObject marineContact = null;
        for (int run = 0; run < Math.max(1, repeat); run++) {
            try (BattleSimulation sim = fixture.build();
                 CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(
                         run == 0 ? output : null, "colony-breach", sim)) {
                ColonyBreachSequence breach = sim.getColonyBreach();
                ColonyEruptionPlan plan = breach.plan();
                assertTrue(plan.isComplete(), "the colony path owes a full eruption plan");
                assertEquals(replayedSites, plan.sites(), "eruption sites must be stable across constructions");
                assertTrue(breach.colonists().size() >= ColonyPopulation.MIN_COLONISTS,
                        "the colony carries " + breach.colonists().size() + " colonists outside the shelter");
                for (ColonyEruptionPlan.Site site : plan.sites()) {
                    assertFalse(sim.isInsideRescueOpeningProtectedZone(site.x(), site.y()),
                            "site " + site + " stands in the shelter or lift zone");
                    assertTrue(sim.getGrid().isWalkable(site.x(), site.y()), "site " + site + " is not open ground");
                }

                sim.setCommandTraceEnabled(true, fixture.kind());
                // Each company shuttle's touchdowns on its pad: the gap between
                // two is its re-arm cycle, which is what a far landing costs.
                Map<Long, ShuttleState> lastState = new HashMap<>();
                JSONArray touchdowns = new JSONArray();
                // The company's first contact: the first tick a marine fired a
                // round or took damage, and where it stood.
                JSONObject contact = null;
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    capture.afterAdvance();
                    if (contact == null) contact = firstMarineContact(sim);
                    for (long id : sim.getAirEntityIds()) {
                        ShuttleMission mission = sim.world().mission(id);
                        if (mission == null || !onMarinePad(sim, mission)) continue;
                        ShuttleState before = lastState.put(id, mission.state);
                        if (mission.state == ShuttleState.LANDED && before != ShuttleState.LANDED) {
                            touchdowns.put(new JSONObject().put("shuttle", id)
                                    .put("tick", sim.getSimTickIndex()));
                        }
                    }
                }
                if (run == 0) {
                    shuttleTouchdowns = touchdowns;
                    marineContact = contact;
                }
                if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
                String trace = sim.getCommandTraceJsonLines();
                if (firstTrace == null) {
                    firstTrace = trace;
                    summary = summarize(sim, breach, fixture, maxTicks, trace);
                } else {
                    assertEquals(firstTrace, trace, "Colony Breach must replay byte-identically");
                }
            }
        }

        summary.put("shuttleTouchdowns", shuttleTouchdowns);
        summary.put("marineContact", marineContact == null ? JSONObject.NULL : marineContact);
        JSONArray beats = summary.getJSONArray("beats");
        List<String> fired = new ArrayList<>();
        for (int i = 0; i < beats.length(); i++) {
            String id = beats.getJSONObject(i).getString("id");
            assertTrue(id.startsWith(ColonyBreachSequence.BEAT_PREFIX), "a foreign beat: " + id);
            fired.add(id.substring(ColonyBreachSequence.BEAT_PREFIX.length()));
        }
        int last = -1;
        for (String key : FLOW) {
            int at = fired.indexOf(key);
            if (at < 0) continue;
            assertTrue(at > last, key + " fired out of the flow's order: " + fired);
            last = at;
        }
        assertTrue(fired.contains("landfall"), "no Marine ever reached the ground: " + fired);
        if (fired.contains("shelter_reached")) {
            assertTrue(fired.contains("tremor") || summary.getInt("ticks")
                            < summary.getInt("shelterReachedTick") + 4 * 30,
                    "the cohort released and the colony never trembled: " + fired);
        }

        Files.writeString(output.resolve("colony-breach.jsonl"), firstTrace, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.json"), summary.toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"), markdown(summary), StandardCharsets.UTF_8);
        System.out.println("[colony-breach-evidence] report " + output.resolve("summary.json"));
    }

    private static JSONObject summarize(BattleSimulation sim, ColonyBreachSequence breach,
                                        BattleFixture fixture, int maxTicks, String trace) throws Exception {
        ColonyBreachSequence.Snapshot snapshot = breach.snapshot(sim);
        // Where the company came down, and how long before anyone died: the
        // first casualty of either side, and the first of the company's own.
        JSONArray pads = new JSONArray();
        for (LandingPad pad : sim.getMarineLandingPads()) {
            pads.put(new JSONObject().put("x", pad.centerX).put("y", pad.centerY)
                    .put("approach", pad.approach.name()));
        }
        int firstContact = -1;
        int firstMarineCasualty = -1;
        for (String line : trace.split("\n")) {
            if (!line.contains("\"event\": \"casualty\"") && !line.contains("\"event\":\"casualty\"")) continue;
            JSONObject event = new JSONObject(line);
            int tick = event.getInt("tick");
            if (firstContact < 0) firstContact = tick;
            if (firstMarineCasualty < 0 && "MARINE".equals(event.optString("faction"))) firstMarineCasualty = tick;
            if (firstMarineCasualty >= 0) break;
        }
        JSONArray sites = new JSONArray();
        for (int i = 0; i < snapshot.sites().size(); i++) {
            ColonyEruptionPlan.Site site = snapshot.sites().get(i);
            sites.put(new JSONObject()
                    .put("number", site.number())
                    .put("role", site.role().name())
                    .put("x", site.x()).put("y", site.y())
                    .put("place", site.placeKind() == null ? JSONObject.NULL : site.placeKind().name())
                    .put("delayTicks", site.delayTicks())
                    .put("eruptionTick", snapshot.eruptionTicks().get(i))
                    .put("erupted", snapshot.erupted().get(i)));
        }
        JSONArray beats = new JSONArray();
        for (StoryBeatLog.Event event : sim.getStoryBeats()) {
            beats.put(new JSONObject().put("tick", event.tick()).put("id", event.id())
                    .put("audience", event.audience().name()));
        }
        var tracker = sim.getCivilianEvacuationTracker();
        return new JSONObject()
                .put("schemaVersion", 1)
                .put("schedulerMode", "SERIAL_DETERMINISTIC")
                .put("fixture", selectedFixture())
                .put("fixtureKind", fixture.kind())
                .put("mapWidth", sim.getGrid().getWidth())
                .put("mapHeight", sim.getGrid().getHeight())
                .put("maxTicks", maxTicks)
                .put("termination", sim.isComplete() ? "COMPLETE" : "TIMEOUT")
                .put("winner", sim.getWinner() != null ? sim.getWinner().name() : JSONObject.NULL)
                .put("ticks", sim.getSimTickIndex())
                .put("phase", snapshot.phase().name())
                .put("landfallTick", snapshot.landfallTick())
                .put("shelterReachedTick", snapshot.shelterReachedTick())
                .put("tremorTick", snapshot.tremorTick())
                .put("firstContactTick", firstContact)
                .put("firstMarineCasualtyTick", firstMarineCasualty)
                .put("marinePads", pads)
                .put("colonists", snapshot.colonists())
                .put("colonistsAlive", snapshot.colonistsAlive())
                .put("evacuated", tracker.evacuatedCount())
                .put("registered", tracker.registeredCount())
                .put("sites", sites)
                .put("beats", beats);
    }

    /** The first marine to have fired or been hurt, as {tick, x, y}, or {@code null}. */
    private static JSONObject firstMarineContact(BattleSimulation sim) throws Exception {
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.identity().type(id).combatant) continue;
            if (sim.telemetry().roundsFired(id) == 0 && sim.telemetry().damageTaken(id) <= 0f) continue;
            return new JSONObject().put("tick", sim.getSimTickIndex())
                    .put("x", (int) sim.world().x(id)).put("y", (int) sim.world().y(id));
        }
        return null;
    }

    /** Whether a shuttle is one of the company's, setting down on a marine pad. */
    private static boolean onMarinePad(BattleSimulation sim, ShuttleMission mission) {
        for (LandingPad pad : sim.getMarineLandingPads()) {
            if (pad.contains((int) mission.lzX, (int) mission.lzY)) return true;
        }
        return false;
    }

    private static String markdown(JSONObject s) throws Exception {
        StringBuilder out = new StringBuilder("# Colony Breach evidence\n\n");
        out.append("Fixture `").append(s.getString("fixture")).append("` on a ")
                .append(s.getInt("mapWidth")).append('x').append(s.getInt("mapHeight"))
                .append(" map: ").append(s.getString("termination")).append(" at tick ")
                .append(s.getInt("ticks")).append(", winner ").append(s.get("winner"))
                .append(", ").append(s.getInt("evacuated")).append('/').append(s.getInt("registered"))
                .append(" evacuated; ").append(s.getInt("colonistsAlive")).append('/')
                .append(s.getInt("colonists")).append(" colonists alive outside the shelter.\n\n")
                .append("Landfall at tick ").append(s.getInt("landfallTick"))
                .append(", the company's first contact at ").append(s.get("marineContact"))
                .append(", first casualty of any side at ").append(s.getInt("firstContactTick"))
                .append(", first marine casualty at ").append(s.getInt("firstMarineCasualtyTick"))
                .append(", shelter reached at ").append(s.getInt("shelterReachedTick"))
                .append(". Marine pads: ").append(s.getJSONArray("marinePads")).append(".\n\n## Eruption sites\n\n| # | role | cell | place | erupts | open |\n|---|---|---|---|---|---|\n");
        JSONArray sites = s.getJSONArray("sites");
        for (int i = 0; i < sites.length(); i++) {
            JSONObject site = sites.getJSONObject(i);
            out.append("| ").append(site.getInt("number") == 0 ? "mother" : String.valueOf(site.getInt("number")))
                    .append(" | ").append(site.getString("role"))
                    .append(" | ").append(site.getInt("x")).append(',').append(site.getInt("y"))
                    .append(" | ").append(site.get("place"))
                    .append(" | ").append(site.getInt("eruptionTick"))
                    .append(" | ").append(site.getBoolean("erupted")).append(" |\n");
        }
        out.append("\n## Beats\n\n| tick | beat |\n|---|---|\n");
        JSONArray beats = s.getJSONArray("beats");
        for (int i = 0; i < beats.length(); i++) {
            JSONObject beat = beats.getJSONObject(i);
            out.append("| ").append(beat.getInt("tick")).append(" | `").append(beat.getString("id")).append("` |\n");
        }
        return out.toString();
    }

    private static BattleFixture loadFixture() throws Exception {
        String selected = selectedFixture();
        String json;
        if (selected.startsWith("/")) {
            try (InputStream stream = ColonyBreachEvidenceTest.class.getResourceAsStream(selected)) {
                if (stream == null) throw new IllegalStateException("Missing Colony Breach fixture: " + selected);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(selected).toAbsolutePath().normalize());
        }
        return BattleFixtureJson.fromJson(new JSONObject(json));
    }

    private static String selectedFixture() {
        String selected = System.getProperty(PREFIX + "fixture.path", "").trim();
        return selected.isBlank() ? DEFAULT_FIXTURE : selected;
    }

    private static int configuredMaxTicks() {
        String configured = System.getProperty(PREFIX + "maxTicks", "").trim();
        int result = configured.isBlank() ? DEFAULT_MAX_TICKS : Integer.parseInt(configured);
        if (result < 1) throw new IllegalArgumentException(PREFIX + "maxTicks must be positive");
        return result;
    }
}
