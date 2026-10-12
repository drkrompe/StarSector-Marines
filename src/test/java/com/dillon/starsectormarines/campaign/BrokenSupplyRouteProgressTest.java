package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import java.io.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrokenSupplyRouteProgressTest {
    private BrokenSupplyRouteProgress junctionWon() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id = p.missionId(); assertTrue(p.begin(id));
        assertEquals(12000, p.settle(id,true,null));
        return p;
    }
    @Test void junctionRetriesAndOnlyExplicitCommitFixesBranch() {
        var p = new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");
        assertFalse(p.commit(BrokenSupplyRouteProgress.LIFT));
        String failed = p.missionId(); assertTrue(p.begin(failed)); assertFalse(p.begin(failed));
        assertEquals(0,p.settle(failed,false,null)); assertEquals(-1,p.settle(failed,true,null));
        String won = p.missionId(); assertNotEquals(failed,won); assertTrue(p.begin(won));
        assertEquals(12000,p.settle(won,true,null)); assertEquals(-1,p.settle(won,true,null));
        assertTrue(p.choicePending()); assertNull(p.missionId()); assertFalse(p.commit(99));
        assertTrue(p.commit(BrokenSupplyRouteProgress.RELAY)); assertFalse(p.commit(BrokenSupplyRouteProgress.LIFT));
        assertFalse(p.successful()); assertEquals(0,p.job()); assertEquals(12000,p.storyPayment());
    }
    @Test void freightCommitmentGoesDirectlyToConvoyAndRecurringJobsNeverReplayStoryPayment() {
        var p = junctionWon(); p.commit(BrokenSupplyRouteProgress.RELAY);
        assertEquals(BrokenSupplyRouteProgress.THUNDER, p.operation());
        assertEquals(12000, p.storyPayment());
        String failed = p.missionId(); p.begin(failed);
        assertEquals(0, p.settle(failed, false, null,
                new FreightRunProgress.Outcome(p.freight().previewAttempt(), List.of(), List.of(), false)));
        assertFalse(p.commit(BrokenSupplyRouteProgress.LIFT)); assertEquals(0, p.job());
        String convoy = p.missionId(); p.begin(convoy);
        var cargo = new FreightRunProgress.Outcome(p.freight().previewAttempt(), List.of(0,1,2), List.of(), true);
        assertEquals(18000, p.settle(convoy, true, null, cargo));
        assertTrue(p.closed()); assertTrue(p.successful()); assertEquals(30000, p.storyPayment());
        String repeat = p.missionId(); assertNotEquals(convoy, repeat); p.begin(repeat);
        assertEquals(-1, p.settle(convoy, true, null, cargo)); assertEquals(12000, p.settle(repeat, true, null));
        assertEquals(-1, p.settle(repeat, true, null)); assertEquals(12000, p.recurringPayment());
        assertEquals(1, p.jobsCompleted()); assertNull(p.storyCrew());
    }
    @Test void rescuedAndDeadPersistThroughCampaignSaveAndRetryPaysCumulativeOnlyOnce() throws Exception {
        CampaignState state = new CampaignState(); state.brokenSupplyRoute = junctionWon();
        var p = state.brokenSupplyRoute; p.commit(BrokenSupplyRouteProgress.LIFT);
        String first = p.missionId(); var preview = p.crew().previewAttempt(); p.begin(first);
        var retreat = new LastLiftProgress.Outcome(preview,List.of(0,1),List.of(2),false);
        assertEquals(0,p.settle(first,false,retreat));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(state); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { state = (CampaignState) in.readObject(); }
        p = state.brokenSupplyRoute;
        assertEquals("jangala",p.planetId()); assertEquals(List.of(3,4,5,6,7,8,9,10,11),p.crew().stranded());
        String retry = p.missionId(); p.begin(retry);
        assertEquals(-1,p.settle(first,false,retreat));
        var rescue = new LastLiftProgress.Outcome(p.crew().previewAttempt(),p.crew().stranded(),List.of(),true);
        assertEquals(9500,p.payment(retry,true,rescue)); assertEquals(9500,p.settle(retry,true,rescue));
        assertEquals(-1,p.settle(retry,true,rescue)); assertEquals(21500,p.storyPayment());
        assertEquals(11,p.storyCrew().rescuedCount()); assertEquals(1,p.storyCrew().deadCount());
        assertEquals(12,p.crew().stranded().size()); assertNotEquals(preview.siteId(),p.crew().previewAttempt().siteId());
        String repeat = p.missionId(); p.begin(repeat);
        assertEquals(10000,p.settle(repeat,true,new LastLiftProgress.Outcome(p.crew().previewAttempt(),p.crew().stranded(),List.of(),true)));
        assertEquals(21500,p.storyPayment()); assertEquals(10000,p.recurringPayment());
    }
    @Test void allResolvedAfterDefeatClosesWithoutManufacturingVictoryOrUnlock() {
        var p = junctionWon(); p.commit(BrokenSupplyRouteProgress.LIFT);
        String first = p.missionId(); p.begin(first);
        p.settle(first,false,new LastLiftProgress.Outcome(p.crew().previewAttempt(),List.of(0),List.of(),false));
        String last = p.missionId(); p.begin(last);
        assertEquals(0,p.settle(last,false,new LastLiftProgress.Outcome(p.crew().previewAttempt(),List.of(),p.crew().stranded(),false)));
        assertTrue(p.closed()); assertFalse(p.successful()); assertFalse(p.available());
        assertNull(p.missionId()); assertEquals(12000,p.storyPayment()); assertEquals(0,p.job());
    }
    @Test void foreignOrMismatchedRescueCannotConsumeAnAttempt() {
        var p = junctionWon(); p.commit(BrokenSupplyRouteProgress.LIFT); String id = p.missionId(); p.begin(id);
        var foreign = new LastLiftProgress("foreign"); var result = new LastLiftProgress.Outcome(foreign.beginAttempt(),List.of(0),List.of(),false);
        assertEquals(-1,p.settle(id,false,result)); assertEquals(-1,p.settle(id,false,null));
        assertEquals(12,p.crew().stranded().size());
        var own = new LastLiftProgress.Outcome(p.crew().previewAttempt(),p.crew().stranded(),List.of(),true);
        assertEquals(-1,p.settle(id,false,own)); assertEquals(10000,p.settle(id,true,own));
    }
    @Test void reflectionSaveRetainsCommitmentAndActiveAttemptAndUnknownValuesFailClosed() throws Exception {
        var p = junctionWon(); p.commit(BrokenSupplyRouteProgress.LIFT); String id=p.missionId(); p.begin(id);
        var codec = new XStream(new StaxDriver()) {
            @Override protected void setupConverters() {
                registerConverter(new ReflectionConverter(getMapper(),getReflectionProvider()),PRIORITY_VERY_LOW);
                registerConverter(new StringConverter()); registerConverter(new IntConverter()); registerConverter(new BooleanConverter());
            }
        };
        XStream.setupDefaultSecurity(codec); codec.allowTypes(new Class[]{BrokenSupplyRouteProgress.class,LastLiftProgress.class});
        var copy=(BrokenSupplyRouteProgress)codec.fromXML(codec.toXML(p));
        assertEquals(id,copy.missionId()); assertFalse(copy.commit(BrokenSupplyRouteProgress.RELAY)); assertFalse(copy.begin(id));
        assertEquals(0,copy.settle(id,false,new LastLiftProgress.Outcome(copy.crew().previewAttempt(),List.of(),List.of(),false)));
        var branch=BrokenSupplyRouteProgress.class.getDeclaredField("branch");branch.setAccessible(true);branch.setInt(copy,99);
        assertFalse(copy.available()); assertNull(copy.missionId()); assertFalse(copy.commit(BrokenSupplyRouteProgress.LIFT));
        var version=BrokenSupplyRouteProgress.class.getDeclaredField("version");version.setAccessible(true);version.setInt(p,99);
        assertEquals(-1,p.settle(id,true,null)); assertFalse(p.available());
    }
}
