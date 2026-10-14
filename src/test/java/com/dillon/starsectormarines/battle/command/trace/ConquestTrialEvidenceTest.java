package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ConquestTrialGround;
import com.dillon.starsectormarines.battle.world.gen.ConquestTrialGround.Placement;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conquest on a trial ground: one landing, one battery fort on the lane or off it, one objective and the keep, played
 * by the production mission layer and both production commanders. Opt-in evidence ({@code commanderEvidence
 * -Pmission=conquest-trial}), excluded from {@code test}. Outcomes are findings, never assertions: the only thing this
 * fails on is a trial that could not be built or did not play.
 *
 * <p>The default matrix crosses the fort's two placements with three seeds at one composition. {@code -Pfixture} takes
 * a JSON array of objects; every field but {@code id} is optional: {@code seed}, {@code placement}
 * ({@code ON_PATH}/{@code OFFSET}), {@code offset}, {@code shuttleType}, {@code shuttles}, {@code seats},
 * {@code cycles}, {@code elites}, {@code militia}, {@code patrolSize}, {@code objectiveGarrison}, {@code keepGarrison}
 * and {@code risk}. {@code -PsnapshotEveryTicks=N} renders review frames and a {@code visuals/<fixture>/review.gif}.
 */
@Tag("conquest-trial-evidence")
class ConquestTrialEvidenceTest {

    private static final String PREFIX = "conquest.trial.evidence";
    /** Within this many cells of the fort's centre a marine is "at the fort" for the readings: about its belt. */
    private static final float FORT_REACH = 45f;

