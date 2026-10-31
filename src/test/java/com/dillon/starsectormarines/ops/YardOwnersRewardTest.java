package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.story.YardOwnersProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The Yard's Owners pays its fee once; its recovered sections are sold in the store, never handed over. */
class YardOwnersRewardTest {
    @Test void victoryPaysTheFeeOnceAcrossAReload() throws Exception {
        var p = afterWarrant(); var m = AgreusArcCampaign.offer(p,"Agreus"); p.begin(m.id,"agreus","Agreus");
        var outcome = victory(m);
        assertEquals(36000,AgreusArcCampaign.settleYard(p,outcome));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { p=(AgreusArcProgress)in.readObject(); }
        assertTrue(p.yardCompleted()); assertNull(p.nextMissionId());
        assertEquals(-1,AgreusArcCampaign.settleYard(p,outcome),"a replayed receipt pays nothing");
    }
    @Test void foreignOrIncompleteReceiptsDoNotConsumeTheAttempt() {
        var p=afterWarrant(); var m=AgreusArcCampaign.offer(p,"Agreus"); p.begin(m.id,"agreus","Agreus");
        assertEquals(-1,AgreusArcCampaign.settleYard(p,victory(Mission.builder(m).source(MissionSource.RECURRING).build())));
        var incomplete=MissionOutcome.builder().mission(m).victory(true).payoutEarned(36000)
                .yardOwners(new YardOwnersProgress.Report(true,true,false,true,true,false,0,0)).build();
        assertEquals(-1,AgreusArcCampaign.settleYard(p,incomplete)); assertFalse(p.yardCompleted());
        assertEquals(36000,AgreusArcCampaign.settleYard(p,victory(m)));
        assertTrue(p.yardCompleted());
    }
    static MissionOutcome victory(Mission m) {
        return MissionOutcome.builder().mission(m).victory(true).payoutEarned(36000)
                .yardOwners(new YardOwnersProgress.Report(true,true,true,true,true,false,0,0)).build();
    }
    static AgreusArcProgress afterWarrant() {
        var p=new AgreusArcProgress(); String id=p.missionId(); p.begin(id,"agreus");
        p.settle(id,"agreus",true,26000,true,true,true,true,true,true,0,0,false);
        id=p.nextMissionId();p.begin(id,"agreus");p.settleWarrant(id,"agreus",true,30000,true,true,true,true,false,0,0);
        return p;
    }
}
