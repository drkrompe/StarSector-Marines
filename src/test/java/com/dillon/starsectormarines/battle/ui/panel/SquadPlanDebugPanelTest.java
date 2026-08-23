package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.FiringSystem;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ForceBalance;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Motion;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Sector;
import com.dillon.starsectormarines.battle.ui.highlight.CellHighlight;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadPlanDebugPanelTest {

    @Test
    void summariesExposeThePublishedContactDecision() {
        SquadContactPicture picture = picture(1f, 0f, Doctrine.DISENGAGE);

        assertEquals("Doctrine DISENGAGE   Posture ADVANCING   Odds UNFAVORABLE",
                SquadPlanDebugPanel.doctrineSummary(picture));
        assertEquals("Threat LEFT_FLANK   Motion APPROACHING   Seen D2/T3",
                SquadPlanDebugPanel.threatSummary(picture));
        assertEquals("Force H5.50/F3   Axis +1.00,+0.00",
                SquadPlanDebugPanel.forceSummary(picture));
        assertEquals("Primary Raider @18,12   Confidence 0.75",
                SquadPlanDebugPanel.primarySummary(picture, "Raider"));
        assertEquals("Primary —", SquadPlanDebugPanel.primarySummary(
                SquadContactPicture.NONE, "—"));
    }

    @Test
    void tacticalAxisIsBoundedNormalizedAndDoctrineColored() {
        Squad squad = new Squad(7, Faction.MARINE);
        squad.centroidX = 10.5f;
        squad.centroidY = 10.5f;
        squad.contactPicture = picture(2f, 0f, Doctrine.HOLD);

        List<CellHighlight> cells = SquadPlanDebugPanel.doctrineAxisCells(squad);

        assertEquals(SquadPlanDebugPanel.DOCTRINE_AXIS_TRACE_CELLS, cells.size());
        for (int i = 0; i < cells.size(); i++) {
            CellHighlight cell = cells.get(i);
            assertEquals(11 + i, cell.cellX);
            assertEquals(10, cell.cellY);
            assertEquals(HighlightOverlay.COLOR_DOCTRINE_HOLD, cell.color);
        }
    }

    @Test
    void selectedSquadShowsWhetherDoctrineHoldCanStillStopMovement() {
        BattleSimulation sim = openSim();
        Squad squad = new Squad(9, Faction.MARINE);
        squad.contactPicture = picture(1f, 0f, Doctrine.HOLD);

        assertEquals("Hold stop ACTIVE   Evidence 0t/30t",
                SquadPlanDebugPanel.holdReactionSummary(squad, sim));
    }

    @Test
    void tacticalAxisIsAbsentWhenThePictureHasNoDirection() {
        Squad squad = new Squad(8, Faction.MARINE);
        squad.contactPicture = SquadContactPicture.NONE;

        assertTrue(SquadPlanDebugPanel.doctrineAxisCells(squad).isEmpty());
    }

    @Test
    void selectedFireSummaryExposesRegistrationCooldownAndLastGate() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("Marine", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        long enemy = sim.spawn(new EntitySpec("Raider", Faction.DEFENDER,
                UnitType.MARINE, 8, 5));
        sim.world().setAttackRange(member, 10f);
        sim.world().setCooldownTimer(member, 0.5f);
        sim.combat().setFireIntent(member, enemy, FireStance.STANCED, false);
        new FiringSystem(sim.getGrid(), sim.getRoster()).tick(sim);

        assertEquals("Fire Ready 0   Reg 1   CD 1   Last REGISTERING 0t",
                SquadPlanDebugPanel.fireSummary(squad, sim));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(20, 12);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static SquadContactPicture picture(float axisX, float axisY,
                                               Doctrine doctrine) {
        return new SquadContactPicture(42, Posture.ADVANCING, axisX, axisY,
                3, 2, 5.5f, 3, ForceBalance.UNFAVORABLE,
                Sector.LEFT_FLANK, Motion.APPROACHING, 99L,
                18, 12, 0.75f, doctrine);
    }
}
