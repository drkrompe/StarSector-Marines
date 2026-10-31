package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Limited Warrant pays its fee once; its Specter section is sold in the store, never handed over. */
class LimitedWarrantRewardTest {
    @Test void victoryPaysTheFeeOnce() {
        var p = afterWeir(); var m = AgreusArcCampaign.offer(p,"Agreus");
        assertTrue(p.begin(m.id,"agreus","Agreus"));
        var outcome = victory(m);
        assertEquals(30000,AgreusArcCampaign.settleWarrant(p,outcome));
        assertTrue(p.warrantCompleted());
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,outcome),"a replayed receipt pays nothing");
    }
    @Test void foreignSourceAndIncompleteResultCannotConsumeTheAttempt() {
        var p=afterWeir(); var m=AgreusArcCampaign.offer(p,"Agreus"); p.begin(m.id,"agreus","Agreus");
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,victory(Mission.builder(m).source(MissionSource.RECURRING).build())));
        var incomplete=MissionOutcome.builder().mission(m).victory(true).payoutEarned(30000)
                .limitedWarrant(new LimitedWarrantProgress.Report(true,true,false,false,false,0,0)).build();
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,incomplete));
        assertFalse(p.warrantCompleted());
        assertEquals(30000,AgreusArcCampaign.settleWarrant(p,victory(m)));
    }
    private static MissionOutcome victory(Mission m) {
        return MissionOutcome.builder().mission(m).victory(true).payoutEarned(30000)
                .limitedWarrant(new LimitedWarrantProgress.Report(true,true,true,true,false,0,0)).build();
    }
    private static AgreusArcProgress afterWeir() {
        var p=new AgreusArcProgress(); String id=p.missionId(); p.begin(id,"agreus");
        p.settle(id,"agreus",true,26000,true,true,true,true,true,true,0,0,false); return p;
    }
}
