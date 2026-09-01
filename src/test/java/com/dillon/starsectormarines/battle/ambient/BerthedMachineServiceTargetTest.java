package com.dillon.starsectormarines.battle.ambient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BerthedMachineServiceTargetTest {

    @Test
    void workFocusLandsInsideTheBodyRatherThanOnTheStandingCell() {
        BerthedMachineServiceTarget target = new BerthedMachineServiceTarget(
                10f, 10f, 0f, 1f, 1f, 3f);

        BerthedMachineServiceTarget.Focus flank = target.focusFrom(14f, 10f);
        BerthedMachineServiceTarget.Focus nose = target.focusFrom(10f, 15f);

        assertEquals(10.78f, flank.worldX(), 0.001f);
        assertEquals(10f, flank.worldY(), 0.001f);
        assertEquals(10f, nose.worldX(), 0.001f);
        assertEquals(12.34f, nose.worldY(), 0.001f);
        assertTrue(inside(target, flank));
        assertTrue(inside(target, nose));
    }

    @Test
    void anElongatedVehicleKeepsItsOwnShapeWhenTheBerthRotates() {
        BerthedMachineServiceTarget target = new BerthedMachineServiceTarget(
                20f, 30f, 4f, 0f, 1f, 3f);

        BerthedMachineServiceTarget.Focus nose = target.focusFrom(26f, 30f);
        BerthedMachineServiceTarget.Focus flank = target.focusFrom(20f, 24f);

        assertEquals(22.34f, nose.worldX(), 0.001f);
        assertEquals(30f, nose.worldY(), 0.001f);
        assertEquals(20f, flank.worldX(), 0.001f);
        assertEquals(29.22f, flank.worldY(), 0.001f);
        assertTrue(inside(target, nose));
        assertTrue(inside(target, flank));
    }

    private static boolean inside(BerthedMachineServiceTarget target,
                                  BerthedMachineServiceTarget.Focus focus) {
        float dx = focus.worldX() - target.centerX();
        float dy = focus.worldY() - target.centerY();
        float lateral = dx * target.forwardY() - dy * target.forwardX();
        float longitudinal = dx * target.forwardX() + dy * target.forwardY();
        float normalized = lateral * lateral / (target.halfBeam() * target.halfBeam())
                + longitudinal * longitudinal / (target.halfLength() * target.halfLength());
        return normalized < 1f;
    }
}
