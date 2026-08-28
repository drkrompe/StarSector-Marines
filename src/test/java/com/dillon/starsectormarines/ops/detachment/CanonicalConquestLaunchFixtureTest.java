package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fixture.FighterWingCommitment;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalConquestLaunchFixtureTest {

    @Test
    void checkedInMatrixMatchesTheDeliberateFullCompanyCaptures()
            throws Exception {
        assertCanonical("conquest-reinforced-south-v3.json",
                reinforcedSouth(), DebugCompanyStage.REINFORCED,
                List.of(6, 6, 6, 6, 5, 5));
        assertCanonical("conquest-full-strength-west-v3.json",
                fullStrengthWest(), DebugCompanyStage.FULL_STRENGTH,
                List.of(12, 12, 11, 11, 11, 11));
    }

    private static void assertCanonical(
            String resourceName, BattleLaunchFixture authored,
            DebugCompanyStage stage, List<Integer> expectedCycles)
            throws Exception {
        String resource = "/battle-fixtures/" + resourceName;
        JSONObject checkedIn;
        try (InputStream stream = CanonicalConquestLaunchFixtureTest.class
                .getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture: " + resource);
            }
            checkedIn = new JSONObject(new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(authored, BattleFixtureJson.fromJson(checkedIn),
                resourceName + " changed without a deliberate authoring update");

        BattleLaunchFixture fixture = assertInstanceOf(BattleLaunchFixture.class,
                BattleFixtureJson.fromJson(checkedIn));
        ConquestBattleFixture construction = assertInstanceOf(
                ConquestBattleFixture.class, fixture.construction());
        assertEquals(MarineArrivalPolicy.PAIRED_HALF_SQUAD,
                construction.arrivalPlan().policy());
        assertEquals(3, construction.arrivalPlan().arrivalConfig().dropZoneCount());
        assertEquals(1,
                construction.arrivalPlan().arrivalConfig().shuttlePairsPerZone());
        assertEquals(expectedCycles, construction.manifest().stream()
                .map(shuttle -> shuttle.cycles).toList());
        assertTrue(construction.manifest().stream().allMatch(shuttle ->
                shuttle.type == ShuttleType.AEROSHUTTLE
                        && shuttle.seatsPerSortie == 6));

        List<MarineSeatCommitment> seats = fixture.launch().marineSeats();
        assertEquals(stage.marines(), seats.size());
        assertEquals(stage.squads, seats.stream()
                .map(MarineSeatCommitment::campaignSquadId).distinct().count());
        assertEquals(stage.squads, seats.stream()
                .filter(MarineSeatCommitment::campaignSquadLeader).count());
        assertTrue(seats.stream().allMatch(seat ->
                seat.campaignSquadStrength() == MarineSquad.CAPACITY));
    }

    private static BattleLaunchFixture reinforcedSouth() {
        TargetProfile target = new TargetProfile(5, 7, 2, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION,
                        EconomicFunction.SPACEPORT));
        return fixture("reinforced", 1L, DebugCompanyStage.REINFORCED,
                72_112L, false, OperationTier.REINFORCED, RiskLevel.LOW,
                target, List.of(new FighterWingCommitment(FighterProfile.TALON,
                        Faction.DEFENDER, 1, 15f, 30f)));
    }

    private static BattleLaunchFixture fullStrengthWest() {
        TargetProfile target = new TargetProfile(7, 5, 6, 2, "hegemony",
                EnumSet.of(EconomicFunction.HEAVY_INDUSTRY,
                        EconomicFunction.MILITARY,
                        EconomicFunction.SPACEPORT));
        return fixture("full-strength", 4_096L,
                DebugCompanyStage.FULL_STRENGTH, 95_408L, true,
                OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM,
                target, List.of());
    }

    private static BattleLaunchFixture fixture(
            String identityPrefix, long seed, DebugCompanyStage stage,
            long companySeed, boolean heavyArmor, OperationTier tier,
            RiskLevel risk, TargetProfile target,
            List<FighterWingCommitment> enemyFighterSupport) {
        MarineRoster roster = DebugCompany.roster(
                stage, stage.squads, new Random(companySeed));
        CampaignMarineDeployment deployment =
                CampaignMarineDeployment.freezeSelection(
                        roster,
                        new LinkedHashSet<>(DebugCompany.lineSquadIds(roster)),
                        stage.marines());
        List<MarineSeatCommitment> seats = stableIdentities(
                identityPrefix, deployment.commitments());
        ShuttleArrivalPlan arrival = new ShuttleArrivalPlan(
                MarineArrivalPolicy.PAIRED_HALF_SQUAD, 0);
        ShuttleArrivalPlan.ResolvedManifest resolved = arrival.resolveManifest(
                List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                seats.size());
        ConquestBattleFixture construction = new ConquestBattleFixture(
                seed, resolved.assignments(), heavyArmor, tier, risk, target,
                List.of(), enemyFighterSupport, arrival);
        BattleLaunchOverlay launch = new BattleLaunchOverlay(
                resolved.firstPlayerShuttle(), seats,
                List.of(), List.of(), List.of(), 0);
        return new BattleLaunchFixture(construction, launch);
    }

    private static List<MarineSeatCommitment> stableIdentities(
            String prefix, List<MarineSeatCommitment> captured) {
        List<MarineSeatCommitment> stable = new ArrayList<>(captured.size());
        for (int i = 0; i < captured.size(); i++) {
            MarineSeatCommitment seat = captured.get(i);
            int squad = i / MarineSquad.CAPACITY + 1;
            stable.add(new MarineSeatCommitment(
                    "%s-marine-%03d".formatted(prefix, i + 1),
                    seat.primaryWeaponId(), seat.equipmentGrade(),
                    seat.soldierProfile(), seat.specialEquipmentId(),
                    seat.armorFamily(), seat.armorCapacity(),
                    seat.armorRating(), seat.armorMoveSpeedMult(),
                    seat.armorIncomingAccuracyMult(),
                    "%s-squad-%02d".formatted(prefix, squad),
                    seat.campaignSquadLabel(),
                    i % MarineSquad.CAPACITY == 0,
                    MarineSquad.CAPACITY, seat.campaignFireTeamIndex()));
        }
        return List.copyOf(stable);
    }
}
