package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Which mount an autonomous mech offers its reservoir to first, against armor and against everything else. */
class MechFireOrderTest {

    private static final float BULWARK = MechVariant.BULWARK.armorRating;

    private static MechWeaponMount[] mounts(MechWeaponComponent arms, MechWeaponComponent left,
                                            MechWeaponComponent right) {
        return new MechWeaponMount[]{
                arms == null ? null : new MechWeaponMount(MechMountSlot.ARMS, arms),
                left == null ? null : new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, left),
                right == null ? null : new MechWeaponMount(MechMountSlot.RIGHT_SHOULDER, right)};
    }

    @Test
    void anythingButArmorKeepsTheSlotOrder() {
        MechWeaponMount[] bulwark = mounts(MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15);
        assertArrayEquals(new int[]{0, 1, 2}, MechFireOrder.order(bulwark, 0f));
    }

    @Test
    void againstArmorTheLaunchersGoBeforeTheChaingun() {
        MechWeaponMount[] bulwark = mounts(MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15);
        assertArrayEquals(new int[]{1, 2, 0}, MechFireOrder.order(bulwark, BULWARK));
    }

    @Test
    void anArmGunThatDefeatsTheArmorKeepsItsPlace() {
        MechWeaponMount[] cannon = mounts(MechWeaponComponent.SINGLE_HEAVY_CANNON,
                MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15);
        assertArrayEquals(new int[]{0, 1, 2}, MechFireOrder.order(cannon, BULWARK));
    }

    @Test
    void anAntiArmorWarheadOutranksTheBastionAndAFragmentationRackGoesLast() {
        MechWeaponMount[] bastion = mounts(MechWeaponComponent.BASTION_AUTOCANNON,
                MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15_FRAG);
        assertArrayEquals(new int[]{1, 0, 2}, MechFireOrder.order(bastion, BULWARK));
    }

    @Test
    void equallyEffectiveMountsKeepTheirSlotOrder() {
        // The heavy cannon and an anti-armor rack both defeat a Bulwark's plate outright.
        MechWeaponMount[] cannon = mounts(MechWeaponComponent.SINGLE_HEAVY_CANNON,
                MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15);
        assertArrayEquals(new int[]{0, 1, 2}, MechFireOrder.order(cannon, BULWARK));
        // Against thin armor every round defeats it, so the order is the slot order again.
        MechWeaponMount[] chaingun = mounts(MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15);
        assertArrayEquals(new int[]{0, 1, 2}, MechFireOrder.order(chaingun, 4f));
    }

    @Test
    void anEmptyMountSortsLast() {
        MechWeaponMount[] hound = mounts(MechWeaponComponent.CHAINGUN, MechWeaponComponent.SRM_5, null);
        assertArrayEquals(new int[]{1, 0, 2}, MechFireOrder.order(hound, BULWARK));
    }
}
