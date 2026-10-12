package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a ship's stance looks for the objective: the assignments its own side's squads hold, and nothing of the enemy's. */
class ObjectiveBiasTest {

    private static SceneWorld world() {
        SceneBuilder b = SceneBuilder.openGround(120, 80).missionCompletion(false);
        b.squad("assault").faction(Faction.MARINE).size(2).at(10, 10).unarmed().stationary()
                .assigned(id -> ObjectiveAssignment.attackMove(id, 80, 20)).done();
        b.squad("holding").faction(Faction.MARINE).size(2).at(10, 40).unarmed().stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, 30, 60)).done();
        b.squad("enemy").faction(Faction.DEFENDER).size(2).at(100, 70).unarmed().stationary()
                .assigned(id -> ObjectiveAssignment.attackMove(id, 5, 5)).done();
        return b.build();
    }

    private static Warship ship() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        return Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 40f, 40f, 0f);
    }

    private static boolean has(List<float[]> focus, float x, float y) {
        return focus.stream().anyMatch(p -> Math.abs(p[0] - x) < 1f && Math.abs(p[1] - y) < 1f);
    }

    @Test
    void anAttackingShipLooksWhereItsAssaultWasSentAndNotAtTheEnemysOrders() throws Exception {
        SceneWorld w = world();
        Warship ship = ship();
        ship.stance(MissionStance.ATTACK);
        List<float[]> focus = ObjectiveBias.focus(w.sim(), ship);
        assertTrue(has(focus, 80.5f, 20.5f), "the assault's objective");
        assertEquals(1, focus.size(), "not the holding squad's ground, not the enemy's destination");
    }

    @Test
    void aDefendingShipLooksWhereItsSquadsHold() throws Exception {
        SceneWorld w = world();
        Warship ship = ship();
        ship.stance(MissionStance.DEFEND);
        List<float[]> focus = ObjectiveBias.focus(w.sim(), ship);
        assertTrue(has(focus, 30.5f, 60.5f));
        assertEquals(1, focus.size());
    }

    @Test
    void aShipWithNoStanceHasNoObjectiveButDeclaredGroundStillCounts() throws Exception {
        SceneWorld w = world();
        Warship ship = ship();
        assertTrue(ObjectiveBias.focus(w.sim(), ship).isEmpty());
        ship.declareFocus(50f, 50f);
        List<float[]> focus = ObjectiveBias.focus(w.sim(), ship);
        assertEquals(1, focus.size());
        assertTrue(has(focus, 50f, 50f));
    }

    @Test
    void proximityIsOneOnTheObjectiveAndFadesToNothing() {
        List<float[]> focus = List.of(new float[]{10f, 10f});
        assertEquals(1f, ObjectiveBias.proximity(10f, 10f, focus), 1e-6f);
        assertEquals(0.5f, ObjectiveBias.proximity(10f + ObjectiveBias.FOCUS_RADIUS_CELLS / 2f, 10f, focus), 1e-5f);
        assertEquals(0f, ObjectiveBias.proximity(10f + ObjectiveBias.FOCUS_RADIUS_CELLS * 2f, 10f, focus));
        assertEquals(0f, ObjectiveBias.proximity(0f, 0f, List.of()));
    }
}
