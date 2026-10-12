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
        if (arms != null) out[0] = new MechWeaponMount(MechMountSlot.ARMS, arms);
        if (left != null) out[1] = new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, left);
        if (right != null) out[2] = new MechWeaponMount(MechMountSlot.RIGHT_SHOULDER, right);
        return out;
    }

    private static MechWeaponMount[] of(MechVariant v) {
        return mounts(v.arms, v.leftShoulder, v.rightShoulder);
    }

    private static final int ARMS = 1, LEFT = 2, RIGHT = 4;

    @Test
    void houndSplitsItsMachinegunFromItsRocketPod() {
        MechWeaponMount[] hound = of(MechVariant.HOUND);
        assertEquals(ARMS, MechFireGroups.primaryMask(hound), "the nose chaingun is the left button");
        assertEquals(LEFT, MechFireGroups.secondaryMask(hound), "the SRM pod is the right button");
        assertEquals("L", MechFireGroups.badge(hound, 0, hound[0]));
        assertEquals("R", MechFireGroups.badge(hound, 0, hound[1]));
    }

    @Test
    void bulwarkPutsBothShoulderRacksOnTheRightButton() {
        MechWeaponMount[] bulwark = of(MechVariant.BULWARK);
        assertEquals(ARMS, MechFireGroups.primaryMask(bulwark));
        assertEquals(LEFT | RIGHT, MechFireGroups.secondaryMask(bulwark));
    }

    @Test
    void indirectLaunchersBelongToNeitherGroup() {
        MechWeaponMount[] sirocco = of(MechVariant.SIROCCO);
        assertEquals(ARMS, MechFireGroups.primaryMask(sirocco));
        assertEquals(0, MechFireGroups.secondaryMask(sirocco), "LRMs cannot be aimed at a cursor point");
        assertEquals("", MechFireGroups.badge(sirocco, 0, sirocco[1]));
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
        assertEquals(ARMS, MechFireGroups.leftMask(hound, 0));
        assertEquals(LEFT, MechFireGroups.leftMask(hound, 2));
        assertEquals(LEFT, MechFireGroups.rightMask(hound, 2));
        assertEquals("LR", MechFireGroups.badge(hound, 2, hound[1]));
        assertEquals("", MechFireGroups.badge(hound, 2, hound[0]));
        assertEquals(0, MechFireGroups.leftMask(hound, 3), "an empty hardpoint fires nothing");
        assertEquals(0, MechFireGroups.leftMask(hound, 9));
        assertEquals(0, MechFireGroups.leftMask(hound, -1));
    }
}
