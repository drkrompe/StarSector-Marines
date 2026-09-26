package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a side's remembered dead make ground cost to cross. The memory's own
 * arithmetic is pinned next door; this is about the costing published from it -
 * where the penalty lands, how far it can go, and that a side which has lost
 * nobody publishes nothing at all.
 */
public class CasualtyRouteCostTest {

    private static final int BLOCK = 8;
    private static final int W = 64;
    private static final int H = 64;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static CasualtyMemory memory(BattleSimulation sim) {
        return new CasualtyMemory(sim.getRoster(), BLOCK, W, H);
    }

    private static long spawn(BattleSimulation sim, Faction faction, int x, int y) {
        return sim.spawn(new EntitySpec("u" + x + "-" + y, faction,
                UnitType.MARINE, x, y));
    }

    private static void kill(CasualtyMemory memory, BattleSimulation sim, long unit) {
        memory.onDeath(new DeathEvent(unit, sim.world().x(unit),
                sim.world().y(unit), 0));
    }

    private static float at(RouteCostField field, int x, int y) {
        return field.costAt(y * W + x);
    }

    @Test
    public void aSideThatHasLostNobodyPublishesNoCosting() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        memory.advance(0);
        assertNull(memory.routeCost(Faction.MARINE),
                "nothing to route around, so nothing for the pathfinder to read");
    }

    @Test
    public void theBlockSomebodyDiedInCostsMoreAndTheRestIsUntouched() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        kill(memory, sim, spawn(sim, Faction.MARINE, 20, 20));
        memory.advance(0);

        RouteCostField cost = memory.routeCost(Faction.MARINE);
        assertNotNull(cost);
        assertTrue(at(cost, 20, 20) > 1f, "dearer where they were killed");
        // The block is the resolution the side actually knows this at, so the
        // whole of it is dear and the cell next door outside it is not.
        assertEquals(at(cost, 20, 20), at(cost, 16, 23), 1e-6f,
                "the whole block, since that is what is known");
        assertEquals(1f, at(cost, 40, 40), 1e-6f, "and nowhere else");
    }

    @Test
    public void groundBoughtWithASquadStaysCrossable() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        for (int i = 0; i < 30; i++) {
            kill(memory, sim, spawn(sim, Faction.MARINE, 20 + i % 4, 20));
        }
        memory.advance(0);

        float worst = at(memory.routeCost(Faction.MARINE), 20, 20);
        assertTrue(worst <= 1f + CasualtyMemory.MAX_ROUTE_PENALTY + 1e-6f,
                "a slaughter is discouraging, never impassable: " + worst);
        // Half the headroom at one squad, so a front that has eaten dozens is
        // still ordered rather than flattened into one wall of maximum cost.
        assertTrue(CasualtyMemory.multiplierFor(6f)
                        < CasualtyMemory.multiplierFor(60f),
                "and more dead is still dearer than fewer");
        assertEquals(1f + CasualtyMemory.MAX_ROUTE_PENALTY / 2f,
                CasualtyMemory.multiplierFor(CasualtyMemory.HALF_PENALTY_LOSSES),
                1e-6f);
    }

    @Test
    public void eachSideReadsOnlyItsOwnDeadAndUnderItsOwnRevision() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        kill(memory, sim, spawn(sim, Faction.MARINE, 20, 20));
        kill(memory, sim, spawn(sim, Faction.DEFENDER, 44, 44));
        memory.advance(0);

        RouteCostField marine = memory.routeCost(Faction.MARINE);
        RouteCostField defender = memory.routeCost(Faction.DEFENDER);
        assertEquals(1f, at(marine, 44, 44), 1e-6f,
                "the marines are not routing around the enemy's dead");
        assertEquals(1f, at(defender, 20, 20), 1e-6f);
        assertTrue(marine.revision() != defender.revision(),
                "two costings for one map must never share a cache identity");
    }

    @Test
    public void aRepublishedCostingCarriesANewRevision() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        kill(memory, sim, spawn(sim, Faction.MARINE, 20, 20));
        memory.advance(0);
        long first = memory.routeCost(Faction.MARINE).revision();

        memory.advance(CasualtyMemory.PUBLISH_INTERVAL_TICKS);
        assertTrue(memory.routeCost(Faction.MARINE).revision() > first,
                "or a retained reverse tree would keep serving the old costing");
    }

    @Test
    public void publicationWaitsForItsCadenceRatherThanTheNextDeath() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        kill(memory, sim, spawn(sim, Faction.MARINE, 20, 20));
        memory.advance(0);
        long published = memory.routeCost(Faction.MARINE).revision();

        kill(memory, sim, spawn(sim, Faction.MARINE, 21, 21));
        memory.advance(1);
        assertEquals(published, memory.routeCost(Faction.MARINE).revision(),
                "a costing every mover reads is rebuilt on a clock, not per death");
    }

    @Test
    public void publicationKeepsOnlyBlockCostsOnTheConquestMap() {
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(560, 336), null);
        CasualtyMemory memory = new CasualtyMemory(roster, 8, 560, 336);
        long unit = roster.spawn(new EntitySpec("lost", Faction.MARINE, UnitType.MARINE, 20, 20));
        memory.onDeath(new DeathEvent(unit, 20, 20, 0));
        memory.advance(0);

        RouteCostField cost = memory.routeCost(Faction.MARINE);
        assertNotNull(cost);
        assertEquals(188_160, cost.size());
        boolean compact = Boolean.parseBoolean(System.getProperty(
                CasualtyMemory.COMPACT_ROUTE_COST_PROPERTY, "true"));
        assertEquals(compact ? 2_940 : 188_160, cost.storedValueCount());
    }

    @Test
    public void heldPublicationsSurviveDeathsDecayAndReplacementWithoutChangingOneBit() {
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(17, 11), null);
        CasualtyMemory memory = new CasualtyMemory(roster, 3, 17, 11);
        long unit = roster.spawn(new EntitySpec("lost", Faction.MARINE, UnitType.MARINE, 16, 10));
        memory.onDeath(new DeathEvent(unit, 16, 10, 0));
        memory.advance(0);
        RouteCostField held = memory.routeCost(Faction.MARINE);
        int[] heldBits = new int[held.size()];
        for (int i = 0; i < heldBits.length; i++) heldBits[i] = Float.floatToRawIntBits(held.costAt(i));

        memory.onDeath(new DeathEvent(unit, 16, 10, 0));
        memory.decay((int) CasualtyMemory.HALF_LIFE_TICKS);
        memory.advance(1);
        assertSame(held, memory.routeCost(Faction.MARINE), "no premature publication");
        memory.onDeath(new DeathEvent(unit, 0, 0, 0));
        memory.advance(CasualtyMemory.PUBLISH_INTERVAL_TICKS);
        RouteCostField replacement = memory.routeCost(Faction.MARINE);
        assertTrue(replacement.revision() > held.revision());
        assertTrue(replacement.costAt(0) > held.costAt(0));
        assertTrue(replacement.costAt(held.size() - 1) < held.costAt(held.size() - 1));
        float[] weights = memory.copyFor(Faction.MARINE);
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 17; x++) {
                int index = y * 17 + x;
                assertEquals(heldBits[index], Float.floatToRawIntBits(held.costAt(index)));
                float expected = CasualtyMemory.multiplierFor(weights[(y / 3) * memory.blockWidth() + x / 3]);
                assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(replacement.costAt(index)));
            }
        }

        memory.decay((int) CasualtyMemory.HALF_LIFE_TICKS * 12);
        memory.advance(2 * CasualtyMemory.PUBLISH_INTERVAL_TICKS);
        assertNull(memory.routeCost(Faction.MARINE), "negligible memory returns to no-cost routing");
        for (int i = 0; i < heldBits.length; i++) {
            assertEquals(heldBits[i], Float.floatToRawIntBits(held.costAt(i)));
        }
    }
}
