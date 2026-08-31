package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground under a berth's aircraft, across the whole life of the berth.
 *
 * <p>An intact aircraft used to block nothing at all while its own burnt-out
 * wreck stopped people, so a marine walked clean through a fighter and round
 * the hulk of it. These pin the footprint appearing and disappearing at the
 * right moments, because both mistakes are silent: a stand that never closes
 * is an apron with pictures on it, and a footprint left behind when the
 * aircraft leaves is an invisible wall on the concrete for the rest of the
 * battle.
 */
class AParkedHullBlocksItsGroundTest {

    private static final int W = 20;
    private static final int H = 20;
    private static final int PAD_X = 10;
    private static final int PAD_Y = 10;
    private static final float DT = 1f / 30f;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static AirfieldService.Berth berth(BattleSimulation sim) {
        return sim.getAirfieldService().addBerth(
                LandingPad.garrison(PAD_X, PAD_Y, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
    }

    /**
     * A hull on a stand is a thing you walk round, and the stand still has a
     * cell of concrete all the way round it to walk on.
     */
    @Test
    void anAircraftOnItsStandClosesTheGroundUnderIt() {
        BattleSimulation sim = openSim();
        berth(sim);
        NavigationGrid grid = sim.getGrid();
        assertTrue(grid.isWalkable(PAD_X, PAD_Y), "the apron starts open");

        new AirfieldSystem(Faction.DEFENDER).tick(DT, sim, sim.getAirfieldService());

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertFalse(grid.isWalkable(PAD_X + dx, PAD_Y + dy),
                        "walked through the aircraft at " + dx + "," + dy);
            }
        }
        assertTrue(grid.isWalkable(PAD_X - 2, PAD_Y),
                "the ground crew has nowhere to stand beside the hull");
        assertTrue(grid.isWalkable(PAD_X + 2, PAD_Y));
        assertTrue(grid.isWalkable(PAD_X, PAD_Y - 2));
        assertTrue(grid.isWalkable(PAD_X, PAD_Y + 2));
    }

    /**
     * A whole aircraft is a solid object where it stands and a burnt one is a
     * frame with holes in it, so the intact hull blocks a sight line that the
     * wreck lets through.
     */
    @Test
    void anIntactHullIsOpaqueWhereItsWreckIsNot() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(DT, sim, sim.getAirfieldService());
        assertTrue(sim.getGrid().blocksLineOfSight(PAD_X, PAD_Y),
                "an intact aircraft is something to see round");

        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(DT, sim, sim.getAirfieldService());

