package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whose aircraft stand on a field is a fact about the field.
 *
 * <p>It was a constructor argument to the tick consumer while there was only one
 * kind of field — a garrison's lot, on a battle map, belonging to the defender.
 * A ship's boat bay is the same arrangement belonging to the other side, and the
 * moment it existed the alternative was building the tick consumer one way for a
 * lot and another way for a bay, over a fact that has nothing to do with what it
 * does.
 */
class AFieldBelongsToItsOwnerTest {

    private static final int W = 24;
    private static final int H = 24;
    private static final float DT = 1f / 30f;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Gantry berth(int x, int y) {
        return new Gantry(x, y, 3, 2, Gantry.Facing.SOUTH, Gantry.Holds.BOAT);
    }

    /** A field nobody has claimed is a garrison's, which is what every battle map has. */
    @Test
    void aFieldIsAGarrisonsUntilSomebodySaysOtherwise() {
        BattleSimulation sim = openSim();
        AirfieldService field = sim.getAirfieldService();
        field.addBayBerth(berth(10, 10), ShuttleType.AEROSHUTTLE);

        new AirfieldSystem().tick(DT, sim, field);

        assertEquals(Faction.DEFENDER, field.owner());
        assertEquals(Faction.DEFENDER,
                sim.identity().faction(field.berths().get(0).airframeId),
                "a battle map's field stopped being the garrison's");
    }

    /** And a ship's bay is the marines' own, so what stands in it is theirs. */
    @Test
    void aBoatInAShipsBayBelongsToTheShip() {
        BattleSimulation sim = openSim();
        AirfieldService bay = sim.getAirfieldService();
        bay.setOwner(Faction.MARINE);
        bay.addBayBerth(berth(10, 10), ShuttleType.AEROSHUTTLE);

        new AirfieldSystem().tick(DT, sim, bay);

        assertEquals(Faction.MARINE,
                sim.identity().faction(bay.berths().get(0).airframeId),
                "the company's own boat came out belonging to the garrison");
    }

    /**
     * A boat berth is a hardstand, because that is what the kind means: a berth
     * something lifts off rather than rolls out of. A shelter would send it
     * looking for a strip, and a ship has none.
     */
    @Test
    void aBoatBerthIsAHardstand() {
        BattleSimulation sim = openSim();
        AirfieldService bay = sim.getAirfieldService();
        AirfieldService.Berth boat = bay.addBayBerth(berth(10, 10), ShuttleType.AEROSHUTTLE);

        assertEquals(AirfieldService.Kind.HARDSTAND, boat.kind);
        assertEquals(10, boat.centerX);
        assertEquals(10, boat.centerY);
        assertEquals(Gantry.Facing.SOUTH.degrees(), boat.facingDegrees, 0.01f,
                "a boat is parked facing whichever way its berth says it leaves");
    }

    /**
     * The berth is what the boat is, so a bay's own aircraft stands there and
     * can be shot at like anything else on the deck.
     */
    @Test
    void theBoatIsAUnitStandingInTheBay() {
        BattleSimulation sim = openSim();
        AirfieldService bay = sim.getAirfieldService();
        bay.setOwner(Faction.MARINE);
        AirfieldService.Berth boat = bay.addBayBerth(berth(10, 10), ShuttleType.AEROSHUTTLE);

        new AirfieldSystem().tick(DT, sim, bay);

        assertNotEquals(0L, boat.airframeId, "the bay produced no boat");
        assertTrue(sim.world().isAlive(boat.airframeId));
        assertEquals(10, sim.world().cellX(boat.airframeId));
        assertEquals(10, sim.world().cellY(boat.airframeId));
    }

    /**
     * A hand at a boat's servicing cell puts hull back on that boat, which is
     * the whole of what a turnaround is and is the same code an apron runs.
     */
    @Test
    void ahandAtTheBerthWorksOnWhatIsInIt() {
        BattleSimulation sim = openSim();
        AirfieldService bay = sim.getAirfieldService();
        bay.setOwner(Faction.MARINE);
        bay.addBayBerth(berth(10, 10), ShuttleType.AEROSHUTTLE);
        bay.installApronWork(List.of(FixtureTask.servingBerth(10, 7, 0, 10, 10)));

        assertEquals(0, bay.berthServicedFrom(10, 7),
                "the cell beside the boat does not name the boat");
        assertEquals(-1, bay.berthServicedFrom(2, 2),
                "a cell nowhere near a boat named one anyway");
    }
}
