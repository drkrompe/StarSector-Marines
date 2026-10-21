package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;
import com.dillon.starsectormarines.battle.evacuation.ColonyBreachSequence;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.evacuation.ColonyPopulation;
import com.dillon.starsectormarines.battle.evacuation.ColonyRedoubts;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private static final int DEFAULT_MAX_TICKS = 24_000;
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
        List<CivilianRedoubt> replayedRedoubts;
        try (BattleSimulation again = fixture.build()) {
            assertNotNull(again.getColonyBreach(), "the rescue must install the breach");
            replayedSites = again.getColonyBreach().plan().sites();
            replayedRedoubts = again.getCivilianRedoubts();
        }

        int repeat = Integer.getInteger("commander.evidence.repeat", 1);
        String firstTrace = null;
        JSONObject summary = null;
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
                List<CivilianRedoubt> redoubts = sim.getCivilianRedoubts();
                assertEquals(replayedRedoubts, redoubts, "redoubts must be stable across constructions");
                if (ColonyRedoubts.enabled()) {
                    assertTrue(redoubts.size() >= 1 + ColonyRedoubts.MIN_HOLDOUTS
                            && redoubts.size() <= 1 + ColonyRedoubts.MAX_HOLDOUTS, "redoubts: " + redoubts);
                }
                int cohort = 0;
                for (CivilianRedoubt redoubt : redoubts) {
                    cohort += redoubt.cohort();
                    if (redoubt.isMain()) continue;
                    assertFalse(sim.isInsideRescueOpeningProtectedZone(redoubt.approachX(), redoubt.approachY()),
                            "holdout " + redoubt + " stands in the shelter or lift zone");
                    assertTrue(redoubt.garrisonSquadId() >= 0, "holdout " + redoubt.number() + " has no garrison");
                    assertTrue(redoubt.cohort() >= ColonyRedoubts.MIN_COHORT
                            && redoubt.cohort() <= ColonyRedoubts.MAX_COHORT, "holdout cohort " + redoubt);
                }
                assertEquals(sim.getCivilianEvacuationTracker().expectedCount(), cohort,
                        "every member of the cohort belongs to one redoubt");
                for (ColonyEruptionPlan.Site site : plan.sites()) {
                    assertFalse(sim.isInsideRescueOpeningProtectedZone(site.x(), site.y()),
                            "site " + site + " stands in the shelter or lift zone");
                    assertTrue(sim.getGrid().isWalkable(site.x(), site.y()), "site " + site + " is not open ground");
                }

                sim.setCommandTraceEnabled(true, fixture.kind());
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    capture.afterAdvance();
                }
                if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
                String trace = sim.getCommandTraceJsonLines();
                if (firstTrace == null) {
                    firstTrace = trace;
                    summary = summarize(sim, breach, fixture, maxTicks);
                } else {
                    assertEquals(firstTrace, trace, "Colony Breach must replay byte-identically");
                }
            }
        }

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
                                        BattleFixture fixture, int maxTicks) throws Exception {
        ColonyBreachSequence.Snapshot snapshot = breach.snapshot(sim);
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
                .put("colonists", snapshot.colonists())
                .put("colonistsAlive", snapshot.colonistsAlive())
                .put("evacuated", tracker.evacuatedCount())
                .put("registered", tracker.registeredCount())
                .put("sites", sites)
                .put("redoubts", redoubts(snapshot))
                .put("beats", beats);
    }

    private static JSONArray redoubts(ColonyBreachSequence.Snapshot snapshot) throws Exception {
        JSONArray out = new JSONArray();
        for (CivilianRedoubt redoubt : snapshot.redoubts()) {
            out.put(new JSONObject()
                    .put("number", redoubt.number())
                    .put("main", redoubt.isMain())
                    .put("x", redoubt.approachX()).put("y", redoubt.approachY())
                    .put("garrisonSquad", redoubt.garrisonSquadId())
                    .put("cohort", redoubt.cohort())
                    .put("reachedTick", redoubt.releasedTick())
                    .put("fellTick", redoubt.fellTick())
                    .put("sealed", redoubt.sealed())
                    .put("walking", redoubt.walking()));
        }
        return out;
    }

    private static String markdown(JSONObject s) throws Exception {
        StringBuilder out = new StringBuilder("# Colony Breach evidence\n\n");
        out.append("Fixture `").append(s.getString("fixture")).append("` on a ")
                .append(s.getInt("mapWidth")).append('x').append(s.getInt("mapHeight"))
                .append(" map: ").append(s.getString("termination")).append(" at tick ")
                .append(s.getInt("ticks")).append(", winner ").append(s.get("winner"))
                .append(", ").append(s.getInt("evacuated")).append('/').append(s.getInt("registered"))
                .append(" evacuated; ").append(s.getInt("colonistsAlive")).append('/')
                .append(s.getInt("colonists")).append(" colonists alive outside the shelter.\n\n## Eruption sites\n\n| # | role | cell | place | erupts | open |\n|---|---|---|---|---|---|\n");
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
        out.append("\n## Redoubts\n\n| # | door | garrison | cohort | reached | fell | sealed at end |\n|---|---|---|---|---|---|---|\n");
        JSONArray redoubts = s.getJSONArray("redoubts");
        for (int i = 0; i < redoubts.length(); i++) {
            JSONObject r = redoubts.getJSONObject(i);
            out.append("| ").append(r.getInt("number")).append(r.getBoolean("main") ? " (shelter)" : "")
                    .append(" | ").append(r.getInt("x")).append(',').append(r.getInt("y"))
                    .append(" | ").append(r.getInt("garrisonSquad"))
                    .append(" | ").append(r.getInt("cohort"))
                    .append(" | ").append(r.getInt("reachedTick"))
                    .append(" | ").append(r.getInt("fellTick"))
                    .append(" | ").append(r.getInt("sealed")).append(" |\n");
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
