package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.SalvageRightsSequence;
import com.dillon.starsectormarines.battle.setup.ColdStartSequence;
import com.dillon.starsectormarines.battle.setup.DepotHoldSequence;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import java.util.Collections;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.ColdStartPicket;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MissionObjectiveFamiliesTest {
    @Test void salvageShowsInterruptedCaptureAndExplicitInspectionGates() {
        var room = new PointOfInterest(PointOfInterest.Kind.DEPOT, 10, 10, 20, 20, 15, 15);
        var sequence = new SalvageRightsSequence(room, room, 2, List.of(), new StoryBeatLog());
        var p = sequence.progress();
        p.advance(2, true, false, false, true, false);
        var halfway = MissionObjectiveFamilies.salvage(sequence);
        assertEquals(.5f, halfway.progress());
        assertEquals("Receiving yard", halfway.focus().name());
        p.advance(1, true, true, false, true, false);
        assertEquals(0, MissionObjectiveFamilies.salvage(sequence).progress());
        assertTrue(MissionObjectiveFamilies.salvage(sequence).blocker().contains("contested"));
        for (int i = 0; i < 3; i++) p.advance(4, true, false, true, true, false);
        p.advance(60, true, false, true, true, false);
        var tail = MissionObjectiveFamilies.salvage(sequence);
        assertEquals(1, tail.progress());
        assertEquals(MissionObjectiveSnapshot.State.ACTIVE, tail.state());
        assertTrue(tail.blocker().contains("road response"));
        p.advance(0, true, false, false, true, true);
        assertTrue(MissionObjectiveFamilies.salvage(sequence).blocker().contains("technician"));
        p.advance(0, true, false, true, true, true);
        assertEquals(MissionObjectiveSnapshot.State.COMPLETE, MissionObjectiveFamilies.salvage(sequence).state());
        assertEquals("", MissionObjectiveFamilies.salvage(sequence).blocker());
    }

    @Test void plantingAndRaidReadTheirActualStepClocks() {
        var charge = new ChargeSiteObjective(8, 9, 5, "Power relay");
        var planted = MissionObjectiveFamilies.charge(charge);
        assertEquals(0f, planted.progress());
        assertTrue(planted.blocker().contains("equipped planter"));
        assertEquals(8, planted.focus().x());
        var raid = new RaidObjective("dispatch", "Dispatch office", 12, 13, 1, 3, 4);
        var seizure = MissionObjectiveFamilies.raid(raid);
        assertEquals(2, seizure.markers().size());
        assertEquals((float) RaidObjective.TARGET_RADIUS, seizure.markers().get(0).radius());
        assertEquals((float) RaidObjective.EGRESS_RADIUS, seizure.markers().get(1).radius());
        assertEquals(0f, planted.focus().radius(), "a planting beacon must not invent a radius");
        assertEquals("Dispatch office", seizure.focus().name());
        assertTrue(seizure.instruction().contains("settled"));
        assertFalse(seizure.progressText().contains("enemy"));
    }

    @Test void compoundUsesPublishedCaptureStateAndResolvedRoomCell() {
        var service = new CompoundService();
        var node = new TacticalNode(TacticalNode.Kind.COMMAND_POST, 5, 6, 1, 1, 10, 10, Faction.DEFENDER, 1, 0);
        var record = service.register(node);
        record.captureCellX = 7;
        record.captureCellY = 8;
        record.state = CompoundService.CompoundState.CONTESTED;
        record.captureProgress = .25f;
        record.attackersPresent = true;
        record.defendersPresent = true;
        var item = MissionObjectiveFamilies.compound(record, 3, "Junction Dispatch");
        assertEquals(.25f, item.progress());
        assertEquals(7, item.focus().x());
        assertTrue(item.blocker().contains("contested"));
        assertFalse(item.blocker().contains("defenders remaining"));
        record.defendersPresent = false;
        assertEquals("", MissionObjectiveFamilies.compound(record, 3, "Junction Dispatch").blocker());
        assertEquals("CAPTURING", MissionObjectiveFamilies.compound(record, 3, "Junction Dispatch").phase());
        record.state = CompoundService.CompoundState.MARINE_HELD;
        var held = MissionObjectiveFamilies.compound(record, 3, "Junction Dispatch");
        assertEquals(MissionObjectiveSnapshot.State.COMPLETE, held.state());
        assertTrue(held.instruction().contains("protect"));
        assertFalse(held.instruction().contains("4s"));
    }

    @Test void coldStartMeasuresWorkButConstructionHasNoInventedTimer() {
        var reserve = Collections.nCopies(24, new EntitySpec("Road team", Faction.DEFENDER, UnitType.MILITIA, 0, 0));
        var sequence = new ColdStartSequence(2, 1, reserve, true, false, new StoryBeatLog());
        sequence.progress().advance(17.5f, true, true, true, true, true, false, false, false);
        assertEquals(.5f, MissionObjectiveFamilies.coldStart(sequence).progress());
        assertEquals("Shore-power pump", MissionObjectiveFamilies.coldStart(sequence).focus().name());
        assertEquals("", MissionObjectiveFamilies.coldStart(sequence).blocker());
        sequence.progress().advance(0, true, true, true, false, true, false, false, false);
        assertTrue(MissionObjectiveFamilies.coldStart(sequence).blocker().contains("Company cover"));
        sequence.progress().advance(0, true, true, true, true, false, false, false, false);
        assertTrue(MissionObjectiveFamilies.coldStart(sequence).blocker().contains("technician"));
        sequence.progress().advance(0, true, true, true, true, true, true, false, false);
        assertTrue(MissionObjectiveFamilies.coldStart(sequence).blocker().contains("contested"));
        sequence.progress().advance(17.5f, true, true, true, true, true, false, false, false);
        var construction = MissionObjectiveFamilies.coldStart(sequence);
        assertEquals(-1f, construction.progress());
        assertTrue(construction.instruction().contains("Build Vulcan Turret"));
        assertEquals("", construction.blocker());
        sequence.progress().advance(0, true, true, true, true, true, true, false, false);
        var contestedConstruction = MissionObjectiveFamilies.coldStart(sequence);
        assertTrue(sequence.progress().contested());
        assertEquals("", contestedConstruction.blocker(), "construction is governed by tender completion, not the work-site presence gate");
        assertFalse(contestedConstruction.instruction().contains("paused"));
        assertTrue(contestedConstruction.instruction().contains("clear exterior ground"));
    }

    @Test void coldStartEscortRallyIsNotMisnamedAsTheWorkshop() {
        var reserve = Collections.nCopies(24, new EntitySpec("Road team", Faction.DEFENDER, UnitType.MILITIA, 0, 0));
        var sequence = new ColdStartSequence(2, 1, reserve, false, false, new StoryBeatLog());
        var item = MissionObjectiveFamilies.coldStart(sequence);
        assertEquals("Repair-carrier escort rally", item.focus().name());
        assertEquals(sequence.targetX(), item.focus().x());
        assertEquals(sequence.targetY(), item.focus().y());
        assertTrue(item.instruction().contains("workshop"));
        assertTrue(item.blocker().contains("unloading point"));
    }

    @Test void picketStrengthAndWithdrawalAreOptionalReportsWithoutCaptureMeters() {
        var post = new ColdStartPicket("south_haul", 5, 176, 38, 151, 96, 3);
        post.observe(10, 3, 0, true, false);
        var contact = MissionObjectiveFamilies.coldStartPicket(post.report());
        assertEquals("CONTACT REPORTED", contact.phase());
        assertEquals(-1, contact.progress());
        assertEquals("3/3 militia alive", contact.progressText());
        post.observe(20, 1, 0, true, false);
        var retreat = MissionObjectiveFamilies.coldStartPicket(post.report());
        assertEquals("FALLING BACK", retreat.phase());
        assertEquals(151, retreat.focus().x());
        assertEquals(96, retreat.focus().y());
        assertFalse(retreat.progressText().contains("overrun"));
        assertTrue(retreat.steps().startsWith("Optional"));
    }

    @Test void depotCaptureAndAnnouncedAttacksAreSeparateObligations() {
        var room = new PointOfInterest(PointOfInterest.Kind.DEPOT, 10, 10, 20, 20, 15, 15);
        var force = List.of(new EntitySpec("Road team", Faction.DEFENDER, UnitType.MILITIA, 0, 0));
        var sequence = new DepotHoldSequence(room, Collections.nCopies(4, force), new StoryBeatLog());
        sequence.progress().advance(4, true, true, false, 4);
        assertEquals(.5f, MissionObjectiveFamilies.depot(sequence).progress());
        assertEquals("", MissionObjectiveFamilies.depot(sequence).blocker());
        sequence.progress().advance(0, true, true, true, 4);
        assertTrue(MissionObjectiveFamilies.depot(sequence).blocker().contains("contested"));
        sequence.progress().advance(8, true, true, false, 4);
        var defense = MissionObjectiveFamilies.depot(sequence);
        assertEquals(-1f, defense.progress());
        assertTrue(defense.progressText().contains("announced road attack"));
        assertFalse(defense.progressText().contains("raiders remaining"));
    }

    @Test void legacyPointAndFootprintMarkersDoNotAcquireCircularGates() {
        var point = new MissionObjectiveSnapshot.Marker("p", "Place", 1, 2, "ACTIVE", MissionObjectiveSnapshot.State.ACTIVE);
        var footprint = new MissionObjectiveSnapshot.Marker("f", "Room", 1, 2, "ACTIVE", MissionObjectiveSnapshot.State.ACTIVE,
                List.of(new MissionObjectiveSnapshot.Cell(1, 2)));
        assertEquals(0f, point.radius());
        assertEquals(0f, footprint.radius());
        assertEquals(1, footprint.footprint().size());
    }

    @Test void untimedObligationHasNoSyntheticPercentageOrEnemyCensus() {
        var item = MissionObjectiveFamilies.generic(new EliminateFactionObjective(Faction.MARINE, Faction.DEFENDER), 1);
        assertEquals(-1f, item.progress());
        assertTrue(item.markers().isEmpty());
        assertEquals("Awaiting mission condition", item.progressText());
    }
}
