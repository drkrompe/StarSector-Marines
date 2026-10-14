package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.command.compound.ObjectiveArea;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compound whose walls are down shares one zone with the street. Its clear is asked of its own ground.
 */
class ObjectiveAreaClearTest {

    /** One open 12x8 floor: a compound footprint at x 0..4 and a street beside it, one zone between them. */
    private static BattleSimulation breachedSim() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(12, 8));
    }

    private static final ObjectiveArea COMPOUND = new ObjectiveArea(0, 0, 4, 7);

    @Test
    void aHostileInTheStreetDoesNotHoldTheCompound() {
        BattleSimulation sim = breachedSim();
        int zone = sim.getZoneGraph().zoneIdAt(2, 2);
        assertEquals(zone, sim.getZoneGraph().zoneIdAt(10, 2), "the breach has made one zone of both");

        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MILITIA, 10, 2));
        assertFalse(ZoneQueries.zoneClearOfHostiles(zone, Faction.MARINE, sim));
        assertTrue(ZoneQueries.areaClearOfHostiles(zone, COMPOUND, Faction.MARINE, sim));
    }

    @Test
    void aHostileOnTheCompoundsGroundStillDoes() {
        BattleSimulation sim = breachedSim();
        int zone = sim.getZoneGraph().zoneIdAt(2, 2);
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MILITIA, 3, 5));
        assertFalse(ZoneQueries.areaClearOfHostiles(zone, COMPOUND, Faction.MARINE, sim));
    }

    @Test
    void noAreaIsTheWholeZone() {
        BattleSimulation sim = breachedSim();
        int zone = sim.getZoneGraph().zoneIdAt(2, 2);
        sim.spawn(new EntitySpec("d1", Faction.DEFENDER, UnitType.MILITIA, 10, 2));
        assertFalse(ZoneQueries.areaClearOfHostiles(zone, null, Faction.MARINE, sim));
    }
}
