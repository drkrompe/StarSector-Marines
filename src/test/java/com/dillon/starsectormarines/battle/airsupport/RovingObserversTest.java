package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roving observer teams: rare (a cap on the side's observers, fixed and roving together, and on teams at once), sent
 * to the best vantage nobody watches yet, moving on from the one they hold, and carrying the radio once seated.
 */
class RovingObserversTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
    }

    @AfterAll
    static void release() {
        TurretSlotResolver.useHullSpecs(null);
    }

    @Test
    void aTeamComesUpOnlyWhenDueAndUnderBothCaps() {
        assertTrue(RovingObservers.mayRove(100, 100, 0, 0));
        assertFalse(RovingObservers.mayRove(99, 100, 0, 0), "not yet due");
        assertFalse(RovingObservers.mayRove(100, 100, RovingObservers.MOST_TEAMS, 0), "enough teams rove");
        assertFalse(RovingObservers.mayRove(100, 100, 0, RovingObservers.CAP),
                "the map already holds the most observers, fixed posts counted");
    }

    @Test
    void aTeamTakesTheBestVantageNobodyWatchesAndMovesOnFromItsOwn() {
        List<int[]> ranked = List.of(new int[]{10, 10}, new int[]{60, 10}, new int[]{110, 10});
        assertArrayEquals(new int[]{60, 10}, RovingObservers.pick(ranked, List.of(new float[]{12f, 14f}), null, 25f));
        assertArrayEquals(new int[]{110, 10}, RovingObservers.pick(ranked, List.of(new float[]{12f, 14f}),
                new int[]{60, 10}, 25f), "a team watching the second moves on to the third");
        assertNull(RovingObservers.pick(ranked, List.of(new float[]{10f, 10f}, new float[]{60f, 10f}, new float[]{110f, 10f}),
                null, 25f), "every vantage is watched");
    }

    @Test
    void aSentTeamCarriesTheRadioOnItsSecondManAndSetsOutForTheVantage() {
        SceneWorld world = SceneBuilder.openGround(60, 40).missionCompletion(false).build();
        BattleSimulation sim = world.sim();
        RovingObservers teams = new RovingObservers(Faction.ALLY, 5f, 20f, s -> List.<int[]>of(new int[]{40, 20}), 1L, 0);
        teams.tick(sim);
        assertEquals(1, teams.sent());
        assertEquals(1, teams.moves());
        assertEquals(1, RovingObservers.observersOnMap(sim, Faction.ALLY));
        long observer = teams.observers().get(0);
        assertTrue(CommsKit.carried(sim.world(), observer));
        assertTrue(Math.hypot(sim.world().x(observer) - 5f, sim.world().y(observer) - 20f) < 3f, "seated at home");
    }

    @Test
    void aTeamMovesOnWhenItHasWatchedItsDwellOrIsPressedAtItsVantageButNeverInAHurry() {
        int stay = Math.round(RovingObservers.MIN_STAY_SECONDS / BattleSimulation.TICK_DT);
        int dwell = Math.round(RovingObservers.DWELL_SECONDS / BattleSimulation.TICK_DT);
        assertFalse(RovingObservers.movesOn(stay - 1, true, true), "too soon, however pressed");
        assertTrue(RovingObservers.movesOn(stay, true, true));
        assertFalse(RovingObservers.movesOn(stay, false, true), "pressed on the way: it fights through");
        assertFalse(RovingObservers.movesOn(dwell - 1, true, false));
        assertTrue(RovingObservers.movesOn(dwell, false, false), "a vantage it never reached is given up too");
    }
}
