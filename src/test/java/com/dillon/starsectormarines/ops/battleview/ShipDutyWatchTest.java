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
