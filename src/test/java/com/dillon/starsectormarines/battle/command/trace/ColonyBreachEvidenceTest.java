package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;
import com.dillon.starsectormarines.battle.evacuation.ColonyBreachSequence;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.evacuation.ColonyPopulation;
import com.dillon.starsectormarines.battle.evacuation.ColonyRedoubts;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.evacuation.SwarmBores;
import com.dillon.starsectormarines.battle.evacuation.SwarmPressureSnapshot;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
 * the beats that fire come in the order the noun doc's flow gives them, and
 * spawning has a source: after the tremor no swarm body appears anywhere but
 * at the mouth of a living bore, and none at all once every bore is collapsed.
 * Whether the cohort gets out, and how the bores grew, are readings in the
 * summary.
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
                // Each company shuttle's touchdowns on its pad: the gap between
                // two is its re-arm cycle, which is what a far landing costs.
                Map<Long, ShuttleState> lastState = new HashMap<>();
                JSONArray touchdowns = new JSONArray();
                // The company's first contact: the first tick a marine fired a
                // round or took damage, and where it stood.
                JSONObject contact = null;
                SpawnAudit audit = new SpawnAudit(sim);
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    capture.afterAdvance();
                    audit.afterAdvance(sim, breach);
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
                assertEquals(0, audit.sourceless, "swarm bodies spawned away from a living bore after the tremor: "
                        + audit.firstSourceless);
                assertEquals(0, audit.afterLastCollapse,
                        "swarm bodies spawned after every bore had collapsed");
                if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
                String trace = sim.getCommandTraceJsonLines();
                if (firstTrace == null) {
                    firstTrace = trace;
                    summary = summarize(sim, breach, fixture, maxTicks, trace);
                    summary.put("boreSpawns", audit.boreSpawns);
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

    /** Every swarm body new on a tick, checked against the living bores' mouths once the tremor has come. */
    private static final class SpawnAudit {
        private final LongOpenHashSet seen = new LongOpenHashSet();
        int sourceless;
        int afterLastCollapse;
        int boreSpawns;
        String firstSourceless = "";

        SpawnAudit(BattleSimulation sim) {
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) seen.add(sim.liveUnitAt(i));
        }

        void afterAdvance(BattleSimulation sim, ColonyBreachSequence breach) {
            int tick = sim.getSimTickIndex();
            boolean breached = breach.tremorTick() >= 0 && tick > breach.tremorTick();
            List<SwarmBore> bores = sim.swarmBores().bores();
            boolean allCollapsed = bores.size() == breach.plan().sites().size()
                    && !sim.swarmBores().hasPending() && sim.swarmBores().livingCount() == 0;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (!seen.add(unit)) continue;
                if (!breached || sim.identity().faction(unit) != Faction.DEFENDER
                        || !sim.identity().type(unit).isSwarm()) continue;
                int x = sim.world().cellX(unit);
                int y = sim.world().cellY(unit);
                boolean atMouth = false;
                for (SwarmBore bore : bores) {
                    if (bore.collapsed() && bore.collapsedTick() < tick) continue;
                    if (Math.max(Math.abs(bore.x() - x), Math.abs(bore.y() - y)) <= SwarmBores.MOUTH_RADIUS) {
                        atMouth = true;
                        break;
                    }
                }
                if (atMouth) {
                    boreSpawns++;
                } else {
                    if (sourceless == 0) firstSourceless = unit + " at " + x + "/" + y + " on tick " + tick;
                    sourceless++;
                }
                if (allCollapsed) afterLastCollapse++;
            }
        }
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
        JSONArray bores = new JSONArray();
        SwarmPressureSnapshot director = sim.getSwarmPressureSnapshot();
        List<SwarmPressureSnapshot.BoreState> boreStates = director == null ? List.of() : director.bores();
        for (SwarmPressureSnapshot.BoreState bore : boreStates) {
            SwarmBore state = sim.swarmBores().byEntity(bore.entityId());
            bores.put(new JSONObject()
                    .put("bore", bore.key())
                    .put("x", bore.x()).put("y", bore.y())
                    .put("tier", bore.tier().name())
                    .put("alive", bore.alive())
                    .put("integrity", Math.round(bore.integrity() * 1000f) / 1000.0)
                    .put("growth", Math.round(bore.growth() * 10f) / 10.0)
                    .put("biomassFed", bore.biomassFed())
                    .put("surges", bore.surges())
                    .put("bodiesSpawned", bore.bodiesSpawned())
                    .put("sporeColonies", bore.sporeColonies() + "/" + state.colonies().size())
                    .put("eruptedTick", bore.eruptedTick())
                    .put("boreTierTick", state.tierTick(BoreTier.BORE))
                    .put("mawTierTick", state.tierTick(BoreTier.MAW))
                    .put("collapsedTick", bore.collapsedTick()));
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
                .put("redoubts", redoubts(snapshot))
                .put("bores", bores)
                .put("directorReason", director == null ? JSONObject.NULL : director.pressureReason())
                .put("directorTarget", director == null ? 0 : director.targetPopulation())
                .put("liveSwarm", director == null ? 0 : director.liveRunners())
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
        out.append("\n## Bores\n\n").append(s.getInt("boreSpawns"))
                .append(" bodies came out of bore mouths after the tremor; director ")
                .append(s.get("directorReason")).append(", target ").append(s.getInt("directorTarget"))
                .append(", ").append(s.getInt("liveSwarm")).append(" swarm on the field at the end.\n\n")
                .append("| bore | cell | tier | alive | integrity | growth | biomass | surges | bodies | spore colonies | erupted | bore tier | maw | collapsed |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        JSONArray bores = s.getJSONArray("bores");
        for (int i = 0; i < bores.length(); i++) {
            JSONObject b = bores.getJSONObject(i);
            out.append("| ").append(b.getString("bore")).append(" | ").append(b.getInt("x")).append(',')
                    .append(b.getInt("y")).append(" | ").append(b.getString("tier")).append(" | ")
                    .append(b.getBoolean("alive")).append(" | ").append(b.get("integrity")).append(" | ")
                    .append(b.get("growth")).append(" | ").append(b.get("biomassFed")).append(" | ")
                    .append(b.getInt("surges")).append(" | ").append(b.getInt("bodiesSpawned")).append(" | ")
                    .append(b.getString("sporeColonies")).append(" | ")
                    .append(b.getInt("eruptedTick")).append(" | ").append(b.getInt("boreTierTick")).append(" | ")
                    .append(b.getInt("mawTierTick")).append(" | ").append(b.getInt("collapsedTick")).append(" |\n");
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
