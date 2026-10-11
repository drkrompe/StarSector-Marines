package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FreightRunProgressTest {
    private BrokenSupplyRouteProgress finale() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id=p.missionId();p.begin(id);p.settle(id,true,null);p.commit(3);
        id=p.missionId();p.begin(id);p.settle(id,true,null);
        assertEquals(4,p.operation()); assertFalse(p.closed()); return p;
    }
    @Test void receiptsSurviveSaveAndRetryOnlyMovesUnresolvedCargo() throws Exception {
        var p=finale(); String id=p.missionId(); p.begin(id);
        var first=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(0),List.of(1),false);
        assertEquals(0,p.settle(id,false,null,first)); assertEquals(-1,p.settle(id,false,null,first));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(var out=new ObjectOutputStream(bytes)){out.writeObject(p);}
        try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){p=(BrokenSupplyRouteProgress)in.readObject();}
        assertEquals(List.of(2),p.freight().waiting()); assertEquals(1,p.freight().deliveredCount()); assertEquals(1,p.freight().lostCount());
        String retry=p.missionId();p.begin(retry);
        var last=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(2),List.of(),true);
        assertEquals(14000,p.payment(retry,true,null,last)); assertEquals(14000,p.settle(retry,true,null,last));
        assertEquals(-1,p.settle(retry,true,null,last)); assertEquals(42000,p.storyPayment());
        assertTrue(p.successful());assertEquals(3,p.operation());assertEquals(1,p.job());
        String recurring=p.missionId();p.begin(recurring);assertEquals(12000,p.settle(recurring,true,null));
        assertEquals(42000,p.storyPayment());assertEquals(12000,p.recurringPayment());
    }
    @Test void allCargoLostClosesWithoutPaymentOrRecurringWork() {
        var p=finale();String id=p.missionId();p.begin(id);
        var lost=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(),List.of(0,1,2),false);
        assertEquals(0,p.settle(id,false,null,lost));assertTrue(p.closed());assertFalse(p.successful());assertFalse(p.available());
        assertEquals(28000,p.storyPayment());assertNull(p.missionId());
    }
    @Test void forgedVictoryForeignAndMissingReceiptsCannotConsumeAttempt() {
        var p=finale();String id=p.missionId();p.begin(id);
        assertEquals(-1,p.settle(id,true,null));
        var noDelivery=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(),List.of(0,1,2),true);
        assertEquals(-1,p.settle(id,true,null,noDelivery));
        var foreign=new FreightRunProgress.Outcome(new FreightRunProgress("other").previewAttempt(),List.of(0,1,2),List.of(),true);
        assertEquals(-1,p.settle(id,true,null,foreign));assertEquals(List.of(0,1,2),p.freight().waiting());
        assertThrows(IllegalArgumentException.class,()->new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(0),List.of(0),false));
        assertThrows(IllegalArgumentException.class,()->new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(0),List.of(),true));
        var valid=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(0,1,2),List.of(),true);
        assertEquals(18000,p.settle(id,true,null,valid));
    }
    @Test void previouslyCompletedRelaySavesKeepTheirRecurringEntitlement() throws Exception {
        var p=finale();
        // The prior schema ended at Relay Nine: new fields are absent/default on reflection load.
        for(String name:List.of("closed","successful")){var f=BrokenSupplyRouteProgress.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(p,true);}
        var job=BrokenSupplyRouteProgress.class.getDeclaredField("job");job.setAccessible(true);job.setInt(p,1);
        var relay=BrokenSupplyRouteProgress.class.getDeclaredField("relayWon");relay.setAccessible(true);relay.setBoolean(p,false);
        var freight=BrokenSupplyRouteProgress.class.getDeclaredField("freight");freight.setAccessible(true);freight.set(p,null);
        assertTrue(p.readable());assertEquals(3,p.operation());assertTrue(p.available());
        String id=p.missionId();p.begin(id);assertEquals(12000,p.settle(id,true,null));assertEquals(28000,p.storyPayment());
    }
}
