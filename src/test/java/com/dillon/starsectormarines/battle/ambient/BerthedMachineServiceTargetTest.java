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

    @Test
    void closeServiceStandKeepsCollisionClearanceAndTheAuthoredSide() {
        BerthedMachineServiceTarget target = new BerthedMachineServiceTarget(
                10.5f, 10f, 0f, 1f, .92f, .92f);
        var stand = target.standFrom(8.5f, 9.5f, .95f, (x, y) -> true);
        var contact = target.focusFrom(stand.worldX(), stand.worldY());
        assertEquals(9.5f, stand.worldX());
        assertEquals(9.5f, stand.worldY());
        assertTrue(Math.hypot(stand.worldX() - 10.5f, stand.worldY() - 10f) >= .95f);
        assertTrue(Math.hypot(stand.worldX() - contact.worldX(),
                stand.worldY() - contact.worldY()) < .45f);
    }

    @Test
    void blockedOrAlreadyReservedCloseStandKeepsTheAuthoredAccessPoint() {
        BerthedMachineServiceTarget target = new BerthedMachineServiceTarget(
                10.5f, 10f, 0f, 1f, .92f, .92f);
        var stand = target.standFrom(8.5f, 9.5f, .95f, (x, y) -> x != 9);
        assertEquals(8.5f, stand.worldX());
        assertEquals(9.5f, stand.worldY());
    }

    @Test
    void longVehicleStandStaysOutsideItsBodyWhenFacingEast() {
        BerthedMachineServiceTarget target = new BerthedMachineServiceTarget(
                20.5f, 30.5f, 1f, 0f, 1f, 3f);
        var stand = target.standFrom(26.5f, 30.5f, 1f, (x, y) -> true);
        assertEquals(23.5f, stand.worldX());
        assertEquals(30.5f, stand.worldY());
    }

    @Test
    void headAccessDoesNotTakeTheFlankWorkersCloseCell() {
        BerthedMachineServiceTarget target = new BerthedMachineServiceTarget(
                146.5f, 26f, 0f, 1f, .92f, .92f);
        var head = target.standFrom(145.5f, 22.5f, .95f, (x, y) -> true);
        var flank = target.standFrom(144.5f, 25.5f, .95f,
                (x, y) -> x != (int) head.worldX() || y != (int) head.worldY());
        assertEquals(146.5f, head.worldX());
        assertEquals(24.5f, head.worldY());
        assertEquals(145.5f, flank.worldX());
        assertEquals(25.5f, flank.worldY());
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
