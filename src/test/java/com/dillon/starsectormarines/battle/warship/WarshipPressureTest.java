package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pieces that keep an equal fight moving: who may press past its flux order, when a losing ship breaks off, which
 * hulls must keep their bow on, where a hardpoint sights from, and a breached mount's repair.
 */
class WarshipPressureTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static Warship wolf(Faction side) throws Exception {
        Warship w = Warship.fit("wolf_hegemony", "wolf_hegemony_Rusk", side, 0f, 0f, 0f);
        w.limitFlux();
        return w;
    }

    private static void fill(Warship ship, float share) {
        ship.fluxPool().spend(share * ship.fluxPool().capacity() - ship.flux());
        ship.fluxPool().update(0f);
    }

    @Test
    void aShipPastItsSoftLimitPressesOnlyATargetDeeperInFlux() throws Exception {
        Warship shooter = wolf(Faction.ALLY);
        Warship target = wolf(Faction.DEFENDER);
        // A posture that wants the pool mostly empty for a fight stops starting fire at half.
        shooter.fluxPool().thresholds(0.5f, 0.3f);
        float cost = 1f;
        fill(shooter, 0.72f);
        assertFalse(shooter.fluxPool().canStart(cost), "the shooter is venting by its own order");

        // Neither shield is up: the ship further gone breaks off rather than trading at the limit.
        fill(target, 0.7f);
        assertFalse(WarshipShipFire.mayStart(shooter, target, cost), "the deeper ship does not press");

        fill(target, 0.85f);
        assertTrue(WarshipShipFire.mayStart(shooter, target, cost), "a target near its limit is pressed");

        fill(shooter, WarshipShipFire.PRESS_CEILING);
        assertFalse(WarshipShipFire.mayStart(shooter, target, cost), "never past the ceiling, short of its own drop");
    }

    @Test
    void aLosingShipBreaksOffAndAWinningOneDoesNot() {
        PostureInputs losing = new PostureInputs(0.6f, 0.5f, false, false, 0f, "", 0, MissionStance.HOLD, 0.9f);
        assertEquals(WarshipPosture.WITHDRAW, new WarshipDoctrine().assess(losing, 1f).posture());

        PostureInputs winning = new PostureInputs(0.9f, 0.5f, false, false, 0f, "", 0, MissionStance.HOLD, 0.6f);
        assertEquals(WarshipPosture.BOMBARD, new WarshipDoctrine().assess(winning, 1f).posture());

        PostureInputs even = new PostureInputs(0.5f, 0.5f, false, false, 0f, "", 0, MissionStance.HOLD, 0.6f);
        assertEquals(WarshipPosture.BOMBARD, new WarshipDoctrine().assess(even, 1f).posture(),
                "hurt, but no worse off than its foe");

        PostureInputs alone = new PostureInputs(0.5f, 0.5f, false, false, 0f, "", 0, MissionStance.HOLD);
        assertEquals(WarshipPosture.BOMBARD, new WarshipDoctrine().assess(alone, 1f).posture(), "no foe, no exchange");
    }

    @Test
    void onlyAFrontShieldKeepsItsBowOn() throws Exception {
        assertTrue(WarshipTactics.frontShielded(wolf(Faction.ALLY)));
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.ALLY, 0f, 0f, 0f);
        assertFalse(WarshipTactics.frontShielded(hound), "a shieldless hull may come about");
    }

    @Test
    void aFrontShieldCoversItsArcAndNotItsStern() throws Exception {
        Warship target = wolf(Faction.DEFENDER);
        // Facing 0: the nose is along +y.
        assertTrue(WarshipDuelHelm.shieldCovers(target, target.centerX(), target.centerY() + 100f));
        assertFalse(WarshipDuelHelm.shieldCovers(target, target.centerX(), target.centerY() - 100f));
    }

    @Test
    void aHardpointSightsFromItsMountPulledInByTheReachShare() {
        assertEquals(10f + 20f * WarshipScale.HULL_REACH_SHARE, WarshipShipRules.sight(10f, 30f), 1e-5f);
        assertEquals(10f, WarshipShipRules.sight(10f, 10f), 1e-5f);
    }

    @Test
    void aBreachedMountIsSilencedForItsRepairAndLostWhenItsPlateIsHitAgain() throws Exception {
        Warship ship = wolf(Faction.ALLY);
        WarshipPlating plating = ship.plating();
        assertNotNull(plating);
        Warship.Mount mount = null;
        int cell = -1;
        for (Warship.Mount m : ship.mounts()) {
            cell = ship.slotCell(m.slot);
            if (cell >= 0) {
                mount = m;
                break;
            }
        }
        assertNotNull(mount, "a mount over the plate");
        for (int i = 0; i < 40 && !plating.breached(cell % plating.cols, cell / plating.cols); i++) {
            plating.hit(cell, 10_000f, 10f, 1f);
        }
        assertTrue(plating.breached(cell % plating.cols, cell / plating.cols));

        ship.knockOutBreachedMounts(1f);
        assertTrue(mount.knockedOut() && !mount.lost(), "silenced, not lost");
        float back = 1f + Warship.MOUNT_REPAIR_SECONDS * WarshipScale.HULL_TEMPO;
        ship.tickMountRepairs(back - 0.1f);
        assertTrue(mount.knockedOut(), "still under repair");
        ship.tickMountRepairs(back);
        assertFalse(mount.knockedOut(), "back in action");

        plating.hit(cell, 10_000f, 10f, back + 1f);
        ship.tickMountRepairs(back + 1f);
        assertTrue(mount.knockedOut() && mount.lost(), "hit again over the hole: gone for good");
    }
}
