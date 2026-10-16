package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.WarrantObligationLedger;
import com.dillon.starsectormarines.marine.ArmorTradition;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A held military warrant stands on the company record beside its employers. */
class CompanyHqWarrantRowsTest {

    @Test
    void aHeldWarrantNamesItsIssuerWhatItOpensAndHowItWasEarned() {
        CampaignState state = new CampaignState();
        MilitaryWarrants.grant(state, ArmorTradition.HEGEMONY,
                List.of(EquipmentTemplateCatalog.armorId("armor.arbalest")),
                MilitaryWarrants.Reason.STORY, 12);

        List<CompanyHqViewModel.EmployerRow> rows = CompanyHqViewModel.warrantRows(
                MilitaryWarrants.held(state), null, 0, key -> "crest/" + key + ".png");

        assertEquals(1, rows.size());
        CompanyHqViewModel.EmployerRow row = rows.get(0);
        assertEquals("Hegemony military warrant", row.name());
        assertTrue(row.record().startsWith("Opens 1 approved suit"), row.record());
        assertTrue(row.record().contains("authored grant"), row.record());
        assertEquals("crest/hegemony.png", row.crest());
    }

    @Test
    void aRowShowsItsNextDeadlineOrWhatRestoresIt() {
        CampaignState state = new CampaignState();
        MilitaryWarrants.grant(state, ArmorTradition.HEGEMONY,
                List.of(EquipmentTemplateCatalog.armorId("armor.arbalest")),
                MilitaryWarrants.Reason.STORY, 0);
        WarrantObligationLedger ledger = state.warrantObligations();
        ledger.open(ArmorTradition.HEGEMONY.key, 0);

        CompanyHqViewModel.EmployerRow active = CompanyHqViewModel.warrantRows(
                MilitaryWarrants.held(state), ledger, 56, key -> "").get(0);
        assertEquals("Active", active.score());
        assertTrue(active.record().endsWith("service due in 34 days"), active.record());

        ledger.term(ArmorTradition.HEGEMONY.key).suspend(60);
        CompanyHqViewModel.EmployerRow suspended = CompanyHqViewModel.warrantRows(
                MilitaryWarrants.held(state), ledger, 61, key -> "").get(0);
        assertEquals("Suspended", suspended.score());
        assertTrue(suspended.classes().contains("tone-danger"), suspended.classes());
        assertEquals("Restore: a service call at Favorable", suspended.record());
    }

    @Test
    void noWarrantNoRow() {
        assertTrue(CompanyHqViewModel.warrantRows(MilitaryWarrants.held(new CampaignState()),
                null, 0, key -> "").isEmpty());
    }
}
