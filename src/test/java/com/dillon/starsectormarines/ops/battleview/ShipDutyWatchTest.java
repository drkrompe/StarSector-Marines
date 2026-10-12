package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSide;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckZone;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShipDutyWatchTest {
    private static Compartment room(int id, RoomPurpose purpose) {
        return new Compartment(id, RoomShape.rectangle(8, 8), id * 10, 0,
                RoomPose.CANONICAL, DeckSide.PORT, DeckZone.MIDSHIPS, purpose, List.of());
    }

    @Test void shortCrewCoversConnAndPlantBeforeDoubling() {
        var bridge = room(0, RoomPurpose.BRIDGE);
        var engine = room(1, RoomPurpose.ENGINE_ROOM);
        var tasks = List.of(FixtureTask.at(1, 1, Affordance.WATCH, 1, 2),
                FixtureTask.at(2, 1, Affordance.WATCH, 2, 2),
                FixtureTask.at(11, 1, Affordance.TEND, 11, 2),
                FixtureTask.at(12, 1, Affordance.WATCH, 12, 2));
        var posts = ShipDutyWatch.plan(List.of(engine, bridge), tasks, new boolean[0], 2);
        assertEquals(List.of(CrewRole.BRIDGE_WATCH, CrewRole.ENGINE_WATCH),
                posts.stream().map(ShipDutyWatch.Post::role).toList());
        assertEquals(12.5f, posts.get(1).route().stops().get(0).worldX());
        assertTrue(posts.stream().allMatch(post -> post.route().stops().size() == 1));
        assertEquals(0, ShipDutyWatch.plan(List.of(bridge), tasks, null, 0).size());
    }

    @Test void sideBanksUseExistingDutyBudgetWithoutTakingEngineeringOrGuard() {
        var bridge = room(0, RoomPurpose.BRIDGE);
        var engine = room(1, RoomPurpose.ENGINE_ROOM);
        var ward = room(2, RoomPurpose.PATIENT_WARD);
        var mess = room(3, RoomPurpose.MESS_HALL);
        var armory = room(4, RoomPurpose.ARMORY);
        // Authored order: plot, helm, systems, west, east, then second seats.
        var tasks = new ArrayList<FixtureTask>();
        for (int x = 0; x < 7; x++) tasks.add(FixtureTask.at(x, 2, Affordance.WATCH, x, 3));
        tasks.add(FixtureTask.at(1, 0, Affordance.GUARD, 0, -1));
        tasks.add(FixtureTask.at(11, 1, Affordance.TEND, 11, 2));
        tasks.add(FixtureTask.at(12, 1, Affordance.TEND, 12, 2));
        tasks.add(FixtureTask.at(21, 1, Affordance.TREAT, 21, 2));
        tasks.add(FixtureTask.at(31, 1, Affordance.COOK, 31, 2));
        tasks.add(FixtureTask.at(41, 1, Affordance.ISSUE, 41, 2));
        var rooms = List.of(ward, armory, engine, bridge, mess);
        for (int budget = 0; budget <= 10; budget++) {
            var posts = ShipDutyWatch.plan(rooms, tasks, null, budget);
            assertEquals(budget, posts.size());
            assertEquals(budget, posts.stream().map(post -> post.route().id()).distinct().count());
            if (budget >= 6) {
                assertEquals(2, posts.stream().filter(post -> post.role() == CrewRole.ENGINE_WATCH).count());
                assertEquals(1, posts.stream().filter(post -> post.role() == CrewRole.SECURITY).count());
            }
            if (budget >= 8) {
                var conn = posts.stream().filter(post -> post.role() == CrewRole.BRIDGE_WATCH).toList();
                assertEquals(List.of(.5f, 1.5f, 2.5f, 3.5f, 4.5f),
                        conn.stream().map(post -> post.route().stops().get(0).worldX()).toList());
                assertTrue(conn.stream().allMatch(post -> post.route().stops().size() == 1));
            }
        }
        var full = ShipDutyWatch.plan(rooms, tasks, null, 10);
        assertEquals(CrewRole.MEDIC, full.get(8).role());
        assertEquals(CrewRole.COOK, full.get(9).role());
        // A missing first side station must not prevent other live work from being staffed.
        tasks.set(3, tasks.get(3).wrecked());
        var damaged = ShipDutyWatch.plan(rooms, tasks, null, 10);
        assertEquals(10, damaged.size());
        assertTrue(damaged.stream().filter(post -> post.role() == CrewRole.BRIDGE_WATCH)
                .noneMatch(post -> post.route().stops().get(0).worldX() == 3.5f));
    }

    @Test void brokenFixturesAndEmptyBerthsDoNotCreateDutyCrew() {
        var bay = room(0, RoomPurpose.HANGAR);
        var tasks = List.of(FixtureTask.servingBerth(1, 1, 0, 1, 2),
                FixtureTask.at(2, 1, Affordance.TEND, 2, 2).wrecked());
        assertTrue(ShipDutyWatch.plan(List.of(bay), tasks, new boolean[]{false}, 8).isEmpty());
        assertEquals(1, ShipDutyWatch.plan(List.of(bay), tasks, new boolean[]{true}, 8).size());
    }

    @Test void servicingWatchKeepsAllMachinesInItsLocalRotation() {
        var bay = room(0, RoomPurpose.HANGAR);
        var tasks = List.of(FixtureTask.servingBerth(1, 1, 0, 1, 2),
                FixtureTask.servingBerth(2, 1, 1, 2, 2),
                FixtureTask.at(3, 1, Affordance.READOUT, 3, 2),
                FixtureTask.at(4, 1, Affordance.REST, 4, 2));
        var route = ShipDutyWatch.plan(List.of(bay), tasks, new boolean[]{true, true}, 8).get(0).route();
        assertEquals(3, route.stops().size());
        assertTrue(route.stops().stream().anyMatch(stop -> stop.pointGroup().endsWith("@0")));
        assertTrue(route.stops().stream().anyMatch(stop -> stop.pointGroup().endsWith("@1")));
        assertTrue(route.stops().stream().noneMatch(stop -> stop.pointGroup().contains("rest")));
    }

    @Test void uniformFollowsPostingWithoutReplacingMarineKit() {
        assertEquals(LayeredArmorFamily.CHARCOAL, ShipDutyWatch.uniform(CrewRole.SECURITY, RoomPurpose.BRIDGE));
        assertEquals(RoomPurpose.CREW_QUARTERS, CrewRole.SECURITY.quarters());
        assertEquals(LayeredArmorFamily.SHIP_COMMAND, ShipDutyWatch.uniform(CrewRole.BRIDGE_WATCH, RoomPurpose.BRIDGE));
        assertEquals(LayeredArmorFamily.SHIP_ENGINEERING, ShipDutyWatch.uniform(CrewRole.BRIDGE_WATCH, RoomPurpose.ENGINE_ROOM));
        assertEquals(LayeredArmorFamily.SHIP_MEDICAL, ShipDutyWatch.uniform(CrewRole.MEDIC, RoomPurpose.PATIENT_WARD));
        assertNull(ShipDutyWatch.uniform(CrewRole.MARINE, RoomPurpose.BARRACKS));
    }

    @Test void entranceGuardUsesOneExistingBilletAfterEssentialWatchBeforeSupport() {
        var bridge = room(0, RoomPurpose.BRIDGE);
        var engine = room(1, RoomPurpose.ENGINE_ROOM);
        var ward = room(2, RoomPurpose.PATIENT_WARD);
        var tasks = List.of(FixtureTask.at(1, 1, Affordance.WATCH, 1, 2),
                FixtureTask.at(2, 1, Affordance.WATCH, 2, 2),
                FixtureTask.at(3, 1, Affordance.WATCH, 3, 2),
                FixtureTask.at(1, 0, Affordance.GUARD, 0, -1),
                FixtureTask.at(11, 1, Affordance.TEND, 11, 2),
                FixtureTask.at(12, 1, Affordance.TEND, 12, 2),
                FixtureTask.at(21, 1, Affordance.TREAT, 21, 2));
        var rooms = List.of(ward, engine, bridge);
        assertTrue(ShipDutyWatch.plan(rooms, tasks, null, 5).stream()
                .noneMatch(post -> post.role() == CrewRole.SECURITY));
        var posts = ShipDutyWatch.plan(rooms, tasks, null, 6);
        assertEquals(6, posts.size());
        var guard = posts.get(5);
        assertEquals(CrewRole.SECURITY, guard.role());
        assertEquals(bridge, guard.room());
        assertEquals(1, guard.route().stops().size());
        assertEquals(1.5f, guard.route().stops().get(0).worldX());
        assertEquals(.5f, guard.route().stops().get(0).worldY());
        assertTrue(guard.route().stops().get(0).pointGroup().contains("guard"));
        assertEquals(CrewRole.MEDIC, ShipDutyWatch.plan(rooms, tasks, null, 7).get(6).role());
        var wrecked = List.of(FixtureTask.at(1, 0, Affordance.GUARD, 0, -1).wrecked());
        assertTrue(ShipDutyWatch.plan(rooms, wrecked, null, 6).isEmpty());
    }
}
