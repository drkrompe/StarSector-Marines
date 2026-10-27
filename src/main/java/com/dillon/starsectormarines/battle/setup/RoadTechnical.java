package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * A scripted enemy technical: a militia pickup with a heavy machine gun that enters at a road mouth, drives the one
 * vehicle-clear route to the cell it was sent to and parks there, its gun fighting whatever it sees. Not commanded.
 * Counterattack's final wave brings one up the south service road to the loading yard before dispatch; Junction's
 * counterattack brings two down the main road. A Hound's chaingun takes one in about a quarter of a minute in the
 * open; walls and buildings are the company's way to get at it without that trade.
 */
final class RoadTechnical {
    static final VehicleType TYPE = VehicleType.MILITIA_TECHNICAL;

    private final int entryX, entryY, parkX, parkY;
    private long id;

    /** The hull's first cell at the south road mouth and the cell it parks on, both clear for its footprint. */
    RoadTechnical(int entryX, int entryY, int parkX, int parkY) {
        this.entryX = entryX;
        this.entryY = entryY;
        this.parkX = parkX;
        this.parkY = parkY;
    }

    int entryX() { return entryX; }
    int entryY() { return entryY; }
    int parkX() { return parkX; }
    int parkY() { return parkY; }

    /** The cells it drives from the road mouth to its park, as x0, y0, x1, y1, ...; empty when there is no way. */
    int[] route(BattleSimulation sim) {
        VehicleClearance lane = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(TYPE.visualWidthCells));
        return GridPathfinder.findPathOnDemand(sim.getGrid(), entryX, entryY, parkX, parkY, index -> 1f, lane::isPassableAt);
    }

    /** Puts the truck on the road mouth and sends it to its park. Once only. */
    void enter(BattleSimulation sim) {
        if (id != 0L) throw new IllegalStateException("the technical enters once");
        int[] path = route(sim);
        id = sim.convoy().spawn(TYPE, Faction.DEFENDER, VehicleMission.deployed(entryX + .5f, entryY + .5f));
        float facing = path.length >= 4 ? AirBody.facingToward(path[2] - path[0], path[3] - path[1]) : 0f;
        sim.convoy().body(id).teleport(entryX + .5f, entryY + .5f, facing);
        if (path.length < 4) return;
        float[] routeX = new float[path.length / 2], routeY = new float[path.length / 2];
        for (int i = 0; i < routeX.length; i++) { routeX[i] = path[i * 2] + .5f; routeY[i] = path[i * 2 + 1] + .5f; }
        VehicleMission mission = sim.convoy().mission(id);
        mission.authoredRouteX = routeX;
        mission.authoredRouteY = routeY;
        mission.authoredLeg = VehicleLeg.MOVE_ORDER;
        mission.authoredRoutePaused = false;
    }

    /** The hull's id, or 0 before it enters. */
    long id() { return id; }

    /** Whether it is still to come or still on the field: the attack is not beaten while it is. */
    boolean standing(BattleSimulation sim) {
        return id == 0L || sim.convoy().isPresent(id);
    }
}
