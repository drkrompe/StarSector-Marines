package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
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
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in neutral observation of Relief's production factory, never a second scene builder. */
@Tag("relief-command-evidence")
class ReliefCommandEvidenceTest {
    private static final String HOLD_ISSUER = "evidence-hold-control";

    @Test void reportsArrivalPressureAndLocalSurvival() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        Path output = Path.of(System.getProperty("relief.command.evidence.outputDir", "build/reports/commander/relief"));
        Files.createDirectories(output);
        String custom = System.getProperty("relief.command.evidence.fixture.path", "");
        JSONArray fixtures = custom.isBlank() ? new JSONArray()
                : new JSONArray(Files.readString(Path.of(custom)));
        if (custom.isBlank()) {
            // The story chapter starts ashore beside Wayline's truck. Each seed reads it against the same company
            // held at its start for thirty seconds (the militia should still have a line to relieve, and the delay
            // should cost it), the repeatable posting's three Hermes trips, and that lift arriving after the window.
            for (int seed : new int[]{8101, 8102, 8103}) {
                fixtures.put(new JSONObject().put("id", seed + "-relief").put("seed", seed).put("surface", "VERDANT")
                        .put("groundStart", true).put("starterCompany", true));
                fixtures.put(new JSONObject().put("id", seed + "-held").put("seed", seed).put("surface", "VERDANT")
                        .put("groundStart", true).put("starterCompany", true).put("holdAtStart", 30));
                for (int delay : new int[]{0, 600}) fixtures.put(new JSONObject()
                        .put("id", seed + (delay == 0 ? "-boat-lift" : "-no-player"))
                        .put("seed", seed).put("surface", "VERDANT").put("groundStart", false).put("playerType", "HERMES")
                        .put("playerBoatId", "relief-boat").put("playerPlating", BoatFitting.STANDARD_PLATING.id())
                        .put("playerDrive", BoatFitting.STANDARD_DRIVE.id())
                        .put("playerCycles", 3).put("playerSeats", 4).put("playerDelay", delay)
                        .put("starterCompany", true));
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
            JSONObject summary = new JSONObject(first.summary()).put("fixture", fixture.getString("id"));
            if (custom.isBlank() && summary.getBoolean("groundStart")) {
                // Mechanics of the start, not the battle's outcome.
                assertEquals(12, summary.getInt("namedMarinesAtStart"), "the whole named squad stands ashore at tick zero");
                assertEquals(1, summary.getInt("companySquadsAtStart"), "the twelve start as one squad");
                assertTrue(summary.getBoolean("companyLeaderAtStart"), "the squad starts with its leader");
                assertEquals(0, summary.getInt("companyAircraft"), "no company boat flies");
                assertEquals(1, summary.getInt("employerAircraft"), "only the employer's militia shuttle flies");
                assertEquals(1, summary.getInt("parkedHaulers"), "Wayline's truck is parked beside the company");
                assertTrue(summary.getBoolean("haulerIntact"), "the civilian hauler is never a target");
            }
            if (custom.isBlank() && summary.getBoolean("groundStart") && summary.getInt("holdAtStartTicks") == 0
                    && ticks >= 3000) {
                int atGate = summary.getInt("firstPlayerAtGateTick");
                int militiaShot = summary.getInt("firstOriginalMilitiaShotTick");
                assertTrue(militiaShot >= 0 && (atGate < 0 || militiaShot < atGate),
                        "the local militia must already be fighting before the company reaches the gate");
            }
            summaries.put(summary);
        }
        Files.writeString(output.resolve("summary.json"), summaries.toString(2));
        System.out.println("[relief-evidence] " + output.resolve("summary.json"));
    }

    private record Result(String summary, String trace) {}

