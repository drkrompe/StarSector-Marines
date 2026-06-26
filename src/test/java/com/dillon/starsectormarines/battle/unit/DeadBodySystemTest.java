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
 * End-to-end coverage for the corpse home: a unit that dies has its world
 * entity <b>transmuted</b> (one row-move) from the live {@code {IDENTITY,
 * HEALTH}} archetype to the corpse archetype off its {@code DeathEvent} — same
 * entity id, identity carried by the row-move (written only at spawn), death
 * cell from the event snapshot, draw position frozen at the spot it fell, and
 * the death pose authored into {@code SPRITE.index}.
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

        assertEquals(1, corpseCount(sim), "drain → the dead unit's entity is now a corpse");
        BattleComponents c = sim.getBattleComponents();
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            Object[] factions = t.objects(c.IDENTITY, BattleComponents.IDENTITY_FACTION).array();
            int[] cellX = t.ints(c.POSITION, BattleComponents.POSITION_CELL_X).array();
            int[] cellY = t.ints(c.POSITION, BattleComponents.POSITION_CELL_Y).array();
            int[] pose = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                assertEquals(id, t.entityAt(r), "the corpse IS the dead unit's entity id");
                // IDENTITY is written once at spawn and never re-written at death
                // — these values reaching the corpse table proves the transmute's
                // row-move carried the shared columns.
                assertEquals(UnitType.MARINE, types[r], "identity rides the row-move");
                assertEquals(Faction.DEFENDER, factions[r]);
                assertEquals(20, cellX[r], "corpse keeps the death cell");
                assertEquals(20, cellY[r]);
                assertTrue(pose[r] >= 0 && pose[r] < 4,
                        "a damage-resolver death authors a valid prone-pose frame into SPRITE.index");
            }
        }
        // Health is gone with the live archetype — "lacks HEALTH" is half the
        // liveness definition, so the corpse reports dead.
        assertFalse(sim.getEntityWorld().has(id, c.HEALTH), "corpse carries no HEALTH");
        assertFalse(sim.world().isAlive(id));
    }

    @Test
    public void corpseFreezesTheRenderPositionWhereTheUnitFell() {
        BattleSimulation sim = openArena(40, 40);
        Entity target = parkArenaWithTarget(sim);
        long id = target.entityId;
        float deathX = sim.world().renderX(target.entityId);
        float deathY = sim.world().renderY(target.entityId);

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
        assertEquals(3, sim.getEntityWorld().entityCount(),
                "every live unit is a {IDENTITY, HEALTH} entity in the world");
    }
}
