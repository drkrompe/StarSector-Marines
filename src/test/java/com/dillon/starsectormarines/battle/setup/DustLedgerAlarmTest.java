package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.DustLedgerChecklist;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.command.objective.SideObjectiveRows;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Sites;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
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
    private static final Sites SITES = new Sites(Area.rect(0, 10, 3, 13), new Cell(1, 11), Area.rect(21, 5, 23, 7),
            new Cell(22, 6), new Cell(23, 5), new Cell(30, 12), Sites.ring(GROUNDS), GROUNDS, List.of(new Cell(10, 6)),
            Area.rect(6, 0, 8, 2), List.of(new Cell(7, 1), new Cell(8, 1)), Area.rect(10, 0, 12, 2),
            Area.rect(10, 10, 12, 12), List.of(new Cell(30, 2), new Cell(31, 2)), new Cell(25, 6), Area.rect(5, 4, 6, 5),
            List.of());

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
        var coupling = sim.getSideObjectives().job(DustLedgerSideObjectives.COUPLING);
        step(sim, ledger);
        assertEquals(SideObjective.State.WORKING, coupling.state(), "the porter has the crate");
        sim.world().setPos(porter, 11.5f, 11.5f);
        step(sim, ledger);
        assertEquals(SideObjective.State.DONE, coupling.state());
        assertTrue(ledger.salvageTeamJoins());
        sim.getUnitIndex().rebuild(sim.getRoster());
        assertTrue(sim.getUnitIndex().withheld(sentry), "quiet: the company holds its fire");
        assertTrue(sim.getUnitIndex().withheld(porter));

        sim.world().setPos(porter, 17.5f, 6.5f);
        step(sim, ledger);
        assertEquals(DustLedgerProgress.Alarm.SIGHTED, ledger.progress().alarm());
        assertTrue(raid.defenderAlarm().active(), "the garrison's raid command is roused");
        assertEquals(DustLedgerProgress.Broker.FLEEING, ledger.progress().broker());
        assertEquals(2, count(sim, Faction.ALLY), "the coupling paid: the salvage team comes");
        assertEquals(1 + 2, count(sim, Faction.DEFENDER), "no nod: the cantina drinkers join");
        sim.getUnitIndex().rebuild(sim.getRoster());
        assertFalse(sim.getUnitIndex().withheld(sentry));
        assertFalse(sim.getUnitIndex().withheld(porter));
        assertEquals(0, ledger.pendingMembers(Faction.DEFENDER));
        assertEquals(List.of("dust-ledger-1-clock", "dust-ledger-2-ledger"),
                DustLedgerChecklist.items(ledger, sim).stream().map(r -> r.id()).toList());
        step(sim, ledger);
        var rows = SideObjectiveRows.items(sim.getSideObjectives(), SideField.of(sim));
        assertEquals(List.of("side-dust-ledger-coupling", "side-dust-ledger-warden", "side-dust-ledger-feed",
                "side-dust-ledger-broker"), rows.stream().map(r -> r.id()).toList());
        assertTrue(rows.stream().allMatch(MissionObjectiveSnapshot.Item::optional));
        assertEquals(MissionObjectiveSnapshot.State.COMPLETE, rows.get(0).state(), "the coupling row reads done");
        assertEquals(MissionObjectiveSnapshot.State.FAILED, rows.get(1).state(), "the nod was not taken in time");
        assertEquals(MissionObjectiveSnapshot.State.ACTIVE, rows.get(3).state(), "the broker is still to be caught");
    }

    @Test void aFeedCutBeforeTheAlarmLeavesTheNestsDeadWhenItComes() {
        BattleSimulation sim = arena();
        RaidObjective raid = new RaidObjective("ledger", "Ledger room", 22, 6, 0, 1, 11);
        sim.addObjective(raid);
        DustLedgerSequence ledger = sim.installDustLedger(SITES, raid, List.of(), List.of());
        long nest = sim.spawn(MapTurret.create("nest", Faction.DEFENDER, TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 26, 10));
        long other = sim.spawn(MapTurret.create("other", Faction.DEFENDER, TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 26, 2));
        ledger.defences(sim, List.of(nest), List.of());
        long linesman = sim.spawn(new EntitySpec("linesman", Faction.MARINE, UnitType.MARINE, 5, 4));
        int ticks = (int) Math.ceil(DustLedgerSideObjectives.FEED_SECONDS / BattleSimulation.TICK_DT) + 1;
        for (int i = 0; i < ticks; i++) step(sim, ledger);
        assertFalse(ledger.feedCut(), "standing on the junction box does nothing until the player chooses");
        sim.getSideObjectives().choose(DustLedgerSideObjectives.FEED, "cut");
        for (int i = 0; i < ticks; i++) step(sim, ledger);
        assertTrue(ledger.feedCut());
        assertTrue(sim.turretState().unpowered(nest));
        assertFalse(sim.turretState().unpowered(other), "only the tap's nests lose power");

        sim.spawn(new EntitySpec("sentry", Faction.DEFENDER, UnitType.MARINE, 22, 6));
        sim.world().setPos(linesman, 17.5f, 6.5f);
        step(sim, ledger);
        assertFalse(ledger.progress().quiet());
        var beats = sim.getStoryBeats().stream().map(e -> e.id()).toList();
        assertTrue(beats.containsAll(List.of("corvus.dust_ledger.feed_approach", "corvus.dust_ledger.feed_cut",
                "corvus.dust_ledger.alarm_sighted", "corvus.dust_ledger.nests_dark")), beats.toString());
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

    private static EntitySpec reserve(Faction faction, int x, int y) {
        return new EntitySpec(faction.name(), faction, UnitType.MARINE, x, y);
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }
}
