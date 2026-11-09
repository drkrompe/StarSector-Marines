package com.dillon.starsectormarines.battle.command.objective;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApronHoldTest {

    private static final float DT = 1f;

    @Test void theCompanyTakesAnApronByHoldingItUncontestedForTheWholeTime() {
        ApronHold apron = new ApronHold(3f);
        apron.tick(true, true, false, DT);
        apron.tick(true, true, false, DT);
        assertFalse(apron.held());
        apron.tick(true, true, false, DT);
        assertTrue(apron.held());
        assertTrue(apron.everTaken());
    }

    @Test void aHostileOnTheApronStartsTheTakingAgain() {
        ApronHold apron = new ApronHold(3f);
        apron.tick(true, true, false, DT);
        apron.tick(true, true, false, DT);
        apron.tick(true, true, true, DT);
        apron.tick(true, true, false, DT);
        apron.tick(true, true, false, DT);
        assertFalse(apron.held(), "the count started again when a hostile stood on it");
    }

    @Test void hostilesRetakeAnApronOnlyWithNoCompanyOnIt() {
        ApronHold apron = taken();
        apron.tick(false, true, true, DT);
        apron.tick(false, true, true, DT);
        apron.tick(false, true, true, DT);
        assertTrue(apron.held(), "a company soldier on the apron, settled or not, stops a retake");
        apron.tick(false, false, true, DT);
        apron.tick(false, false, true, DT);
        apron.tick(false, false, true, DT);
        assertFalse(apron.held());
        assertTrue(apron.everTaken(), "a lost entrance was still taken once");
    }

    @Test void aLostApronIsTakenBackTheSameWay() {
        ApronHold apron = taken();
        for (int i = 0; i < 3; i++) apron.tick(false, false, true, DT);
        for (int i = 0; i < 3; i++) apron.tick(true, true, false, DT);
        assertTrue(apron.held());
    }

    private static ApronHold taken() {
        ApronHold apron = new ApronHold(3f);
        for (int i = 0; i < 3; i++) apron.tick(true, true, false, DT);
        assertTrue(apron.held());
        return apron;
    }
}
