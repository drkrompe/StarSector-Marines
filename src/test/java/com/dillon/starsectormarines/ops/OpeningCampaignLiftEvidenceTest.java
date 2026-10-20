package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.mission.story.OpeningOperationStory;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import java.lang.reflect.Proxy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Campaign manifests and real ferry flight, run with the opt-in Relief evidence task. */
@Tag("relief-command-evidence")
class OpeningCampaignLiftEvidenceTest {
    private SectorAPI previousSector;

    @BeforeEach void detachedHost() {
        previousSector = Global.getSector();
        Global.setSector((SectorAPI) Proxy.newProxyInstance(SectorAPI.class.getClassLoader(),
                new Class<?>[]{SectorAPI.class}, (proxy, method, args) ->
                        method.getName().equals("getScripts") ? List.of() : null));
    }

    @AfterEach void restoreHost() { Global.setSector(previousSector); }

    /**
     * Relief's story chapter starts its named squad ashore beside Wayline's truck, with no company boat in the
     * air; Counterattack is the first chapter one Hermes ferries the whole squad into.
     */
    @Test
    void openingChaptersDeliverTheWholeNamedSquad() throws Exception {
        Path output = Path.of(System.getProperty("relief.command.evidence.outputDir",
                "build/reports/commander/relief"));
        Files.createDirectories(output);
        JSONArray reports = new JSONArray();
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            for (long seed : new long[]{8101L, 8102L}) {
                if (kind == OpeningOperationKind.RELIEF) {
                    reports.put(groundStart(seed));
                    continue;
                }
                for (boolean fitted : new boolean[]{false, true}) {
                    var roster = DebugMissionCatalog.starterCompany(1);
                    var selected = roster.squads().stream().filter(s -> !s.reserve())
                            .map(s -> s.id()).collect(Collectors.toSet());
                    var mission = new OpeningOperationStory(kind).build(
                            new StoryEligibilityContext(null, null, null, roster, seed, true));
                    var boat = new FittedBoat(ShuttleType.HERMES,
                            fitted ? BoatFitting.requireById("reinforced_plating") : BoatFitting.STANDARD_PLATING,
                            fitted ? BoatFitting.requireById("tuned_drive") : BoatFitting.STANDARD_DRIVE,
                            "company-hermes");
                    var plan = MissionLaunch.captureScenario(null, mission, List.of(boat),
                            FlybyRoster.EMPTY, List.of(), List.of(), null, seed,
                            roster.lineReadySoldiers().size());
                    var manifest = plan.detachment().shuttleManifest;
                    int employer = plan.firstPlayerShuttle();
                    var deployment = CampaignMarineDeployment.freezeSelection(roster, selected,
                            CampaignMarineDeployment.requiredSeats(manifest, employer));
                    assertEquals(12, deployment.size(), "The selected squad must not be cut to one boat load");
                    var expected = deployment.commitments().stream().map(c -> c.campaignSoldierId())
                            .collect(Collectors.toSet());
                    try (var sim = plan.builder().get()) {
                        BattleLaunchOverlay.capture(employer, deployment, FlybyRoster.EMPTY,
                                FlybyRoster.EMPTY, List.of(), 0, mission.fieldPresencePolicy)
                                .applyTo(sim, CommandPowerResources.FREE);
                        long playerBoat = Arrays.stream(sim.getAirEntityIds())
                                .filter(id -> sim.world().airFaction(id) == Faction.MARINE)
                                .findFirst().orElseThrow();
                        assertSame(boat, sim.world().airframe(playerBoat));
                        assertEquals(3, sim.world().mission(playerBoat).totalCycles);
                        Set<String> landed = new HashSet<>();
                        Set<Integer> squads = new HashSet<>();
                        JSONArray arrivals = new JSONArray();
                        while (!sim.isComplete() && sim.getSimTickIndex() < 6000 && landed.size() < 12) {
                            sim.advance(BattleSimulation.TICK_DT);
                            for (int i = 0; i < sim.liveUnitCount(); i++) {
                                long unit = sim.liveUnitAt(i);
                                if (sim.identity().faction(unit) != Faction.MARINE) continue;
                                String soldier = (String) sim.getEntityWorld().getObject(unit,
                                        sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                                assertTrue(expected.contains(soldier), "No anonymous replacement personnel");
                                if (!landed.add(soldier)) continue;
                                squads.add(sim.squad().squadId(unit));
                                arrivals.put(new JSONObject().put("tick", sim.getSimTickIndex())
                                        .put("totalLanded", landed.size())
                                        .put("sortie", sim.world().mission(playerBoat).currentCycle + 1));
                            }
                        }
                        String context = kind + " / " + seed + " / fitted=" + fitted;
                        assertEquals(expected, landed, context + ": all twelve must physically land");
                        assertEquals(1, squads.size(), context + ": all trips must reunite into one squad");
                        var squad = sim.getSquads().stream().filter(s -> squads.contains(s.id))
                                .findFirst().orElseThrow();
                        assertEquals(12, squad.originalSize, context);
                        assertEquals(12, squad.expectedSize, context);
                        assertFalse(SquadFormUpSystem.formingUp(squad), context);
                        reports.put(new JSONObject().put("operation", kind.name()).put("seed", seed)
                                .put("fitted", fitted).put("landed", landed.size()).put("battleSquads", squads.size())
                                .put("sorties", sim.world().mission(playerBoat).currentCycle + 1)
                                .put("arrivalTick", sim.getSimTickIndex()).put("arrivals", arrivals));
                    }
                }
            }
        }
        Files.writeString(output.resolve("campaign-lift.json"), reports.toString(2));
    }

