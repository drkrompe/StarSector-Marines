package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LayeredAppearanceTest {

    @Test
    public void facingUsesContinuousNorthUpConvention() {
        assertEquals(0f, LayeredAppearance.facingDegrees(0, 1), 0.001f);
        assertEquals(-90f, LayeredAppearance.facingDegrees(1, 0), 0.001f);
        assertEquals(-180f, LayeredAppearance.facingDegrees(0, -1), 0.001f);
        assertEquals(90f, LayeredAppearance.facingDegrees(-1, 0), 0.001f);
    }

    @Test
    public void approachFacingUsesBoundedShortestArc() {
        assertEquals(-174f, LayeredAppearance.approachFacing(
                180f, -90f, 6f), 0.001f);
        assertEquals(174f, LayeredAppearance.approachFacing(
                -180f, 90f, 6f), 0.001f);
        assertEquals(12f, LayeredAppearance.approachFacing(
                10f, 12f, 6f), 0.001f);
    }

    @Test
    public void helmetLookUsesShortestArcAndPlausibleClamp() {
        assertEquals(20f, LayeredAppearance.headLookDegrees(170f, -170f), 0.001f);
        assertEquals(-20f, LayeredAppearance.headLookDegrees(-170f, 170f), 0.001f);
        assertEquals(65f, LayeredAppearance.headLookDegrees(0f, 120f), 0.001f);
        assertEquals(-65f, LayeredAppearance.headLookDegrees(0f, -120f), 0.001f);
    }

    @Test
    public void locomotionAlternatesOneVisibleFootAtATime() {
        assertEquals(1f, LayeredAppearance.leftFootReveal(0.25f), 0.001f);
        assertEquals(0f, LayeredAppearance.rightFootReveal(0.25f), 0.001f);
        assertEquals(0f, LayeredAppearance.leftFootReveal(0.75f), 0.001f);
        assertEquals(1f, LayeredAppearance.rightFootReveal(0.75f), 0.001f);
    }

    @Test
    public void firingRecoilPeaksMidPoseAndReturns() {
        assertEquals(0f, LayeredAppearance.recoilSw(LayeredAppearance.POSE_FIRING, 0f), 0.001f);
        assertTrue(LayeredAppearance.recoilSw(LayeredAppearance.POSE_FIRING, 0.5f) > 0f);
        assertEquals(0f, LayeredAppearance.recoilSw(LayeredAppearance.POSE_FIRING, 1f), 0.001f);
        assertTrue(LayeredAppearance.recoilSw(LayeredAppearance.POSE_ROCKET_FIRE, 0.5f)
                > LayeredAppearance.recoilSw(LayeredAppearance.POSE_FIRING, 0.5f));
    }

    @Test
    public void meleeSwipeStartsAtImpactAndRetractsSmoothly() {
        assertEquals(0f, LayeredAppearance.meleeSwipe(LayeredAppearance.POSE_IDLE, 0f),
                0.001f);
        assertEquals(1f, LayeredAppearance.meleeSwipe(LayeredAppearance.POSE_FIRING, 0f),
                0.001f);
        assertTrue(LayeredAppearance.meleeSwipe(LayeredAppearance.POSE_FIRING, 0.5f) > 0f);
        assertEquals(0f, LayeredAppearance.meleeSwipe(LayeredAppearance.POSE_FIRING, 1f),
                0.001f);
    }

    @Test
    public void strikingClawAlternatesAcrossTheWrappedLocomotionCycle() {
        assertTrue(LayeredAppearance.leftClawStrikes(0.25f));
        assertTrue(LayeredAppearance.leftClawStrikes(1.25f));
        assertFalse(LayeredAppearance.leftClawStrikes(0.75f));
    }

    @Test
    public void mechTorsoTracksWithinASeventyDegreeHipTraverse() {
        assertEquals(-45f, LayeredMechAppearance.torsoFacing(0f, -45f), 0.001f);
        assertEquals(-70f, LayeredMechAppearance.torsoFacing(0f, -120f), 0.001f);
        assertEquals(70f, LayeredMechAppearance.torsoFacing(0f, 120f), 0.001f);
        assertEquals(-110f, LayeredMechAppearance.torsoFacing(180f, -90f), 0.001f);
    }

    @Test
    public void mechFootStepHasMechanicalPlantAndHoldStages() {
        assertEquals(0f, LayeredMechAppearance.mechanicalFootReveal(0.10f, false), 0.001f);
        assertEquals(1f, LayeredMechAppearance.mechanicalFootReveal(0.40f, false), 0.001f);
        assertEquals(1f, LayeredMechAppearance.mechanicalFootReveal(0.52f, false), 0.001f);
        assertEquals(0f, LayeredMechAppearance.mechanicalFootReveal(0.90f, false), 0.001f);
    }

    @Test
    public void houndUsesAWiderFootStanceThanBroadChassis() {
        assertEquals(0.32f, LayeredMechAppearance.footLateralOffset(
                LayeredMechAppearance.CHASSIS_HOUND), 0.001f);
        assertEquals(0.17f, LayeredMechAppearance.footLateralOffset(
                LayeredMechAppearance.CHASSIS_CLEAN), 0.001f);
        assertEquals(0.17f, LayeredMechAppearance.footLateralOffset(
                LayeredMechAppearance.CHASSIS_SIROCCO), 0.001f);
        assertEquals(-0.20f, LayeredMechAppearance.footRearOffset(
                LayeredMechAppearance.CHASSIS_HOUND), 0.001f);
        assertEquals(-0.28f, LayeredMechAppearance.footRearOffset(
                LayeredMechAppearance.CHASSIS_CLEAN), 0.001f);
    }

    @Test
    public void infantryPrimaryWeaponsMapToDistinctLayerFamilies() {
        assertEquals(LayeredWeaponFamily.RIFLE, LayeredWeaponFamily.fromPrimary(null));
        assertEquals(LayeredWeaponFamily.LASER_GUN,
                LayeredWeaponFamily.fromPrimary(MarineWeapon.PULSE_RIFLE));
        assertEquals(LayeredWeaponFamily.SMG,
                LayeredWeaponFamily.fromPrimary(MarineWeapon.SMG));
        assertEquals(LayeredWeaponFamily.DMR,
                LayeredWeaponFamily.fromPrimary(MarineWeapon.DMR));
    }
}