    private Result run(JSONObject fixture, int maxTicks, Path output, boolean capture) throws Exception {
        long seed = fixture.getLong("seed");
        // Custom fixtures that name a player boat keep the lift shape they were written for.
        boolean groundStart = fixture.optBoolean("groundStart", !fixture.has("playerType"));
        FittedBoat boat = groundStart ? null : new FittedBoat(ShuttleType.valueOf(fixture.getString("playerType")),
                BoatFitting.requireById(fixture.optString("playerPlating", BoatFitting.STANDARD_PLATING.id())),
                BoatFitting.requireById(fixture.optString("playerDrive", BoatFitting.STANDARD_DRIVE.id())),
                fixture.optString("playerBoatId", "relief-boat"));
        var player = groundStart ? null
                : new ShuttleAssignment(boat, fixture.getInt("playerCycles"), fixture.getInt("playerSeats"));
        int companySize = groundStart ? fixture.optInt("companySize", 12) : player.embarkedPersonnel;
        List<ShuttleAssignment> manifest = groundStart
                ? List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1))
                : List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1), player);
        SurfacePalette surface = SurfacePalette.valueOf(fixture.optString("surface", "ROCK"));
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), surface, SettlementLink.ROAD);
        try (BattleSimulation sim = BattleSetup.createOpeningOperation(seed, manifest, 1,
                OpeningOperationKind.RELIEF, profile, false, groundStart)) {
            boolean starterCompany = fixture.optBoolean("starterCompany", groundStart);
            if (groundStart && !starterCompany)
                throw new IllegalArgumentException("A ground start fields named personnel only");
            if (starterCompany) {
                MarineRoster roster = new MarineRoster();
                roster.ensureActiveSoldiers(companySize);
                var selected = roster.squads().stream().filter(s -> !s.reserve())
                        .map(s -> s.id()).collect(Collectors.toSet());
                var deployment = CampaignMarineDeployment.freezeSelection(roster, selected,
                        groundStart ? companySize : CampaignMarineDeployment.requiredSeats(manifest, 1));
                BattleLaunchOverlay.capture(1, deployment, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                        List.of(), 0, null).applyTo(sim, CommandPowerResources.FREE);
            }
            int companyAircraft = 0, employerAircraft = 0;
            for (long air : sim.getAirEntityIds()) {
                if (sim.world().airFaction(air) == Faction.MARINE) {
                    companyAircraft++;
                    assertSame(boat, sim.world().airframe(air), "Relief must fly the committed player boat");
                    sim.world().mission(air).pendingDelay += (float) fixture.optDouble("playerDelay", 0);
                } else employerAircraft++;
            }
            int namedAtStart = 0;
            Set<Integer> squadsAtStart = new HashSet<>();
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long id = sim.liveUnitAt(i);
                if (sim.identity().faction(id) != Faction.MARINE || !sim.identity().type(id).combatant) continue;
                if (sim.getEntityWorld().getObject(id, sim.getBattleComponents().IDENTITY,
                        BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID) != null) namedAtStart++;
                squadsAtStart.add(sim.squad().squadId(id));
            }
            boolean leaderAtStart = !squadsAtStart.isEmpty() && squadsAtStart.stream()
                    .allMatch(id -> sim.resolveUnit(sim.getSquad(id).leaderId) != 0L);
            List<Long> haulers = new ArrayList<>();
            for (int i = 0; i < sim.convoy().vehicleCount(); i++) {
                long vehicle = sim.convoy().vehicleAt(i);
                if (sim.convoy().faction(vehicle) == Faction.CIVILIAN
                        && sim.convoy().vehicleType(vehicle) == VehicleType.CARGO_CARRIER) haulers.add(vehicle);
            }
            float haulerStructure = 0;
            for (long hauler : haulers) haulerStructure += sim.convoy().structure(hauler) + sim.convoy().armor(hauler);
            // Diagnostic player order: the company stands where it got off the truck, then the mission takes it back.
            int holdTicks = (int) Math.round(fixture.optDouble("holdAtStart", 0) * 30);
            List<Integer> heldSquads = new ArrayList<>();
            if (holdTicks > 0) for (int squad : squadsAtStart) {
                sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad,
                                (int) sim.companyGroundStartX(), (int) sim.companyGroundStartY(), 3),
                        CommandAuthority.PLAYER_INTERVENTION, HOLD_ISSUER, "hold the company at its start");
                heldSquads.add(squad);
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
            Set<Long> landedPlayer = new HashSet<>();
            JSONArray playerArrivals = new JSONArray();
            int firstEmployerAtGate = -1, firstPlayerAtGate = -1;
            int assembled = -1, firstRaiderAtGate = -1, firstPlayerLoss = -1;
            int firstLocalShot = -1;
            int firstShot = -1, firstLanding = -1, firstPlayerShot = -1, firstLocalLoss = -1, localAtLanding = -1;
            ContactMixEvidence mix = new ContactMixEvidence();
            try (CommanderEvidenceCapture visuals = CommanderEvidenceCapture.open(capture ? output : null, "relief", sim,
                    (battle, caption, marks) -> {
                        ReviewAnnotations.Builder extra = ReviewAnnotations.builder();
                        for (long air : battle.getAirEntityIds()) {
                            var landing = battle.world().mission(air);
                            extra.label(landing.lzX, landing.lzY,
                                    battle.world().airFaction(air) == Faction.ALLY ? "EMPLOYER LZ" : "PLAYER LZ",
                                    ReviewStyle.LANDING);
                        }
                        if (battle.companyStartsOnGround())
                            extra.label(battle.companyGroundStartX(), battle.companyGroundStartY() + 4,
                                    "WAYLINE TRUCK / COMPANY START", ReviewStyle.LANDING);
                        extra.label(74, 45, "RELAY OFFICE", ReviewStyle.NOTE);
                        extra.label(99, 24, "INSPECTION", ReviewStyle.NOTE);
                        extra.label(135, 30, "FREIGHT ROAD", ReviewStyle.NOTE);
                        extra.label(114, 58, "SERVICE ROAD", ReviewStyle.NOTE);

                        for (var squad : battle.getSquads()) {
                            if (squad.faction == Faction.ALLY && squad.assignedNode != null) {
                                extra.label(squad.assignedNode.anchorX, squad.assignedNode.anchorY, squad.assignedNode.anchorX > 90 ? "EXTERIOR SCREEN" : "GATE / MILITIA", ReviewStyle.OBJECTIVE);
                            }
                        }
                        return renderer.render(battle, caption + " / LOCAL " + living(battle, originalMilitia) + " / RESERVE " + battle.pendingGroundArrivals(Faction.DEFENDER), extra.build());
                    })) {
                while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                    sim.advance(BattleSimulation.TICK_DT);
                    mix.sample(sim);
                    int tick = sim.getSimTickIndex();
                    if (tick == holdTicks) for (int squad : heldSquads)
                        if (sim.getSquad(squad) != null) sim.handoffSquadCommand(squad, HOLD_ISSUER,
                                CommandAuthority.MISSION_COMMAND, OpeningOperationCommand.issuer(Faction.MARINE), null,
                                "evidence hold released to the mission");
                    int local = living(sim, originalMilitia);
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long unit = sim.liveUnitAt(i);
                        if (sim.identity().faction(unit) == Faction.MARINE
                                && sim.identity().type(unit).combatant && landedPlayer.add(unit))
                            playerArrivals.put(new JSONObject().put("tick", tick)
                                    .put("totalLanded", landedPlayer.size())
                                    .put("playerAlive", count(sim, Faction.MARINE))
                                    .put("alliesAlive", count(sim, Faction.ALLY)));
                        if (firstPlayerAtGate < 0 && sim.identity().faction(unit) == Faction.MARINE
                                && sim.identity().type(unit).combatant
                                && Math.hypot(sim.world().x(unit) - 83, sim.world().y(unit) - 30) <= 8)
                            firstPlayerAtGate = tick;
                        if (firstEmployerAtGate < 0 && sim.identity().faction(unit) == Faction.ALLY
                                && !originalMilitia.contains(unit)
                                && Math.hypot(sim.world().x(unit) - 83, sim.world().y(unit) - 30) <= 8)
                            firstEmployerAtGate = tick;
                    }
                    if (assembled < 0 && landedPlayer.size() == companySize
                            && sim.getSquads().stream().filter(s -> s.faction == Faction.MARINE)
                            .noneMatch(SquadFormUpSystem::formingUp)) assembled = tick;
                    if (firstPlayerLoss < 0 && count(sim, Faction.MARINE) < landedPlayer.size()) firstPlayerLoss = tick;
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long id = sim.liveUnitAt(i);
                        if (firstRaiderAtGate < 0 && sim.identity().faction(id) == Faction.DEFENDER
                                && Math.hypot(sim.world().x(id) - 83, sim.world().y(id) - 30) <= 8)
                            firstRaiderAtGate = tick;
                    }
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
            float haulerAfter = 0;
            for (long hauler : haulers) if (sim.convoy().isPresent(hauler))
                haulerAfter += sim.convoy().structure(hauler) + sim.convoy().armor(hauler);
            return new Result(new JSONObject().put("seed", seed).put("profile", surface.name() + (starterCompany
                            ? " / detached starter roster, surplus Rooks and Cordon shells"
                            : " / controlled construction, no campaign loadout"))
                    .put("starterCompany", starterCompany).put("playerArrivals", playerArrivals)
                    .put("groundStart", groundStart).put("holdAtStartTicks", holdTicks)
                    .put("namedMarinesAtStart", namedAtStart).put("companySquadsAtStart", squadsAtStart.size())
                    .put("companyLeaderAtStart", leaderAtStart)
                    .put("companyAircraft", companyAircraft).put("employerAircraft", employerAircraft)
                    .put("parkedHaulers", haulers.size()).put("haulerIntact", haulerAfter >= haulerStructure)
                    .put("firstPlayerAtGateTick", firstPlayerAtGate)
                    .put("playerLosses", landedPlayer.size() - count(sim, Faction.MARINE))
                    .put("originalMilitiaLosses", originalMilitia.size() - living(sim, originalMilitia))
                    .put("firstEmployerAtGateTick", firstEmployerAtGate)
                    .put("squadAssembledTick", assembled).put("firstRaiderAtGateTick", firstRaiderAtGate)
                    .put("firstPlayerLossTick", firstPlayerLoss)
                    .put("playerBoatId", boat == null ? JSONObject.NULL : boat.boatId())
                    .put("playerType", boat == null ? JSONObject.NULL : boat.pattern().name())
                    .put("playerPlating", boat == null ? JSONObject.NULL : boat.plating().id())
                    .put("playerDrive", boat == null ? JSONObject.NULL : boat.drive().id())
                    .put("storyBeats", StoryBeatEvidence.snapshot(sim)).put("contactMix", mix.json()).put("reactiveDefence", ReactiveDefenceEvidence.figures(sim))
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
