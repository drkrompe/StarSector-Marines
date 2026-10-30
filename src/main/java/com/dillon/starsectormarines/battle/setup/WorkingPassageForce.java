package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BasinOccupiers.Hull;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The rival crew on Working Passage's day, sized for the company after Cold Start: five squads and four machines.
 * Each watch on Morrow's route holds hired guns beside its scrappers, with a ship gun dug in beside it
 * ({@code BasinGunNests}). The four road teams keep their four-six-four-six shape and timing, now carrying hired
 * guns and driving in behind a hull: a technical with the first, a rocket technical with the quarry team and the
 * third, and a technical and a tank with the last.
 * UNTUNED first pass: unplayed against the company.
 */
final class WorkingPassageForce {
    private WorkingPassageForce() {}

    /** Hired guns and scrappers at each watch. */
    static final int WATCH_REGULARS = 2, WATCH_MILITIA = 4;
    /** Each road team's size and its hired guns, in release order; the rest are scrappers. */
    static final int[] TEAM_SIZES = {4, 6, 4, 6}, TEAM_REGULARS = {2, 2, 2, 3};
    /** Hulls each road team brings, by release order, parking on their own cells. */
    static final Map<Integer, List<Hull>> TEAM_MACHINES = Map.of(
            0, List.of(new Hull(VehicleType.MILITIA_TECHNICAL, 196, 60, 0f)),
            1, List.of(new Hull(VehicleType.SRM_TECHNICAL, 262, 122, 270f)),
            2, List.of(new Hull(VehicleType.SRM_TECHNICAL, 200, 70, 0f)),
            3, List.of(new Hull(VehicleType.MILITIA_TECHNICAL, 200, 100, 0f),
                    new Hull(VehicleType.BREAKTHROUGH_TANK, 210, 96, 0f)));
    static final int[] SOUTH_MOUTH = {194, 14}, QUARRY_MOUTH = {283, 135};

    /** Every watch on the route at tick zero. */
    static void garrison(BattleSimulation sim, Random rng) {
        for (ShepherdBasin.Place watch : ShepherdBasin.PASSAGE_WATCHES)
            BasinOccupiers.hold(sim, watch.id(), watch.x(), watch.y(), WATCH_REGULARS, WATCH_MILITIA, false, rng);
    }

    /** The four road teams, team 1 on the quarry road, regulars first in each. */
    static List<EntitySpec> roadTeams(NavigationGrid grid, Random rng) {
        List<EntitySpec> reserve = new ArrayList<>();
        for (int batch = 0; batch < TEAM_SIZES.length; batch++) {
            ShepherdBasin.Place road = batch == 1 ? ShepherdBasin.QUARRY_ROAD : ShepherdBasin.SOUTH_HAUL_ROAD;
            reserve.addAll(BasinOccupiers.team(grid, "passage-road-" + batch, road.x(), road.y(),
                    TEAM_REGULARS[batch], TEAM_SIZES[batch] - TEAM_REGULARS[batch], rng));
        }
        return reserve;
    }

    /** Each team's hulls, keyed by release order. */
    static Map<Integer, List<RoadTechnical>> teamMachines() {
        return Map.of(0, BasinOccupiers.driveIn(TEAM_MACHINES.get(0), SOUTH_MOUTH[0], SOUTH_MOUTH[1]),
                1, BasinOccupiers.driveIn(TEAM_MACHINES.get(1), QUARRY_MOUTH[0], QUARRY_MOUTH[1]),
                2, BasinOccupiers.driveIn(TEAM_MACHINES.get(2), SOUTH_MOUTH[0], SOUTH_MOUTH[1]),
                3, BasinOccupiers.driveIn(TEAM_MACHINES.get(3), SOUTH_MOUTH[0], SOUTH_MOUTH[1]));
    }
}
