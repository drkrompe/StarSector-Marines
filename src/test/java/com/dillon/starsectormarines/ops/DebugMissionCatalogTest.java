package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.loot.LootGenerator;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DebugMissionCatalogTest {
    @ParameterizedTest @EnumSource(ShuttleType.class)
    void debugBoatsTakeTheTopTierOfEveryFittingTheirHullAccepts(ShuttleType pattern) {
        var boat = DebugMissionCatalog.maxFitted(pattern);
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            int top = BoatFitting.catalog(slot).stream().filter(f -> f.supports(pattern))
                    .mapToInt(BoatFitting::tier).max().orElseThrow();
            assertEquals(top, boat.fitting(slot).tier(), pattern + " " + slot);
        }
    }

    @Test void aMaxFittedHermesLiftsHeavyMachines() {
        assertEquals(LiftRating.HEAVY, DebugMissionCatalog.maxFitted(ShuttleType.HERMES).liftRating());
    }

    @Test void workingPassageExpectedCompanyUsesServiceRiflesWithStartingArmorAndNoHound() {
        var mission = DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission();
        var context = new MarineOpsContext(null);
        context.resetDebugPreset(mission);
        var company = context.getDebugCompanyRoster();
        var starter = DebugMissionCatalog.starterCompany(2);
        assertEquals(24, company.lineReadySoldiers().size());
        assertTrue(company.lineReadySoldiers().stream().allMatch(s ->
                WeaponRegistry.PULSE_RIFLE_ID.equals(s.primaryId()) && s.primaryGrade() == EquipmentGrade.SERVICE));
        assertEquals(starter.lineReadySoldiers().stream().map(s -> s.armorId()).toList(),
                company.lineReadySoldiers().stream().map(s -> s.armorId()).toList());
        assertEquals(0, DebugMissionCatalog.preset(mission).mechs());
        assertTrue(DebugMissionCatalog.preset(mission).summary().contains("service pulse rifles"));
        assertTrue(mission.requirements.contains("no Hound or officer bonuses"));
        context.setDebugSquadCount(3);
        assertEquals(36, context.getDebugCompanyRoster().lineReadySoldiers().size());
        assertTrue(context.getDebugCompanyRoster().lineReadySoldiers().stream().allMatch(s ->
                WeaponRegistry.PULSE_RIFLE_ID.equals(s.primaryId()) && s.primaryGrade() == EquipmentGrade.SERVICE));
        assertEquals("04", DebugMissionCatalog.Scenario.WORKING_PASSAGE.sequence());
    }
    @ParameterizedTest @EnumSource(value = DebugMissionCatalog.Scenario.class, names = {"WORKING_PASSAGE", "WELLHOLLOW_BREACH", "WELLHOLLOW_LINE", "WELLHOLLOW_ROAD", "WELLHOLLOW_BURN", "WELLHOLLOW_FRONT"}, mode = EnumSource.Mode.EXCLUDE)
    void otherAuthoredCompaniesKeepTheirStarterIssue(DebugMissionCatalog.Scenario scenario) {
        var context = new MarineOpsContext(null);
        context.resetDebugPreset(scenario.mission());
        var company = context.getDebugCompanyRoster();
        var starter = DebugMissionCatalog.starterCompany(scenario.squads);
        assertEquals(starter.lineReadySoldiers().stream().map(s -> s.primaryId()).toList(),
                company.lineReadySoldiers().stream().map(s -> s.primaryId()).toList());
        assertEquals(starter.lineReadySoldiers().stream().map(s -> s.primaryGrade()).toList(),
                company.lineReadySoldiers().stream().map(s -> s.primaryGrade()).toList());
        assertEquals(starter.lineReadySoldiers().stream().map(s -> s.armorId()).toList(),
                company.lineReadySoldiers().stream().map(s -> s.armorId()).toList());
        assertFalse(DebugMissionCatalog.preset(scenario.mission()).serviceRifles());
    }
    @Test void catalogHasUniqueDetachedIdentitiesAndEveryOperationTier() {
        var missions = DebugMissionCatalog.missions(null);
        assertEquals(missions.size(), missions.stream().map(m -> m.id).distinct().count());
        assertTrue(missions.stream().allMatch(m -> m.source.isDebug()));
        for (var type : List.of(MissionType.CONQUEST)) for (var tier : OperationTier.values())
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
        var lift = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
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
    @Test void locationGroupsKeepStoryOrderAndSeparateShelvedConcepts() {
        var catalog = DebugMissionCatalog.missions(null);
        assertEquals(List.of("Jangala", "Asharu", "Warion", "Garnir", "Unfixed site", "Agreus", "Bloomed world"), DebugMissionScreen.locations(catalog, 0));
        var jangala = DebugMissionScreen.visible(catalog, 0, "Jangala");
        assertEquals(List.of("01", "02", "03", "04", "R1", "R2", "R3"),
                jangala.stream().map(m -> DebugMissionCatalog.scenario(m).sequence()).toList());
        assertEquals(List.of("01", "02", "03", "04", "WB1", "WB2", "WB3", "WB4", "WB5"), DebugMissionScreen.visible(catalog, 0, "Asharu").stream()
                .map(m -> DebugMissionCatalog.scenario(m).sequence()).toList());
        var grouped = DebugMissionScreen.visible(catalog, 0, "All locations");
        assertEquals(jangala, grouped.subList(0, jangala.size()));
        assertEquals("Asharu", DebugMissionScreen.locationOf(grouped.get(jangala.size())));
    }
    @ParameterizedTest @EnumSource(value = DebugMissionCatalog.Scenario.class, names = "WELLHOLLOW_.*", mode = EnumSource.Mode.MATCH_ALL)
    void wellhollowChaptersPlayTheirFixedMapWithTheWardensBriefingAndTheChallengeForce(DebugMissionCatalog.Scenario scenario) {
        var mission = scenario.mission();
        assertEquals(WellhollowBloomCampaign.fixedSeed(scenario.wellhollow()), mission.battleSeed);
        assertEquals(mission.battleSeed, scenario.mission().battleSeed);
        assertEquals("warden", mission.dialogue.get(0).speakerKey());
        var preset = DebugMissionCatalog.preset(mission);
        assertEquals(WellhollowBloomCampaign.RECOMMENDED_SQUADS, preset.squads());
        assertEquals(WellhollowBloomCampaign.RECOMMENDED_MACHINES, preset.mechs());
    }
    @Test void recoveredHoundIsItsCampaignChaingunFit() {
        var support = (MechSupport) DebugMechRoster.recoveredHound().applyTo(List.of()).get(0);
        assertEquals(List.of(WaylineHound.deployment()), support.deployments());
    }
}
