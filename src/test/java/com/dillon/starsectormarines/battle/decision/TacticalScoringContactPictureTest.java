package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
}
