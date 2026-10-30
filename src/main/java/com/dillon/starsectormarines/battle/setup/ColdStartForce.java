package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BasinOccupiers.Hull;
import com.dillon.starsectormarines.battle.setup.BasinOccupiers.Post;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The rival crew on Cold Start's night, sized for the company after Salvage Rights: five squads and four machines.
 * Until shore power is restored the crew holds the pump work site with hired guns beside its scrappers, behind ship
 * guns dug in at the pump ({@code BasinGunNests}); the company has to clear those guns to start the pump. The four
 * road teams keep their six-strong shape and entry rules, each now carrying hired guns and driving in behind a hull:
 * the first two teams up the south haul road a technical each, the quarry team a mortar carrier and a rocket
 * technical to shell the build ground, and the last team a technical and a tank. A team denied at the road mouth
 * never brings its hulls. UNTUNED first pass: unplayed against the company.
 */
final class ColdStartForce {
    private ColdStartForce() {}

    /** The pump work site's garrison, while the pump is theirs. */
    static final Post PUMP = new Post("pump", 171, 117, 4, 6);
    /** Hired guns in each road team of six, in release order; the rest are scrappers. */
    static final int[] TEAM_REGULARS = {2, 2, 2, 3};
    /** Hulls each road team brings, by release order, parking on their own cells. */
    static final Map<Integer, List<Hull>> TEAM_MACHINES = Map.of(
            0, List.of(new Hull(VehicleType.MILITIA_TECHNICAL, 176, 70, 0f)),
            1, List.of(new Hull(VehicleType.SRM_TECHNICAL, 178, 96, 0f)),
            2, List.of(new Hull(VehicleType.FIRE_SUPPORT_APC, 262, 128, 270f),
                    new Hull(VehicleType.SRM_TECHNICAL, 240, 118, 270f)),
            3, List.of(new Hull(VehicleType.MILITIA_TECHNICAL, 170, 92, 0f),
                    new Hull(VehicleType.BREAKTHROUGH_TANK, 160, 96, 0f)));
    /** Where each team's hulls come onto the map: the quarry mouth for the quarry team, else the south haul road. */
    static final int[] SOUTH_MOUTH = {194, 14}, QUARRY_MOUTH = {283, 135};

    /** The pump's garrison, unless an earlier attempt restored shore power. */
    static void garrison(BattleSimulation sim, boolean shorePower, Random rng) {
        if (!shorePower) BasinOccupiers.hold(sim, PUMP, rng);
    }

    /** The four road teams of six, team 2 on the quarry road, regulars first in each. */
    static List<EntitySpec> roadTeams(NavigationGrid grid, Random rng) {
        List<EntitySpec> reserve = new ArrayList<>();
        for (int batch = 0; batch < TEAM_REGULARS.length; batch++) {
            ShepherdBasin.Place road = batch == 2 ? ShepherdBasin.QUARRY_ROAD : ShepherdBasin.SOUTH_HAUL_ROAD;
            reserve.addAll(BasinOccupiers.team(grid, "repair-road-" + batch, road.x(), road.y(),
                    TEAM_REGULARS[batch], 6 - TEAM_REGULARS[batch], rng));
        }
        return reserve;
    }

    /** Each team's hulls, keyed by release order. */
    static Map<Integer, List<RoadTechnical>> teamMachines() {
        return Map.of(0, BasinOccupiers.driveIn(TEAM_MACHINES.get(0), SOUTH_MOUTH[0], SOUTH_MOUTH[1]),
                1, BasinOccupiers.driveIn(TEAM_MACHINES.get(1), SOUTH_MOUTH[0], SOUTH_MOUTH[1]),
                2, BasinOccupiers.driveIn(TEAM_MACHINES.get(2), QUARRY_MOUTH[0], QUARRY_MOUTH[1]),
                3, BasinOccupiers.driveIn(TEAM_MACHINES.get(3), SOUTH_MOUTH[0], SOUTH_MOUTH[1]));
    }
}
