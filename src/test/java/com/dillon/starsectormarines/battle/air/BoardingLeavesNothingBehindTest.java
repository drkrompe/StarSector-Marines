package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unit taken off the battlefield alive leaves nothing behind.
 *
 * <p>Two things remove a living unit rather than killing it: a marine walking
 * up a shuttle's ramp, and an airframe leaving its hardstand to become an air
 * entity. Both used to only drop the dense roster slot, which is all a
 * <em>death</em> needs — the death drain transmutes the row to a corpse a tick
 * later. Nothing transmutes a unit that did not die, so its
 * {@code IDENTITY + POSITION + SPRITE + HEALTH} row stayed in the entity world
 * forever, and the render pass walks that row rather than the roster.
 *
 * <p>The symptom was precise: the body kept drawing where it had been standing,
 * with no health bar over it, because the bar sweep walks the roster and the
 * body sweep walks the components.
 */
class BoardingLeavesNothingBehindTest {

    private static final int W = 24;
    private static final int H = 24;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /** Rows the render pass would walk for drawable bodies. */
    private static int drawableRows(BattleSimulation sim) {
        BattleComponents c = sim.getBattleComponents();
        int rows = 0;
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.liveSprites)) rows += t.rowCount();
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.layeredSprites)) rows += t.rowCount();
        return rows;
    }

    /**
     * A marine who boards stops being drawn.
     *
     * <p>Asserted on what the render pass actually walks rather than on the
     * roster, because the roster was always right — a boarded marine has been
     * off it since the day boarding was written, and the body kept drawing
     * anyway.
     */
    @Test
    void aMarineWhoBoardsStopsBeingDrawn() {
        BattleSimulation sim = openSim();
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                20f, 20f, 10f, 10f, 2f, 2f, 0f, 2);
        ShuttleMission mission = sim.world().mission(craft);
        mission.state = ShuttleState.LOADING;
        mission.marinesRemaining = 0;
        mission.boardingPatience = 60f;

        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        mission.embarkSquadId = squadId;
        sim.spawn(new EntitySpec("m1", Faction.DEFENDER, UnitType.MARINE, 10, 10)
                .squad(squadId));
        assertTrue(drawableRows(sim) > 0, "the marine was never drawable to begin with");

        // Standing on the ramp, so the next tick takes them aboard.
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, mission.marinesRemaining, "the marine never boarded");
        assertEquals(0, sim.liveUnitCount(), "boarded and still on the roster");
        // Counted across both sprite queries a marine can match, so a row left
        // in either one fails this.
        assertEquals(0, drawableRows(sim),
                "boarded and the body is still there to draw");
    }

    /**
     * An airframe that leaves its hardstand stops being drawn as a unit.
     *
     * <p>Same removal, same leak: it has stopped being a unit rather than
     * stopped existing, and until now "stopped being a unit" left the whole
     * row behind.
     */
    @Test
    void anAirframeThatLaunchesStopsBeingDrawn() {
        BattleSimulation sim = openSim();
        AirfieldService airfield = sim.getAirfieldService();
        AirfieldService.Berth berth = airfield.addBerth(
                LandingPad.garrison(10, 10, LandingPad.Approach.SOUTH),
                ShuttleType.AEROSHUTTLE, 0f);
        AirfieldSystem system = new AirfieldSystem(Faction.DEFENDER);
        system.tick(BattleSimulation.TICK_DT, sim, airfield);
        long airframe = berth.airframeId;
        assertTrue(airframe != 0L, "nothing was stood on the pad");
        int parked = drawableRows(sim);

        airfield.launch(berth);
        system.tick(BattleSimulation.TICK_DT, sim, airfield);

        assertEquals(0, sim.liveUnitCount(), "launched and still on the roster");
        assertTrue(drawableRows(sim) < parked || parked == 0,
                "launched and the hull is still there to draw");
        assertTrue(!sim.getEntityWorld().has(airframe, sim.getBattleComponents().POSITION),
                "launched and the airframe entity is still in the world");
    }
}
