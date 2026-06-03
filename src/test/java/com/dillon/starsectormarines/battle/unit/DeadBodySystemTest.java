package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.DeadBody;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the corpse home: a unit that dies gets a
 * {@code DeadBody} component attached off its {@code DeathEvent} (the dead-sprite
 * render reads that store instead of scanning the legacy units list), and the
 * body — like the render position it pairs with — survives release from the
 * live {@link UnitRegistry}.
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
    private static Unit parkArenaWithTarget(BattleSimulation sim) {
        sim.addUnit(new Unit("m0", Faction.MARINE, UnitType.MARINE, 1, 1));
        sim.addUnit(new Unit("d-keepalive", Faction.DEFENDER, UnitType.MARINE, 38, 38));
        Unit target = new Unit("d0", Faction.DEFENDER, UnitType.MARINE, 20, 20);
        sim.addUnit(target);
        return target;
    }

    @Test
    public void killedUnitGetsADeadBodyOnTheDeathDrain() {
        BattleSimulation sim = openArena(40, 40);
        Unit target = parkArenaWithTarget(sim);
        long id = target.entityId;

        sim.applyDamage(target, 100_000f, 20f, 20f);
        assertFalse(sim.world().isAlive(id), "lethal hit kills the unit");
        // Buffered: the body is attached when the death mailbox drains in the tick.
        assertFalse(sim.getDeadBodies().has(id),
                "the DeadBody attaches on the death drain, not inline");

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.getDeadBodies().has(id), "drain → the dead unit gets a DeadBody");
        DeadBody body = sim.getDeadBodies().get(id);
        assertNotNull(body);
        assertEquals(UnitType.MARINE, body.type, "body carries the dead unit's archetype");
        assertEquals(Faction.DEFENDER, body.faction, "body carries the dead unit's side");
        assertTrue(body.deathPoseIdx >= 0 && body.deathPoseIdx < 4,
                "a damage-resolver death rolls a valid prone-pose frame");
    }

    @Test
    public void deadBodyAndItsRenderPositionSurviveRegistryRelease() {
        BattleSimulation sim = openArena(40, 40);
        Unit target = parkArenaWithTarget(sim);
        long id = target.entityId;
        float deathX = target.getRenderX();
        float deathY = target.getRenderY();

        sim.applyDamage(target, 100_000f, 20f, 20f);
        sim.advance(BattleSimulation.TICK_DT);

        // The unit is gone from the live registry...
        assertFalse(sim.getUnitRegistry().isLive(id), "dead unit released from the dense registry");
        // ...but the corpse home + its shared render position both persist, keyed
        // by the same entity id — the composition the dead-sprite render reads.
        assertTrue(sim.getDeadBodies().has(id), "the body survives release");
        assertEquals(deathX, sim.getUnitRegistry().getRenderPositions().getX(id), 1e-6f,
                "the body's render position is the spot it fell");
        assertEquals(deathY, sim.getUnitRegistry().getRenderPositions().getY(id), 1e-6f);
    }

    @Test
    public void liveUnitsHaveNoDeadBody() {
        BattleSimulation sim = openArena(40, 40);
        parkArenaWithTarget(sim);

        for (int i = 0; i < 5; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.getDeadBodies().isEmpty(), "no deaths → no corpse bodies");
    }
}
