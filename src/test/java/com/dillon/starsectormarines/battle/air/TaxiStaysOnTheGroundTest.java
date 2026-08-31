package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aircraft on its wheels goes round the hangar, not through it.
 *
 * <p>A body built for flight has nothing in it that stops, so steering one
 * straight at a runway threshold took it through whatever stood in between —
 * which on a real field is the shed it had just come out of.
 */
class TaxiStaysOnTheGroundTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);
    private static final float SHELTER_X = 25.5f;
    private static final float SHELTER_Y = 25.5f;

    /**
     * A field with a wall across it: the shed sits behind a solid run that
     * the straight line to the threshold crosses, with a gap at one end.
     */
    private static BattleSimulation openWalledField() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int x = 4; x < 50; x++) grid.setWalkable(x, 18, false);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        sim.getAirfieldService().installRunway(STRIP);
        return sim;
    }

    /** Taxis the whole way out, watching every tick for a cell it should not be in. */
    @Test
    void aTaxiingAircraftNeverStandsInsideAWall() {
        try (BattleSimulation sim = openWalledField()) {
            AirfieldService airfield = sim.getAirfieldService();
            AirfieldService.Berth shed = airfield.addShelterBerth(
                    new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                    FighterProfile.BROADSWORD);
            long craft = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                    50.5f, 30.5f, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f);
            ShuttleMission mission = sim.world().mission(craft);
            mission.homeBerth = shed;
            sim.world().setHp(craft, airfield.launch(shed));
            mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, 50.5f, 30.5f);
            sim.world().kinematics(craft).teleport(SHELTER_X, SHELTER_Y, 0f);

            NavigationGrid grid = sim.getGrid();
            int insideWall = 0;
            for (int i = 0; i < 4000 && mission.state == ShuttleState.TAXI_OUT; i++) {
                sim.advance(BattleSimulation.TICK_DT);
                AirBody body = sim.world().kinematics(craft);
                int cx = (int) Math.floor(body.x);
                int cy = (int) Math.floor(body.y);
                if (grid.inBounds(cx, cy) && !grid.isWalkable(cx, cy)) insideWall++;
            }

            assertEquals(0, insideWall, "taxied through a wall for " + insideWall + " ticks");
            assertEquals(ShuttleState.HOLDING_SHORT, mission.state,
                    "never got round to the threshold");
        }
    }

    /** And it still gets there, so the wall check has not simply stopped it. */
    @Test
    void aTaxiingAircraftStillReachesTheThreshold() {
        try (BattleSimulation sim = openWalledField()) {
            AirfieldService airfield = sim.getAirfieldService();
            AirfieldService.Berth shed = airfield.addShelterBerth(
                    new Gantry((int) SHELTER_X, (int) SHELTER_Y, 2, 2, Gantry.Facing.SOUTH),
                    FighterProfile.BROADSWORD);
            long craft = sim.spawnSortie(FighterProfile.BROADSWORD, Faction.DEFENDER,
                    50.5f, 30.5f, SHELTER_X, SHELTER_Y, SHELTER_X, SHELTER_Y, 0f);
            ShuttleMission mission = sim.world().mission(craft);
            mission.homeBerth = shed;
            sim.world().setHp(craft, airfield.launch(shed));
            mission.departFromRunway(STRIP, SHELTER_X, SHELTER_Y, 50.5f, 30.5f);
            sim.world().kinematics(craft).teleport(SHELTER_X, SHELTER_Y, 0f);

            for (int i = 0; i < 4000 && mission.state == ShuttleState.TAXI_OUT; i++) {
                sim.advance(BattleSimulation.TICK_DT);
            }

            assertEquals(ShuttleState.HOLDING_SHORT, mission.state);
            assertTrue(sim.world().kinematics(craft).distanceTo(10.5f, 6.5f) < 2f,
                    "held short somewhere that is not the threshold");
        }
    }
}
