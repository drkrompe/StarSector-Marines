package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dillon.starsectormarines.battle.story.SalvageRightsProgress.Phase.*;

class SalvageRightsProgressTest {
    @Test void absentCompanyAndContestedGroundCannotEarnCapture(){
        var p=new SalvageRightsProgress();
        p.advance(100,false,false,true,true,true);assertEquals(RECEIVING,p.phase());
        p.advance(100,true,true,true,true,true);assertEquals(RECEIVING,p.phase());
        p.advance(4,true,false,false,true,false);assertEquals(WORKSHOP,p.phase());
    }
    @Test void livingEngineerCompanyAndSafeGroundAreAllRequiredAndResponseCannotBeSkipped(){
        var p=inspection();
        p.advance(100,true,false,false,true,true);assertEquals(0,p.inspection());
        p.advance(100,false,false,true,true,true);assertEquals(0,p.inspection());
        p.advance(100,true,true,true,true,true);assertEquals(0,p.inspection());
        p.advance(30,true,false,true,true,false);assertEquals(30,p.inspection());
        p.advance(100,true,true,true,true,false);assertEquals(30,p.inspection());
        p.advance(30,true,false,true,true,false);assertEquals(INSPECTION,p.phase());
        p.advance(1,true,false,true,true,true);assertEquals(SECURED,p.phase());
        p.advance(1,false,true,false,false,false);assertEquals(SECURED,p.phase());
    }
    @Test void lossOfInspectionPartyFailsEvenWithAllEnemiesDead(){
        var p=inspection();p.advance(1,true,false,false,false,true);assertEquals(FAILED,p.phase());
    }
    @Test void captureRequiresFourContinuousSecondsAndPublishesItsActualGates(){
        var p=new SalvageRightsProgress();
        p.advance(2,true,false,false,true,false);
        assertEquals(2,p.capture());assertTrue(p.companyAtTarget());assertFalse(p.contested());
        p.advance(1,true,true,false,true,false);
        assertEquals(0,p.capture());assertTrue(p.contested());
        p.advance(3,true,false,true,true,true);
        assertEquals(RECEIVING,p.phase());assertEquals(3,p.capture());
        assertTrue(p.engineerPresent());assertTrue(p.responseCommitted());
        p.advance(1,true,false,true,true,true);
        assertEquals(WORKSHOP,p.phase());assertEquals(0,p.capture());
    }
    private SalvageRightsProgress inspection(){var p=new SalvageRightsProgress();for(int i=0;i<3;i++)p.advance(4,true,false,false,true,false);return p;}
}
