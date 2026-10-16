package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.mech.MechDamageDoctrine.Posture;
import com.dillon.starsectormarines.battle.mech.MechSections.Hit;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What section loss does to doctrine ({@code mech-locational-damage.md}, slice 3), asked of the
 * decisions directly: a crippled mech is a turret, a disarmed one declines to attack and withdraws
 * if it can walk, and a lost mount takes its reach with it.
 */
class MechDamageDoctrineTest {

    private static final MechVariant VARIANT = MechVariant.BULWARK;

    @Test
    void postureFollowsLegsAndWeapons() {
        assertEquals(Posture.FIGHT, MechDamageDoctrine.posture(false, true));
        assertEquals(Posture.TURRET, MechDamageDoctrine.posture(true, true));
        assertEquals(Posture.WITHDRAW, MechDamageDoctrine.posture(false, false));
        assertEquals(Posture.HOLD, MechDamageDoctrine.posture(true, false));
    }

    @Test
    void onlyALegsUpPostureWalksAndOnlyAnArmedOneAttacks() {
        assertTrue(Posture.FIGHT.walks());
        assertTrue(Posture.WITHDRAW.walks());
        assertFalse(Posture.TURRET.walks(), "a turret requests no movement");
        assertFalse(Posture.HOLD.walks());

        assertTrue(Posture.FIGHT.attacks());
        assertTrue(Posture.TURRET.attacks(), "a crippled mech keeps firing");
        assertFalse(Posture.WITHDRAW.attacks());
        assertFalse(Posture.HOLD.attacks());
    }

    @Test
    void onlyAFightingMechAnchorsItsLanceOrAScreenedAdvance() {
        assertTrue(Posture.FIGHT.anchorsOthers());
        assertFalse(Posture.TURRET.anchorsOthers(), "the lance is not held on a crippled mech");
        assertFalse(Posture.WITHDRAW.anchorsOthers());
        assertFalse(Posture.HOLD.anchorsOthers());
    }

    @Test
    void anUntouchedLoadoutFightsAndHasNoSections() {
        MechLoadoutComponent loadout = VARIANT.createLoadout(null);
        assertFalse(MechDamageDoctrine.crippled(loadout.sections()));
        assertTrue(MechDamageDoctrine.armed(loadout.mounts()));
        assertEquals(Posture.FIGHT, MechDamageDoctrine.postureOf(loadout));
        assertEquals(Posture.FIGHT, MechDamageDoctrine.postureOf(null));
    }

    @Test
    void losingEveryMountDisarmsButLosingOneDoesNot() {
        MechLoadoutComponent loadout = VARIANT.createLoadout(null);
        MechMountSlot[] slots = MechMountSlot.values();
        for (int i = 0; i < slots.length; i++) {
            assertTrue(MechDamageDoctrine.armed(loadout.mounts()), "still armed before " + slots[i]);
            loadout.destroyMount(slots[i]);
        }
        assertFalse(MechDamageDoctrine.armed(loadout.mounts()));
        assertEquals(Posture.WITHDRAW, MechDamageDoctrine.postureOf(loadout));
        assertFalse(MechDamageDoctrine.armed(null));
    }

    @Test
    void legsAtZeroMakeATurretAndThenAHold() {
        MechLoadoutComponent loadout = VARIANT.createLoadout(null);
        MechSections sections = loadout.sectionsFor(VARIANT.maxStructure, VARIANT.maxStructure,
                0f, 0f);
        sections.resolve(MechSection.LEGS, sections.structure(MechSection.LEGS), 100f, 0f,
                VARIANT.armorRating, new DurabilityModel.Resolution(), new Hit());

        assertTrue(MechDamageDoctrine.crippled(sections));
        assertEquals(Posture.TURRET, MechDamageDoctrine.postureOf(loadout));

        for (MechMountSlot slot : MechMountSlot.values()) loadout.destroyMount(slot);
        assertEquals(Posture.HOLD, MechDamageDoctrine.postureOf(loadout));
    }

    @Test
    void aLanceDeclinesAttackOnlyWhenNoStandingMechIsArmed() {
        assertFalse(MechDamageDoctrine.lanceDeclinesAttack(0, 0), "an empty lance declines nothing");
        assertFalse(MechDamageDoctrine.lanceDeclinesAttack(3, 1));
        assertTrue(MechDamageDoctrine.lanceDeclinesAttack(3, 0));
    }

    @Test
    void aDisarmedLanceWithdrawsOnlyIfSomeoneCanWalk() {
        assertTrue(MechDamageDoctrine.lanceWithdraws(2, 0, 1));
        assertFalse(MechDamageDoctrine.lanceWithdraws(2, 0, 0), "crippled and disarmed: hold");
        assertFalse(MechDamageDoctrine.lanceWithdraws(2, 1, 2), "an armed lance does not withdraw");
    }

    @Test
    void theReachLeftIsTheSurvivingMountsReach() {
        MechLoadoutComponent loadout = VARIANT.createLoadout(null);
        float arms = loadout.mount(MechMountSlot.ARMS).weaponDef().range;
        loadout.destroyMount(MechMountSlot.LEFT_SHOULDER);
        loadout.destroyMount(MechMountSlot.RIGHT_SHOULDER);
        assertEquals(arms, loadout.maxWeaponRange(), "doctrine reaches only for what survives");
        loadout.destroyMount(MechMountSlot.ARMS);
        assertEquals(0f, loadout.maxWeaponRange());
    }
}
