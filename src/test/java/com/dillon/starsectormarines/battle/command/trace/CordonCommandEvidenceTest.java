package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.cordon.CordonConvoys;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonRefugees;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.CordonBattleFixture;
import com.dillon.starsectormarines.battle.fabrication.FabricationService;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Cordon through production construction ({@code cordon-nouns.md}):
 * {@code commanderEvidence -Pmission=cordon-line} or {@code cordon-road}, over
 * every seed of the variant's story-map pool unless {@code -Pfixture} names one.
 * Asserts the mechanics (the company landed, every tide the timetable reached
 * came on its tick, the outcome agrees with the redoubts) and reports the
 * battle: beats in order, the line's state each minute, and how it ended.
 */
@Tag("cordon-command-evidence")
class CordonCommandEvidenceTest {

    /** Cells from a calling redoubt's walls an allied hull counts as answering it. */
    private static final float ARMOUR_AT_CALL = 12f;
    private static final String NL = "\n";

    /** The window and a minute over, so a held line finishes inside the budget. */
    private static final int DEFAULT_MAX_TICKS = 28_800;
    private static final int TIMELINE_EVERY_TICKS = 1_800;

    @Test
    void line() throws Exception {
        run("cordon-line", "cordon.line.command.evidence", CordonVariant.LINE);
    }

    @Test
    void road() throws Exception {
        run("cordon-road", "cordon.road.command.evidence", CordonVariant.ROAD);
    }

    private static void run(String mission, String prefix, CordonVariant variant) throws Exception {
        // commanderEvidence names one mission; the other variant's run is not asked for.
        Assumptions.assumeTrue(System.getProperty(prefix + ".outputDir") != null, mission + " not selected");
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        int maxTicks = Integer.getInteger(prefix + ".maxTicks", DEFAULT_MAX_TICKS);
        Path output = Path.of(System.getProperty(prefix + ".outputDir")).toAbsolutePath().normalize();
        List<Selected> fixtures = selectedFixtures(prefix, mission, variant);
        int repeat = EvidenceFanOut.repeat();
        List<Callable<RunResult>> replays = new ArrayList<>(fixtures.size() * repeat);
        for (Selected selected : fixtures) {
            for (int replica = 0; replica < repeat; replica++) {
                Path visualRoot = replica == 0 ? output : null;
                replays.add(() -> play(selected.fixture(), maxTicks, visualRoot, selected.id()));
            }
        }
        List<RunResult> results = EvidenceFanOut.run(replays);

        boolean pool = fixtures.size() > 1;
        StringBuilder matrix = new StringBuilder("# Cordon evidence: ").append(mission).append("\n\n")
                .append("Every seed of the ").append(variant).append("'s story-map pool (`story-map-pools.md`), ")
                .append(EvidenceFanOut.replayWording(repeat)).append(". Outcomes are readings, not assertions; ")
                .append("each seed's report is in its own folder.\n\n")
                .append("| biome | seed | outcome | winner | ended (min) | redoubts fallen |\n|---|---|---|---|---|---|\n");
        JSONArray all = new JSONArray();
        List<String> broken = new ArrayList<>();
        for (int i = 0; i < fixtures.size(); i++) {
            Selected selected = fixtures.get(i);
            List<RunResult> mine = results.subList(i * repeat, (i + 1) * repeat);
            EvidenceFanOut.assertReplaysByteStable(selected.id(), "command events",
                    mine.stream().map(RunResult::trace).toList());
            RunResult first = mine.get(0);
            Path dir = pool ? output.resolve(selected.id()) : output;
            Files.createDirectories(dir.resolve("traces"));
            Files.writeString(dir.resolve("traces/" + selected.id() + ".jsonl"), first.trace(), StandardCharsets.UTF_8);
            JSONObject summary = new JSONObject()
                    .put("schemaVersion", 1)
                    .put("schedulerMode", "SERIAL_DETERMINISTIC")
                    .put("fixture", selected.source())
                    .put("biome", selected.fixture().biome().name())
                    .put("seed", selected.fixture().seed())
                    .put("maxTicks", maxTicks)
                    .put("repeatCount", repeat)
                    .put("termination", first.complete() ? "COMPLETE" : "TIMEOUT")
                    .put("winner", first.winner() != null ? first.winner() : JSONObject.NULL)
                    .put("outcome", first.outcome())
                    .put("ticks", first.ticks())
                    .put("fallen", first.fallen())
                    .put("beats", first.beats())
                    .put("timeline", first.timeline());
            Files.writeString(dir.resolve("summary.json"), summary.toString(2) + '\n', StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("summary.md"), first.report(), StandardCharsets.UTF_8);
            all.put(new JSONObject().put("biome", selected.fixture().biome().name()).put("seed", selected.fixture().seed()).put("folder", pool ? selected.id() : ".")
                    .put("outcome", first.outcome()).put("winner", first.winner() != null ? first.winner() : JSONObject.NULL)
                    .put("ticks", first.ticks()).put("fallen", first.fallen()));
            matrix.append(String.format(Locale.ROOT, "| %s | %d | %s | %s | %.1f | %d |%n",
                    selected.fixture().biome().name().toLowerCase(Locale.ROOT), selected.fixture().seed(),
                    first.outcome(), first.winner() != null ? first.winner() : "—",
                    first.ticks() * BattleSimulation.TICK_DT / 60f, first.fallen()));
            broken.addAll(mechanics(selected.id(), first));
        }
        if (pool) {
            Files.writeString(output.resolve("summary.json"), new JSONObject().put("schemaVersion", 1)
                    .put("mission", mission).put("seeds", all).toString(2) + '\n', StandardCharsets.UTF_8);
            Files.writeString(output.resolve("summary.md"), matrix.toString(), StandardCharsets.UTF_8);
        }
        System.out.println("[cordon-command-evidence] report " + output.resolve("summary.md"));
        assertTrue(broken.isEmpty(), String.join("; ", broken));
    }

