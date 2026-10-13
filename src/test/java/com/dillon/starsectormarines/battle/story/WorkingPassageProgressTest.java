package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.WorkingPassageProgress.Phase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkingPassageProgressTest {
    private static void step(WorkingPassageProgress p,float dt,boolean company,boolean hostile,boolean arrived,boolean built,boolean response,boolean departed){
        p.advance(dt,true,false,company,hostile,arrived,built,response,departed);
    }
    private static WorkingPassageProgress spool(){
        var p=new WorkingPassageProgress();
        step(p,5,true,false,true,false,true,false);
        step(p,0,true,false,true,true,true,false);
        step(p,5,true,false,true,false,true,false);
        step(p,0,true,false,true,true,true,false);
        step(p,5,true,false,true,false,true,false);
        assertEquals(Phase.SPOOL,p.phase());return p;
    }
    @Test void gainRequiresCompanyAndActualArrivalAndConstructionRequiresResponse(){
        var p=new WorkingPassageProgress();
        step(p,100,false,false,true,true,true,false);assertEquals(0,p.work());
        step(p,5,true,false,false,true,true,false);assertEquals(Phase.BEND,p.phase());
        step(p,0,true,false,true,false,true,false);assertEquals(Phase.BEND_BUILD,p.phase());
        step(p,100,true,false,true,false,true,false);assertEquals(Phase.BEND_BUILD,p.phase());
        step(p,100,true,false,true,true,false,false);assertEquals(Phase.BEND_BUILD,p.phase());
        step(p,0,true,false,true,true,true,false);assertEquals(Phase.NARROWS,p.phase());
    }
    @Test void contestedOccupationResetsClearanceAndSpoolPauses(){
        var p=new WorkingPassageProgress();
        step(p,4,true,false,false,false,true,false);
        step(p,1,true,true,true,false,true,false);assertEquals(0,p.work());assertEquals(Phase.BEND,p.phase());
        p=spool();step(p,20,true,false,true,false,true,false);
        step(p,100,true,true,true,false,true,false);step(p,100,false,false,true,false,true,false);
        step(p,100,true,false,false,false,true,false);assertEquals(20,p.work());assertEquals(Phase.SPOOL,p.phase());
    }
    @Test void spoolAndPhysicalDepartureAreIndependentGates(){
        var p=spool();
        step(p,WorkingPassageProgress.SPOOL_SECONDS,true,false,true,false,false,true);assertEquals(Phase.SPOOL,p.phase());
        step(p,0,true,false,true,false,true,false);assertEquals(Phase.DEPARTURE,p.phase());
        step(p,10000,true,false,true,false,true,false);assertFalse(p.report(0).departed());
        step(p,0,true,false,true,false,true,true);assertTrue(p.report(2).departed());assertEquals(2,p.report(2).militiaLost());
        p.fail(true);assertEquals(Phase.COMPLETE,p.phase());
    }
    @Test void disabledHullNeverAwardsDepartureAndReportClampsConfirmedLosses(){
        var p=spool();p.advance(1,true,true,true,false,true,true,true,true);
        assertEquals(Phase.FAILED,p.phase());assertTrue(p.report(99).disabled());assertTrue(p.report(-1).aborted());
        assertFalse(p.report(0).departed());assertEquals(8,p.report(99).militiaLost());assertEquals(0,p.report(-1).militiaLost());
        for(float dt:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY}){
            var fresh=new WorkingPassageProgress();assertThrows(IllegalArgumentException.class,()->step(fresh,dt,true,false,true,true,true,true));
        }
    }
    @Test void physicalDepartureCompletesAfterHullCeasesBeingAnActiveAnchor(){
        var p=spool();
        step(p,WorkingPassageProgress.SPOOL_SECONDS,true,false,true,false,true,false);
        p.advance(0,false,false,true,false,false,false,true,true);
        assertEquals(Phase.COMPLETE,p.phase());
        assertTrue(p.report(0).departed());
        assertFalse(p.report(0).disabled());
        assertFalse(p.report(0).aborted());

        var timeout=spool();
        step(timeout,WorkingPassageProgress.SPOOL_SECONDS,true,false,true,false,true,false);
        timeout.advance(0,false,true,true,false,false,false,true,false);
        assertEquals(Phase.FAILED,timeout.phase());
        assertFalse(timeout.report(0).departed());
    }
}
