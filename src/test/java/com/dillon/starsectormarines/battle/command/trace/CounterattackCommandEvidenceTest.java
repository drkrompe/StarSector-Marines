package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in neutral observation of Counterattack's production factory, never a second scene builder. */
@Tag("counterattack-command-evidence")
class CounterattackCommandEvidenceTest {
    /** Production campaign tags, not generated per-sortie squads: two boats carry one twelve-person squad. */
    @Test void pairedCampaignBoatsJoinOneSquadAndReleaseFormUp() throws Exception {
        Path output = Path.of(System.getProperty("counterattack.command.evidence.outputDir",
                "build/reports/commander/counterattack"));
        Files.createDirectories(output);
        JSONArray results = new JSONArray();
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            MarineRoster roster = new MarineRoster();
            roster.ensureActiveSoldiers(12);
            CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 12);
            List<ShuttleAssignment> manifest = List.of(
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6));
            try (BattleSimulation sim = BattleSetup.createOpeningOperation(8101, manifest, 1,
                    kind, TargetProfile.NEUTRAL)) {
                deployment.applyTo(sim, 1);
                float forwardPadX = 0;
                Set<String> playerPads = new HashSet<>();
                for (long air : sim.getAirEntityIds()) {
                    if (sim.world().airFaction(air) != Faction.MARINE) continue;
                    var mission = sim.world().mission(air);
                    forwardPadX = Math.max(forwardPadX, mission.lzX);
                    playerPads.add(mission.lzX + "," + mission.lzY);
                    assertEquals(1, mission.totalCycles, "No later drop is scheduled");
                }
                assertEquals(2, playerPads.size(), "The regression needs two distinct transport pads");
                int assembledTick = -1, advancedTick = -1;
                Squad company = null;
                while (!sim.isComplete() && sim.getSimTickIndex() < 1800) {
                    sim.advance(BattleSimulation.TICK_DT);
                    List<Squad> squads = sim.getSquads().stream()
                            .filter(squad -> squad.faction == Faction.MARINE).toList();
                    if (squads.stream().mapToInt(squad -> squad.originalSize).sum() < 12) continue;
                    assertEquals(1, squads.size(), kind + ": all twelve landed but split into incomplete squads");
                    company = squads.get(0);
                    assertEquals(12, company.expectedSize);
                    assertEquals(12, company.originalSize);
                    assertNotNull(company.campaignSquadId);
                    assertFalse(SquadFormUpSystem.formingUp(company));
                    assertNotNull(company.assignmentForExecution(), "The joint advance must be executable");
                    if (assembledTick < 0) {
                        assembledTick = sim.getSimTickIndex();
                        assertTrue(company.formUpElapsed < SquadFormUpSystem.FORM_UP_TIMEOUT,
                                "Assembly must release on arrival, not after the timeout");
                    }
                    if (company.centroidX > forwardPadX + 6) {
                        advancedTick = sim.getSimTickIndex();
                        break;
                    }
                }
                assertTrue(assembledTick >= 0, kind + ": the committed squad never assembled");
                assertTrue(advancedTick >= 0, kind + ": the assembled squad never left the landing apron");
                results.put(new JSONObject().put("mission", kind.name())
                        .put("companySquads", 1).put("delivered", company.originalSize)
                        .put("expected", company.expectedSize).put("assembledTick", assembledTick)
                        .put("advancedTick", advancedTick).put("centroidX", company.centroidX)
                        .put("forwardPadX", forwardPadX));
            }
        }
        Files.writeString(output.resolve("paired-campaign-arrival.json"), results.toString(2));
    }

    @Test void reportsDepotAdvanceAndDefense() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        Path output = Path.of(System.getProperty("counterattack.command.evidence.outputDir", "build/reports/commander/counterattack"));
        Files.createDirectories(output);
        String custom = System.getProperty("counterattack.command.evidence.fixture.path", "");
        JSONArray fixtures = custom.isBlank() ? new JSONArray()
                : new JSONArray(Files.readString(Path.of(custom)));
        if (custom.isBlank()) {
            for (int seed : new int[]{8101, 8102, 8103}) {
                for (int delay : new int[]{0, 30, 600}) fixtures.put(new JSONObject()
                        .put("id", seed + (delay == 0 ? "-counterattack" : delay == 30 ? "-late" : "-no-player"))
                        .put("seed", seed).put("surface", "VERDANT").put("playerType", "AEROSHUTTLE")
                        .put("playerBoatId", "counterattack-boat").put("playerPlating", BoatFitting.STANDARD_PLATING.id())
                        .put("playerDrive", BoatFitting.STANDARD_DRIVE.id())
                        .put("playerCycles", 2).put("playerSeats", 6).put("playerDelay", delay));
                fixtures.put(new JSONObject().put("id", seed + "-paired").put("seed", seed)
                        .put("surface", "VERDANT").put("playerType", "AEROSHUTTLE")
                        .put("playerCycles", 1).put("playerBoats", 2).put("playerSeats", 6).put("playerDelay", 0));
            }
        }
        int ticks = Integer.getInteger("counterattack.command.evidence.maxTicks", 9000);
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
                else assertEquals(first, result, "Counterattack measurements and trace must replay exactly");
            }
            Files.writeString(report.resolve("summary.json"), first.summary());
            Files.writeString(report.resolve("trace.jsonl"), first.trace());
            Files.writeString(report.resolve("fixture.json"), fixture.toString(2));
            summaries.put(new JSONObject(first.summary()).put("fixture", fixture.getString("id")));
        }
        Files.writeString(output.resolve("summary.json"), summaries.toString(2));
        System.out.println("[counterattack-evidence] " + output.resolve("summary.json"));
    }

    private record Result(String summary, String trace) {}

    private Result run(JSONObject fixture, int maxTicks, Path output, boolean capture) throws Exception {
        long seed = fixture.getLong("seed");
        FittedBoat boat = new FittedBoat(ShuttleType.valueOf(fixture.getString("playerType")),
                BoatFitting.requireById(fixture.optString("playerPlating", BoatFitting.STANDARD_PLATING.id())),
                BoatFitting.requireById(fixture.optString("playerDrive", BoatFitting.STANDARD_DRIVE.id())),
                fixture.optString("playerBoatId", "counterattack-boat"));
        var player = new ShuttleAssignment(boat, fixture.getInt("playerCycles"), fixture.getInt("playerSeats"));
        List<ShuttleAssignment> manifest = new ArrayList<>();
        manifest.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
        manifest.add(player);
        for (int i = 1; i < fixture.optInt("playerBoats", 1); i++) {
            var extra = new FittedBoat(boat.pattern(), boat.plating(), boat.drive(), boat.boatId() + "-" + i);
            manifest.add(new ShuttleAssignment(extra, player.cycles, player.seatsPerSortie));
        }
        SurfacePalette surface = SurfacePalette.valueOf(fixture.optString("surface", "ROCK"));
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), surface, SettlementLink.ROAD);
        try (BattleSimulation sim = BattleSetup.createOpeningOperation(seed, manifest, 1,
                OpeningOperationKind.COUNTERATTACK, profile)) {
            int playerIndex = 1;
            for (long air : sim.getAirEntityIds()) {
                if (sim.world().airFaction(air) == Faction.MARINE) {
                    assertSame(manifest.get(playerIndex++).airframe, sim.world().airframe(air), "Counterattack must fly each committed player boat");
                    sim.world().mission(air).pendingDelay += (float) fixture.optDouble("playerDelay", 0);
                }
            }
            JSONArray samples = new JSONArray();
            JSONArray changes = new JSONArray();
            Map<Integer, String> lastOrders = new HashMap<>();
            int firstLanding = -1, firstShot = -1, firstAllyShot = -1, secondLanding = -1;
            Set<Long> landed = new HashSet<>();
            int gateTick = -1, yardTick = -1, depotTick = -1;
            BattleReviewFrameRenderer renderer = capture ? new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080) : null;
            sim.setCommandTraceEnabled(true, "COUNTERATTACK");
            try (CommanderEvidenceCapture visuals = CommanderEvidenceCapture.open(capture ? output : null, "counterattack", sim,
                    (battle, caption, marks) -> {
                        ReviewAnnotations.Builder extra = ReviewAnnotations.builder();
                        for (long air : battle.getAirEntityIds()) {
                            var landing = battle.world().mission(air);
                            extra.label(landing.lzX, landing.lzY,
                                    battle.world().airFaction(air) == Faction.ALLY ? "EMPLOYER LZ" : "PLAYER LZ",
                                    ReviewStyle.LANDING);
                        }
                        extra.label(73, 46, "FREIGHT GATE", ReviewStyle.OBJECTIVE);
                        extra.label(95, 46, "LOADING YARD", ReviewStyle.OBJECTIVE);
                        extra.label(125, 49, "DISPATCH WAREHOUSE", ReviewStyle.OBJECTIVE);
                        extra.label(45, 47, "APPROACH", ReviewStyle.NOTE);
                        return renderer.render(battle, caption + " / ALLY " + count(battle, Faction.ALLY), extra.build());
                    })) {
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    int tick = sim.getSimTickIndex();
                    if (firstLanding < 0 && count(sim, Faction.MARINE) > 0) firstLanding = tick;
                    for (var shot : sim.getShotsThisFrame()) {
                        if (firstShot < 0 && shot.shooterFaction == Faction.MARINE) firstShot = tick;
                        if (firstAllyShot < 0 && shot.shooterFaction == Faction.ALLY) firstAllyShot = tick;
                    }
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long unit = sim.liveUnitAt(i);
                        if (sim.identity().faction(unit) == Faction.MARINE) landed.add(unit);
                    }
                    if (secondLanding < 0 && landed.size() > player.seatsPerSortie) secondLanding = tick;
                    var marine = ((OpeningOperationCommand) sim.getCommander(Faction.MARINE)).operationPicture();
                    if (gateTick < 0 && near(sim, 73, 46)) gateTick = tick;
                    if (yardTick < 0 && near(sim, 95, 46)) yardTick = tick;
                    if (depotTick < 0 && marine != null && near(sim, marine.placeCellX(), marine.placeCellY())) depotTick = tick;
                    var defender = ((OpeningOperationCommand) sim.getCommander(Faction.DEFENDER)).operationPicture();
                    if (defender != null) for (var intent : defender.squadIntents()) {
                        String order = intent.reason() + ":" + intent.targetCellX() + "," + intent.targetCellY();
                        if (!order.equals(lastOrders.put(intent.squadId(), order)))
                            changes.put(new JSONObject().put("tick", tick).put("squad", intent.squadId())
                                    .put("reason", intent.reason().name()).put("x", intent.targetCellX()).put("y", intent.targetCellY()));
                    }
                    if (tick == 1 || tick % 150 == 0 || sim.isComplete())
                        samples.put(new JSONObject().put("tick", tick).put("allies", count(sim, Faction.ALLY))
                                .put("player", count(sim, Faction.MARINE)).put("raiders", count(sim, Faction.DEFENDER)));
                    visuals.afterAdvance();
                }
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);

            JSONArray finalSquads = new JSONArray();
            for (var squad : sim.getSquads()) finalSquads.put(new JSONObject()
                    .put("id", squad.id).put("faction", squad.faction.name())
                    .put("goal", squad.currentGoal == null ? "none" : squad.currentGoal.name())
                    .put("action", squad.currentPlan == null || squad.currentPlan.currentStep() == null
                            ? "none" : squad.currentPlan.currentStep().action.name()));
            return new Result(new JSONObject().put("finalSquads", finalSquads).put("seed", seed)
                    .put("profile", surface.name() + " / controlled factory loadouts")
                    .put("playerBoatId", boat.boatId()).put("playerType", boat.pattern().name())
                    .put("playerPlating", boat.plating().id()).put("playerDrive", boat.drive().id())
                    .put("storyBeats", StoryBeatEvidence.snapshot(sim))
                    .put("termination", sim.isComplete() ? "COMPLETE" : "TIMEOUT")
                    .put("winner", sim.getWinner() == null ? JSONObject.NULL : sim.getWinner().name())
                    .put("ticks", sim.getSimTickIndex()).put("firstPlayerLandingTick", firstLanding)
                    .put("secondPlayerLandingTick", secondLanding).put("playerDelivered", landed.size())
                    .put("playerCycles", player.cycles).put("playerSeats", player.seatsPerSortie).put("playerBoats", manifest.size() - 1)
                    .put("firstPlayerShotTick", firstShot).put("firstAllyShotTick", firstAllyShot)
                    .put("gateTick", gateTick).put("yardTick", yardTick).put("depotTick", depotTick)
                    .put("playerRemaining", count(sim, Faction.MARINE)).put("alliesRemaining", count(sim, Faction.ALLY))
                    .put("raidersRemaining", count(sim, Faction.DEFENDER))
                    .put("defenderOrders", changes).put("samples", samples).toString(2), sim.getCommandTraceJsonLines());
        }
    }

    private static boolean near(BattleSimulation sim, int x, int y) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE) continue;
            float dx = sim.world().x(id) - x, dy = sim.world().y(id) - y;
            if (dx * dx + dy * dy <= 36) return true;
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