    /** Mechanics, not outcomes (CLAUDE.md, Behavior scenes): what one run broke, if anything. */
    private static List<String> mechanics(String id, RunResult run) {
        List<String> broken = new ArrayList<>();
        if (!run.beatIds().contains("cordon.landfall")) broken.add(id + ": the company never came down");
        for (int t = 0; t < run.tidesDue(); t++) {
            if (!run.beatIds().contains("cordon.tide_" + (t + 1))) broken.add(id + ": tide " + (t + 1) + " never came");
        }
        if ("HELD".equals(run.outcome()) && run.fallen() > CordonSequence.Timetable.STANDARD.allowedFallen()) {
            broken.add(id + ": a held line lost more than allowed");
        }
        if ("BROKEN".equals(run.outcome()) && !(run.beatIds().contains("cordon.line_broken")
                || run.beatIds().contains("cordon.gap_closed") || run.beatIds().contains("cordon.convoys_lost")
                || run.beatIds().contains("cordon.convoys_short"))) {
            broken.add(id + ": a broken cordon does not say how");
        }
        return broken;
    }

    private record Selected(CordonBattleFixture fixture, String id, String source) { }

    /**
     * The fixture {@code -Pfixture} names, or else every seed of the variant's
     * story-map pool, the checked-in fixture standing in for everything but the seed.
     */
    private static List<Selected> selectedFixtures(String prefix, String mission, CordonVariant variant) throws Exception {
        String fallback = "/battle-fixtures/" + mission + "-v1.json";
        String selected = System.getProperty(prefix + ".fixture.path", "").trim();
        if (!selected.isBlank()) return List.of(new Selected(loadFixture(selected, variant), mission, selected));
        CordonBattleFixture template = loadFixture(fallback, variant);
        List<Selected> out = new ArrayList<>();
        for (SurfacePalette biome : SurfacePalette.values()) {
            String b = biome.name().toLowerCase(Locale.ROOT);
            for (StoryMapPool.Entry entry : StoryMapPools.pool(CordonSetup.storyMap(variant)).entries(biome)) {
                out.add(new Selected(new CordonBattleFixture(entry.seed(), variant, biome, template.manifest()),
                        mission + "-" + b + "-seed" + entry.seed(), fallback + " at pooled " + b + " seed " + entry.seed()));
            }
        }
        return out;
    }

