package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Sites;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sable's road reserve: it waits for a company unit in the ring, then musters on a short clock, a Hound tied to a squad. */
class DustLedgerReinforcementsTest {
    private static final int W = 60, H = 14;
    private static final Area GROUNDS = Area.rect(20, 4, 24, 8);
    private static final List<Cell> MUSTER = muster();

    private static List<Cell> muster() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 1; y <= 4; y++) for (int x = 50; x <= 52; x++) cells.add(new Cell(x, y));
        cells.add(new Cell(55, 2));
        return cells;
    }

    private static final Sites SITES = new Sites(Area.rect(0, 10, 3, 13), new Cell(1, 11), Area.rect(21, 5, 23, 7),
            new Cell(22, 6), new Cell(23, 5), new Cell(30, 12), Sites.ring(GROUNDS), GROUNDS, List.of(new Cell(10, 6)),
            Area.rect(6, 0, 8, 2), List.of(new Cell(7, 1), new Cell(8, 1)), Area.rect(10, 0, 12, 2),
            Area.rect(10, 10, 12, 12), List.of(new Cell(30, 2), new Cell(31, 2)), new Cell(25, 6), Area.rect(5, 4, 6, 5),
            MUSTER);

    @Test void theReserveWaitsForACompanyUnitInTheRingThenMustersOnAShortClock() {
        BattleSimulation sim = arena();
        long linesman = sim.spawn(new EntitySpec("linesman", Faction.MARINE, UnitType.MARINE, 5, 4));
        DustLedgerReinforcements reserve = sim.getDustLedger().reinforcements();
        step(sim, 2f);
        assertFalse(reserve.called(), "a company on the apron does not call the reserve");
        assertEquals(0, sim.getDustLedger().pendingMembers(Faction.DEFENDER));

        sim.world().setPos(linesman, 17.5f, 6.5f);
        step(sim, 1f);
        assertTrue(reserve.called(), "a company unit inside the ring calls it");
        assertEquals(13, sim.getDustLedger().pendingMembers(Faction.DEFENDER), "two squads of six and the Hound are on the way");
        step(sim, DustLedgerReinforcements.DELAY_SECONDS - 3f);
        assertFalse(reserve.arrived(), "not before the clock runs out");
        step(sim, 4f);
        assertTrue(reserve.arrived());
        assertEquals(0, sim.getDustLedger().pendingMembers(Faction.DEFENDER));
        assertEquals(12, sim.squadMemberCount(reserve.squad(0)) + sim.squadMemberCount(reserve.squad(1)));
        assertEquals(Faction.DEFENDER, sim.getSquad(reserve.squad(0)).faction);
    }

    @Test void theHoundCarriesLightMachineGunsAndNoPodAndFollowsOneSquad() {
        BattleSimulation sim = arena();
        sim.spawn(new EntitySpec("linesman", Faction.MARINE, UnitType.MARINE, 21, 6));
        DustLedgerReinforcements reserve = sim.getDustLedger().reinforcements();
        step(sim, DustLedgerReinforcements.DELAY_SECONDS + 2f);
        assertTrue(reserve.arrived());
        long hound = reserve.hound();
        var loadout = sim.world().mechLoadout(hound);
        assertEquals(MechVariant.HOUND, loadout.variant);
        assertEquals(MechWeaponComponent.LIGHT_MACHINE_GUN, loadout.mount(MechMountSlot.ARMS).component, "the Hound's nose gun");
        assertNull(loadout.mount(MechMountSlot.LEFT_SHOULDER), "no rocket pod");
        assertNull(loadout.mount(MechMountSlot.RIGHT_SHOULDER), "no rocket pod");
        assertEquals(Faction.DEFENDER, sim.identity().faction(hound));
        assertEquals(reserve.squad(1), sim.getMechAttachmentService().anchorOf(reserve.houndSquad()),
                "the Hound sticks to the second squad");
    }

    private static void step(BattleSimulation sim, float seconds) {
        for (int i = 0, n = Math.round(seconds / BattleSimulation.TICK_DT); i < n; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static BattleSimulation arena() {
        BattleSimulation sim = flat();
        RaidObjective raid = new RaidObjective("ledger", "Ledger room", 22, 6, 0, 1, 11);
        sim.addObjective(raid);
        DustLedgerSequence ledger = sim.installDustLedger(SITES, raid, List.of(), List.of());
        List<List<EntitySpec>> squads = new ArrayList<>();
        for (int squad = 0; squad < DustLedgerReinforcements.SQUADS; squad++) {
            List<EntitySpec> members = new ArrayList<>();
            for (int i = 0; i < DustLedgerReinforcements.SQUAD_SIZE; i++)
                members.add(new EntitySpec("reserve-" + squad + "-" + i, Faction.DEFENDER, UnitType.MILITIA, 0, 0));
            squads.add(members);
        }
        ledger.roadReserve(squads);
        return sim;
    }

    private static BattleSimulation flat() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }
}
