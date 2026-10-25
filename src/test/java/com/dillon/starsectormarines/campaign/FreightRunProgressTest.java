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
        String id=p.missionId();p.begin(id);p.settle(id,true,null);
        assertEquals(4,p.operation()); assertFalse(p.closed()); return p;
    }
    @Test void aDefeatLeavesNoTraceAndTheRetryFieldsTheWholeCohort() throws Exception {
        var p=finale(); String id=p.missionId(); p.begin(id);
        var first=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(0),List.of(1),false);
        assertEquals(0,p.settle(id,false,null,first)); assertEquals(-1,p.settle(id,false,null,first));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(var out=new ObjectOutputStream(bytes)){out.writeObject(p);}
        try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){p=(BrokenSupplyRouteProgress)in.readObject();}
        assertEquals(List.of(0,1,2),p.freight().waiting()); assertEquals(0,p.freight().deliveredCount()); assertEquals(0,p.freight().lostCount());
        assertFalse(p.closed());
        String retry=p.missionId();assertNotEquals(id,retry);p.begin(retry);
        var won=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(0,2),List.of(1),true);
        assertEquals(14000,p.payment(retry,true,null,won)); assertEquals(14000,p.settle(retry,true,null,won));
        assertEquals(-1,p.settle(retry,true,null,won)); assertEquals(26000,p.storyPayment());
        assertTrue(p.successful());assertEquals(3,p.operation());assertEquals(1,p.job());
        String recurring=p.missionId();p.begin(recurring);assertEquals(12000,p.settle(recurring,true,null));
        assertEquals(26000,p.storyPayment());assertEquals(12000,p.recurringPayment());
    }
    @Test void losingEveryCarrierIsARetryNotAClosedAccount() {
        var p=finale();String id=p.missionId();p.begin(id);
        var lost=new FreightRunProgress.Outcome(p.freight().previewAttempt(),List.of(),List.of(0,1,2),false);
        assertEquals(0,p.settle(id,false,null,lost));assertFalse(p.closed());assertTrue(p.available());
        assertEquals(12000,p.storyPayment());assertEquals(List.of(0,1,2),p.freight().waiting());
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
}
