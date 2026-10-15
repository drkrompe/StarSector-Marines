package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.airsupport.AirPoolAllowance;
import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolStock;
import com.dillon.starsectormarines.battle.command.AssaultStyle;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.infantry.SquadSupportDeclaration;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
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
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
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
 * <p>The default matrix crosses the fort's two placements with three seeds at the combined-arms force (sixteen six-seat
 * Aeroshuttles, the Conquest squad kit, two lances and a three-bomber, two-fighter pool), with the fort on the lane
 * played to 18,000 ticks, plus one rifles-only control per placement: four twelve-seat Valkyries and nothing else,
 * the force the trial ground used to default to. Three rifle squads cannot take an entrenched keep, so the control
 * is kept for what it shows that a bigger force hides: a squad that freezes or wanders is visible when there is
 * nobody else to take the ground. A fixture may state {@code maxTicks}; {@code -PmaxTicks} overrides every fixture.
 * {@code -Pfixture} takes
 * a JSON array of objects; every field but {@code id} is optional: {@code seed}, {@code placement}
 * ({@code ON_PATH}/{@code OFFSET}), {@code offset}, {@code shuttleType}, {@code shuttles}, {@code seats},
 * {@code cycles}, {@code elites}, {@code militia}, {@code patrolSize}, {@code objectiveGarrison}, {@code keepGarrison}
 * {@code risk}, and {@code airDefence} (true stamps the fort's optional anti-air yard; absent none).
 * {@code -PsnapshotEveryTicks=N} renders review frames and a {@code visuals/<fixture>/review.gif}.
 *
 * <p>The phased-assault fields are additive and, when omitted, leave production defaults alone: {@code muster} (false declares the
 * mission's {@code AssaultStyle.TRICKLE}; {@code musterPlans:false} is rejected, since plans are part of the muster),
 * {@code supportKit} ({@code {"mortarTeamEvery":6,"observerEvery":4}}, or {@code "conquest"} for those periods; absent
 * issues none); {@code mechDrops} (lances of the Conquest composition the marine commander may call; absent none);
 * {@code airPool} ({@code {"bombers":3,"fighters":2,"regen":true}}; absent none). {@code defenderAirPool} (the same shape, plus
 * {@code observerEvery}, default 3: the defenders' pool and the comms operators it is called through, issued by the same
 * kit path a mission declares; absent none). The trial map is authored with no
 * mission layer above it, so none of the mission's declarations apply unless the fixture states them.
 */
@Tag("conquest-trial-evidence")
class ConquestTrialEvidenceTest {

    private static final String PREFIX = "conquest.trial.evidence";
    /** Within this many cells of the fort's centre a marine is "at the fort" for the readings: about its belt. */
    private static final float FORT_REACH = 45f;

    @Test
    void playsTheTrialMatrix() throws Exception {
        InstalledHullSpecs.install();
        InstalledHullEngines.install();
        try {
            playMatrix();
        } finally {
            InstalledHullEngines.uninstall();
        }
    }

    private void playMatrix() throws Exception {
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
                .append(maxTicks).append(" ticks at most unless a fixture states its own. Outcomes are findings, not assertions.\n\n")
                .append("| fixture | fort | seed | marines | defenders | winner | ticks | objective taken | keep taken "
                        + "| marines at fort | marine losses | defender losses | position runs (start/end) |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        JSONArray summaries = new JSONArray();
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < fixtures.length(); i++) {
            JSONObject fixture = fixtures.getJSONObject(i);
            int ticks = Integer.getInteger(PREFIX + ".maxTicks") != null ? maxTicks : fixture.optInt("maxTicks", maxTicks);
            Outcome o = play(fixture, ticks, output);
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
            String name = placement.name().toLowerCase(Locale.ROOT).replace('_', '-');
            int ticks = placement == Placement.ON_PATH ? 18_000 : 9_000;
            for (int seed : new int[]{41, 42, 43}) {
                out.put(new JSONObject().put("id", name + "-" + seed).put("seed", seed).put("placement", placement.name())
                        .put("shuttleType", ShuttleType.AEROSHUTTLE.name()).put("shuttles", 16).put("seats", 6)
                        .put("supportKit", "conquest").put("mechDrops", 2)
                        .put("airPool", new JSONObject().put("bombers", 3).put("fighters", 2).put("regen", true))
                        .put("maxTicks", ticks));
            }
            out.put(new JSONObject().put("id", name + "-41-rifles").put("seed", 41).put("placement", placement.name())
                    .put("maxTicks", 9_000));
        }
        return out;
    }

    private record Outcome(String id, String row, String detail, JSONObject json, String trace) {}

    /** Plays one fixture. Standing fire missions are standing behaviour, so the retired {@code standingFireMissions} field is refused rather than ignored. */
    private static Outcome play(JSONObject f, int maxTicks, Path visualRoot) throws Exception {
        if (f.has("standingFireMissions")) {
            throw new IllegalArgumentException("fixture '" + f.optString("id", "?") + "': standingFireMissions is retired; "
                    + "standing fire missions are standing behaviour and no longer a fixture field");
        }
        return playFixture(f, maxTicks, visualRoot);
    }

    /** The mission's declarations the fixture states; the neutral profile declares none. */
    private static TargetProfile profileOf(JSONObject f) {
        TargetProfile profile = TargetProfile.NEUTRAL;
        if (!f.optBoolean("muster", true)) profile = profile.withDeclaredAssault(AssaultStyle.TRICKLE);
        if (!f.optBoolean("musterPlans", true)) {
            throw new IllegalArgumentException("fixture " + f.optString("id") + ": musterPlans=false is no longer a mode; "
                    + "a mustering company always executes the stamp's plan. Use muster=false for the trickle.");
        }
        Object kit = f.opt("supportKit");
        if (kit instanceof JSONObject k) {
            profile = profile.withDeclaredSupportKit(SquadSupportDeclaration.NONE.with(Faction.MARINE,
                    new SquadSupportKit(k.optInt("mortarTeamEvery", 0), k.optInt("observerEvery", 0))));
        } else if ("conquest".equals(kit)) {
            profile = profile.withDeclaredSupportKit(SquadSupportDeclaration.NONE.with(Faction.MARINE,
                    new SquadSupportKit(6, 4)));
        }
        int lances = f.optInt("mechDrops", 0);
        if (lances > 0) {
            profile = profile.withDeclaredMechDrops(MechDropDeclaration.NONE.with(Faction.MARINE, new MechDropAllowance(lances,
                    List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.HOUND, MechVariant.SIROCCO))));
        }
        JSONObject pool = f.optJSONObject("airPool");
        if (pool != null) {
            boolean regen = pool.optBoolean("regen", true);
            int bombers = pool.optInt("bombers", 0);
            int fighters = pool.optInt("fighters", 0);
            profile = profile.withDeclaredAirPool(profile.declaredAirPool().with(Faction.MARINE, AirPoolAllowance.of(
                    new AirPoolStock(bombers, bombers, regen ? 40f : 0f),
                    new AirPoolStock(fighters, fighters, regen ? 25f : 0f), true)));
        }
        JSONObject defence = f.optJSONObject("defenderAirPool");
        if (defence != null) {
            boolean regen = defence.optBoolean("regen", true);
            int bombers = defence.optInt("bombers", 0);
            int fighters = defence.optInt("fighters", 0);
            profile = profile.withDeclaredAirPool(profile.declaredAirPool().with(Faction.DEFENDER, AirPoolAllowance.of(
                    new AirPoolStock(bombers, bombers, regen ? 40f : 0f),
                    new AirPoolStock(fighters, fighters, regen ? 25f : 0f), true)));
            profile = profile.withDeclaredSupportKit(profile.declaredSupportKit().with(Faction.DEFENDER,
                    new SquadSupportKit(0, defence.optInt("observerEvery", 3))));
        }
        return profile;
    }

    /** How many of the fort's own wall cells (gates excluded) are no longer standing. */
    private static int openedWallCells(BattleSimulation sim, ConquestTrialGround.Ground ground) {
        FortPlan plan = ground.fort().plan();
        int open = 0;
        for (int x = 0; x < plan.width(); x++) {
            for (int y = 0; y < plan.height(); y++) {
                if (!plan.isWall(x, y)) continue;
                int[] cell = ground.fort().placement().cell(x, y);
                if (sim.getGrid().isWalkable(cell[0], cell[1])) open++;
            }
        }
        return open;
    }

    /** Company lifecycle events, counted from the notes the muster writes into its plan summaries. */
    private static int[] companyLife(String trace) {
        String[] needles = {"company formed for fort", " commits (", " broken at strength", " stands down:", " dissolved:",
                " released squad ", " asks for a strike"};
        int[] counts = new int[needles.length];
        for (String line : trace.split("\n")) {
            if (!line.contains("\"perspective\":\"MARINE\"")) continue;
            for (int i = 0; i < needles.length; i++) {
                int from = 0;
                while ((from = line.indexOf(needles[i], from)) >= 0) {
                    counts[i]++;
                    from += needles[i].length();
                }
            }
        }
        return counts;
    }

    private static Outcome playFixture(JSONObject f, int maxTicks, Path visualRoot) throws Exception {
        String id = f.getString("id");
        long seed = f.optLong("seed", 41L);
        Placement placement = Placement.valueOf(f.optString("placement", Placement.ON_PATH.name()));
        ConquestTrialGround.Ground ground = ConquestTrialGround.generate(seed, placement,
                f.optInt("offset", ConquestTrialGround.DEFAULT_OFFSET),
                f.optInt("objectiveGarrison", 8), f.optInt("keepGarrison", 12), f.optBoolean("airDefence", false));
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
                OperationTier.ESTABLISHED, risk, profileOf(f), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                GroundRosterRegistry.resolve(""), roster);
        try (BattleSimulation sim = build.sim();
             CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(visualRoot, id, sim)) {
            FortOutcomes forts = new FortOutcomes();
            FortDefenderPositionProbe positionUse = new FortDefenderPositionProbe();
            FireSupportProbe fireSupport = new FireSupportProbe();
            ContestedAirProbe contested = new ContestedAirProbe(fortCentre[0], fortCentre[1], FORT_REACH);
            int wallOpenAtStart = openedWallCells(sim, ground);
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
                fireSupport.sample(sim);
                contested.sample(sim);
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
            int wallOpenAtEnd = openedWallCells(sim, ground);
            int[] life = companyLife(trace);
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
                    + "- fort anti-air aspect: " + f.optBoolean("airDefence", false) + "; strikes refused as too defended: "
                    + (sim.airPool() == null ? 0 : sim.airPool().director(Faction.MARINE).refusals()) + "\n"
                    + "- mission assault style: " + profileOf(f).declaredAssault() + "; "
                    + "declared kit/mechs/air: "
                    + f.optString("supportKit", "-") + " / " + f.optInt("mechDrops", 0) + " / "
                    + f.optString("airPool", "-") + "\n"
                    + String.format(Locale.ROOT, "- fort wall cells opened: %d (of which %d at the start)%n",
                            wallOpenAtEnd, wallOpenAtStart)
                    + String.format(Locale.ROOT, "- companies (marine trace notes): formed %d, committed %d, broken %d, "
                            + "stood down %d, dissolved %d, recruits released %d, strikes asked %d%n",
                            life[0], life[1], life[2], life[3], life[4], life[5], life[6])
                    + forts.describe(sim) + fireSupport.describe(sim) + contested.describe(sim) + ConquestCommandBalanceTest.fortOrders(trace)
                    + positionUse.describe();
            JSONObject json = new JSONObject().put("fixture", f).put("winner", winner).put("ticks", ticks)
                    .put("objectiveTakenTick", objectiveTaken).put("keepTakenTick", keepTaken)
                    .put("firstMarineAtFortTick", firstAtFort).put("peakMarinesAtFort", peakAtFort)
                    .put("marineSamplesAtFort", marineSamplesAtFort).put("peakMarines", peakMarines)
                    .put("wallCellsOpened", wallOpenAtEnd - wallOpenAtStart)
                    .put("companiesFormed", life[0]).put("companiesCommitted", life[1]).put("companiesBroken", life[2])
                    .put("companiesStoodDown", life[3]).put("strikesAsked", life[6])
                    .put("marinesEnd", marinesEnd).put("defendersStart", defendersAtStart).put("defendersEnd", defendersEnd)
                    .put("tubeSetDowns", sim.fieldMortars().setUps()).put("tubeRounds", fireSupport.marineRounds()[0])
                    .put("tubeMissionRounds", fireSupport.marineRounds()[1])
                    .put("nestsKilled", fireSupport.nestKills()[0]).put("nestsKilledByMortar", fireSupport.nestKills()[1])
                    .put("nestsKilledByAir", fireSupport.nestKills()[2])
                    .put("airPoolByMission", FireSupportProbe.poolJson(sim))
                    .put("contestedAir", contested.json(sim))
                    .put("sectionsOpened", sim.fieldMortars().sections().opened())
                    .put("sectionsExpired", sim.fieldMortars().sections().expired())
                    .put("sectionsAbandoned", sim.fieldMortars().sections().abandoned())
                    .put("sectionsUnderFire", sim.fieldMortars().sections().underFire())
                    .put("sectionSecondsHeld", Math.round(sim.fieldMortars().sections().secondsHeld()))
                    .put("sectionRounds", sim.fieldMortars().sections().roundsFired());
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
