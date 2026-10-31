package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirTasking.Ground;
import com.dillon.starsectormarines.battle.cordon.CordonAirBase.Candidate;
import com.dillon.starsectormarines.battle.cordon.CordonAirBase.Pick;
import com.dillon.starsectormarines.battle.cordon.CordonAirBase.Reason;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.layout.Airfield;
import com.dillon.starsectormarines.battle.world.gen.layout.Site;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Where the air base's lot is reserved, and what its own sorties go at. */
class CordonAirBaseTest {

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

    @Test
    void theAirBaseIsAnAlliedFieldLotInTheRearCornerFrontingTheFront() {
        // The rear is the south (low y), so the front lies toward high y: the approach the strip fronts.
        Airfield south = CordonPlan.airBase(true, true, 560, 336);
        assertEquals(AirbaseLot.Size.FIELD, south.size());
        assertEquals(LandingPad.Purpose.ALLIED_AIRFIELD, south.purpose());
        assertEquals(AirbaseLot.Facing.NORTH, south.facing());
        Site held = south.reservation();
        assertEquals(CordonPlan.AIR_BASE_REAR, held.y0());
        assertEquals(CordonPlan.AIR_BASE_SIDE, held.x0());
        // The rear to the north, the installation to the west: the far corner, the whole reservation on the map.
        Airfield north = CordonPlan.airBase(false, false, 560, 336);
        assertEquals(AirbaseLot.Facing.SOUTH, north.facing());
        Site far = north.reservation();
        assertEquals(336 - 1 - CordonPlan.AIR_BASE_REAR, far.y1());
        assertEquals(560 - 1 - CordonPlan.AIR_BASE_SIDE, far.x1());
    }
}
