package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FleeBehaviorTest {

    @Test
    void perceptionBoundaryIsInclusiveButAnythingBeyondItIsRejected() {
        try (BattleSimulation sim = simulation()) {
            long civilian = spawn(sim, "civilian", Faction.CIVILIAN,
                    UnitType.CIVILIAN, 16, 16);
            long threat = spawn(sim, "threat", Faction.DEFENDER,
                    UnitType.MILITIA, 30, 16);

            assertEquals(threat,
                    FleeBehavior.findNearestThreat(civilian, sim),
                    "a combatant exactly fourteen cells away must be sensed");

            sim.world().setPos(threat, 30.51f, 16.5f);
            rebuildIndex(sim);

            assertEquals(0L,
                    FleeBehavior.findNearestThreat(civilian, sim),
                    "the spatial gather padding must not expand perception");
        }
    }

    @Test
    void ignoresNoncombatantsAndSensesCombatantsFromEitherFaction() {
        try (BattleSimulation sim = simulation()) {
            long civilian = spawn(sim, "civilian", Faction.CIVILIAN,
                    UnitType.CIVILIAN, 16, 16);
            spawn(sim, "bystander", Faction.CIVILIAN,
                    UnitType.ENGINEER, 17, 16);
            long defender = spawn(sim, "defender", Faction.DEFENDER,
                    UnitType.MILITIA, 21, 16);
            long marine = spawn(sim, "marine", Faction.MARINE,
                    UnitType.MARINE, 24, 16);

            assertEquals(defender,
                    FleeBehavior.findNearestThreat(civilian, sim));

            sim.world().setPos(defender, 48.5f, 16.5f);
            rebuildIndex(sim);

            assertEquals(marine,
                    FleeBehavior.findNearestThreat(civilian, sim),
                    "marine and defender combatants must both spook civilians");
        }
    }

    @Test
    void snapshotPaddingFindsLiveThreatsWithoutChangingTheLiveRadius() {
        try (BattleSimulation sim = simulation()) {
            long civilian = spawn(sim, "civilian", Faction.CIVILIAN,
                    UnitType.CIVILIAN, 16, 16);
            long threat = spawn(sim, "threat", Faction.DEFENDER,
                    UnitType.SWARM_RUNNER, 31, 16);

            sim.world().setPos(threat, 31f, 16.5f);
            rebuildIndex(sim); // snapshot distance 14.5
            sim.world().setPos(threat, 30.4f, 16.5f); // live distance 13.9

            assertEquals(threat,
                    FleeBehavior.findNearestThreat(civilian, sim),
                    "a threat moving into perception after rebuild must not be missed");

            rebuildIndex(sim); // snapshot distance 13.9
            sim.world().setPos(threat, 30.6f, 16.5f); // live distance 14.1

            assertEquals(0L,
                    FleeBehavior.findNearestThreat(civilian, sim),
                    "a threat moving out of perception must be rejected by the live check");
        }
    }

    @Test
    void equalDistanceTieUsesLaterDenseRosterEntryAcrossBuckets() {
        try (BattleSimulation sim = simulation()) {
            long civilian = spawn(sim, "civilian", Faction.CIVILIAN,
                    UnitType.CIVILIAN, 16, 16);
            spawn(sim, "earlier", Faction.DEFENDER,
                    UnitType.MILITIA, 17, 16);
            long later = spawn(sim, "later", Faction.MARINE,
                    UnitType.MARINE, 15, 16);

            rebuildIndex(sim);

            assertEquals(later,
                    FleeBehavior.findNearestThreat(civilian, sim),
                    "the old <= scan made the later dense entry win an exact tie");
        }
    }

    @Test
    void releasedCandidateIsAbsentFromTheCurrentSnapshot() {
        try (BattleSimulation sim = simulation()) {
            long civilian = spawn(sim, "civilian", Faction.CIVILIAN,
                    UnitType.CIVILIAN, 16, 16);
            long released = spawn(sim, "released", Faction.DEFENDER,
                    UnitType.MILITIA, 18, 16);
            long remaining = spawn(sim, "remaining", Faction.MARINE,
                    UnitType.MARINE, 22, 16);

            assertEquals(released,
                    FleeBehavior.findNearestThreat(civilian, sim));

            sim.getRoster().releaseFromRegistry(released);

            assertEquals(remaining,
                    FleeBehavior.findNearestThreat(civilian, sim));
        }
    }

    private static BattleSimulation simulation() {
        int width = 64;
        int height = 40;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static long spawn(BattleSimulation sim, String name,
                              Faction faction, UnitType type,
                              int cellX, int cellY) {
        return sim.spawn(new EntitySpec(name, faction, type, cellX, cellY));
    }

    private static void rebuildIndex(BattleSimulation sim) {
        sim.getUnitIndex().rebuild(sim.getRoster());
    }
}
