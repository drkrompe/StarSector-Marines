package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.battle.story.ColdStartProgress;
import com.dillon.starsectormarines.battle.story.DistrictEscortProgress;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress;
import com.dillon.starsectormarines.battle.story.WorkingPassageProgress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Debug key's Win reports an outright win; each arc must accept it as one. */
class ForcedWinSettlementTest {

    @Test
    void waterUnderSealAcceptsTheWonReport() {
        var p = new AgreusArcProgress();
        String id = p.missionId();
        assertTrue(p.begin(id, "agreus"));
        var r = new WaterUnderSealProgress().wonReport();
        assertEquals(AgreusArcProgress.PAYMENT, p.settle(id, "agreus", true, AgreusArcProgress.PAYMENT, r.cargoDelivered(),
                r.northRoomReconnected(), r.southRoomReconnected(), r.turretConstructed(), r.attackResolved(),
                r.companySurvived(), r.techniciansLost(), r.militiaLost(), r.cargoLost()));
    }

    @Test
    void districtEscortAcceptsTheWonReport() {
        var p = new AgreusDistrictEscortProgress(AgreusYardAgreement.HOUSE_CUSTODY);
        String id = p.missionId();
        assertTrue(p.begin(id, "agreus", "Agreus"));
        var r = new DistrictEscortProgress().wonReport();
        assertEquals(AgreusDistrictEscortProgress.PAYMENT, p.settle(id, "agreus", true, AgreusDistrictEscortProgress.PAYMENT,
                r.linkedUp(), r.cargoDelivered(), r.partyDelivered(), r.companyAtEgress(), r.cargoLost(),
                r.partyLost(), r.constablesLost()));
    }

    @Test
    void theWorkingShipChapterChainAcceptsWonReportsThroughDeparture() {
        var p = new ShepherdRecoveryProgress();
        String id = p.missionId();
        p.begin(id, "Asharu");
        assertEquals(ShepherdRecoveryProgress.PAYMENT, p.settle(id, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true));
        id = p.missionId();
        p.begin(id, "Asharu");
        var cold = new ColdStartProgress(false, false).wonReport();
        assertEquals(ShepherdRecoveryProgress.COLD_PAYMENT, p.settleColdStart(id, "Asharu", true, ShepherdRecoveryProgress.COLD_PAYMENT,
                cold.crewLost(), cold.cargoDelivered(), cold.shorePower(), cold.turretConstructed(), cold.repaired()));
        id = p.missionId();
        p.begin(id, "Asharu");
        var passage = new WorkingPassageProgress().wonReport();
        assertTrue(p.acceptsWorkingPassage(id, "Asharu", true, 0, passage.militiaLost(), passage.departed(),
                passage.disabled(), passage.aborted(), "member-1", 12));
    }
}
