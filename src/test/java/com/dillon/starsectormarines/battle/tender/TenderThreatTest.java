package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tender knows what its side has seen and what its own eyes see, and learns the rest by being shot. */
class TenderThreatTest {

    @AfterEach
    void restore() {
        System.clearProperty(TenderThreat.PROPERTY);
    }

    private static SceneWorld world() {
        SceneBuilder b = SceneBuilder.openGround(160, 80).missionCompletion(false);
        b.squad("hidden").faction(Faction.DEFENDER).size(1).at(80, 40).unarmed().stationary().done();
        b.squad("overhead").faction(Faction.DEFENDER).size(1).at(140, 40).unarmed().stationary().done();
        return b.build();
    }

    private static Tender tender(float x, float y) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        return Tender.fit(Faction.MARINE, x, y, 0f);
    }

    @Test
    void anEnemyNobodyHasSeenIsUnknownButOneUnderTheHullIsSeen() throws Exception {
        SceneWorld w = world();
        Tender t = tender(130f, 40f);
        float[] known = TenderThreat.known(w.sim(), t);
        assertFalse(TenderThreat.near(known, 80f, 40f, 2f), "nobody on the tender's side has seen it");
        assertTrue(TenderThreat.near(known, 140f, 40f, 2f), "ten cells under the hull");
    }

    @Test
    void theControlSwitchKnowsEveryEnemy() throws Exception {
        SceneWorld w = world();
        Tender t = tender(130f, 40f);
        System.setProperty(TenderThreat.PROPERTY, "false");
        float[] known = TenderThreat.known(w.sim(), t);
        assertTrue(TenderThreat.near(known, 80f, 40f, 2f));
        assertTrue(TenderThreat.near(known, 140f, 40f, 2f));
    }

    @Test
    void aBuilderLosingHitPointsIsAHitAndAFreshOneIsNot() {
        SceneWorld w = world();
        World world = w.sim().world();
        long a = w.members("hidden")[0];
        Map<Long, Float> last = new HashMap<>();
        assertFalse(TenderThreat.anyHit(List.of(a), world, last), "first sight has nothing to compare");
        assertFalse(TenderThreat.anyHit(List.of(a), world, last), "unchanged");
        world.setHp(a, world.hp(a) - 1f);
        assertTrue(TenderThreat.anyHit(List.of(a), world, last));
        assertFalse(TenderThreat.anyHit(List.of(a), world, last), "a hit is reported once");
        assertFalse(TenderThreat.anyHit(List.of(), world, last));
        assertTrue(last.isEmpty(), "a builder no longer out is forgotten");
    }

    @Test
    void nearIsInclusiveOfItsRadius() {
        float[] pts = {10f, 10f};
        assertTrue(TenderThreat.near(pts, 13f, 14f, 5f));
        assertFalse(TenderThreat.near(pts, 13f, 14.1f, 5f));
        assertFalse(TenderThreat.near(new float[0], 0f, 0f, 100f));
    }
}