    /** The story Relief launch through production capture, the launch overlay and a boat-less withdrawal. */
    private static JSONObject groundStart(long seed) throws Exception {
        var roster = DebugMissionCatalog.starterCompany(1);
        var selected = roster.squads().stream().filter(s -> !s.reserve())
                .map(s -> s.id()).collect(Collectors.toSet());
        var mission = new OpeningOperationStory(OpeningOperationKind.RELIEF).build(
                new StoryEligibilityContext(null, null, null, roster, seed, true));
        assertEquals(MarineArrivalPolicy.SECURED_GROUND, mission.marineArrivalPolicy);
        // A company that has repaired a boat brings it along; it stays aboard.
        var boat = FittedBoat.standard(ShuttleType.HERMES);
        var plan = MissionLaunch.captureScenario(null, mission, List.of(boat),
                FlybyRoster.EMPTY, List.of(), List.of(), null, seed,
                roster.lineReadySoldiers().size());
        int employer = plan.firstPlayerShuttle();
        assertEquals(1, employer, "the employer's militia shuttle leads the manifest");
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected,
                roster.lineReadySoldiers().size());
        assertEquals(12, deployment.size());
        var expected = deployment.commitments().stream().map(c -> c.campaignSoldierId())
                .collect(Collectors.toSet());
        try (var sim = plan.builder().get()) {
            BattleLaunchOverlay.capture(employer, deployment, FlybyRoster.EMPTY,
                    FlybyRoster.EMPTY, List.of(), 0, mission.fieldPresencePolicy)
                    .applyTo(sim, CommandPowerResources.FREE);
            String context = "RELIEF / " + seed;
            assertTrue(sim.companyStartsOnGround(), context);
            // A committed boat is parked off the map in reserve, as Cold Start's are; it never flies in.
            assertTrue(Arrays.stream(sim.getAirEntityIds())
                    .filter(id -> sim.world().airFaction(id) == Faction.MARINE)
                    .allMatch(id -> sim.world().mission(id).state == ShuttleState.GONE
                            && sim.world().mission(id).marinesRemaining == 0), context + ": no company boat flies");
            int haulers = 0;
            for (int i = 0; i < sim.convoy().vehicleCount(); i++) {
                long vehicle = sim.convoy().vehicleAt(i);
                if (sim.convoy().faction(vehicle) == Faction.CIVILIAN
                        && sim.convoy().vehicleType(vehicle) == VehicleType.CARGO_CARRIER) haulers++;
            }
            assertEquals(1, haulers, context + ": Wayline's truck is parked at the apron");
            Set<String> ashore = new HashSet<>();
            Set<Integer> squads = new HashSet<>();
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE) continue;
                String soldier = (String) sim.getEntityWorld().getObject(unit,
                        sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                assertTrue(expected.contains(soldier), "No anonymous replacement personnel");
                ashore.add(soldier);
                squads.add(sim.squad().squadId(unit));
            }
            assertEquals(expected, ashore, context + ": all twelve stand ashore at tick zero");
            assertEquals(1, squads.size(), context + ": as one squad");
            var squad = sim.getSquads().stream().filter(s -> squads.contains(s.id)).findFirst().orElseThrow();
            assertEquals(12, squad.originalSize, context);
            assertTrue(sim.resolveUnit(squad.leaderId) != 0L, context + ": with its leader");
            // Withdrawal needs no boat: the company leaves the way it came, and nobody is lost by leaving.
            for (int tick = 0; tick < 300; tick++) sim.advance(BattleSimulation.TICK_DT);
            int aliveBefore = 0;
            for (int i = 0; i < sim.liveUnitCount(); i++)
                if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.MARINE) aliveBefore++;
            assertTrue(sim.retreat(), context + ": withdrawal is accepted with no company boat on the map");
            assertTrue(sim.isComplete(), context);
            var outcome = MissionResolver.compute(sim, mission, null, Set.copyOf(selected));
            assertFalse(outcome.victory, context);
            assertEquals(aliveBefore, outcome.survivingSoldierIds.size(), context + ": withdrawal fabricates no losses");
            return new JSONObject().put("operation", "RELIEF").put("seed", seed).put("groundStart", true)
                    .put("ashoreAtTickZero", ashore.size()).put("battleSquads", squads.size())
                    .put("companyAircraft", 0).put("parkedHaulers", haulers)
                    .put("withdrawalAccepted", true).put("survivorsAfterWithdrawal", outcome.survivingSoldierIds.size());
        }
    }
}