        assertFalse(sim.getGrid().blocksLineOfSight(PAD_X, PAD_Y),
                "the wreck should be shootable across");
        assertFalse(sim.getGrid().isWalkable(PAD_X, PAD_Y),
                "and still be something to walk round");
    }

    /**
     * The hull does not blind the apron it is standing on, and above all does
     * not blind anybody trying to shoot <em>it</em>.
     *
     * <p>A sight line exempts its two endpoints and nothing else, so a hull
     * opaque across its whole footprint is a hull no round can reach: the raid
     * on the field — the entire reason the apron is worth walking onto — went
     * from three aircraft burned in five seconds to none in three minutes.
     * The overhang is therefore see-through and only the cell the aircraft
     * stands on is not, which is the convention a defence post's emplacement
     * already follows.
     */
    @Test
    void aRifleTeamOnTheApronCanStillHitTheAircraft() {
        BattleSimulation sim = openSim();
        berth(sim);
        new AirfieldSystem(Faction.DEFENDER).tick(DT, sim, sim.getAirfieldService());

        for (int dx = -4; dx <= 4; dx++) {
            assertTrue(sim.getGrid().hasLineOfSight(PAD_X + dx, PAD_Y - 4, PAD_X, PAD_Y),
                    "the aircraft shielded itself from a shot at " + dx);
        }
        assertFalse(sim.getGrid().hasLineOfSight(PAD_X, PAD_Y - 4, PAD_X, PAD_Y + 4),
                "and shielded nothing at all across its own fuselage");
    }

    /**
     * The concrete goes back to being concrete when the aircraft leaves it.
     *
     * <p>Asserted on the grid rather than on the berth's state, because this is
     * the failure that leaves nothing to see: a footprint outliving the hull
     * that made it is an invisible wall on the apron for the rest of the
     * battle, and the berth would say AWAY the whole time.
     */
    @Test
    void launchingGivesTheConcreteBack() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(DT, sim, sim.getAirfieldService());

        sim.getAirfieldService().launch(berth);
        system.tick(DT, sim, sim.getAirfieldService());

        // And it stays back: an aircraft under its own power is not a parked
        // one, so nothing re-closes the stand while the sortie is out.
        for (int i = 0; i < 60; i++) system.tick(DT, sim, sim.getAirfieldService());
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertTrue(sim.getGrid().isWalkable(PAD_X + dx, PAD_Y + dy),
                        "an invisible wall was left at " + dx + "," + dy);
                assertFalse(sim.getGrid().blocksLineOfSight(PAD_X + dx, PAD_Y + dy),
                        "and something to see round at " + dx + "," + dy);
            }
        }
    }

    /**
     * An aircraft lost over the objective leaves an empty stand, and empty
     * means empty.
     *
     * <p>No wreck comes down on this one, so nothing re-closes the ground the
     * hull was holding. Every ending has to give it back, which is why there is
     * one release rather than a release per ending.
     */
    @Test
    void anAircraftLostAwayFromItsStandLeavesNothingOnIt() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(DT, sim, airfield);
        airfield.launch(berth);
        airfield.destroyed(berth);

        system.tick(DT, sim, airfield);

        assertEquals(AirfieldService.BerthState.DESTROYED, berth.state);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertTrue(sim.getGrid().isWalkable(PAD_X + dx, PAD_Y + dy),
                        "the stand kept the dead aircraft's ground at " + dx + "," + dy);
            }
        }
    }

    /**
     * The hull hands its ground to the wreck rather than leaving the intact
     * hull's marks underneath it.
     *
     * <p>The two stamps disagree about sight on purpose, so a wreck settled on
     * top of a footprint nobody released would be a wreck that is opaque where
     * it is supposed to be shootable across — and it would read the dead
     * aircraft's own closed cells when working out who could step where.
     */
    @Test
    void aBurnedHullHandsItsGroundToTheWreck() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(DT, sim, sim.getAirfieldService());

        sim.applyDamage(berth.airframeId, 100_000f, 100_000f);
        system.tick(DT, sim, sim.getAirfieldService());

        assertTrue(berth.wreckOnPad);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertFalse(sim.getGrid().isWalkable(PAD_X + dx, PAD_Y + dy),
                        "the wreck left a hole at " + dx + "," + dy);
                assertFalse(sim.getGrid().blocksLineOfSight(PAD_X + dx, PAD_Y + dy),
                        "the intact hull's opaque cell survived under the wreck at "
                                + dx + "," + dy);
            }
        }
    }

    /**
     * An aircraft put back on its stand blocks again. The turnaround happens on
     * the concrete with people round it, so the hull is standing there for the
     * whole of it.
     */
    @Test
    void anAircraftBackOnItsStandBlocksAgain() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth berth = berth(sim);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(DT, sim, airfield);
        airfield.launch(berth);
        system.tick(DT, sim, airfield);
        assertTrue(sim.getGrid().isWalkable(PAD_X, PAD_Y), "the stand is empty while it is away");

        airfield.recover(berth, ShuttleType.AEROSHUTTLE.maxHp());
        system.tick(DT, sim, airfield);

        assertEquals(AirfieldService.BerthState.REFITTING, berth.state);
        assertFalse(sim.getGrid().isWalkable(PAD_X, PAD_Y),
                "an aircraft being worked on is still an aircraft standing there");
    }

    /**
     * An arriving hull moves nobody, and seals nobody in.
     *
     * <p>A berth is placed at the start of the battle and again on every
     * completed refit, so a hull that stepped people clear would hand the side
     * that owns the field a free repeatable shove at whoever is standing on it.
     * It takes the cells nobody is in and leaves the rest open instead — the
     * same trade a settling wreck makes when there is nowhere to put somebody,
     * applied one step earlier.
     */
    @Test
    void anArrivingHullMovesNobodyAndSealsNobodyIn() {
        BattleSimulation sim = openSim();
        berth(sim);
        long marine = sim.spawn(
                new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, PAD_X, PAD_Y));

        new AirfieldSystem(Faction.DEFENDER).tick(DT, sim, sim.getAirfieldService());

        assertEquals(PAD_X, sim.world().cellX(marine), "the aircraft shoved them off the pad");
        assertEquals(PAD_Y, sim.world().cellY(marine));
        assertTrue(sim.getGrid().isWalkable(PAD_X, PAD_Y),
                "sealed into a cell they can never leave");
        assertFalse(sim.getGrid().isWalkable(PAD_X + 1, PAD_Y),
                "one occupied cell should not cost the hull the rest of its ground");
    }

    /**
     * A hull gives back exactly the ground it took. A shed's own wall can lie
     * inside the square an aircraft covers, and opening the whole square when
     * the aircraft rolls out would punch a hole in the shed.
     */
    @Test
    void aHullNeverGivesBackGroundItDidNotTake() {
        BattleSimulation sim = openSim();
        AirfieldService.Berth berth = berth(sim);
        sim.getGrid().setWalkable(PAD_X - 1, PAD_Y, false);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(DT, sim, sim.getAirfieldService());

        sim.getAirfieldService().launch(berth);
        system.tick(DT, sim, sim.getAirfieldService());

        assertFalse(sim.getGrid().isWalkable(PAD_X - 1, PAD_Y),
                "the aircraft handed back a wall it never stood on");
        assertTrue(sim.getGrid().isWalkable(PAD_X, PAD_Y), "and kept ground it did stand on");
    }
}
