package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.battle.command.JunctionCommand;
import com.dillon.starsectormarines.battle.command.ResourceType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** Production map and simulation evidence, opt-in and excluded from normal tests. */
@Tag("junction-command-evidence")
class JunctionCommandEvidenceTest {
    @Test void reportsTerritorialAdvanceAndHold() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        Path output = Path.of(System.getProperty("junction.command.evidence.outputDir", "build/reports/commander/junction"));
        Files.createDirectories(output);
        String custom = System.getProperty("junction.command.evidence.fixture.path", "");
        JSONArray fixtures = custom.isBlank() ? new JSONArray() : new JSONArray(Files.readString(Path.of(custom)));
        if (custom.isBlank()) for (int seed : new int[]{9101, 9102, 9103}) {
            for (int boats : new int[]{2, 4}) fixtures.put(new JSONObject().put("id", seed + "-" + boats + "-squads")
                    .put("seed", seed).put("playerType", "VALKYRIE").put("playerBoats", boats).put("playerSeats", 12).put("playerCycles", 1));
            fixtures.put(new JSONObject().put("id", seed + "-understrength").put("seed", seed)
                    .put("playerType", "AEROSHUTTLE").put("playerBoats", 2).put("playerSeats", 4).put("playerCycles", 1));
        }
        if (custom.isBlank()) {
            fixtures.put(new JSONObject().put("id", "9101-unguarded").put("seed", 9101).put("playerType", "VALKYRIE")
                    .put("playerBoats", 2).put("playerSeats", 12).put("playerCycles", 1).put("holdAtLanding", true));
            fixtures.put(new JSONObject().put("id", "9101-single-ferry").put("seed", 9101).put("playerType", "AEROSHUTTLE")
                    .put("playerBoats", 1).put("playerSeats", 6).put("playerCycles", 4));
            fixtures.put(new JSONObject().put("id", "9101-no-player").put("seed", 9101).put("playerType", "VALKYRIE")
                    .put("playerBoats", 2).put("playerSeats", 12).put("playerCycles", 1).put("playerDelay", 600));
            fixtures.put(new JSONObject().put("id", "9101-refitted").put("seed", 9101).put("playerType", "VALKYRIE")
                    .put("playerBoats", 2).put("playerSeats", 12).put("playerCycles", 1)
                    .put("playerPlating", "reinforced_plating").put("playerDrive", "tuned_drive"));
        }
        int ticks = Integer.getInteger("junction.command.evidence.maxTicks", 12000);
        if (ticks < 1) throw new IllegalArgumentException("maxTicks must be positive");
        JSONArray summaries = new JSONArray();
        for (int index = 0; index < fixtures.length(); index++) {
            JSONObject fixture = fixtures.getJSONObject(index);
            Path report = output.resolve(fixture.getString("id"));
            Files.createDirectories(report);
            Result first = null;
            for (int replica = 0; replica < EvidenceFanOut.repeat(); replica++) {
                Result result = run(fixture, ticks, report, replica == 0);
                if (first == null) first = result;
                else assertEquals(first, result, "Junction measurements and trace must replay exactly");
            }
            Files.writeString(report.resolve("summary.json"), first.summary());
            Files.writeString(report.resolve("trace.jsonl"), first.trace());
            Files.writeString(report.resolve("fixture.json"), fixture.toString(2));
            summaries.put(new JSONObject(first.summary()).put("fixture", fixture.getString("id")));
        }
        Files.writeString(output.resolve("summary.json"), summaries.toString(2));
        System.out.println("[junction-evidence] " + output.resolve("summary.json"));
    }
    private record Result(String summary, String trace) {}
    private Result run(JSONObject fixture, int maxTicks, Path output, boolean capture) throws Exception {
        long seed = fixture.getLong("seed");
        List<ShuttleAssignment> manifest = new ArrayList<>();
        for (int i = 0; i < fixture.getInt("playerBoats"); i++) {
            var boat = new FittedBoat(ShuttleType.valueOf(fixture.getString("playerType")),
                    BoatFitting.requireById(fixture.optString("playerPlating", BoatFitting.STANDARD_PLATING.id())),
                    BoatFitting.requireById(fixture.optString("playerDrive", BoatFitting.STANDARD_DRIVE.id())), "junction-boat-" + i);
            manifest.add(new ShuttleAssignment(boat, fixture.getInt("playerCycles"), fixture.getInt("playerSeats")));
        }
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        try (BattleSimulation sim = BattleSetup.createJunction(seed, manifest, profile)) {
            int playerIndex = 0;
            JSONArray boats = new JSONArray();
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(manifest.get(playerIndex++).airframe, sim.world().airframe(air), "Junction must fly each committed fitted boat");
                var boat = (FittedBoat) sim.world().airframe(air);
                boats.put(new JSONObject().put("id", boat.boatId()).put("pattern", boat.pattern().name())
                        .put("plating", boat.plating().id()).put("drive", boat.drive().id()));
                sim.world().mission(air).pendingDelay += (float) fixture.optDouble("playerDelay", 0);
            }
            assertEquals(manifest.size(), playerIndex);
            assertEquals(20, count(sim, Faction.DEFENDER));
            int reserves = 0;
            for (var squad : sim.getSquads()) if (squad.faction == Faction.DEFENDER && squad.assignedNode == null) {
                reserves++;
                assertEquals(JunctionCommand.issuer(Faction.DEFENDER), sim.getSquadCommandDirective(squad.id).issuer());
                assertEquals(4, sim.squadMemberCount(squad.id));
            }
            assertEquals(1, reserves);
            var sites = new ArrayList<>(sim.getCompoundService().getRecords());
            assertEquals(4, sites.size());
            Set<Integer> rooms = new HashSet<>();
            for (int i = 1; i < sites.size(); i++) {
                int room = sim.getCompoundService().captureZoneId(sites.get(i), sim);
                assertTrue(room >= 0 && rooms.add(room), "Each hostile site must have a distinct resolved capture room");
            }
            JSONArray transitions = new JSONArray(), samples = new JSONArray();
            List<CompoundState> previous = sites.stream().map(r -> r.state).toList();
            Set<Long> landed = new HashSet<>();
            int firstLanding = -1, reserveTick = -1, holdArrivalTick = -1, holdOccupancyTicks = 0;
            int peakPlayerSquads = 0;
            BattleReviewFrameRenderer renderer = capture ? new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080) : null;
            sim.setCommandTraceEnabled(true, "JUNCTION");
            try (CommanderEvidenceCapture visuals = CommanderEvidenceCapture.open(capture ? output : null, "junction", sim,
                    (battle, caption, marks) -> {
                        ReviewAnnotations.Builder extra = ReviewAnnotations.builder();
                        for (int i = 0; i < sites.size(); i++) {
                            var r = sites.get(i);
                            extra.label(r.node.anchorX, r.node.anchorY, JunctionCompound.SITE_NAMES.get(i).toUpperCase() + " / " + r.state,
                                    r.state == CompoundState.MARINE_HELD ? ReviewStyle.LANDING : ReviewStyle.OBJECTIVE);
                        }
                        extra.label(174, 8, "TO RILL", ReviewStyle.NOTE);
                        extra.label(176, 88, "TO RELAY NINE", ReviewStyle.NOTE);
                        return renderer.render(battle, caption + " / PLAYER " + count(battle, Faction.MARINE)
                                + " / MILITIA " + count(battle, Faction.ALLY) + " / RAIDERS " + count(battle, Faction.DEFENDER), extra.build());
                    })) {
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    int tick = sim.getSimTickIndex();
                    if (firstLanding < 0 && count(sim, Faction.MARINE) > 0) firstLanding = tick;
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long unit = sim.liveUnitAt(i);
                        if (sim.identity().faction(unit) == Faction.MARINE) landed.add(unit);
                    }
                    peakPlayerSquads = Math.max(peakPlayerSquads, (int) sim.getSquads().stream()
                            .filter(s -> s.faction == Faction.MARINE && sim.squadMemberCount(s.id) > 0).count());
                    for (int i = 0; i < sites.size(); i++) if (previous.get(i) != sites.get(i).state)
                        transitions.put(new JSONObject().put("tick", tick).put("site", JunctionCompound.SITE_NAMES.get(i))
                                .put("from", previous.get(i).name()).put("to", sites.get(i).state.name()));
                    previous = sites.stream().map(r -> r.state).toList();
                    if (reserveTick < 0 && sim.getStoryBeats().stream().anyMatch(e -> e.id().endsWith("reserve_committed"))) reserveTick = tick;
                    var militia = ((JunctionCommand) sim.getCommander(Faction.ALLY)).picture();
                    if (militia != null && militia.targetSite() > 0 && inside(sim, Faction.ALLY, sites.get(militia.targetSite()).node)) {
                        if (holdArrivalTick < 0) holdArrivalTick = tick;
                        holdOccupancyTicks++;
                    }
                    if (fixture.optBoolean("holdAtLanding", false)) for (var squad : sim.getSquads())
                        if (squad.faction == Faction.ALLY) sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad.id, 21, 56, 3),
                                CommandAuthority.PLAYER_INTERVENTION, "evidence-hold-control", "hold militia at landing to expose unattended capture");
                    if (tick == 1 || tick % 300 == 0 || sim.isComplete()) {
                        JSONArray reserveMembers = new JSONArray();
                        for (var squad : sim.getSquads()) if (squad.faction == Faction.DEFENDER && squad.assignedNode == null)
                            for (int m = 0; m < sim.squadMemberCount(squad.id); m++) {
                                long unit = sim.squadMemberAt(squad.id, m);
                                reserveMembers.put(new JSONObject().put("x", sim.world().x(unit)).put("y", sim.world().y(unit)));
                            }
                        samples.put(new JSONObject().put("tick", tick).put("player", count(sim, Faction.MARINE))
                                .put("allies", count(sim, Faction.ALLY)).put("raiders", count(sim, Faction.DEFENDER)).put("reserve", reserveMembers));
                    }
                    visuals.afterAdvance();
                }
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            for (Faction faction : Faction.values()) for (ResourceType type : ResourceType.values())
                assertEquals(0f, sim.getBattleResources().getBalance(faction, type), "Junction has no territorial production");
            JSONArray finalSquads = new JSONArray();
            for (var squad : sim.getSquads()) finalSquads.put(new JSONObject().put("id", squad.id).put("faction", squad.faction.name())
                    .put("alive", sim.squadMemberCount(squad.id)).put("goal", squad.currentGoal == null ? "none" : squad.currentGoal.name())
                    .put("action", squad.currentPlan == null || squad.currentPlan.currentStep() == null ? "none" : squad.currentPlan.currentStep().action.name()));
            return new Result(new JSONObject().put("seed", seed).put("boats", boats)
                    .put("termination", sim.isComplete() ? "COMPLETE" : "TIMEOUT").put("winner", sim.getWinner() == null ? JSONObject.NULL : sim.getWinner().name())
                    .put("ticks", sim.getSimTickIndex()).put("firstPlayerLandingTick", firstLanding).put("playerDelivered", landed.size())
                    .put("peakPlayerSquads", peakPlayerSquads).put("reserveCommittedTick", reserveTick).put("militiaFootprintArrivalTick", holdArrivalTick)
                    .put("militiaFootprintOccupancyTicks", holdOccupancyTicks).put("playerRemaining", count(sim, Faction.MARINE))
                    .put("alliesRemaining", count(sim, Faction.ALLY)).put("raidersRemaining", count(sim, Faction.DEFENDER))
                    .put("storyBeats", StoryBeatEvidence.snapshot(sim)).put("transitions", transitions).put("samples", samples).put("finalSquads", finalSquads).toString(2), sim.getCommandTraceJsonLines());
        }
    }
    private static boolean inside(BattleSimulation sim, Faction faction, TacticalNode node) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == faction && sim.world().cellX(id) > node.left && sim.world().cellX(id) < node.right
                    && sim.world().cellY(id) > node.top && sim.world().cellY(id) < node.bottom) return true;
        }
        return false;
    }
    private static int count(BattleSimulation sim, Faction faction) {
        int count = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == faction && sim.identity().type(id).combatant) count++;
        }
        return count;
    }
}
