package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.DustLedgerChecklist;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.command.objective.SideObjectiveRows;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Sites;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the adapter does when its rules decide: the broker at setup, and the alarm's effects on the battle. Each step
 * ticks the side objectives before the sequence, as the battle does.
 */
class DustLedgerAlarmTest {
    private static final int W = 40, H = 14;
    private static final Area GROUNDS = Area.rect(20, 4, 24, 8);
    private static final Sites SITES = new Sites(Area.rect(0, 10, 3, 13), Area.rect(21, 5, 23, 7), new Cell(22, 6),
            new Cell(23, 5), new Cell(30, 12), Sites.ring(GROUNDS), List.of());

    @Test void aSightingInsideTheRingRaisesTheAlarmAndSendsTheBrokerRunning() {
        BattleSimulation sim = arena();
        RaidObjective raid = new RaidObjective("ledger", "Ledger room", 22, 6, 0, 1, 11);
        sim.addObjective(raid);
        DustLedgerSequence ledger = sim.installDustLedger(SITES, raid);
        assertEquals(Faction.CIVILIAN, sim.identity().faction(ledger.broker()));
        assertEquals(23, sim.world().cellX(ledger.broker()));

        long sentry = sim.spawn(new EntitySpec("sentry", Faction.DEFENDER, UnitType.MARINE, 22, 6));
        long porter = sim.spawn(new EntitySpec("porter", Faction.MARINE, UnitType.MARINE, 1, 11));
        step(sim, ledger);
        sim.getUnitIndex().rebuild(sim.getRoster());
        assertTrue(sim.getUnitIndex().withheld(sentry), "quiet: the company holds its fire");
        assertTrue(sim.getUnitIndex().withheld(porter));

        sim.world().setPos(porter, 17.5f, 6.5f);
        step(sim, ledger);
        assertEquals(DustLedgerProgress.Alarm.SIGHTED, ledger.progress().alarm());
        assertTrue(raid.defenderAlarm().active(), "the garrison's raid command is roused");
        assertEquals(DustLedgerProgress.Broker.FLEEING, ledger.progress().broker());
        assertEquals(0, count(sim, Faction.ALLY), "the alarm brings no allies");
        assertEquals(1, count(sim, Faction.DEFENDER), "the alarm brings no reserve of its own");
        sim.getUnitIndex().rebuild(sim.getRoster());
        assertFalse(sim.getUnitIndex().withheld(sentry));
        assertFalse(sim.getUnitIndex().withheld(porter));
        assertEquals(0, ledger.pendingMembers(Faction.DEFENDER));
        assertEquals(List.of("dust-ledger-1-clock", "dust-ledger-2-ledger"),
                DustLedgerChecklist.items(ledger, sim).stream().map(r -> r.id()).toList());
        step(sim, ledger);
        var rows = SideObjectiveRows.items(sim.getSideObjectives(), SideField.of(sim));
        assertEquals(List.of("side-dust-ledger-broker"), rows.stream().map(r -> r.id()).toList(), "the broker is the one bonus");
        assertTrue(rows.get(0).optional());
        assertEquals(MissionObjectiveSnapshot.State.ACTIVE, rows.get(0).state(), "the broker is still to be caught");
    }


    private static void step(BattleSimulation sim, DustLedgerSequence ledger) {
        sim.getSideObjectives().tick(sim);
        ledger.advance(sim);
    }

    private static int count(BattleSimulation sim, Faction faction) {
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) if (sim.identity().faction(sim.liveUnitAt(i)) == faction) n++;
        return n;
    }


    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }
}
