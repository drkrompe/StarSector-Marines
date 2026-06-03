package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the corpse home: a unit that dies gets a corpse
 * entity spawned into the battle {@code EntityWorld} off its {@code DeathEvent}
 * (the dead-sprite render walks the corpse archetype's columns instead of
 * scanning the legacy units list), carrying its identity, death cell, the
 * draw position frozen at the spot it fell, and the authored death pose.
 *
 * <p>The arena parks a live unit of each faction far from the target so the
 * battle stays in progress across the death drain — otherwise the win-check
 * would complete and {@code advance()} would no-op.
 */
public class DeadBodySystemTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    /** A target DEFENDER to kill, with a live unit of each faction kept far away so the battle doesn't end. */
    private static Entity parkArenaWithTarget(BattleSimulation sim) {
        sim.addUnit(new Entity("m0", Faction.MARINE, UnitType.MARINE, 1, 1));
        sim.addUnit(new Entity("d-keepalive", Faction.DEFENDER, UnitType.MARINE, 38, 38));
        Entity target = new Entity("d0", Faction.DEFENDER, UnitType.MARINE, 20, 20);
        sim.addUnit(target);
        return target;
    }

    private static int corpseCount(BattleSimulation sim) {
        int n = 0;
        for (ArchetypeTable t : sim.getEntityWorld().matched(sim.getBattleComponents().corpses)) {
            n += t.rowCount();
        }
        return n;
    }

    @Test
    public void killedUnitGetsACorpseEntityOnTheDeathDrain() {
        BattleSimulation sim = openArena(40, 40);
        Entity target = parkArenaWithTarget(sim);
        long id = target.entityId;

        sim.applyDamage(target, 100_000f, 20f, 20f);
        assertFalse(sim.world().isAlive(id), "lethal hit kills the unit");
        // Buffered: the corpse spawns when the death mailbox drains in the tick.
        assertEquals(0, corpseCount(sim), "the corpse spawns on the death drain, not inline");

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, corpseCount(sim), "drain → the dead unit gets a corpse entity");
        BattleComponents c = sim.getBattleComponents();
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            Object[] factions = t.objects(c.IDENTITY, BattleComponents.IDENTITY_FACTION).array();
            int[] cellX = t.ints(c.POSITION, BattleComponents.POSITION_CELL_X).array();
            int[] cellY = t.ints(c.POSITION, BattleComponents.POSITION_CELL_Y).array();
            int[] pose = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                assertEquals(UnitType.MARINE, types[r], "corpse carries the dead unit's archetype");
                assertEquals(Faction.DEFENDER, factions[r], "corpse carries the dead unit's side");
                assertEquals(20, cellX[r], "corpse keeps the death cell");
                assertEquals(20, cellY[r]);
                assertTrue(pose[r] >= 0 && pose[r] < 4,
                        "a damage-resolver death authors a valid prone-pose frame into SPRITE.index");
            }
        }
    }

    @Test
    public void corpseFreezesTheRenderPositionWhereTheUnitFell() {
        BattleSimulation sim = openArena(40, 40);
        Entity target = parkArenaWithTarget(sim);
        long id = target.entityId;
        float deathX = target.getRenderX();
        float deathY = target.getRenderY();

        sim.applyDamage(target, 100_000f, 20f, 20f);
        sim.advance(BattleSimulation.TICK_DT);

        // The unit is gone from the live registry...
        assertFalse(sim.getUnitRegistry().isLive(id), "dead unit released from the dense registry");
        // ...but the corpse entity persists with the draw position snapshotted at
        // death — composed into its own RENDER_POSITION columns, no released
        // Entity handle and no shared-store read after the spawn.
        assertEquals(1, corpseCount(sim));
        BattleComponents c = sim.getBattleComponents();
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            float[] rx = t.floats(c.RENDER_POSITION, BattleComponents.RENDER_POSITION_X).array();
            float[] ry = t.floats(c.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                assertEquals(deathX, rx[r], 1e-6f, "the corpse's draw position is the spot it fell");
                assertEquals(deathY, ry[r], 1e-6f);
            }
        }
    }

    @Test
    public void liveUnitsHaveNoCorpse() {
        BattleSimulation sim = openArena(40, 40);
        parkArenaWithTarget(sim);

        for (int i = 0; i < 5; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertEquals(0, corpseCount(sim), "no deaths → no corpse entities");
        assertEquals(0, sim.getEntityWorld().entityCount(), "the world holds only corpses for now");
    }
}
