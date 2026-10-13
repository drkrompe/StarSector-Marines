package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.ColdStartProgress.Phase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColdStartProgressTest {
    private static void work(ColdStartProgress progress, float dt, boolean company, boolean crew, boolean hostile) {
        progress.advance(dt,true,true,true,company,crew,hostile,false,false);
    }

    @Test void unloadingAndPowerNeedCompanyCrewAndUncontestedGround() {
        var progress=new ColdStartProgress(false,false);
        work(progress,100,false,true,false);
        work(progress,100,true,false,false);
        work(progress,100,true,true,true);
        assertEquals(Phase.ESCORT,progress.phase());assertEquals(0,progress.work());
        progress.advance(100,true,true,false,true,true,false,false,false);
        assertEquals(Phase.ESCORT,progress.phase());
        work(progress,ColdStartProgress.UNLOAD_SECONDS,true,true,false);
        assertEquals(Phase.POWER,progress.phase());assertTrue(progress.report(0).cargoDelivered());
        work(progress,10,true,true,false);assertEquals(10,progress.work());
        work(progress,100,false,true,false);
        work(progress,100,true,false,false);
        work(progress,100,true,true,true);
        assertEquals(10,progress.work());assertEquals(Phase.POWER,progress.phase());
        work(progress,ColdStartProgress.POWER_SECONDS-10,true,true,false);
        assertEquals(Phase.CONSTRUCTION,progress.phase());assertTrue(progress.report(0).shorePower());
    }

    @Test void repairCannotCompleteBeforeThePhysicalFinalResponseOrWithoutSafeLivingCover() {
        var progress=new ColdStartProgress(true,true);
        progress.advance(1,true,true,false,false,false,false,true,false);
        assertEquals(Phase.REPAIR,progress.phase());
        work(progress,ColdStartProgress.REPAIR_SECONDS,true,true,false);
        assertEquals(Phase.REPAIR,progress.phase());assertEquals(ColdStartProgress.REPAIR_SECONDS,progress.work());
        progress.advance(0,true,true,false,false,true,false,false,true);
        progress.advance(0,true,true,false,true,false,false,false,true);
        progress.advance(0,true,true,false,true,true,true,false,true);
        assertEquals(Phase.REPAIR,progress.phase());assertFalse(progress.report(0).repaired());
        progress.advance(0,true,true,false,true,true,false,false,true);
        assertEquals(Phase.COMPLETE,progress.phase());assertTrue(progress.report(0).repaired());
        progress.advance(100,false,false,false,false,false,true,false,false);
        assertEquals(Phase.COMPLETE,progress.phase());
    }

    @Test void carrierLossAbortsUndeliveredCargoAndTechnicianLossRetainsCompletedWork() {
        var cargoLost=new ColdStartProgress(false,false);
        cargoLost.advance(1,true,false,false,true,true,false,false,false);
        assertEquals(Phase.FAILED,cargoLost.phase());assertFalse(cargoLost.report(0).cargoDelivered());
        work(cargoLost,100,true,true,false);assertEquals(Phase.FAILED,cargoLost.phase());
        var crewLost=new ColdStartProgress(true,true);
        crewLost.advance(1,false,false,false,true,true,false,true,true);
        var report=crewLost.report(2);
        assertEquals(Phase.FAILED,crewLost.phase());assertTrue(report.cargoDelivered());assertTrue(report.shorePower());
        assertFalse(report.repaired());assertFalse(report.turretConstructed());assertEquals(2,report.crewLost());
    }

    @Test void deliveredCargoNoLongerDependsOnTheCarrierAndRetryRequiresAnotherPhysicalTurret() {
        var progress=new ColdStartProgress(true,false);
        progress.advance(ColdStartProgress.POWER_SECONDS,true,false,false,true,true,false,false,false);
        assertEquals(Phase.CONSTRUCTION,progress.phase());
        progress.advance(1,true,false,false,true,true,false,true,false);
        assertTrue(progress.report(0).turretConstructed());
        progress.advance(1,false,false,false,true,true,false,false,false);
        assertEquals(Phase.FAILED,progress.phase());
        var retry=new ColdStartProgress(progress.report(2).cargoDelivered(),progress.report(2).shorePower());
        assertEquals(Phase.CONSTRUCTION,retry.phase());assertEquals(0,retry.work());assertFalse(retry.report(0).turretConstructed());
        retry.advance(1000,true,false,false,true,true,false,false,true);
        assertEquals(Phase.CONSTRUCTION,retry.phase());assertFalse(retry.report(0).repaired());
        retry.advance(1,true,false,false,true,true,false,true,false);
        assertEquals(Phase.REPAIR,retry.phase());assertEquals(0,retry.work());
    }

    @Test void invalidInitialMilestonesAndInvalidTimeAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new ColdStartProgress(false,true));
        var progress=new ColdStartProgress(false,false);
        for(float time:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->work(progress,time,true,true,false));
        assertEquals(Phase.ESCORT,progress.phase());assertEquals(0,progress.work());
    }
}
