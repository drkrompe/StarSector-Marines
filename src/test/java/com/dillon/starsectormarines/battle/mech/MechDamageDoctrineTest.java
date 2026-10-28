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
 * What section loss does to doctrine ({@code mechs-nouns.md}, Section loss), asked of the
 * decisions directly: a crippled mech limps on and fights, a disarmed one declines to attack and
 * withdraws, and a lost mount takes its reach with it.
 */
class MechDamageDoctrineTest {

    private static final MechVariant VARIANT = MechVariant.BULWARK;

    @Test
    void postureFollowsLegsAndWeapons() {
        assertEquals(Posture.FIGHT, MechDamageDoctrine.posture(false, true));
        assertEquals(Posture.LIMPING, MechDamageDoctrine.posture(true, true));
        assertEquals(Posture.WITHDRAW, MechDamageDoctrine.posture(false, false));
        assertEquals(Posture.WITHDRAW, MechDamageDoctrine.posture(true, false), "crippled legs still carry it off");
    }

    @Test
    void onlyAnArmedPostureAttacks() {
        assertTrue(Posture.FIGHT.attacks());
        assertTrue(Posture.LIMPING.attacks(), "a crippled mech keeps fighting");
        assertFalse(Posture.WITHDRAW.attacks());
    }

    @Test
    void onlyAFightingMechAnchorsItsLanceOrAScreenedAdvance() {
        assertTrue(Posture.FIGHT.anchorsOthers());
        assertFalse(Posture.LIMPING.anchorsOthers(), "the lance is not held to a crippled mech's pace");
        assertFalse(Posture.WITHDRAW.anchorsOthers());
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
    void legsAtZeroLimpAndThenWithdraw() {
        MechLoadoutComponent loadout = VARIANT.createLoadout(null);
        MechSections sections = loadout.sectionsFor(VARIANT.maxStructure, VARIANT.maxStructure,
                0f, 0f);
        sections.resolve(MechSection.LEGS, sections.structure(MechSection.LEGS), 100f, 0f,
                VARIANT.armorRating, new DurabilityModel.Resolution(), new Hit());

        assertTrue(MechDamageDoctrine.crippled(sections));
        assertEquals(Posture.LIMPING, MechDamageDoctrine.postureOf(loadout));

        for (MechMountSlot slot : MechMountSlot.values()) loadout.destroyMount(slot);
        assertEquals(Posture.WITHDRAW, MechDamageDoctrine.postureOf(loadout));
    }

    @Test
    void aLanceDeclinesAttackOnlyWhenNoStandingMechIsArmed() {
        assertFalse(MechDamageDoctrine.lanceDeclinesAttack(0, 0), "an empty lance declines nothing");
        assertFalse(MechDamageDoctrine.lanceDeclinesAttack(3, 1));
        assertTrue(MechDamageDoctrine.lanceDeclinesAttack(3, 0));
    }

    @Test
    void theReachLeftIsTheSurvivingMountsReach() {
        MechLoadoutComponent loadout = VARIANT.createLoadout(null);
        float arms = loadout.armsRange();
        loadout.destroyMount(MechMountSlot.LEFT_SHOULDER);
        loadout.destroyMount(MechMountSlot.RIGHT_SHOULDER);
        assertEquals(arms, loadout.maxWeaponRange(), "doctrine reaches only for what survives");
        for (MechMountSlot arm : VARIANT.armMounts()) loadout.destroyMount(arm);
        assertEquals(0f, loadout.maxWeaponRange());
    }
}
