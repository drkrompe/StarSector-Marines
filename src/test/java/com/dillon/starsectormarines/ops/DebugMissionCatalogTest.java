package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.loot.LootGenerator;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DebugMissionCatalogTest {
    @Test void catalogHasUniqueDetachedIdentitiesAndEveryOperationTier() {
        var missions = DebugMissionCatalog.missions(null);
        assertEquals(missions.size(), missions.stream().map(m -> m.id).distinct().count());
        assertTrue(missions.stream().allMatch(m -> m.source.isDebug()));
        for (var type : MissionType.values()) for (var tier : OperationTier.values())
            if (tier.atLeast(type.tierFloor)) assertTrue(missions.stream().anyMatch(m ->
                    DebugMissionCatalog.scenario(m) == null && m.type == type && m.tier == tier), type + " / " + tier);
    }
    @ParameterizedTest @EnumSource(DebugMissionCatalog.Scenario.class)
    void authoredPresetHasStableSurveyAndExactPlayerSeats(DebugMissionCatalog.Scenario scenario) {
        var mission = scenario.mission();
        var preset = DebugMissionCatalog.preset(mission);
        var context = new MarineOpsContext(null);
        context.resetDebugPreset(mission);
        var company = context.getDebugCompanyRoster();
        assertEquals(preset.squads() * 12, company.lineReadySoldiers().size());
        var lift = Collections.nCopies(preset.boats(), FittedBoat.standard(preset.boat()));
        var mechs = preset.mechs() == 1 ? DebugMechRoster.recoveredHound() : DebugMechRoster.randomized(0, 0);
        var plan = MissionLaunch.captureScenario(context, mission, lift, FlybyRoster.EMPTY, List.of(MechSupport.ID), List.of(), mechs, 42);
        var repeat = MissionLaunch.captureScenario(context, mission, lift, FlybyRoster.EMPTY, List.of(MechSupport.ID), List.of(), mechs, 42);
        assertEquals(plan.key(), repeat.key());
        assertEquals(preset.squads() * 12, plan.detachment().shuttleManifest.stream().skip(plan.firstPlayerShuttle()).mapToInt(s -> s.embarkedPersonnel).sum());
        assertSame(mission, DebugMissionDifficulty.atTier(mission, OperationTier.FULL_STRENGTH));
        assertSame(mission, DebugMissionDifficulty.atSprawl(mission, PrecinctPlan.Sprawl.DENSE));
        int revision = context.debugPresetRevision();
        context.setDebugSquadCount(8);
        context.resetDebugPreset(mission);
        assertEquals(revision + 1, context.debugPresetRevision());
        assertEquals(preset.squads(), context.getDebugSquadCount());
        assertNotSame(company, context.getDebugCompanyRoster());
    }
    @Test void debugRewardsAndReturnNeverNeedCampaignState() {
        var context = new MarineOpsContext(null);
        var mission = Mission.builder(DebugMissionCatalog.Scenario.COUNTERATTACK.mission()).salvageNegotiated(100).build();
        var outcome = MissionOutcome.builder().mission(mission).victory(true).salvageEntitlement(100).build();
        assertSame(LootManifest.EMPTY, LootGenerator.generate(outcome));
        MissionResolver.apply(outcome);
        context.setLastOutcome(outcome);
        context.forfeitLoot();
        context.finishOperation(false);
        assertEquals(ScreenId.DEBUG_MISSIONS, context.getCurrentScreen());
    }
    @Test void recoveredHoundIsItsCampaignChaingunFit() {
        var support = (MechSupport) DebugMechRoster.recoveredHound().applyTo(List.of()).get(0);
        assertEquals(List.of(WaylineHound.deployment()), support.deployments());
    }
}
