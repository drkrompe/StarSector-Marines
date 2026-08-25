package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ContactInitiative;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ForceBalance;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Motion;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Sector;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalScoringContactPictureTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(64, 40);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static Squad marineSquad(BattleSimulation sim, int size) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        for (int i = 0; i < size; i++) {
            long member = sim.spawn(new EntitySpec("m" + i, Faction.MARINE,
                    UnitType.MARINE, 10, 18 + i).squad(squadId));
            if (i == 0) squad.leaderId = member;
        }
        squad.assignedObjective = ObjectiveAssignment.escort(squadId, 50, 20);
        return squad;
    }

    @Test
    void objectiveAxisClassifiesFrontFlankAndRearDeterministically() {
        assertEquals(Sector.FRONT,
                TacticalScoring.classifySector(1f, 0f, 10f, 0f));
        assertEquals(Sector.LEFT_FLANK,
                TacticalScoring.classifySector(1f, 0f, 0f, -10f));
        assertEquals(Sector.RIGHT_FLANK,
                TacticalScoring.classifySector(1f, 0f, 0f, 10f));
        assertEquals(Sector.REAR,
                TacticalScoring.classifySector(1f, 0f, -10f, 0f));

        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        sim.spawn(new EntitySpec("front", Faction.DEFENDER,
                UnitType.MARINE, 20, 20));
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(Posture.ADVANCING, squad.contactPicture.posture());
        assertEquals(Sector.FRONT, squad.contactPicture.dominantSector());
        assertEquals(1, squad.contactPicture.contactCount());
    }

    @Test
    void motionRequiresConsecutiveDirectObservations() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long enemy = sim.spawn(new EntitySpec("moving", Faction.DEFENDER,
                UnitType.MARINE, 20, 20));

        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(Motion.UNKNOWN, squad.contactPicture.primaryMotion());

        sim.world().setCellPos(enemy, 21, 20);
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(Motion.WITHDRAWING, squad.contactPicture.primaryMotion());

        sim.world().setCellPos(enemy, 20, 20);
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(Motion.APPROACHING, squad.contactPicture.primaryMotion());

        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(15, y, false);
        }
        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(Motion.UNKNOWN, squad.contactPicture.primaryMotion(),
                "a retained but no-longer-direct track cannot expose motion");
    }

    @Test
    void confidenceWeightedHostilesCompareAgainstKnownSupport() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        for (int i = 0; i < 6; i++) {
            sim.spawn(new EntitySpec("d" + i, Faction.DEFENDER,
                    UnitType.MARINE, 18, 15 + i));
        }
        sim.advance(BattleSimulation.TICK_DT);

        SquadContactPicture picture = squad.contactPicture;
        assertEquals(6f, picture.hostileStrength(), 0.001f);
        assertEquals(4, picture.friendlyStrength());
        assertEquals(ForceBalance.UNFAVORABLE, picture.forceBalance());
        assertNotEquals(Doctrine.ADVANCE, picture.doctrine());
    }

    @Test
    void doctrineUsesPostureMustHoldAndReleaseHysteresis() {
        assertEquals(Doctrine.ADVANCE, TacticalScoring.selectDoctrine(
                Posture.ADVANCING, ForceBalance.FAVORABLE, Sector.FRONT,
                Motion.WITHDRAWING, false, Doctrine.ADVANCE, true));
        assertEquals(Doctrine.DISENGAGE, TacticalScoring.selectDoctrine(
                Posture.ADVANCING, ForceBalance.UNFAVORABLE, Sector.REAR,
                Motion.APPROACHING, false, Doctrine.ADVANCE, true));
        assertEquals(Doctrine.HOLD, TacticalScoring.selectDoctrine(
                Posture.DEFENDING, ForceBalance.UNFAVORABLE, Sector.REAR,
                Motion.APPROACHING, true, Doctrine.HOLD, true));
        assertEquals(Doctrine.HOLD, TacticalScoring.selectDoctrine(
                Posture.ADVANCING, ForceBalance.EVEN, Sector.FRONT,
                Motion.LATERAL, false, Doctrine.DISENGAGE, true),
                "an improved picture first steps disengage down to a held line");
        assertEquals(Doctrine.ADVANCE, TacticalScoring.selectDoctrine(
                Posture.ADVANCING, ForceBalance.FAVORABLE, Sector.FRONT,
                Motion.LATERAL, false, Doctrine.DISENGAGE, true),
                "a clearly favorable front releases the squad back to advance");
        assertEquals(Doctrine.HOLD, TacticalScoring.selectDoctrine(
                Posture.ADVANCING, ForceBalance.FAVORABLE, Sector.LEFT_FLANK,
                Motion.LATERAL, false, Doctrine.HOLD, true),
                "a held line does not oscillate on one marginally favorable tick");
        assertEquals(Doctrine.ADVANCE, TacticalScoring.selectDoctrine(
                Posture.ADVANCING, ForceBalance.FAVORABLE, Sector.LEFT_FLANK,
                Motion.UNKNOWN, false, Doctrine.HOLD, true, false),
                "lost contact cannot sustain a doctrine-only hold indefinitely");
    }

    @Test
    void contactInitiativeReceivesApproachButProsecutesPartialLateralLine() {
        assertEquals(ContactInitiative.RECEIVE,
                TacticalScoring.selectContactInitiative(Doctrine.HOLD,
                        Posture.ADVANCING, ForceBalance.FAVORABLE,
                        Motion.APPROACHING, false, true,
                        1, 12, 1, 3));
        assertEquals(ContactInitiative.RECEIVE,
                TacticalScoring.selectContactInitiative(Doctrine.HOLD,
                        Posture.ADVANCING, ForceBalance.FAVORABLE,
                        Motion.LATERAL, false, true,
                        6, 12, 2, 3));
        assertEquals(ContactInitiative.PROSECUTE,
                TacticalScoring.selectContactInitiative(Doctrine.HOLD,
                        Posture.ADVANCING, ForceBalance.FAVORABLE,
                        Motion.LATERAL, false, true,
                        1, 12, 1, 3));
        assertEquals(ContactInitiative.NONE,
                TacticalScoring.selectContactInitiative(Doctrine.HOLD,
                        Posture.ADVANCING, ForceBalance.FAVORABLE,
                        Motion.LATERAL, false, false,
                        1, 12, 1, 3),
                "remembered intel guides awareness but cannot start a close maneuver");
    }

    @Test
    void publishedPictureCountsMembersAndTeamsOnPrimaryFiringLine() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        for (int i = 0; i < 12; i++) {
            long member = sim.spawn(new EntitySpec("line-m" + i, Faction.MARINE,
                    UnitType.MARINE, 8 + i % 4, 17 + i / 4)
                    .squad(squadId).fireTeam(i / 4).visionRange(40f)
                    .attackRange(i == 0 ? 30f : 6f));
            if (i == 0) squad.leaderId = member;
        }
        long enemy = sim.spawn(new EntitySpec("lateral", Faction.DEFENDER,
                UnitType.MARINE, 10, 32).visionRange(40f));
        squad.assignedObjective = ObjectiveAssignment.escort(squadId, 55, 18);

        sim.advance(BattleSimulation.TICK_DT);
        sim.world().setCellPos(enemy, 11, 32);
        sim.advance(BattleSimulation.TICK_DT);

        SquadContactPicture picture = squad.contactPicture;
        assertEquals(Motion.LATERAL, picture.primaryMotion());
        assertEquals(1, picture.primaryEngageableMembers());
        assertEquals(1, picture.primaryEngageableFireTeams());
        assertEquals(3, picture.liveFireTeams());
        assertEquals(ContactInitiative.PROSECUTE, picture.contactInitiative());
        assertFalse(TacticalScoring.shouldHardHoldAdvance(squad, picture,
                sim.getSimTickIndex()));
    }

    @Test
    void hiddenUnrememberedHostileNeverEntersPicture() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        sim.spawn(new EntitySpec("hidden", Faction.DEFENDER,
                UnitType.MARINE, 25, 20));
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(17, y, false);
        }

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0, squad.contactPicture.contactCount());
        assertEquals(ForceBalance.NONE, squad.contactPicture.forceBalance());
        assertEquals(Doctrine.ADVANCE, squad.contactPicture.doctrine());
    }

    @Test
    void lostContactHoldExpiresWhileBeliefRemainsAvailable() {
        BattleSimulation sim = openSim();
        Squad squad = marineSquad(sim, 4);
        long enemy = sim.spawn(new EntitySpec("flank", Faction.DEFENDER,
                UnitType.MARINE, 10, 30));
        sim.world().setMaxHp(enemy, 1_000f);
        sim.world().setHp(enemy, 1_000f);

        sim.advance(BattleSimulation.TICK_DT);
        assertEquals(Doctrine.HOLD, squad.contactPicture.doctrine());
        assertTrue(TacticalScoring.contactHoldIsFresh(squad,
                squad.contactPicture, sim.getSimTickIndex()));

        for (int x = 0; x < sim.getGrid().getWidth(); x++) {
            sim.getGrid().setWalkable(x, 25, false);
        }
        for (int i = 0; i <= TacticalScoring.HOLD_AFTER_LOS_TICKS; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(squad.hasBelievedContacts(),
                "the long-lived belief still guides awareness after hard HOLD releases");
        assertEquals(0, squad.contactPicture.directContactCount());
        assertEquals(Doctrine.ADVANCE, squad.contactPicture.doctrine());
        assertFalse(TacticalScoring.contactHoldIsFresh(squad,
                squad.contactPicture, sim.getSimTickIndex()));
    }

    @Test
    void dispersedFireteamKeepsItsLocalContactInTheSquadPicture() {
        NavigationGrid grid = new NavigationGrid(100, 80);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        for (int i = 0; i < 7; i++) {
            sim.spawn(new EntitySpec("rear" + i, Faction.MARINE,
                    UnitType.MARINE, 5, 10 + i).squad(squadId)
                    .fireTeam(i / Squad.FIRE_TEAM_SIZE));
        }
        long forward = sim.spawn(new EntitySpec("forward", Faction.MARINE,
                UnitType.MARINE, 50, 50).squad(squadId).fireTeam(2));
        long enemy = sim.spawn(new EntitySpec("contact", Faction.DEFENDER,
                UnitType.MARINE, 60, 50));
        sim.world().setAttackRange(forward, 12f);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(TacticalScoring.cellDistance(squad.centroidX, squad.centroidY,
                60.5f, 50.5f) > TacticalScoring.CONTACT_PICTURE_RADIUS,
                "test prerequisite: the squad centroid is not local to contact");
        assertEquals(1, squad.contactPicture.contactCount());
        assertEquals(1, squad.contactPicture.directContactCount());
        assertEquals(enemy, squad.contactPicture.primaryContactId());
        assertEquals(1, squad.contactPicture.friendlyStrength(),
                "only allies local to this contact count as immediate support");
        assertEquals(ForceBalance.EVEN, squad.contactPicture.forceBalance());
        assertEquals(Doctrine.HOLD, squad.contactPicture.doctrine());
        assertEquals(8, squad.contactPicture.liveMembers());
        assertEquals(3, squad.contactPicture.liveFireTeams());
        assertEquals(1, squad.contactPicture.primaryEngageableMembers());
        assertEquals(1, squad.contactPicture.primaryEngageableFireTeams());
    }
}
