package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFrame.CompanyReport;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Reason;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompanySupportTacticsTest {
    @Test void waitsForAssemblyThenScreensRecoveryDetourWithoutRallyChurn() {
        var tactics = new CompanySupportTactics();
        var waiting = tactics.intent(7, 20, 50, List.of(new CompanyReport(1, 23, 52, false, true)));
        assertEquals(Reason.DEPOT_WAIT_FOR_COMPANY, waiting.reason());
        assertEquals(20, waiting.x());
        assertEquals(waiting, tactics.intent(7, 22, 51, List.of()));
        var escort = tactics.intent(7, 22, 51, List.of(new CompanyReport(1, 40, 46, true, true)));
        assertEquals(40, escort.x());
        assertEquals(Reason.DEPOT_SCREEN_COMPANY, escort.reason());
        assertEquals(escort, tactics.intent(7, 39, 46, List.of(new CompanyReport(1, 42, 47, true, true))));
        var recovery = tactics.intent(7, 43, 46, List.of(new CompanyReport(1, 82, 38, true, true)));
        assertEquals(82, recovery.x());
        assertEquals(38, recovery.y());
        assertEquals(recovery, tactics.intent(7, 81, 38, List.of(new CompanyReport(1, 82, 38, true, true))));
    }

    @Test void retainsItsCompanyPartnerUntilLostAndUsesHoundOnlyWithoutInfantry() {
        var tactics = new CompanySupportTactics();
        var first = tactics.intent(7, 20, 50, List.of(new CompanyReport(1, 25, 50, true, true),
                new CompanyReport(2, 40, 50, true, true)));
        assertEquals(1, first.leadId());
        assertEquals(1, tactics.intent(7, 39, 50, List.of(new CompanyReport(2, 40, 50, true, true),
                new CompanyReport(1, 30, 50, true, true))).leadId());
        var hound = new CompanyReport(9, 80, 40, true, false);
        assertEquals(Reason.DEPOT_WAIT_FOR_COMPANY, tactics.intent(7, 39, 50,
                List.of(new CompanyReport(2, 40, 50, false, true), hound)).reason());
        assertEquals(9, tactics.intent(7, 39, 50, List.of(hound)).leadId());
        var noCompany = tactics.intent(7, 45, 45, List.of());
        assertEquals(80, noCompany.x());
        assertEquals(Reason.DEPOT_WAIT_FOR_COMPANY, noCompany.reason());
    }
}
