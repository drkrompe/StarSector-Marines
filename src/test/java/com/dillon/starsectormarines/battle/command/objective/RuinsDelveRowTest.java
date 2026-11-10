package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.RuinsDelveRow.Picture;
import com.dillon.starsectormarines.battle.command.objective.RuinsDelveRow.Stand;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective.RuinsPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsDelveRowTest {

    private static Picture picture(RuinsPhase phase, int vaultRoute, Stand stand, boolean fresh) {
        return new Picture(phase, 7, 18, 24, 1, 0, 3, 1, 42, 2, 4, vaultRoute, stand, fresh);
    }

    private static Picture stirring(RuinsPhase phase, boolean dugIn, boolean wayBackHeld) {
        return new Picture(phase, 7, 18, 24, 1, 0, 3, 1, 42, 2, 4, -1, null, false,
                new RuinsDelveRow.Stir("ruinsPressureRoused", 0.4f, "ruinsDelveRowReserveThinned", dugIn, wayBackHeld));
    }

    @Test
    void theStirFillsTheBarAndLeadsTheLineInTheDelveOnly() {
        assertEquals(-1f, RuinsDelveRow.item("R", picture(RuinsPhase.DELVE, -1, null, false)).progress(),
                "a site with no stir has no bar");
        var outside = RuinsDelveRow.item("R", stirring(RuinsPhase.APPROACH, false, false));
        assertEquals(-1f, outside.progress(), "nothing stirs before the entry");
        var moving = RuinsDelveRow.item("R", stirring(RuinsPhase.DELVE, false, false));
        assertEquals(0.4f, moving.progress());
        assertTrue(moving.blocker().isEmpty());
        assertTrue(moving.progressText().endsWith(RuinsDelveRow.glance(stirring(RuinsPhase.DELVE, false, false))),
                "the level leads, the company's glance follows");
        assertFalse(moving.progressText().startsWith(RuinsDelveRow.glance(stirring(RuinsPhase.DELVE, false, false))));
    }

    @Test
    void aSittingCompanyIsWarnedOnTheLine() {
        var row = RuinsDelveRow.item("R", stirring(RuinsPhase.DELVE, true, false));
        assertFalse(row.blocker().isEmpty());
        assertEquals(row.blocker(), row.progressText(), "the warning takes the compact line");
    }

    @Test
    void aWayBackAPushHoldsReadsContested() {
        assertFalse(RuinsDelveRow.reads(stirring(RuinsPhase.DELVE, false, false))
                .equals(RuinsDelveRow.reads(stirring(RuinsPhase.DELVE, false, true))));
    }

    @Test
    void theRowHeadsWithThePhaseAndThePullOutPoint() {
        var approach = RuinsDelveRow.item("R", picture(RuinsPhase.APPROACH, -1, null, false));
        assertEquals("APPROACH / PULL OUT: LANDING", approach.phase());
        assertTrue(approach.progressText().startsWith("7 in a circle"), approach.progressText());
        assertEquals("R-delve", approach.id(), "the row sorts straight after the vault's card");

        var delve = RuinsDelveRow.item("R", picture(RuinsPhase.DELVE, -1, null, false));
        assertEquals("DELVE / PULL OUT: ENTRY", delve.phase());
        assertEquals("Layer 2 of 4", delve.steps());
    }

    @Test
    void theVaultRouteShowsOnlyOnceTheVaultIsSighted() {
        assertFalse(RuinsDelveRow.reads(picture(RuinsPhase.DELVE, -1, null, false)).contains("Vault route"));
        assertTrue(RuinsDelveRow.reads(picture(RuinsPhase.DELVE, 60, null, false)).contains("Vault route 60"));
    }

    @Test
    void aFreshStandsTallyLeadsTheRowThenGivesWayToTheCompany() {
        Stand held = new Stand("Layer 2 held", 6, 1, 2, "ruinsCloseHeld", "ruinsSurveyScavengersComms");
        var fresh = RuinsDelveRow.item("R", picture(RuinsPhase.DELVE, -1, held, true));
        assertEquals("Layer 2 held / 6 down / 2 finds / next held", fresh.progressText());
        assertTrue(fresh.instruction().contains("Pull out, or press on."), "the whole report stays in the instruction");
        var later = RuinsDelveRow.item("R", picture(RuinsPhase.DELVE, -1, held, false));
        assertTrue(later.progressText().startsWith("7 in a circle"), later.progressText());
    }

    @Test
    void aStandReportsItsTallyTheNextLayersReadingAndTheChoice() {
        assertEquals("", RuinsDelveRow.stand(null), "nothing to report before the first stand");
        String held = RuinsDelveRow.stand(new Stand("Layer 2 held", 6, 1, 2, "ruinsCloseStronglyHeld",
                "ruinsSurveyScavengersComms"));
        assertTrue(held.startsWith("Layer 2 held: 6 down, 1 machines lost, 2 finds banked."), held);
        assertTrue(held.contains("strongly held"), held);
        assertTrue(held.endsWith("Pull out, or press on."), held);
        String last = RuinsDelveRow.stand(new Stand("Layer 4 held", 0, 0, 0, null, null));
        assertFalse(last.contains("Next layer"), "no reading when no layer lies ahead");
    }
}
