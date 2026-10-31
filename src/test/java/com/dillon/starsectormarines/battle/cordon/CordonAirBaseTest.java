package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirTasking.Ground;
import com.dillon.starsectormarines.battle.cordon.CordonAirBase.Candidate;
import com.dillon.starsectormarines.battle.cordon.CordonAirBase.Pick;
import com.dillon.starsectormarines.battle.cordon.CordonAirBase.Reason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Where the air base's apron is laid, and what its own sorties go at. */
class CordonAirBaseTest {

    @Test
    void theApronIsTheRowOfStandsNearestWhereItIsSoughtOnFreeGround() {
        // Free everywhere but a band through the anchor's rows: the row moves just clear of it.
        CordonAirBase.CellTest free = (x, y) -> x >= 0 && y >= 0 && x < 100 && y < 100 && (y < 45 || y > 55);
        List<int[]> row = CordonAirBase.site(free, 50, 50, 4, 6, 2, 30);
        assertNotNull(row);
        assertEquals(4, row.size());
        int y = row.get(0)[1];
        // A footprint two cells either side keeps the row's middle eight rows off the band's middle.
        assertEquals(8, Math.abs(y - 50));
        for (int i = 1; i < row.size(); i++) {
            assertEquals(6, row.get(i)[0] - row.get(i - 1)[0]);
            assertEquals(y, row.get(i)[1]);
        }
    }

    @Test
    void noApronWhereNoRowFits() {
        CordonAirBase.CellTest narrow = (x, y) -> x >= 40 && x < 52;
        assertNull(CordonAirBase.site(narrow, 45, 45, 4, 6, 2, 20));
    }

    @Test
    void aCallingRedoubtsSwarmComesBeforeASeenMassAndABoreLast() {
        Candidate bore = new Candidate(Reason.BORE, 1, 1, Ground.WORKS, 0, 0f, 0f);
        Candidate mass = new Candidate(Reason.MASS, 2, 2, Ground.OPEN, 20, 20f, 0f);
        Candidate relief = new Candidate(Reason.RELIEF, 3, 3, Ground.OPEN, 5, 5f, 0f);
        Pick pick = CordonAirBase.choose(List.of(bore, mass, relief), 1, 1);
        assertEquals(Reason.RELIEF, pick.reason());
        assertEquals(AirPoolKind.FIGHTER, pick.kind());
        assertEquals(Reason.MASS, CordonAirBase.choose(List.of(bore, mass), 1, 1).reason());
    }

    @Test
    void aBoreTakesOnlyABomber() {
        Candidate bore = new Candidate(Reason.BORE, 1, 1, Ground.OPEN, 0, 0f, 0f);
        assertNull(CordonAirBase.choose(List.of(bore), 0, 2));
        assertEquals(AirPoolKind.BOMBER, CordonAirBase.choose(List.of(bore), 1, 0).kind());
    }

    @Test
    void noFighterOnWorksAndNothingOverGroundBelievedTooDefended() {
        Candidate works = new Candidate(Reason.MASS, 1, 1, Ground.WORKS, 6, 6f, 0f);
        assertNull(CordonAirBase.choose(List.of(works), 0, 2));
        Candidate hot = new Candidate(Reason.MASS, 1, 1, Ground.OPEN, 6, 6f, 0.9f);
        // A fighter's low pass takes half again a bomber's share: 1.35 of an airframe is past the ceiling.
        assertNull(CordonAirBase.choose(List.of(hot), 0, 2));
        assertEquals(AirPoolKind.BOMBER, CordonAirBase.choose(List.of(hot), 1, 0).kind());
    }

    @Test
    void theHeavierTargetWinsWithinAReason() {
        Candidate thin = new Candidate(Reason.MASS, 1, 1, Ground.OPEN, 4, 4f, 0f);
        Candidate thick = new Candidate(Reason.MASS, 9, 9, Ground.OPEN, 9, 9f, 0f);
        Pick pick = CordonAirBase.choose(List.of(thin, thick), 1, 1);
        assertEquals(9f, pick.x());
    }
}