    private static RunResult play(BattleFixture fixture, int maxTicks, Path visualRoot, String runId) throws Exception {
        try (BattleSimulation sim = fixture.build();
             CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(visualRoot, runId, sim)) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            CordonSequence cordon = sim.getCordon();
            JSONArray timeline = new JSONArray();
            StringBuilder table = new StringBuilder();
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
                capture.afterAdvance();
                if (sim.getSimTickIndex() % TIMELINE_EVERY_TICKS == 0) {
                    JSONObject row = sample(sim, cordon);
                    timeline.put(row);
                    table.append(tableRow(row));
                }
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            JSONArray beats = StoryBeatEvidence.snapshot(sim);
            List<String> ids = new ArrayList<>();
            for (StoryBeatLog.Event event : sim.getStoryBeats()) ids.add(event.id());
            int tidesDue = 0;
            for (int seconds : cordon.timetable().tideSeconds()) {
                if (Math.round(seconds / BattleSimulation.TICK_DT) < sim.getSimTickIndex()) tidesDue++;
            }
            String winner = sim.getWinner() != null ? sim.getWinner().name() : null;
            String report = report(runId, sim, cordon, winner, table.toString());
            return new RunResult(sim.getCommandTraceJsonLines(), sim.getSimTickIndex(), sim.isComplete(), winner,
                    cordon.outcome().name(), cordon.fallen(), beats, ids, timeline, tidesDue, report);
        }
    }

    private static JSONObject sample(BattleSimulation sim, CordonSequence cordon) throws Exception {
        int marines = 0, swarm = 0, allies = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Faction faction = sim.identity().faction(unit);
            if (faction == Faction.MARINE && sim.identity().type(unit).combatant) marines++;
            else if (faction == Faction.ALLY) allies++;
            else if (faction == Faction.DEFENDER && sim.identity().type(unit).isSwarm()) swarm++;
        }
        JSONArray redoubts = new JSONArray();
        for (CordonRedoubt r : cordon.redoubts()) {
            int standing = 0;
            for (int squad : r.teams()) standing += sim.squadMemberCount(squad);
            redoubts.put(new JSONObject().put("number", r.number()).put("state", r.state().name())
                    .put("standing", standing).put("garrison", r.garrison()));
        }
        int bores = 0;
        for (SwarmBore bore : sim.swarmBores().bores()) if (!bore.collapsed()) bores++;
        // Allied armour: hulls alive, and how many stand within reach of a calling redoubt.
        int hulls = 0, atCall = 0;
        if (cordon.armour() != null) {
            for (long hull : cordon.armour().command().hulls()) {
                hulls++;
                GroundBody body = sim.convoy().body(hull);
                for (CordonRedoubt r : cordon.redoubts()) {
                    float nearX = Math.max(r.left(), Math.min(r.right(), body.x));
                    float nearY = Math.max(Math.min(r.top(), r.bottom()), Math.min(Math.max(r.top(), r.bottom()), body.y));
                    if (r.state() == CordonRedoubt.State.CALLING && body.distanceTo(nearX, nearY) <= ARMOUR_AT_CALL) {
                        atCall++;
                        break;
                    }
                }
            }
        }
        return new JSONObject().put("tick", sim.getSimTickIndex())
                .put("minute", sim.getSimTickIndex() * BattleSimulation.TICK_DT / 60f)
                .put("marines", marines).put("allies", allies).put("swarm", swarm)
                .put("livingBores", bores).put("armour", hulls).put("armourAtCall", atCall)
                .put("redoubts", redoubts)
                .put("convoys", cordon.convoys() == null ? "-" : cordon.convoys().describe(sim));
    }

    private static String tableRow(JSONObject row) throws Exception {
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "| %.0f | %d | %d | %d | %d | %d (%d) |",
                row.getDouble("minute"), row.getInt("marines"), row.getInt("allies"), row.getInt("swarm"),
                row.getInt("livingBores"), row.getInt("armour"), row.getInt("armourAtCall")));
        JSONArray redoubts = row.getJSONArray("redoubts");
        for (int i = 0; i < redoubts.length(); i++) {
            JSONObject r = redoubts.getJSONObject(i);
            line.append(' ').append(r.getString("state").toLowerCase(Locale.ROOT)).append(' ')
                    .append(r.getInt("standing")).append('/').append(r.getInt("garrison")).append(" |");
        }
        return line.append(' ').append(row.getString("convoys")).append(" |\n").toString();
    }

    private static String report(String runId, BattleSimulation sim, CordonSequence cordon, String winner,
                                 String table) {
        StringBuilder out = new StringBuilder("# Cordon evidence: ").append(runId).append("\n\n")
                .append("Forced-serial, zero-input production Cordon (").append(cordon.variant())
                .append("). Outcomes are readings, not assertions.\n\n")
                .append("- Outcome: ").append(cordon.outcome()).append('\n')
                .append("- Winner: ").append(winner != null ? winner : "—").append('\n')
                .append(String.format(Locale.ROOT, "- Ended: tick %d (%.1f min)%n", sim.getSimTickIndex(),
                        sim.getSimTickIndex() * BattleSimulation.TICK_DT / 60f))
                .append("- Redoubts fallen: ").append(cordon.fallen()).append(" of ").append(cordon.redoubts().size())
                .append(" (").append(cordon.timetable().allowedFallen()).append(" allowed)\n")
                .append("- Gap redoubts: ").append(cordon.gap()).append('\n')
                .append("- Eruptions: ").append(cordon.eruptions()).append(" of ")
                .append(cordon.timetable().eruptionSeconds().size()).append(" on the timetable\n");
        if (cordon.refugees() != null) {
            out.append("- Refugees: ").append(cordon.refugees().out()).append(" out, ")
                    .append(cordon.refugees().lost()).append(" lost of ")
                    .append(cordon.refugees().groups() * CordonRefugees.GROUP_SIZE).append(" in ")
                    .append(cordon.refugees().groups()).append(" groups\n");
        }
        if (cordon.columns() != null) {
            out.append("- Allied columns joined: ").append(cordon.columns().joined()).append(" of ")
                    .append(cordon.timetable().columnSeconds().size()).append('\n');
        }
        if (cordon.convoys() != null) {
            CordonConvoys convoys = cordon.convoys();
            out.append("- Convoys: ").append(convoys.out()).append(" out, ").append(convoys.lost())
                    .append(" lost of ").append(convoys.convoys()).append(" (")
                    .append(cordon.timetable().convoysNeeded()).append(" needed); townspeople ")
                    .append(convoys.peopleOut()).append(" out, ").append(convoys.peopleLost())
                    .append(" lost, ").append(convoys.town()).append(" still in the town").append(NL);
            for (long truck : convoys.trucks()) {
                GroundBody body = sim.convoy().body(truck);
                out.append(String.format(Locale.ROOT, "  - Truck %d at %.0f,%.0f, structure %.0f/%.0f%s%s",
                        truck, body.x, body.y, sim.world().hp(truck), sim.world().maxHp(truck),
                        sim.convoy().mission(truck).authoredRoutePaused ? ", held" : "", NL));
            }
        }
        if (cordon.logistics() != null) {
            out.append("- Logistics base: ").append(cordon.logistics().built()).append(" machines built, ")
                    .append(cordon.logistics().carried()).append(" squads carried up, ")
                    .append(cordon.logistics().waiting()).append(" waiting at the base, ")
                    .append(cordon.logistics().queued()).append(" machines waiting to leave the shed, ")
                    .append(cordon.logistics().carriers().size()).append(" carriers and ")
                    .append(cordon.armour() == null ? 0 : cordon.armour().command().hulls().size())
                    .append(" allied hulls alive\n");
            out.append("  - Machine door: ").append(cordon.logistics().doorX()).append(',')
                    .append(cordon.logistics().doorY()).append("; apron muster ")
                    .append(cordon.logistics().musterX()).append(',')
                    .append(cordon.logistics().musterY()).append(NL);
            for (long hull : cordon.armour().command().hulls()) {
                GroundBody body = sim.convoy().body(hull);
                VehicleMoveOrderService.RefusedOrder refused = sim.getVehicleMoveOrderService().refusal(hull);
                out.append(String.format(Locale.ROOT, "  - Hull %d %s at %.0f,%.0f, structure %.0f/%.0f%s%s",
                        hull, sim.convoy().vehicleType(hull), body.x, body.y, sim.world().hp(hull),
                        sim.world().maxHp(hull), refused == null ? "" : ", refused " + refused.reason()
                                + " to " + refused.requestedX() + "," + refused.requestedY(), NL));
            }
            if (sim.getFabrication() != null) {
                for (FabricationService.Works bay : sim.getFabrication().bays()) {
                    out.append("  - Shed bay ").append(bay.siteId).append(": ").append(bay.completed)
                            .append(" finished, on the stocks ").append(bay.hasFrame()
                                    ? String.format(Locale.ROOT, "%s at %.0f%%", bay.vehicle,
                                    100f * sim.world().hp(bay.frameId) / sim.world().maxHp(bay.frameId)) : "nothing")
                            .append('\n');
                }
            }
        }
        for (CordonRedoubt r : cordon.redoubts()) {
            out.append("- Redoubt ").append(r.number()).append(": cells ").append(r.left()).append(',').append(r.top())
                    .append(" to ").append(r.right()).append(',').append(r.bottom()).append(", guns ")
                    .append(r.guns().size()).append('\n');
        }
        out.append("\n## Beats\n\n| min | beat |\n|---|---|\n");
        for (StoryBeatLog.Event event : sim.getStoryBeats()) {
            out.append(String.format(Locale.ROOT, "| %.2f | %s |%n", event.tick() * BattleSimulation.TICK_DT / 60f,
                    event.id()));
        }
        out.append("\n## The line each minute\n\nArmour: allied hulls alive (at a calling redoubt). Convoys: each on "
                + "the road, its lead truck's cell along the corridor")
                .append(cordon.convoys() == null ? "" : " (the gap is cell " + cordon.convoys().gapIndex() + ")")
                .append(", and why it is held.\n\n| min | marines | allies | swarm | bores | armour |");
        for (CordonRedoubt r : cordon.redoubts()) out.append(" redoubt ").append(r.number()).append(" |");
        out.append(" convoys |\n|---|---|---|---|---|---|");
        for (int i = 0; i <= cordon.redoubts().size(); i++) out.append("---|");
        return out.append('\n').append(table).toString();
    }

    private static CordonBattleFixture loadFixture(String path, CordonVariant variant) throws Exception {
        String json;
        if (path.startsWith("/")) {
            try (InputStream stream = CordonCommandEvidenceTest.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing Cordon fixture: " + path);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(path).toAbsolutePath().normalize());
        }
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
        if (!(fixture instanceof CordonBattleFixture cordon) || cordon.variant() != variant) {
            throw new IllegalArgumentException("Cordon " + variant + " evidence requires a Cordon " + variant
                    + " fixture: " + fixture.kind());
        }
        return cordon;
    }

    private record RunResult(String trace, int ticks, boolean complete, String winner, String outcome, int fallen,
                             JSONArray beats, List<String> beatIds, JSONArray timeline, int tidesDue, String report) { }
}
