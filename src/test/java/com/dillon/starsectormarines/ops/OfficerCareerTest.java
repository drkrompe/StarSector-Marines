package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.OfficerCareer;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.Trait;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OfficerCareerTest {
    @Test void choicesAreRankBudgetedBoundedAndSurviveSerialization() throws Exception {
        var officer = new MarineCaptain("Vale", null, Rank.LIEUTENANT, 0f);
        assertTrue(officer.train(OfficerSpecialty.TACTICAL_COVER));
        assertFalse(officer.train(OfficerSpecialty.TACTICAL_COVER));
        officer.setRank(Rank.CAPTAIN);
        assertTrue(officer.train(OfficerSpecialty.TACTICAL_COVER));
        officer.setRank(Rank.MAJOR);
        assertTrue(officer.train(OfficerSpecialty.TACTICAL_COVER));
        officer.setRank(Rank.LT_COLONEL);
        assertFalse(officer.train(OfficerSpecialty.TACTICAL_COVER), "three ranks is the cap");
        assertFalse(officer.train(OfficerSpecialty.RECOVERY_PLANNING), "a retired specialty is not offered");
        assertTrue(officer.career().record("attempt-one", true, 500));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(officer); }
        MarineCaptain copy;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (MarineCaptain) in.readObject();
        }
        assertEquals(3, copy.career().level(OfficerSpecialty.TACTICAL_COVER));
        assertEquals(1, copy.career().availablePoints(copy.rank()));
        assertFalse(copy.career().record("attempt-one", true, 500));
        assertEquals(500, copy.career().earnedXp());
        copy.setStatus(Status.KIA);
        assertFalse(copy.train(OfficerSpecialty.FIELD_MEDICINE));
    }

    @Test void aRetiredSpecialtysPointsAreRefundedOnLoad() throws Exception {
        var officer = new MarineCaptain("Vale", null, Rank.CAPTAIN, 0f);
        assertTrue(officer.train(OfficerSpecialty.TACTICAL_COVER));
        // An older save trained a specialty that is no longer offered.
        Field training = OfficerCareer.class.getDeclaredField("training");
        training.setAccessible(true);
        @SuppressWarnings("unchecked") var trained = (List<OfficerSpecialty>) training.get(officer.career());
        trained.add(OfficerSpecialty.SALVAGE_STEWARDSHIP);
        assertEquals(0, officer.career().availablePoints(officer.rank()));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(officer); }
        MarineCaptain copy;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (MarineCaptain) in.readObject();
        }
        assertEquals(0, copy.career().level(OfficerSpecialty.SALVAGE_STEWARDSHIP));
        assertEquals(1, copy.career().level(OfficerSpecialty.TACTICAL_COVER));
        assertEquals(1, copy.career().availablePoints(copy.rank()), "the retired point comes back");
        assertEquals(List.of(OfficerSpecialty.TACTICAL_COVER, OfficerSpecialty.FIELD_MEDICINE), OfficerSpecialty.offered());
    }

    @Test void oldHighRankOfficerGetsChoicesWithoutInventedServiceHistory() {
        var officer = new MarineCaptain("Old hand", null, Rank.COLONEL, 0f);
        assertEquals(5, officer.career().availablePoints(officer.rank()));
        assertEquals(0, officer.career().deployments());
        assertEquals(0, OfficerCommandProfile.capture(officer).cover());
    }

    @Test void creditAndEffectsFollowFrozenSquadsNotLaterHomeAssignments() {
        var roster = roster();
        var lead = captain(roster, "Lead");
        var second = captain(roster, "Second");
        second.traits().add(Trait.NATURAL_LEADER);
        var idle = captain(roster, "Idle");
        var squads = line(roster);
        roster.assignCaptainToSquad(lead.id(), squads.get(0).id());
        roster.assignCaptainToSquad(second.id(), squads.get(1).id());
        lead.train(OfficerSpecialty.TACTICAL_COVER);
        var deployment = CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24, lead);
        float baseline = CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24).seat(0).armorIncomingAccuracyMult;
        // Tactical Cover rides with the squad's frozen officer profile and applies in battle as its
        // own modifier; the armour's figure is left as issued.
        assertEquals(baseline, deployment.seat(0).armorIncomingAccuracyMult, .00001f);
        assertEquals(.95f, deployment.seat(0).campaignSquad.commandGroupIdentity.profile()
                .incomingAccuracyMultiplier(), .00001f);
        assertEquals(CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24).seat(12).armorIncomingAccuracyMult,
                deployment.seat(12).armorIncomingAccuracyMult, .00001f);
        var frozenCommander = OfficerCommandProfile.capture(lead);
        roster.assignCaptainToSquad(idle.id(), squads.get(1).id());
        lead.setName("Changed"); second.traits().clear();
        var survivors = deployment.commitments().stream().map(s -> s.campaignSoldierId()).collect(Collectors.toSet());
        var results = OfficerMissionResult.compute(deployment.commitments(), frozenCommander,
                survivors, Set.of(), true, 10000, "same-job", 10f, 24, 0);
        assertEquals(2, results.size());
        assertEquals("Lead", results.get(0).officer().name());
        assertEquals(1500, results.get(1).xpGained());
        var outcome = MissionOutcome.builder().missionId("same-job").missionName("Raid")
                .victory(true).officers("attempt-a", results).build();
        OfficerMissionResult.apply(roster, outcome, 10);
        OfficerMissionResult.apply(roster, outcome, 11);
        assertEquals(Rank.CAPTAIN, lead.rank());
        assertEquals(0, lead.xp()); assertEquals(500, second.xp());
        assertEquals(1, lead.career().deployments());
        assertEquals(1, second.career().deployments()); assertEquals(0, idle.career().deployments());
        assertEquals(1, lead.career().availablePoints(lead.rank()));
        var retry = MissionOutcome.builder().missionId("same-job").missionName("Raid")
                .victory(true).officers("attempt-b", results).build();
        OfficerMissionResult.apply(roster, retry, 12);
        assertEquals(2, lead.career().deployments());
    }

    @Test void heldBackOfficerGetsNothingAndLossCreditIncludesBoatPassengers() {
        var roster = roster(); var lead = captain(roster, "Lead"); var second = captain(roster, "Second");
        var squads = line(roster);
        roster.assignCaptainToSquad(lead.id(), squads.get(0).id());
        roster.assignCaptainToSquad(second.id(), squads.get(1).id());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24, lead);
        var dead = Set.of(deployment.seat(0).campaignSoldierId);
        var result = OfficerMissionResult.compute(deployment.commitments(), OfficerCommandProfile.capture(lead),
                Set.of(), dead, false, 0, "boat-down", 20f, 1, 1);
        assertEquals(1, result.size()); assertEquals(dead, result.get(0).soldierIds());
        assertEquals(500, result.get(0).xpGained());
        assertTrue(result.get(0).status() == Status.KIA || result.get(0).injuredUntilDay() == 65f);
        assertEquals(result, OfficerMissionResult.compute(deployment.commitments(), OfficerCommandProfile.capture(lead),
                Set.of(), dead, false, 0, "boat-down", 20f, 1, 1));
    }

    @Test void wipedFoundingOfficerIsWoundedNeverKilled() {
        var roster = roster(); var lead = captain(roster, "Lead");
        var squads = line(roster);
        roster.assignCaptainToSquad(lead.id(), squads.get(0).id());
        roster.beginOfficerInduction(lead);
        var deployment = CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24, lead);
        var dead = Set.of(deployment.seat(0).campaignSoldierId);
        // Every seed that would kill an ordinary officer must wound the founder for the wipe's 45 days.
        for (int i = 0; i < 40; i++) {
            var result = OfficerMissionResult.compute(deployment.commitments(), OfficerCommandProfile.capture(lead),
                    Set.of(), dead, false, 0, "boat-down-" + i, 20f, 1, 1);
            assertEquals(Status.INJURED, result.get(0).status());
            assertEquals(65f, result.get(0).injuredUntilDay());
        }
    }

    @Test void unavailableHomeOfficerDoesNotGrantCover() {
        var roster = roster(); var lead = captain(roster, "Lead");
        lead.train(OfficerSpecialty.TACTICAL_COVER);
        roster.assignCaptainToSquad(lead.id(), line(roster).get(0).id()); lead.setStatus(Status.INJURED);
        var deployment = CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24, null);
        assertNull(deployment.seat(0).campaignSquad.commandGroupIdentity.profile());
        assertEquals(CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24).seat(0).armorIncomingAccuracyMult,
                deployment.seat(0).armorIncomingAccuracyMult);
    }

    @Test void resultsListEveryOfficer() {
        var roster = roster(); var first = captain(roster, "First"); var second = captain(roster, "Second");
        first.train(OfficerSpecialty.TACTICAL_COVER);
        second.setRank(Rank.CAPTAIN);
        second.train(OfficerSpecialty.FIELD_MEDICINE); second.train(OfficerSpecialty.FIELD_MEDICINE);
        var results = List.of(new OfficerMissionResult(OfficerCommandProfile.capture(first), Set.of("a"), 100, Status.ACTIVE, 0, null),
                new OfficerMissionResult(OfficerCommandProfile.capture(second), Set.of("b"), 100, Status.ACTIVE, 0, null));
        var outcome = MissionOutcome.builder().officers("results", results).build();
        var rows = ResultsScreen.resultRows(outcome, null, key -> key);
        assertEquals(2, rows.stream().filter(row -> row.id().startsWith("result-officer-")).count());
        var debug = MissionOutcome.builder().missionSource(MissionSource.DEBUG).officers("debug", results).build();
        OfficerMissionResult.apply(roster, debug, 1);
        assertEquals(0, first.career().deployments());
    }

    @Test void resolverUsesTheAppliedDeploymentAndKeepsOneReceiptAcrossRecomputation() {
        var roster = roster(); var lead = captain(roster, "Lead"); var second = captain(roster, "Second");
        roster.assignCaptainToSquad(lead.id(), line(roster).get(0).id());
        roster.assignCaptainToSquad(second.id(), line(roster).get(1).id());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, ids(roster), 24, lead);
        try (var sim = new BattleSimulation(new NavigationGrid(20, 20), new CellTopology(20, 20));
             var retry = new BattleSimulation(new NavigationGrid(20, 20), new CellTopology(20, 20))) {
            deployment.applyTo(sim);
            sim.setOperationOfficer(OfficerCommandProfile.capture(lead));
            sim.spawn(new EntitySpec("one", Faction.MARINE, UnitType.MARINE, 2, 2)
                    .campaignSoldierId(deployment.seat(0).campaignSoldierId));
            sim.spawn(new EntitySpec("two", Faction.MARINE, UnitType.MARINE, 3, 3)
                    .campaignSoldierId(deployment.seat(12).campaignSoldierId));
            var mission = Mission.builder().id("repeated-job").name("Patrol").type(MissionType.ASSAULT)
                    .source(MissionSource.GENERATED).risk(RiskLevel.LOW).requirements("Patrol")
                    .mapPosition(.5f, .5f).requiredDrops(1).build();
            var first = MissionResolver.compute(sim, mission, lead);
            var repeated = MissionResolver.compute(sim, mission, lead);
            assertEquals(2, first.officers.size());
            assertEquals(first.officers, repeated.officers);
            assertEquals(first.officerAttemptId, repeated.officerAttemptId);
            assertNotEquals(sim.officerAttemptId(), retry.officerAttemptId());
            OfficerMissionResult.apply(roster, first, 1); OfficerMissionResult.apply(roster, repeated, 1);
            assertEquals(1, second.career().deployments());
        }
    }

    private static MarineRoster roster() { var r = new MarineRoster(); r.bootstrapInitialComplement(24); return r; }
    private static MarineCaptain captain(MarineRoster r, String name) {
        var c = new MarineCaptain(name, null, Rank.LIEUTENANT, 0f); r.add(c); return c;
    }
    private static List<MarineSquad> line(MarineRoster r) { return r.squads().stream().filter(s -> !s.reserve()).toList(); }
    private static Set<String> ids(MarineRoster r) { return line(r).stream().map(MarineSquad::id).collect(Collectors.toSet()); }
}
