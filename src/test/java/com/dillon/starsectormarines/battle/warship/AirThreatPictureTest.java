package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The threat picture reads the battle and writes nothing; a hostile shuttle is a drop, never a threat. */
class AirThreatPictureTest {

    private static SceneWorld world() {
        return SceneBuilder.openGround(120, 60).missionCompletion(false)
                .unit("d", Faction.DEFENDER, UnitType.MARINE, 100, 30)
                .unit("m", Faction.MARINE, UnitType.MARINE, 10, 30)
                .build();
    }

    private static void flyUntilIncoming(BattleSimulation sim, long craft) {
        ShuttleMission mission = sim.world().mission(craft);
        for (int i = 0; i < 600 && mission.state != ShuttleState.INCOMING; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertEquals(ShuttleState.INCOMING, mission.state);
    }

    @Test
    void aHostileLoadedShuttleIsAnInboundDropAndNotAThreat() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld w = world();
        BattleSimulation sim = w.sim();
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 20f, 30f, 0f);
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER, 90.5f, 30.5f, 119f, 30.5f, 119f, 30.5f, 0f, 2);
        flyUntilIncoming(sim, craft);

        AirThreatPicture picture = AirThreatPicture.take(sim, ship, List.of(ship));

        assertEquals(0f, picture.score(), "a shuttle never threatens a ship");
        assertEquals(1, picture.drops().size());
        AirThreatPicture.InboundDrop drop = picture.drops().get(0);
        assertEquals(craft, drop.craftId());
        assertEquals(90.5f, drop.lzX());
        assertEquals(2, drop.marines());
        assertTrue(drop.etaSeconds() > AirThreatPicture.SETTLE_SECONDS, "still on its way: " + drop.etaSeconds());
        assertEquals(0, picture.armedCraft());
    }

    @Test
    void itReadsTheAirEntityListWithoutChangingItAndOwnCraftAreCoverNotDrops() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld w = world();
        BattleSimulation sim = w.sim();
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 20f, 30f, 0f);
        long hostile = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER, 90.5f, 30.5f, 119f, 30.5f, 119f, 30.5f, 0f, 2);
        long own = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE, 30.5f, 20.5f, 0f, 20.5f, 0f, 20.5f, 0f, 2);
        flyUntilIncoming(sim, hostile);
        long[] before = sim.getAirEntityIds().clone();
        ShuttleState hostileState = sim.world().mission(hostile).state;
        float hostileX = sim.world().kinematics(hostile).x;

        AirThreatPicture picture = AirThreatPicture.take(sim, ship, List.of(ship));

        assertArrayEquals(before, sim.getAirEntityIds());
        assertEquals(hostileState, sim.world().mission(hostile).state);
        assertEquals(hostileX, sim.world().kinematics(hostile).x);
        assertEquals(1, picture.drops().size(), "only the hostile one is a drop");
        assertTrue(picture.ownAir() >= 1, "the ship's own craft is cover to protect: " + Arrays.toString(before) + " own=" + own);
    }

    @Test
    void hostileShipsAndDeclaredThreatsAddToTheScore() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        SceneWorld w = world();
        BattleSimulation sim = w.sim();
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 20f, 30f, 0f);
        Warship enemy = Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER, 90f, 30f, 0f);
        Warship friend = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 20f, 10f, 0f);

        AirThreatPicture calm = AirThreatPicture.take(sim, ship, List.of(ship, friend));
        assertEquals(0f, calm.score());
        assertEquals("none", calm.describe());

        ship.declareAirThreat(0.5f, 10f);
        AirThreatPicture busy = AirThreatPicture.take(sim, ship, List.of(ship, friend, enemy));
        assertEquals(1, busy.hostileShips());
        assertEquals(0.5f + AirThreatPicture.HOSTILE_SHIP_WEIGHT, busy.score(), 1e-4f,
                "a declared 0.5 and one undamaged hostile hull");
        assertTrue(busy.describe().contains("hostile ship"), busy.describe());
    }
}
