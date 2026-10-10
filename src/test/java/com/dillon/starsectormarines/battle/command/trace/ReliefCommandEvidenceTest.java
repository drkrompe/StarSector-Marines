package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Opt-in neutral observation of Relief's production factory, never a second scene builder. */
@Tag("relief-command-evidence")
class ReliefCommandEvidenceTest {
    @Test void reportsArrivalPressureAndLocalSurvival() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        Path output = Path.of(System.getProperty("relief.command.evidence.outputDir", "build/reports/commander/relief"));
        Files.createDirectories(output);
        String custom = System.getProperty("relief.command.evidence.fixture.path", "");
        JSONArray fixtures = custom.isBlank() ? new JSONArray()
                : new JSONArray(Files.readString(Path.of(custom)));
        if (custom.isBlank()) {
            for (int seed : new int[]{8101, 8102, 8103}) {
                for (int delay : new int[]{0, 30, 600}) fixtures.put(new JSONObject()
                        .put("id", seed + (delay == 0 ? "-relief" : delay == 30 ? "-late" : "-no-player"))
                        .put("seed", seed).put("surface", "VERDANT").put("playerType", "AEROSHUTTLE")
                        .put("playerBoatId", "relief-boat").put("playerPlating", BoatFitting.STANDARD_PLATING.id())
                        .put("playerDrive", BoatFitting.STANDARD_DRIVE.id())
                        .put("playerCycles", 1).put("playerSeats", 6).put("playerDelay", delay));
            }
        }
        int ticks = Integer.getInteger("relief.command.evidence.maxTicks", 6000);
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
                else assertEquals(first, result, "Relief measurements and trace must replay exactly");
            }
            Files.writeString(report.resolve("summary.json"), first.summary());
            Files.writeString(report.resolve("trace.jsonl"), first.trace());
            Files.writeString(report.resolve("fixture.json"), fixture.toString(2));
            summaries.put(new JSONObject(first.summary()).put("fixture", fixture.getString("id")));
        }
        Files.writeString(output.resolve("summary.json"), summaries.toString(2));
        System.out.println("[relief-evidence] " + output.resolve("summary.json"));
    }

    private record Result(String summary, String trace) {}

    private Result run(JSONObject fixture, int maxTicks, Path output, boolean capture) throws Exception {
        long seed = fixture.getLong("seed");
        FittedBoat boat = new FittedBoat(ShuttleType.valueOf(fixture.getString("playerType")),
                BoatFitting.requireById(fixture.optString("playerPlating", BoatFitting.STANDARD_PLATING.id())),
                BoatFitting.requireById(fixture.optString("playerDrive", BoatFitting.STANDARD_DRIVE.id())),
                fixture.optString("playerBoatId", "relief-boat"));
        var player = new ShuttleAssignment(boat, fixture.getInt("playerCycles"), fixture.getInt("playerSeats"));
        List<ShuttleAssignment> manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1), player);
        SurfacePalette surface = SurfacePalette.valueOf(fixture.optString("surface", "ROCK"));
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), surface, SettlementLink.ROAD);
        try (BattleSimulation sim = BattleSetup.createOpeningOperation(seed, manifest, 1,
                OpeningOperationKind.RELIEF, profile)) {
            for (long air : sim.getAirEntityIds()) {
                if (sim.world().airFaction(air) == Faction.MARINE) {
                    assertSame(boat, sim.world().airframe(air), "Relief must fly the committed player boat");
                    sim.world().mission(air).pendingDelay += (float) fixture.optDouble("playerDelay", 0);
                }
            }
            List<Long> originalMilitia = new ArrayList<>();
            JSONArray initialSquads = new JSONArray();
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long id = sim.liveUnitAt(i);
                if (sim.identity().faction(id) == Faction.ALLY && sim.identity().type(id).combatant) originalMilitia.add(id);
            }
            for (var squad : sim.getSquads().stream().sorted(Comparator.comparingInt(s -> s.id)).toList()) {
                var directive = sim.getSquadCommandDirective(squad.id);
                initialSquads.put(new JSONObject().put("id", squad.id).put("side", squad.faction.name())
                        .put("order", directive == null ? "none" : directive.authority().name()));
            }
            JSONArray landings = new JSONArray();
            for (long air : sim.getAirEntityIds()) {
                var mission = sim.world().mission(air);
                landings.put(new JSONObject().put("side", sim.world().airFaction(air).name())
                        .put("x", mission.lzX).put("y", mission.lzY).put("delay", mission.pendingDelay));
            }
            BattleReviewFrameRenderer renderer = capture ? new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080) : null;
            sim.setCommandTraceEnabled(true, "RELIEF");
            JSONArray samples = new JSONArray();
            int firstLocalShot = -1;
            int firstShot = -1, firstLanding = -1, firstPlayerShot = -1, firstLocalLoss = -1, localAtLanding = -1;
            try (CommanderEvidenceCapture visuals = CommanderEvidenceCapture.open(capture ? output : null, "relief", sim,
                    (battle, caption, marks) -> {
                        ReviewAnnotations.Builder extra = ReviewAnnotations.builder();
                        for (long air : battle.getAirEntityIds()) {
                            var landing = battle.world().mission(air);
                            extra.label(landing.lzX, landing.lzY,
                                    battle.world().airFaction(air) == Faction.ALLY ? "EMPLOYER LZ" : "PLAYER LZ",
                                    ReviewStyle.LANDING);
                        }
                        extra.label(42, 45, "RELAY OFFICE", ReviewStyle.NOTE);
                        extra.label(67, 24, "INSPECTION", ReviewStyle.NOTE);
                        extra.label(103, 30, "FREIGHT ROAD", ReviewStyle.NOTE);
                        extra.label(82, 58, "SERVICE ROAD", ReviewStyle.NOTE);
                        boolean postMarked = false;
                        for (var squad : battle.getSquads()) {
                            if (!postMarked && squad.faction == Faction.ALLY && squad.assignedNode != null) {
                                postMarked = true;
                                extra.label(squad.assignedNode.anchorX, squad.assignedNode.anchorY, "CHECKPOINT / MILITIA", ReviewStyle.OBJECTIVE);
                            }
                        }
                        return renderer.render(battle, caption + " / LOCAL " + living(battle, originalMilitia) + " / RESERVE " + battle.pendingGroundArrivals(Faction.DEFENDER), extra.build());
                    })) {
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    int tick = sim.getSimTickIndex();
                    int local = living(sim, originalMilitia);
                    if (firstLocalLoss < 0 && local < originalMilitia.size()) firstLocalLoss = tick;
                    if (firstLanding < 0 && count(sim, Faction.MARINE) > 0) { firstLanding = tick; localAtLanding = local; }
                    for (var shot : sim.getShotsThisFrame()) {
                        if (firstLocalShot < 0 && originalMilitia.contains(shot.shooterId)) firstLocalShot = tick;
                        if (firstShot < 0 && (shot.shooterFaction == Faction.DEFENDER || shot.shooterFaction == Faction.ALLY)) firstShot = tick;
                        if (firstPlayerShot < 0 && shot.shooterFaction == Faction.MARINE) firstPlayerShot = tick;
                    }
                    if (tick == 1 || tick % 150 == 0 || sim.isComplete()) {
                        samples.put(new JSONObject().put("tick", tick).put("locals", local)
                                .put("allies", count(sim, Faction.ALLY)).put("player", count(sim, Faction.MARINE))
                                .put("raiders", count(sim, Faction.DEFENDER))
                                .put("pendingRaiders", sim.pendingGroundArrivals(Faction.DEFENDER)));
                    }
                    visuals.afterAdvance();
                }
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);

            JSONArray arrivals = new JSONArray();
            for (var arrival : sim.getReliefAssault().arrivals()) arrivals.put(new JSONObject()
                    .put("road", arrival.road()).put("tick", arrival.tick()).put("members", arrival.members()));
            var picture = ((OpeningOperationCommand) sim.getCommander(Faction.MARINE)).operationPicture();
            return new Result(new JSONObject().put("seed", seed).put("profile", surface.name() + " / controlled construction, no campaign loadout")
                    .put("playerBoatId", boat.boatId()).put("playerType", boat.pattern().name())
                    .put("playerPlating", boat.plating().id()).put("playerDrive", boat.drive().id())
                    .put("termination", sim.isComplete() ? "COMPLETE" : "TIMEOUT")
                    .put("winner", sim.getWinner() == null ? JSONObject.NULL : sim.getWinner().name())
                    .put("ticks", sim.getSimTickIndex()).put("firstMilitiaOrRaiderShotTick", firstShot)
                    .put("firstOriginalMilitiaShotTick", firstLocalShot)
                    .put("firstPlayerLandingTick", firstLanding).put("firstPlayerShotTick", firstPlayerShot)
                    .put("firstLocalLossTick", firstLocalLoss).put("originalMilitiaAtPlayerLanding", localAtLanding)
                    .put("originalMilitiaRemaining", living(sim, originalMilitia))
                    .put("placeX", picture == null ? -1 : picture.placeCellX()).put("placeY", picture == null ? -1 : picture.placeCellY())
                    .put("arrivals", arrivals).put("pendingRaiders", sim.pendingGroundArrivals(Faction.DEFENDER))
                    .put("initialSquads", initialSquads).put("landings", landings).put("samples", samples).toString(2), sim.getCommandTraceJsonLines());
        }
    }

    private static int living(BattleSimulation sim, List<Long> ids) {
        int count = 0;
        for (long id : ids) if (sim.world().isAlive(id)) count++;
        return count;
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
