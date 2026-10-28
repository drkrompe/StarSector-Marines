package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.setup.MissileAaTestSetup;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production construction and roster-overlay evidence, never ordinary unit tests. */
@Tag("counterattack-command-evidence")
class DebugMissionLaunchEvidenceTest {
    /**
     * Hull specs and vanilla data for every case, not only the one that asks: a mission with a warship or tender
     * (Working Passage's Shepherd) reads its .ship spec at setup, and passed only when another case happened to
     * install them first.
     */
    @BeforeAll static void catalogs() throws Exception {
        HeadlessArmoryPreviewRenderer.installCatalogs(Path.of("mod"));
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }
    @Test void missileAaNativeLaunchLandsItsExactNamedCompanyAndStaysOpen() throws Exception {
        InstalledHullEngines.install();
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var mission = DebugMissionCatalog.Scenario.MISSILE_AA.mission();
            assertEquals(Strings.get("missileAaTestTitle"), mission.name);
            assertFalse(mission.name.startsWith("@"));
            assertNotEquals("missileAaTestTitle", mission.name);
            assertNull(StoryScene.forMission(mission), "The range has no invented story illustration");
            var context = new MarineOpsContext(null);
            context.resetDebugPreset(mission);
            var company = context.getDebugCompanyRoster();
            var expected = company.lineReadySoldiers().stream().map(soldier -> soldier.id())
                    .collect(Collectors.toSet());
            assertEquals(12, expected.size());
            var preset = DebugMissionCatalog.preset(mission);
            var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
            var plan = MissionLaunch.captureScenario(context, mission, lift, FlybyRoster.EMPTY,
                    List.of(), List.of(), null, mission.battleSeed);
            try (var prepared = MissionLaunch.finishSimulation(context, mission, FlybyRoster.EMPTY,
                    plan, plan.builder().get())) {
                var sim = prepared.simulation();
                var loaded = new HashSet<String>();
                int boats = 0;
                for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                    boats++;
                    var flight = sim.world().mission(air);
                    for (var cycle : flight.cycleLoadouts) for (var seat : cycle) {
                        assertNotNull(seat.campaignSoldierId, "Native launch replaces every anonymous factory seat");
                        assertTrue(loaded.add(seat.campaignSoldierId), "Every company identity is loaded once");
                    }
                }
                assertEquals(lift.size(), boats);
                assertEquals(expected, loaded);
                var landed = new HashMap<String, Long>();
                for (int tick = 0; tick < 3300; tick++) {
                    sim.advance(1f / 30f);
                    assertFalse(sim.isComplete(), "Debug range must keep running after finite waves");
                    for (int index = 0; index < sim.liveUnitCount(); index++) {
                        long unit = sim.liveUnitAt(index);
                        if (sim.identity().faction(unit) != Faction.MARINE || sim.turretState().isTurret(unit)) continue;
                        String soldier = (String) sim.getEntityWorld().getObject(unit,
                                sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                        assertNotNull(soldier);
                        assertTrue(expected.contains(soldier));
                        Long previous = landed.putIfAbsent(soldier, unit);
                        if (previous == null) assertTrue(sim.world().y(unit) < 35f, "Company lands at the safe apron");
                        else assertEquals(Long.valueOf(unit), previous, "No second body for one named marine");
                    }
                }
                assertEquals(expected, landed.keySet());
                assertEquals(3300, sim.getSimTickIndex());
                var picture = MissionObjectiveProjection.capture(sim);
                assertEquals(3, picture.objectives().size());
                for (var lane : MissileAaTestSetup.LANES) {
                    var item = picture.objectives().stream()
                            .filter(value -> value.id().equals("missile-aa-" + lane.id())).findFirst().orElseThrow();
                    assertEquals(lane.label(), item.name());
                    assertEquals(lane.x(), item.focus().x());
                    assertEquals(lane.y(), item.focus().y());
                    assertTrue(item.progressText().contains("6/6"), item.progressText());
                    assertFalse(item.progressText().contains("missileAa"), "Readout uses localized strings");
                    boolean batterySurvives = false;
                    for (int index = 0; index < sim.liveUnitCount(); index++) {
                        long unit = sim.liveUnitAt(index);
                        if (sim.turretState().isTurret(unit)
                                && sim.turretState().structure(unit).id.equals(lane.structureId())) {
                            batterySurvives = sim.world().isAlive(unit);
                            break;
                        }
                    }
                    System.out.println("MISSILE_AA_NATIVE_LANE: " + lane.id() + " / " + item.progressText()
                            + " / batterySurvives=" + batterySurvives);
                }
                System.out.println("MISSILE_AA_NATIVE_LAUNCH: company=" + landed.size()
                        + ", boats=" + boats + ", ticks=" + sim.getSimTickIndex()
                        + ", complete=" + sim.isComplete() + ", lanes=" + picture.objectives().size());
            }
        } finally {
            InstalledHullEngines.uninstall();
        }
    }

    @Test void standardOperationsAndRescueUseTheirDefaultForces() {
        var catalog = DebugMissionCatalog.missions(null);
        var samples = new ArrayList<Mission>();
        for (var type : MissionType.values()) samples.add(catalog.stream().filter(m -> DebugMissionCatalog.scenario(m) == null && m.type == type).findFirst().orElseThrow());
        samples.addAll(catalog.stream().filter(m -> m.source == MissionSource.DEBUG_CANONICAL_CIVILIAN_RESCUE || m.source == MissionSource.DEBUG_CIVILIAN_RESCUE).toList());
        for (var mission : samples) {
            var context = new MarineOpsContext(null);
            context.resetDebugPreset(mission);
            var preset = DebugMissionCatalog.preset(mission);
            var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
            var plan = MissionLaunch.captureScenario(context, mission, lift, FlybyRoster.EMPTY, List.of(), List.of(), null, 42);
            try (var battle = MissionLaunch.finishSimulation(context, mission, FlybyRoster.EMPTY, plan, plan.builder().get())) {
                battle.simulation().advance(1f / 30f);
            }
        }
    }
    @ParameterizedTest @EnumSource(DebugMissionCatalog.Scenario.class)
    void everyAuthoredMissionBuildsAndAcceptsItsDefaultCompany(DebugMissionCatalog.Scenario scenario) {
        var mission = scenario.mission();
        var preset = DebugMissionCatalog.preset(mission);
        var context = new MarineOpsContext(null);
        context.resetDebugPreset(mission);
        var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
        var mechs = preset.mechs() == 1 ? DebugMechRoster.recoveredHound() : DebugMechRoster.randomized(0, 0);
        var plan = MissionLaunch.captureScenario(context, mission, lift, FlybyRoster.EMPTY, List.of(MechSupport.ID), List.of(), mechs, mission.battleSeed);
        try (var battle = MissionLaunch.finishSimulation(context, mission, FlybyRoster.EMPTY, plan, plan.builder().get())) {
            for (int tick = 0; tick < 30; tick++) battle.simulation().advance(1f / 30f);
            assertNotNull(battle.simulation());
            assertEquals(preset.squads() * 12, context.getDebugCompanyRoster().lineReadySoldiers().size());
        }
    }
}