    @Test
    void playsTheTrialMatrix() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits(),
                "evidence runs force the serial scheduler");
        Path output = Path.of(System.getProperty(PREFIX + ".outputDir", "build/reports/commander/conquest-trial"));
        Files.createDirectories(output);
        int maxTicks = Integer.getInteger(PREFIX + ".maxTicks", 9000);
        JSONArray fixtures = fixtures();
        StringBuilder md = new StringBuilder("# Conquest trial ground\n\n")
                .append("Production Conquest mission layer and commanders on a synthetic ")
                .append(ConquestTrialGround.WIDTH).append('x').append(ConquestTrialGround.HEIGHT)
                .append(" map: one landing, one battery fort, one objective, the keep. ")
                .append(maxTicks).append(" ticks at most. Outcomes are findings, not assertions.\n\n")
                .append("| fixture | fort | seed | marines | defenders | winner | ticks | objective taken | keep taken "
                        + "| marines at fort | marine losses | defender losses | position runs (start/end) |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        JSONArray summaries = new JSONArray();
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < fixtures.length(); i++) {
            JSONObject fixture = fixtures.getJSONObject(i);
            Outcome o = play(fixture, maxTicks, output);
            md.append(o.row()).append('\n');
            detail.append("\n## ").append(o.id()).append("\n\n").append(o.detail());
            summaries.put(o.json());
            Files.writeString(output.resolve(o.id() + ".trace.jsonl"), o.trace(), StandardCharsets.UTF_8);
        }
        md.append(detail);
        Files.writeString(output.resolve("summary.md"), md.toString(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.json"), summaries.toString(2), StandardCharsets.UTF_8);
        System.out.println("[conquest-trial] report " + output.resolve("summary.md").toAbsolutePath());
    }

    private static JSONArray fixtures() throws Exception {
        String custom = System.getProperty(PREFIX + ".fixture.path", "").trim();
        if (!custom.isEmpty()) return new JSONArray(Files.readString(Path.of(custom)));
        JSONArray out = new JSONArray();
        for (Placement placement : Placement.values()) {
            for (int seed : new int[]{41, 42, 43}) {
                out.put(new JSONObject().put("id", placement.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-" + seed)
                        .put("seed", seed).put("placement", placement.name()));
            }
        }
        return out;
    }

    private record Outcome(String id, String row, String detail, JSONObject json, String trace) {}

    private static Outcome play(JSONObject f, int maxTicks, Path visualRoot) throws Exception {
        String id = f.getString("id");
        long seed = f.optLong("seed", 41L);
        Placement placement = Placement.valueOf(f.optString("placement", Placement.ON_PATH.name()));
        ConquestTrialGround.Ground ground = ConquestTrialGround.generate(seed, placement,
                f.optInt("offset", ConquestTrialGround.DEFAULT_OFFSET),
                f.optInt("objectiveGarrison", 8), f.optInt("keepGarrison", 12));
        List<ShuttleAssignment> manifest = new ArrayList<>();
        ShuttleType type = ShuttleType.valueOf(f.optString("shuttleType", ShuttleType.VALKYRIE.name()));
        for (int s = 0; s < f.optInt("shuttles", 4); s++) {
            manifest.add(new ShuttleAssignment(type, f.optInt("cycles", 1), f.optInt("seats", 12)));
        }
        RiskLevel risk = RiskLevel.valueOf(f.optString("risk", RiskLevel.MEDIUM.name()));
        DefenderRoster roster = DefenderRoster.explicit(f.optInt("elites", 6), f.optInt("militia", 42), List.of(),
                f.optInt("patrolSize", 6), risk);
        ShuttleArrivalPlan arrival = ShuttleArrivalPlan.legacy();
        int[] fortCentre = ground.fort().placement().cell(ground.fort().plan().width() / 2,
                ground.fort().plan().height() / 2);

        BattleSetup.MapBuild build = BattleSetup.populateConquest(ground.map(), seed, TraversalAxis.SOUTH_TO_NORTH,
                new Random(seed), manifest, arrival, arrival.sizedConfigFor(manifest), false,
                OperationTier.ESTABLISHED, risk, TargetProfile.NEUTRAL, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                GroundRosterRegistry.resolve(""), roster);
        try (BattleSimulation sim = build.sim();
             CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(visualRoot, id, sim)) {
            FortOutcomes forts = new FortOutcomes();
            FortDefenderPositionProbe positionUse = new FortDefenderPositionProbe();
            FightingPositionIndex startPositions = sim.fightingPositions();
            assertTrue(startPositions.runCount() > 0, "the trial fort stands a belt of fighting positions");
            sim.setCommandTraceEnabled(true, "conquest-trial");
            int defendersAtStart = count(sim, Faction.DEFENDER);
            int peakMarines = 0;
            int objectiveTaken = -1;
            int keepTaken = -1;
            int firstAtFort = -1;
            int peakAtFort = 0;
            int marineSamplesAtFort = 0;
            CompoundService compounds = sim.getCompoundService();
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
                capture.afterAdvance();
                int tick = sim.getSimTickIndex();
                forts.sample(sim);
                positionUse.sample(sim);
                peakMarines = Math.max(peakMarines, count(sim, Faction.MARINE));
                if (objectiveTaken < 0 && held(compounds, ground.objective())) objectiveTaken = tick;
                if (keepTaken < 0 && held(compounds, ground.keep())) keepTaken = tick;
                if (tick % 30 == 0) {
                    int at = within(sim, Faction.MARINE, fortCentre[0], fortCentre[1], FORT_REACH);
                    if (at > 0 && firstAtFort < 0) firstAtFort = tick;
                    peakAtFort = Math.max(peakAtFort, at);
                    marineSamplesAtFort += at;
                }
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            String trace = sim.getCommandTraceJsonLines();
            int marinesEnd = count(sim, Faction.MARINE);
            int defendersEnd = count(sim, Faction.DEFENDER);
            FightingPositionCount positions = FightingPositionCount.of(startPositions, sim.fightingPositions());
            String winner = sim.isComplete() && sim.getWinner() != null ? sim.getWinner().name() : "timeout";
            int ticks = sim.getSimTickIndex();
            String row = String.format(Locale.ROOT,
                    "| %s | %s | %d | %d shuttles x %d seats x %d | %d | %s | %d | %s | %s | first %s, peak %d, %d samples "
                            + "| %d of %d | %d of %d | %s |",
                    id, placement, seed, manifest.size(), manifest.get(0).seatsPerSortie, manifest.get(0).cycles,
                    defendersAtStart, winner, ticks, tickOrNever(objectiveTaken), tickOrNever(keepTaken),
                    tickOrNever(firstAtFort), peakAtFort, marineSamplesAtFort, peakMarines - marinesEnd, peakMarines,
                    defendersAtStart - defendersEnd, defendersAtStart,
                    positions.startRuns() + "/" + positions.endRuns() + " runs, " + positions.startCells() + "/" + positions.endCells() + " cells");
            String detail = "- fort centre (" + fortCentre[0] + "," + fortCentre[1] + "), lane x=" + ConquestTrialGround.LANE_X
                    + "; objective " + ground.objective().kind + " at (" + ground.objective().anchorX + ","
                    + ground.objective().anchorY + "), keep at (" + ground.keep().anchorX + "," + ground.keep().anchorY + ")\n"
                    + forts.describe(sim) + ConquestCommandBalanceTest.fortOrders(trace) + positionUse.describe();
            JSONObject json = new JSONObject().put("fixture", f).put("winner", winner).put("ticks", ticks)
                    .put("objectiveTakenTick", objectiveTaken).put("keepTakenTick", keepTaken)
                    .put("firstMarineAtFortTick", firstAtFort).put("peakMarinesAtFort", peakAtFort)
                    .put("marineSamplesAtFort", marineSamplesAtFort).put("peakMarines", peakMarines)
                    .put("marinesEnd", marinesEnd).put("defendersStart", defendersAtStart).put("defendersEnd", defendersEnd);
            return new Outcome(id, row, detail, json, trace);
        }
    }

    private static boolean held(CompoundService compounds, TacticalNode node) {
        CompoundService.Record record = compounds.getRecord(node);
        return record != null && record.state == CompoundState.MARINE_HELD;
    }

    private static String tickOrNever(int tick) {
        return tick < 0 ? "never" : Integer.toString(tick);
    }

    private static int count(BattleSimulation sim, Faction faction) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == faction && sim.identity().type(id).combatant) count++;
        }
        return count;
    }

    private static int within(BattleSimulation sim, Faction faction, int cx, int cy, float reach) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != faction || !sim.identity().type(id).combatant) continue;
            float dx = sim.world().cellX(id) - cx;
            float dy = sim.world().cellY(id) - cy;
            if (dx * dx + dy * dy <= reach * reach) count++;
        }
        return count;
    }
}
