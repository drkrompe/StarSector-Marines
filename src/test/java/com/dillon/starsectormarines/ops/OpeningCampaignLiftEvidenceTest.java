package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.mission.story.OpeningOperationStory;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
    @Test
    void oneHermesDeliversTheWholeNamedSquadAcrossBothOpeningChapters() throws Exception {
        Path output = Path.of(System.getProperty("relief.command.evidence.outputDir",
                "build/reports/commander/relief"));
        Files.createDirectories(output);
        JSONArray reports = new JSONArray();
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            for (long seed : new long[]{8101L, 8102L}) {
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
}
