package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.layout.Deferred;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The patrol base's rules ({@code raid-mission.md}, Site templates): its program is the base's process, and its patrol
 * roads run in from their posts to the works' gates, one to a gate, each with a post stood once the places grow.
 * Plans only; no map is generated.
 */
class MilitaryWorksTest {

    private static final RaidSiteTemplate BASE = RaidSiteTemplate.forIndustry("militarybase");

    /** One motor shed per machine beside the depot, magazine, fuel point and dispatch; a second depot for a big pool. */
    @Test
    void theBaseIsAMotorPoolWithWhatThePatrolsDrawOn() {
        MilitaryWorks works = new MilitaryWorks();
        assertEquals(3, count(works.works(3), MilitaryWorks.MOTOR_POOL));
        for (RoomPurpose step : List.of(MilitaryWorks.MAGAZINE, MilitaryWorks.FUEL_POINT)) {
            assertEquals(1, count(works.works(3), step), step.name());
        }
        // Dispatch is a control room, as the gate guard posts are.
        assertTrue(count(works.works(3), MilitaryWorks.DISPATCH) >= 1);
        assertEquals(1, count(works.works(MilitaryWorks.SECOND_DEPOT_FROM - 1), MilitaryWorks.SUPPLY_DEPOT));
        assertEquals(2, count(works.works(MilitaryWorks.SECOND_DEPOT_FROM), MilitaryWorks.SUPPLY_DEPOT));
        assertThrows(IllegalArgumentException.class, () -> works.works(0));
    }

    /**
     * Every seed sends patrols out by two of the works' gates: each road runs from beside the works in to a gate, is
     * laid through the districts on its way, and has its post stated with it.
     */
    @Test
    void patrolRoadsRunInToTheWorksGatesOneToAGate() {
        TargetProfile market = new TargetProfile(4, 5, 2, 1, "independent",
                EnumSet.of(EconomicFunction.MILITARY), SurfacePalette.ROCK, SettlementLink.ROAD);
        for (long seed = 1; seed <= 4; seed++) {
            PrecinctPlan plan = BASE.lay(market, Fortification.Demand.UNSTATED, 3, 280, 168, new Random(seed));
            Set<Side> gates = new HashSet<>();
            Set<String> posts = new HashSet<>();
            for (Piece piece : plan.layout().pieces()) {
                if (piece instanceof Deferred d && d.name().startsWith(MilitaryWorks.PATROL)) posts.add(d.name());
            }
            int roads = 0;
            for (Piece piece : plan.layout().pieces()) {
                if (!(piece instanceof Road road) || !road.name().startsWith(MilitaryWorks.PATROL)) continue;
                roads++;
                assertEquals(RaidSiteTemplate.WORKS, road.to().place(), road.name());
                assertTrue(road.to().gate(), road.name() + " ends at a gate");
                assertTrue(road.through(), road.name() + " is laid through the districts");
                assertTrue(gates.add(road.to().side()), "two patrol roads share the " + road.to().side() + " gate");
                assertTrue(posts.contains(road.name() + "-post"), road.name() + " has no post");
            }
            assertEquals(MilitaryWorks.PATROL_POSTS, roads, "seed " + seed);
        }
    }

    private static int count(FortressProgram program, RoomPurpose purpose) {
        int n = 0;
        for (FortressBuilding b : program.buildings()) if (b.purpose() == purpose) n += b.count();
        return n;
    }
}
