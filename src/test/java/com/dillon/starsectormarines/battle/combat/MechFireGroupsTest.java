package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The partition of a chassis' direct-fire hardpoints between the two mouse buttons. */
class MechFireGroupsTest {
    private static MechWeaponMount[] mounts(MechWeaponComponent arms, MechWeaponComponent left,
                                            MechWeaponComponent right) {
        MechWeaponMount[] out = new MechWeaponMount[MechMountSlot.values().length];
        put(out, MechMountSlot.ARMS, arms);
        put(out, MechMountSlot.LEFT_SHOULDER, left);
        put(out, MechMountSlot.RIGHT_SHOULDER, right);
        return out;
    }

    private static MechWeaponMount[] of(MechVariant v) {
        MechWeaponMount[] out = new MechWeaponMount[MechMountSlot.values().length];
        for (MechMountSlot slot : MechMountSlot.values()) put(out, slot, v.stockWeapon(slot));
        return out;
    }

    private static void put(MechWeaponMount[] out, MechMountSlot slot, MechWeaponComponent component) {
        if (component != null) out[slot.ordinal()] = new MechWeaponMount(slot, component);
    }

    private static final int ARMS = bit(MechMountSlot.ARMS), LEFT_ARM = bit(MechMountSlot.LEFT_ARM),
            RIGHT_ARM = bit(MechMountSlot.RIGHT_ARM), LEFT = bit(MechMountSlot.LEFT_SHOULDER),
            RIGHT = bit(MechMountSlot.RIGHT_SHOULDER);

    private static int bit(MechMountSlot slot) {
        return 1 << slot.ordinal();
    }

    @Test
    void houndSplitsItsMachinegunFromItsRocketPod() {
        MechWeaponMount[] hound = of(MechVariant.HOUND);
        assertEquals(ARMS, MechFireGroups.primaryMask(hound), "the nose chaingun is the left button");
        assertEquals(LEFT, MechFireGroups.secondaryMask(hound), "the SRM pod is the right button");
        assertEquals("L", MechFireGroups.badge(hound, MechTriggerPicks.LINKED, hound[MechMountSlot.ARMS.ordinal()]));
        assertEquals("R", MechFireGroups.badge(hound, MechTriggerPicks.LINKED, hound[MechMountSlot.LEFT_SHOULDER.ordinal()]));
    }

    @Test
    void bulwarkPutsBothArmGunsOnTheLeftButtonAndBothShoulderRacksOnTheRight() {
        MechWeaponMount[] bulwark = of(MechVariant.BULWARK);
        assertEquals(LEFT_ARM | RIGHT_ARM, MechFireGroups.primaryMask(bulwark));
        assertEquals(LEFT | RIGHT, MechFireGroups.secondaryMask(bulwark));
    }

    @Test
    void arcingLaunchersJoinTheRightButton() {
        MechWeaponMount[] warden = of(MechVariant.WARDEN);
        assertEquals(ARMS, MechFireGroups.primaryMask(warden));
        assertEquals(LEFT | RIGHT, MechFireGroups.secondaryMask(warden), "LRMs drop on the cursor point");
        assertEquals("R", MechFireGroups.badge(warden, MechTriggerPicks.LINKED, warden[MechMountSlot.LEFT_SHOULDER.ordinal()]));
    }

    @Test
    void allMissileChassisKeepsEverythingOnTheLeftButton() {
        MechWeaponMount[] racks = mounts(null, MechWeaponComponent.SRM_5, MechWeaponComponent.PIONEER_ROCKET_CRADLE);
        assertEquals(LEFT | RIGHT, MechFireGroups.primaryMask(racks));
        assertEquals(0, MechFireGroups.secondaryMask(racks));
    }

    private static int key(MechMountSlot slot) {
        return slot.ordinal() + 1;
    }

    @Test
    void eachTriggerCyclesLinkedThenEachMountOfItsGroupInHardpointOrder() {
        MechWeaponMount[] bulwark = of(MechVariant.BULWARK);
        int linked = MechFireGroups.LINKED;
        assertArrayEquals(new int[]{linked, key(MechMountSlot.LEFT_ARM), key(MechMountSlot.RIGHT_ARM)},
                MechFireGroups.members(bulwark, FireTrigger.LEFT));
        assertArrayEquals(new int[]{linked, key(MechMountSlot.LEFT_SHOULDER), key(MechMountSlot.RIGHT_SHOULDER)},
                MechFireGroups.members(bulwark, FireTrigger.RIGHT));
        int pick = linked;
        for (int expected : new int[]{key(MechMountSlot.LEFT_ARM), key(MechMountSlot.RIGHT_ARM), linked}) {
            pick = MechFireGroups.next(bulwark, FireTrigger.LEFT, pick);
            assertEquals(expected, pick);
        }
        assertEquals(key(MechMountSlot.RIGHT_ARM), MechFireGroups.member(bulwark, FireTrigger.LEFT, 2),
                "key 3 jumps the left button to the second gun");
        assertEquals(-1, MechFireGroups.member(bulwark, FireTrigger.LEFT, 3));
        assertEquals(linked, MechFireGroups.next(bulwark, FireTrigger.LEFT, key(MechMountSlot.LEFT_SHOULDER)),
                "a pick that is not on the list falls back to LINKED");
    }

    @Test
    void linkedFiresTheWholeGroupAndAOneMountPickHoldsTheRest() {
        MechWeaponMount[] bulwark = of(MechVariant.BULWARK);
        assertEquals(LEFT_ARM | RIGHT_ARM, MechTriggerPicks.LINKED.leftMask(bulwark));
        var one = new MechTriggerPicks(key(MechMountSlot.RIGHT_ARM), key(MechMountSlot.LEFT_SHOULDER));
        assertEquals(RIGHT_ARM, one.leftMask(bulwark));
        assertEquals(LEFT, one.rightMask(bulwark));
        assertEquals("", MechFireGroups.badge(bulwark, one, bulwark[MechMountSlot.LEFT_ARM.ordinal()]));
        assertEquals("L", MechFireGroups.group(bulwark, bulwark[MechMountSlot.LEFT_ARM.ordinal()]),
                "an unpicked gun stays listed on its button");
        assertEquals(0, MechFireGroups.mask(bulwark, FireTrigger.LEFT, key(MechMountSlot.LEFT_SHOULDER)),
                "a rack is never the left button's pick");
    }

    @Test
    void cyclingSkipsADestroyedHardpointButKeepsAnEmptyRack() {
        MechWeaponMount[] bulwark = of(MechVariant.BULWARK);
        bulwark[MechMountSlot.LEFT_ARM.ordinal()] = null;
        bulwark[MechMountSlot.LEFT_SHOULDER.ordinal()].ammo = 0;
        assertArrayEquals(new int[]{MechFireGroups.LINKED}, MechFireGroups.members(bulwark, FireTrigger.LEFT),
                "one gun left is its own LINKED");
        assertTrue(MechFireGroups.isMember(bulwark, FireTrigger.RIGHT, key(MechMountSlot.LEFT_SHOULDER)));
        MechWeaponMount[] gunsOnly = mounts(MechWeaponComponent.NOSE_CHAINGUN, null, null);
        assertEquals(0, MechFireGroups.members(gunsOnly, FireTrigger.RIGHT).length);
    }
}
