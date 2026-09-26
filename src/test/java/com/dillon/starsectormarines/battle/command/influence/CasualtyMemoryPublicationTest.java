package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A death in the battle reaches the frozen snapshot a commander plans against.
 * The unit test beside this one pins the memory's own arithmetic; this pins the
 * wiring — dispatcher to memory to published field — which is the half that
 * silently does nothing if the subscription is missing.
 */
public class CasualtyMemoryPublicationTest {

    private static final int W = 64;
    private static final int H = 64;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void aMarineLossShowsUpInTheMarineCommandersOwnPicture() {
        BattleSimulation sim = openSim();
        // Both sides present, or the simulation returns without advancing.
        sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 60, 60));
        long lost = sim.spawn(new EntitySpec("m", Faction.MARINE,
                UnitType.MARINE, 20, 20));
        // A second marine, because killing the only one empties the side, decides
        // the battle, and stops the clock - the snapshot would then never be
        // rebuilt and this would read a stale zero rather than a missing wire.
        sim.spawn(new EntitySpec("m2", Faction.MARINE, UnitType.MARINE, 40, 40));

        assertEquals(0f, sim.getCommanderInfluence(Faction.MARINE)
                .lossesAtWorld(20, 20), 1e-6f, "nothing lost yet");

        sim.applyDamage(lost, 100_000f, 100_000f);
        // Death events are dispatched from the damage path, so a raw HP write
        // kills the unit without anyone being told. Past the influence service's publication interval, so the snapshot
        // the commander reads is rebuilt rather than the one it already had.
        for (int i = 0; i < CommanderInfluenceService.UPDATE_INTERVAL_TICKS + 2; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertFalse(sim.getRoster().isAliveById(lost), "the marine actually died");
        assertTrue(sim.getCommanderInfluence(Faction.MARINE).lossesAtWorld(20, 20) > 0f,
                "the marine commander remembers losing somebody there");
        assertEquals(0f, sim.getCommanderInfluence(Faction.DEFENDER)
                        .lossesAtWorld(20, 20), 1e-6f,
                "and the defender learns nothing from it — this is a side's "
                        + "memory of its own dead, not a report on the enemy's");
    }

    /**
     * The same wire seen from the other end. The commander's snapshot is built
     * lazily by whoever asks for it, and the movers' costing is not: it has to
     * be aged and republished from the tick loop, or a battle with no
     * diagnostic reader would leave the pathfinder nothing to consult.
     */
    @Test
    public void aMarineLossMakesThatGroundDearerForTheMarinesToCross() {
        BattleSimulation sim = openSim();
        sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 60, 60));
        long lost = sim.spawn(new EntitySpec("m", Faction.MARINE,
                UnitType.MARINE, 20, 20));
        sim.spawn(new EntitySpec("m2", Faction.MARINE, UnitType.MARINE, 40, 40));

        assertNull(sim.getRouteCostField(Faction.MARINE),
                "nothing lost, so nothing to route around");

        sim.applyDamage(lost, 100_000f, 100_000f);
        for (int i = 0; i < CommanderInfluenceService.UPDATE_INTERVAL_TICKS + 2; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        RouteCostField cost = sim.getRouteCostField(Faction.MARINE);
        assertNotNull(cost, "published without anybody asking for a snapshot");
        assertTrue(cost.costAt(20 * W + 20) > 1f,
                "and the ground the marine died on costs the marines more");
        assertNull(sim.getRouteCostField(Faction.DEFENDER),
                "while the defenders, who have lost nobody, route as before");
    }
}
