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
        assertEquals("L", MechFireGroups.badge(hound, 0, hound[MechMountSlot.ARMS.ordinal()]));
        assertEquals("R", MechFireGroups.badge(hound, 0, hound[MechMountSlot.LEFT_SHOULDER.ordinal()]));
    }

    @Test
    void bulwarkPutsBothArmGunsOnTheLeftButtonAndBothShoulderRacksOnTheRight() {
        MechWeaponMount[] bulwark = of(MechVariant.BULWARK);
        assertEquals(LEFT_ARM | RIGHT_ARM, MechFireGroups.primaryMask(bulwark));
        assertEquals(LEFT | RIGHT, MechFireGroups.secondaryMask(bulwark));
    }

    @Test
    void arcingLaunchersJoinTheRightButton() {
        MechWeaponMount[] sirocco = of(MechVariant.SIROCCO);
        assertEquals(ARMS, MechFireGroups.primaryMask(sirocco));
        assertEquals(LEFT | RIGHT, MechFireGroups.secondaryMask(sirocco), "LRMs drop on the cursor point");
        assertEquals("R", MechFireGroups.badge(sirocco, 0, sirocco[MechMountSlot.LEFT_SHOULDER.ordinal()]));
    }

    @Test
    void allMissileChassisKeepsEverythingOnTheLeftButton() {
        MechWeaponMount[] racks = mounts(null, MechWeaponComponent.SRM_5, MechWeaponComponent.PIONEER_ROCKET_CRADLE);
        assertEquals(LEFT | RIGHT, MechFireGroups.primaryMask(racks));
        assertEquals(0, MechFireGroups.secondaryMask(racks));
    }

    @Test
    void numberKeyRepointsOnlyTheLeftButton() {
        MechWeaponMount[] hound = of(MechVariant.HOUND);
        int leftShoulderKey = MechMountSlot.LEFT_SHOULDER.ordinal() + 1;
        assertEquals(ARMS, MechFireGroups.leftMask(hound, 0));
        assertEquals(LEFT, MechFireGroups.leftMask(hound, leftShoulderKey));
        assertEquals(LEFT, MechFireGroups.rightMask(hound, leftShoulderKey));
        assertEquals("LR", MechFireGroups.badge(hound, leftShoulderKey, hound[MechMountSlot.LEFT_SHOULDER.ordinal()]));
        assertEquals("", MechFireGroups.badge(hound, leftShoulderKey, hound[MechMountSlot.ARMS.ordinal()]));
        assertEquals(0, MechFireGroups.leftMask(hound, MechMountSlot.RIGHT_SHOULDER.ordinal() + 1),
                "an empty hardpoint fires nothing");
        assertEquals(0, MechFireGroups.leftMask(hound, 9));
        assertEquals(0, MechFireGroups.leftMask(hound, -1));
    }
}
