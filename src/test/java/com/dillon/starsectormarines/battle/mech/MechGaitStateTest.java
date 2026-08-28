package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechGaitStateTest {

    @Test
    void supportingFootStaysWorldPlantedWhileTheOtherFootSwings() {
        MechGaitState gait = MechGaitState.create(5.5f, 5.5f, 0f, MechVariant.HOUND);
        float plantedRightX = gait.rightFootX();
        float plantedRightY = gait.rightFootY();
        float initialLeftY = gait.leftFootY();
        float bodyY = 5.5f;

        for (int tick = 0; tick < 12 && gait.swingFoot() == MechGaitState.NO_SWING_FOOT;
             tick++) {
            bodyY += MechVariant.HOUND.moveSpeed * BattleSimulation.TICK_DT;
            gait.advance(5.5f, bodyY, 0f, 0f, BattleSimulation.TICK_DT);
        }

        assertEquals(MechGaitState.LEFT_FOOT, gait.swingFoot());
        for (int tick = 0; tick < 3; tick++) {
            bodyY += MechVariant.HOUND.moveSpeed * BattleSimulation.TICK_DT;
            gait.advance(5.5f, bodyY, 0f, 0f, BattleSimulation.TICK_DT);
        }

        assertEquals(plantedRightX, gait.rightFootX(), 0.0001f);
        assertEquals(plantedRightY, gait.rightFootY(), 0.0001f,
                "the support pad must not inherit actor translation");
        assertTrue(gait.leftFootY() > initialLeftY,
                "the swing pad advances toward its predicted landing");
        assertTrue(gait.waistOffsetX() > 0f,
                "the waist transfers toward the planted right foot");
    }

    @Test
    void pivotingKeepsOneFootFixedAndRotatesOnlyTheSwingingPad() {
        MechGaitState gait = MechGaitState.create(5.5f, 5.5f, 0f, MechVariant.BULWARK);
        float plantedRightX = gait.rightFootX();
        float plantedRightY = gait.rightFootY();
        float plantedRightFacing = gait.rightFootFacing();
        float initialLeftFacing = gait.leftFootFacing();
        float hipFacing = 0f;

        for (int tick = 0; tick < 12 && gait.swingFoot() == MechGaitState.NO_SWING_FOOT;
             tick++) {
            hipFacing -= 4f;
            gait.advance(5.5f, 5.5f, hipFacing, -120f, BattleSimulation.TICK_DT);
        }

        assertEquals(MechGaitState.LEFT_FOOT, gait.swingFoot());
        while (gait.swingFoot() != MechGaitState.NO_SWING_FOOT) {
            gait.advance(5.5f, 5.5f, hipFacing, 0f, BattleSimulation.TICK_DT);
        }

        assertEquals(plantedRightX, gait.rightFootX(), 0.0001f);
        assertEquals(plantedRightY, gait.rightFootY(), 0.0001f);
        assertEquals(plantedRightFacing, gait.rightFootFacing(), 0.0001f,
                "the support foot retains its planted world yaw");
        assertNotEquals(initialLeftFacing, gait.leftFootFacing(), 0.01f,
                "the lifted pad adopts the new hip bearing at touchdown");
    }

    @Test
    void teleportReplantsBothFeetAndClearsInertialOffsets() {
        MechGaitState gait = MechGaitState.create(5.5f, 5.5f, 0f, MechVariant.HOUND);
        gait.advance(5.5f, 5.8f, 0f, 0f, BattleSimulation.TICK_DT);

        gait.advance(12.5f, 12.5f, 90f, 0f, BattleSimulation.TICK_DT);

        assertEquals(MechGaitState.NO_SWING_FOOT, gait.swingFoot());
        assertEquals(0f, gait.waistOffsetX(), 0.0001f);
        assertEquals(0f, gait.waistOffsetY(), 0.0001f);
        assertTrue(gait.leftFootX() > 12.5f && gait.rightFootX() > 12.5f,
                "a west-facing neutral stance replants both pads behind the body");
    }
}
