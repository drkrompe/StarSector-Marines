package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
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
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production construction and roster-overlay evidence, never ordinary unit tests. */
@Tag("counterattack-command-evidence")
class DebugMissionLaunchEvidenceTest {
    @BeforeAll static void catalogs() throws Exception { HeadlessArmoryPreviewRenderer.installCatalogs(Path.of("mod")); }
    @Test void standardOperationsAndRescueUseTheirDefaultForces() {
        var catalog = DebugMissionCatalog.missions(null);
        var samples = new ArrayList<Mission>();
        for (var type : MissionType.values()) samples.add(catalog.stream().filter(m -> DebugMissionCatalog.scenario(m) == null && m.type == type).findFirst().orElseThrow());
        samples.addAll(catalog.stream().filter(m -> m.source == MissionSource.DEBUG_CANONICAL_CIVILIAN_RESCUE || m.source == MissionSource.DEBUG_CIVILIAN_RESCUE).toList());
        for (var mission : samples) {
            var context = new MarineOpsContext(null);
            context.resetDebugPreset(mission);
            var preset = DebugMissionCatalog.preset(mission);
            var lift = Collections.nCopies(preset.boats(), FittedBoat.standard(preset.boat()));
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
        var lift = Collections.nCopies(preset.boats(), FittedBoat.standard(preset.boat()));
        var mechs = preset.mechs() == 1 ? DebugMechRoster.recoveredHound() : DebugMechRoster.randomized(0, 0);
        var plan = MissionLaunch.captureScenario(context, mission, lift, FlybyRoster.EMPTY, List.of(MechSupport.ID), List.of(), mechs, mission.battleSeed);
        try (var battle = MissionLaunch.finishSimulation(context, mission, FlybyRoster.EMPTY, plan, plan.builder().get())) {
            for (int tick = 0; tick < 30; tick++) battle.simulation().advance(1f / 30f);
            assertNotNull(battle.simulation());
            assertEquals(preset.squads() * 12, context.getDebugCompanyRoster().lineReadySoldiers().size());
        }
    }
}
