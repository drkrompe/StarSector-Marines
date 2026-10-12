package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.JobSite;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShipWorkRoundTest {
    private record Room(RoomPurpose purpose) implements JobSite {
        public int id() { return 1; }
        public int centreX() { return 4; }
        public int centreY() { return 4; }
        public boolean contains(int x, int y) { return x >= 0 && y >= 0 && x < 8 && y < 8; }
    }
    private static final AmbientTaskRoute BASE = new AmbientTaskRoute("medic-1-0", 0, .72f, 14,
            AmbientThreatPolicy.HOSTILE_COMBATANT, List.of(
            new AmbientTaskRoute.Stop(1.5f, 1.5f, 31, AmbientActivity.WORKING, 1.5f, 2.5f, "1:treat"),
            new AmbientTaskRoute.Stop(20.5f, 1.5f, 41, AmbientActivity.RESTING, 20.5f, 2.5f, "2:rest")));

    @Test void medicVisitsDistinctBedsThenKeepsOffWatchRoute() {
        var tasks = List.of(FixtureTask.at(1, 1, Affordance.TREAT, 1, 2),
                FixtureTask.at(4, 1, Affordance.TREAT, 4, 2),
                FixtureTask.at(6, 1, Affordance.TREAT, 6, 2).wrecked(),
                FixtureTask.at(3, 4, Affordance.FABRICATE, 3, 5),
                FixtureTask.at(20, 1, Affordance.TREAT, 20, 2));
        var room = new Room(RoomPurpose.PATIENT_WARD);
        var route = ShipWorkRound.expand(CrewRole.MEDIC, room, tasks, null, BASE, 0, false);
        assertEquals(List.of(1.5f, 4.5f, 20.5f), route.stops().stream().map(AmbientTaskRoute.Stop::worldX).toList());
        assertTrue(route.stops().get(0).specificPoint());
        assertTrue(route.stops().get(1).specificPoint());
        assertSame(BASE.stops().get(1), route.stops().get(2));
        var onDuty = ShipWorkRound.expand(CrewRole.MEDIC, room, tasks, null, BASE, 1, true);
        assertEquals(List.of(4.5f, 1.5f), onDuty.stops().stream().map(AmbientTaskRoute.Stop::worldX).toList());
    }

    @Test void properTradesWorkRepairAndInspectionStations() {
        var tasks = List.of(FixtureTask.at(1, 1, Affordance.FABRICATE, 1, 2),
                FixtureTask.at(3, 1, Affordance.REPAIR, 3, 2),
                FixtureTask.at(5, 1, Affordance.READOUT, 5, 2));
        var shop = ShipWorkRound.expand(CrewRole.MACHINIST, new Room(RoomPurpose.PARTS_CAGE), tasks, null, BASE, 0, true);
        assertEquals(3, shop.stops().size());
        var armory = ShipWorkRound.expand(CrewRole.ARMOURER, new Room(RoomPurpose.ARMORY), tasks, null, BASE, 0, true);
        assertEquals(1, armory.stops().size());
        assertEquals("1:repair", armory.stops().get(0).pointGroup());
        assertTrue(ShipWorkRound.postingPriority(CrewRole.MACHINIST, RoomPurpose.PARTS_CAGE)
                < ShipWorkRound.postingPriority(CrewRole.MACHINIST, RoomPurpose.PRODUCTION_FLOOR));
        assertTrue(ShipWorkRound.postingPriority(CrewRole.STOREKEEPER, RoomPurpose.STOCKROOM)
                < ShipWorkRound.postingPriority(CrewRole.STOREKEEPER, RoomPurpose.HANGAR));
    }

    @Test void fixedCommandMarineAndBerthedMachineRoutinesStayIntact() {
        for (CrewRole role : List.of(CrewRole.BRIDGE_WATCH, CrewRole.SECURITY, CrewRole.MARINE, CrewRole.MECH_TECH))
            assertSame(BASE, ShipWorkRound.expand(role, new Room(RoomPurpose.BRIDGE), List.of(), null, BASE, 0, false));
    }
}
