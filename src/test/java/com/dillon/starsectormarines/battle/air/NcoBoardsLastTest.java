package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NCO is the last onto the boat.
 *
 * <p>A lift that cannot take the whole squad must leave the leader on the
 * field with the people it could not carry. The other half of the same law is
 * the frozen manifest, which seats the NCO first in their squad's run so they
 * ride its first lift; both exist because every posture's cohesion pull is a
 * pull toward the leader, and a squad separated from its NCO is a squad walking
 * the wrong way.
 */
class NcoBoardsLastTest {

    private static final int W = 24;
    private static final int H = 24;

    /** The ramp: where {@code AirSystem} measures boarding reach from. */
    private static final float RAMP_X = 10f;
    private static final float RAMP_Y = 10f;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /**
     * A craft holding on its pad with {@code seats} to offer, and three marines
     * of one squad already standing at the ramp. The first of them leads.
     */
    private static long[] squadAtTheRamp(BattleSimulation sim, int seats) {
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                20f, 20f, RAMP_X, RAMP_Y, 2f, 2f, 0f, seats);
        ShuttleMission mission = sim.world().mission(craft);
        mission.state = ShuttleState.LOADING;
        mission.marinesRemaining = 0;
        mission.boardingPatience = 60f;

        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        mission.embarkSquadId = squadId;
        long[] party = new long[]{
                sim.spawn(new EntitySpec("nco", Faction.DEFENDER, UnitType.MARINE, 10, 10)
                        .squad(squadId)),
                sim.spawn(new EntitySpec("m2", Faction.DEFENDER, UnitType.MARINE, 11, 10)
                        .squad(squadId)),
                sim.spawn(new EntitySpec("m3", Faction.DEFENDER, UnitType.MARINE, 10, 11)
                        .squad(squadId))};
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = party[0];
        return new long[]{craft, party[0], party[1], party[2]};
    }

    /** Three at the ramp and two seats: the NCO is the one still standing. */
    @Test
    void aShortLiftLeavesTheNcoWithWhoeverItCouldNotTake() {
        BattleSimulation sim = openSim();
        long[] ids = squadAtTheRamp(sim, 2);
        ShuttleMission mission = sim.world().mission(ids[0]);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(2, mission.marinesRemaining, "the lift did not fill");
        assertTrue(sim.getRoster().isAliveById(ids[1]),
                "the NCO flew out from under the marines it leads");
        assertFalse(sim.getRoster().isAliveById(ids[2]), "a rifleman was left behind");
        assertFalse(sim.getRoster().isAliveById(ids[3]), "a rifleman was left behind");
        assertEquals(1, sim.liveUnitCount());
    }

    /** Last aboard is not never aboard: with seats for everybody, everybody flies. */
    @Test
    void aLiftWithRoomForTheWholeSquadTakesTheNcoToo() {
        BattleSimulation sim = openSim();
        long[] ids = squadAtTheRamp(sim, 3);
        ShuttleMission mission = sim.world().mission(ids[0]);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(3, mission.marinesRemaining, "the whole squad did not board");
        assertEquals(0, sim.liveUnitCount(), "somebody was left on the pad");
        assertFalse(sim.getRoster().isAliveById(ids[1]), "the NCO never boarded");
    }
}
