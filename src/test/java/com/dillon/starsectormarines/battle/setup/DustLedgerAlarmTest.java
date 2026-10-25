package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.DustLedgerChecklist;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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

/** What the adapter does when its rules decide: the broker at setup, and the alarm's effects on the battle. */
class DustLedgerAlarmTest {
    private static final int W = 40, H = 14;
    private static final Area GROUNDS = Area.rect(20, 4, 24, 8);
    private static final Sites SITES = new Sites(Area.rect(0, 10, 3, 13), new Cell(1, 11), Area.rect(21, 5, 23, 7),
            new Cell(22, 6), new Cell(23, 5), new Cell(30, 12), Sites.ring(GROUNDS), GROUNDS, List.of(new Cell(10, 6)),
            Area.rect(6, 0, 8, 2), List.of(new Cell(7, 1), new Cell(8, 1)), Area.rect(10, 0, 12, 2),
            Area.rect(10, 10, 12, 12), List.of(new Cell(30, 2), new Cell(31, 2)), new Cell(25, 6));

    @Test void aSightingInsideTheRingRaisesTheAlarmAndReleasesWhatTheQuietPhaseEarned() {
        BattleSimulation sim = arena();
        RaidObjective raid = new RaidObjective("ledger", "Ledger room", 22, 6, 0, 1, 11);
        sim.addObjective(raid);
        DustLedgerSequence ledger = sim.installDustLedger(SITES, raid,
                List.of(reserve(Faction.DEFENDER, 7, 1), reserve(Faction.DEFENDER, 8, 1)),
                List.of(reserve(Faction.ALLY, 30, 2), reserve(Faction.ALLY, 31, 2)));
        assertEquals(Faction.CIVILIAN, sim.identity().faction(ledger.broker()));
        assertEquals(23, sim.world().cellX(ledger.broker()));

        long sentry = sim.spawn(new EntitySpec("sentry", Faction.DEFENDER, UnitType.MARINE, 22, 6));
        long porter = sim.spawn(new EntitySpec("porter", Faction.MARINE, UnitType.MARINE, 1, 11));
        ledger.advance(sim);
        assertTrue(ledger.crateCarried());
        sim.world().setPos(porter, 11.5f, 11.5f);
        ledger.advance(sim);
        assertTrue(ledger.crateDelivered());
        assertEquals(DustLedgerProgress.Side.PAID, ledger.progress().coupling());
        sim.getUnitIndex().rebuild(sim.getRoster());
        assertTrue(sim.getUnitIndex().withheld(sentry), "quiet: the company holds its fire");
        assertTrue(sim.getUnitIndex().withheld(porter));

        sim.world().setPos(porter, 17.5f, 6.5f);
        ledger.advance(sim);
        assertEquals(DustLedgerProgress.Alarm.SIGHTED, ledger.progress().alarm());
        assertTrue(raid.defenderAlarm().active(), "the garrison's raid command is roused");
        assertEquals(DustLedgerProgress.Broker.FLEEING, ledger.progress().broker());
        assertEquals(2, count(sim, Faction.ALLY), "the coupling paid: the salvage team comes");
        assertEquals(1 + 2, count(sim, Faction.DEFENDER), "no nod: the cantina drinkers join");
        sim.getUnitIndex().rebuild(sim.getRoster());
        assertFalse(sim.getUnitIndex().withheld(sentry));
        assertFalse(sim.getUnitIndex().withheld(porter));
        assertEquals(0, ledger.pendingMembers(Faction.DEFENDER));
        var rows = DustLedgerChecklist.items(ledger, sim);
        assertEquals(List.of("dust-ledger-1-clock", "dust-ledger-2-ledger", "dust-ledger-3-broker",
                "dust-ledger-4-coupling", "dust-ledger-5-warden"), rows.stream().map(r -> r.id()).toList());
        assertEquals(MissionObjectiveSnapshot.State.COMPLETE, rows.get(3).state(), "the coupling row reads paid");
        assertEquals(MissionObjectiveSnapshot.State.FAILED, rows.get(4).state(), "the nod was not taken in time");
    }

    private static int count(BattleSimulation sim, Faction faction) {
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) if (sim.identity().faction(sim.liveUnitAt(i)) == faction) n++;
        return n;
    }

    private static EntitySpec reserve(Faction faction, int x, int y) {
        return new EntitySpec(faction.name(), faction, UnitType.MARINE, x, y);
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }
}
