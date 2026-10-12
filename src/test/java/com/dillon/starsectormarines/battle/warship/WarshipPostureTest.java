package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a posture reaches the ship: the flux pair, the pin, the declared threat, withdrawal, and a doctrine-free ship. */
class WarshipPostureTest {

    private static SceneWorld world() {
        return SceneBuilder.openGround(120, 60).missionCompletion(false)
                .unit("d", Faction.DEFENDER, UnitType.MARINE, 110, 30).build();
    }

    private static Warship onslaught(SceneWorld w) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 30f, 30f, -90f);
        ship.direct(10f, true);
        w.sim().addWarship(ship);
        return ship;
    }

    @Test
    void aLowerSoftPairVentsEarlierAndTheShippedPairDoesNot() {
        WarshipFlux shipped = new WarshipFlux(1000f, 100f);
        shipped.enabled(true);
        shipped.spend(520f);
        shipped.update(0f);
        assertEquals(WarshipFlux.State.FIRING, shipped.state());

        WarshipFlux conserving = new WarshipFlux(1000f, 100f);
        conserving.enabled(true);
        conserving.thresholds(0.5f, 0.3f);
        conserving.spend(520f);
        conserving.update(0f);
        assertEquals(WarshipFlux.State.VENTING, conserving.state());
        assertFalse(conserving.canStartFire());
    }

    @Test
    void aShipWithNoDoctrineIsBombardForeverWhateverIsDeclared() throws Exception {
        SceneWorld w = world();
        Warship ship = onslaught(w);
        ship.declareAirThreat(5f, 60f);
        SceneRun.of(w).run(90);
        assertEquals(WarshipPosture.BOMBARD, ship.posture());
        assertEquals(0, ship.postureTransitions());
        assertFalse(ship.postureReason().isBlank());
    }

    @Test
    void aDeclaredThreatTurnsADoctrineShipToAirSuperiorityAndItReturnsAfterTheDwell() throws Exception {
        SceneWorld w = world();
        Warship ship = onslaught(w);
        ship.useDoctrine();
        SceneRun run = SceneRun.of(w);
        run.run(45);
        assertEquals(WarshipPosture.BOMBARD, ship.posture());
        ship.declareAirThreat(2f, 5f);
        run.run(60);
        assertEquals(WarshipPosture.AIR_SUPERIORITY, ship.posture());
        assertTrue(ship.postureReason().contains("declared"), ship.postureReason());
        assertEquals(PostureProfile.AIR_SUPERIORITY.softLimit(), ship.profile().softLimit());
        // The declaration lapses after five seconds; the dwell (eight more) must pass before the fight is over.
        run.run((int) ((5f + WarshipDoctrine.AIR_QUIET_SECONDS + 3f) * 30f));
        assertEquals(WarshipPosture.BOMBARD, ship.posture());
        assertEquals(2, ship.postureTransitions());
        assertEquals(PostureProfile.BOMBARD.softLimit(), ship.profile().softLimit());
    }

    @Test
    void aPinnedPostureStopsTheDoctrine() throws Exception {
        SceneWorld w = world();
        Warship ship = onslaught(w);
        ship.useDoctrine();
        ship.pinPosture(WarshipPosture.SUPPORT);
        ship.declareAirThreat(5f, 60f);
        SceneRun.of(w).run(120);
        assertEquals(WarshipPosture.SUPPORT, ship.posture());
        assertTrue(ship.postureReason().contains("pinned"));
        ship.unpinPosture();
        SceneRun.of(w).run(60);
        assertEquals(WarshipPosture.AIR_SUPERIORITY, ship.posture());
    }

    @Test
    void aShipTakenBelowTheHullLimitWithdrawsAndDeparts() throws Exception {
        SceneWorld w = world();
        Warship ship = onslaught(w);
        ship.useDoctrine();
        SceneRun run = SceneRun.of(w);
        run.run(35);
        ship.takeDamage(ship.maxHitPoints * 0.7f, 100f);
        assertTrue(ship.hullFraction() < WarshipDoctrine.WITHDRAW_HULL);
        run.run(70);
        assertEquals(WarshipPosture.WITHDRAW, ship.posture());
        assertTrue(ship.departing(), "withdraw leaves through the existing station-time departure");
        assertTrue(ship.areas().isEmpty());
    }

    @Test
    void anAirSuperiorityShipHoldsAtMostOneSmallAreaInsideItsOpportunityRange() throws Exception {
        SceneWorld w = world();
        Warship ship = onslaught(w);
        ship.pinPosture(WarshipPosture.AIR_SUPERIORITY);
        assertTrue(ship.opportunityRange() < WarshipDirector.reach(ship));
        assertEquals(1, ship.directorMaxAreas());
        assertEquals(5f, ship.directorRadius());
        assertFalse(ship.directorBringsGunsToBear());
        ship.unpinPosture();
        ship.pinPosture(WarshipPosture.BOMBARD);
        assertEquals(Float.POSITIVE_INFINITY, ship.opportunityRange());
    }
}
