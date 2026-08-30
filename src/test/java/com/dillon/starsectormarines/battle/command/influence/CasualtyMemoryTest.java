package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A side's memory of its own dead: who it counts, and how long it lasts. */
public class CasualtyMemoryTest {

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

    private static long spawn(BattleSimulation sim, Faction faction,
                              UnitType type, int x, int y) {
        return sim.spawn(new EntitySpec("u" + x + "-" + y + "-" + type, faction, type, x, y));
    }

    private static void kill(CasualtyMemory memory, BattleSimulation sim, long unit) {
        memory.onDeath(new DeathEvent(unit, sim.world().x(unit), sim.world().y(unit), 0));
    }

    @Test
    public void aLossIsRememberedAgainstTheSideThatSufferedIt() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        kill(memory, sim, spawn(sim, Faction.MARINE, UnitType.MARINE, 20, 20));

        assertTrue(memory.copyFor(Faction.MARINE)[(20 / BLOCK) * (W / BLOCK) + 20 / BLOCK] > 0f,
                "the side that lost somebody remembers it");
        assertEquals(0f, memory.copyFor(Faction.DEFENDER)[(20 / BLOCK) * (W / BLOCK) + 20 / BLOCK],
                1e-6f,
                "and the other side learns nothing — this is own-force memory, "
                        + "not intelligence about the enemy");
    }

    @Test
    public void nonCombatantLossesDoNotMakeGroundLookLethal() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        kill(memory, sim, spawn(sim, Faction.CIVILIAN, UnitType.CIVILIAN, 20, 20));

        // A civilian caught in the open says something about the map and
        // nothing about whether a fire team can cross it; counting them would
        // make an evacuation corridor read as a killing ground.
        assertEquals(0f, memory.copyFor(Faction.CIVILIAN)[(20 / BLOCK) * (W / BLOCK) + 20 / BLOCK],
                1e-6f);
    }

    @Test
    public void memoryHalvesOverItsHalfLifeAndEventuallyClears() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        int idx = (20 / BLOCK) * (W / BLOCK) + 20 / BLOCK;
        for (int i = 0; i < 8; i++) {
            kill(memory, sim, spawn(sim, Faction.MARINE, UnitType.MARINE, 20, 20 + i % 4));
        }
        float fresh = memory.copyFor(Faction.MARINE)[idx];
        assertTrue(fresh > 0f);

        memory.decay((int) CasualtyMemory.HALF_LIFE_TICKS);
        float halved = memory.copyFor(Faction.MARINE)[idx];
        assertEquals(fresh / 2f, halved, fresh * 0.02f,
                "ground does not become safe on a deadline; it fades");

        // Far enough out it stops mattering entirely rather than lingering as a
        // vanishing number that still breaks ties forever.
        memory.decay((int) CasualtyMemory.HALF_LIFE_TICKS * 12);
        assertEquals(0f, memory.copyFor(Faction.MARINE)[idx], 1e-6f);
    }

    @Test
    public void asecondSquadLostInThePlacePushesItBackUp() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        int idx = (20 / BLOCK) * (W / BLOCK) + 20 / BLOCK;
        for (int i = 0; i < 4; i++) {
            kill(memory, sim, spawn(sim, Faction.MARINE, UnitType.MARINE, 20, 20 + i));
        }
        memory.decay((int) CasualtyMemory.HALF_LIFE_TICKS);
        float faded = memory.copyFor(Faction.MARINE)[idx];

        for (int i = 0; i < 4; i++) {
            kill(memory, sim, spawn(sim, Faction.MARINE, UnitType.MARINE, 21, 20 + i));
        }
        assertTrue(memory.copyFor(Faction.MARINE)[idx] > faded,
                "a place that costs a second squad becomes discouraging again");
    }

    @Test
    public void aDeathOffTheMapIsIgnoredRatherThanWrapping() {
        BattleSimulation sim = openSim();
        CasualtyMemory memory = memory(sim);
        long unit = spawn(sim, Faction.MARINE, UnitType.MARINE, 1, 1);
        memory.onDeath(new DeathEvent(unit, -5f, -5f, 0));
        memory.onDeath(new DeathEvent(unit, W + 40f, H + 40f, 0));

        for (float value : memory.copyFor(Faction.MARINE)) {
            assertEquals(0f, value, 1e-6f, "an out-of-bounds cell lands nowhere");
        }
    }
}
