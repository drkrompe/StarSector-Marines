package com.dillon.starsectormarines.campaign;

import java.io.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShepherdRecoveryProgressTest {
    @Test void victoryAwardsOnlyOneClaimAndRetryKeepsConfirmedCrewLosses()throws Exception{
        var p=new ShepherdRecoveryProgress();String first=p.missionId();
        assertTrue(p.begin(first,"Asharu"));
        assertEquals(-1,p.settle(first,"Garnir",true,18000,0,true));
        assertEquals(-1,p.settle(first,"Asharu",true,18000,0,false));
        assertEquals(-1,p.settle(first,"Asharu",true,18000,2,true));
        assertEquals(-1,p.settle(first,"Asharu",true,99999,0,true));
        assertEquals(0,p.settle(first,"Asharu",false,0,1,false));
        assertEquals(1,p.inspectionLosses());assertFalse(p.claimSecured());
        String retry=p.missionId();assertNotEquals(first,retry);
        assertTrue(p.begin(retry,"Asharu"));
        var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(p);}
        try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){p=(ShepherdRecoveryProgress)in.readObject();}
        assertEquals(18000,p.settle(retry,"Asharu",true,18000,0,true));
        assertTrue(p.claimSecured());assertEquals(1,p.inspectionLosses());assertTrue(ShepherdRecoveryProgress.coldStart(p.missionId()));
        assertEquals(-1,p.settle(retry,"Asharu",true,18000,0,true));
        assertFalse(p.begin(first,"Asharu"));
    }
    @Test void legacySerializedSalvageVictoryUnlocksRepairsWithoutResettingLosses() throws Exception {
        // Serialized by the previous claim-only implementation, before repair fields existed.
        String claim="rO0ABXNyAD5jb20uZGlsbG9uLnN0YXJzZWN0b3JtYXJpbmVzLmNhbXBhaWduLlNoZXBoZXJkUmVjb3ZlcnlQcm9ncmVzcwy7ctp0UXvCAgAFSQAHYXR0ZW1wdFoADGNsYWltU2VjdXJlZEkAEGluc3BlY3Rpb25Mb3NzZXNMAAhhY3RpdmVJZHQAEkxqYXZhL2xhbmcvU3RyaW5nO0wADGFjdGl2ZVBsYW5ldHEAfgABeHAAAAACAQAAAAFwcA==";
        try(var in=new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(claim)))) {
            var p=(ShepherdRecoveryProgress)in.readObject();
            assertTrue(p.claimSecured());assertEquals(1,p.inspectionLosses());
            assertFalse(p.cargoDelivered());assertFalse(p.shorePower());assertFalse(p.repaired());
            assertEquals(ShepherdRecoveryProgress.COLD_PREFIX+"2",p.missionId());
            assertTrue(p.begin(p.missionId(),"Asharu"));
        }
    }

    @Test void failedRepairsPersistOnlyCompletedMilestonesAndLossesAcrossLoad() throws Exception {
        var p=new ShepherdRecoveryProgress();var claim=p.missionId();
        p.begin(claim,"Asharu");p.settle(claim,"Asharu",true,18000,0,true);
        var id=p.missionId();assertTrue(p.begin(id,"Asharu"));
        assertEquals(-1,p.settleColdStart(id,"Garnir",false,0,1,true,true,false,false));
        assertEquals(-1,p.settleColdStart(id,"Asharu",true,22000,2,true,true,true,true));
        assertEquals(0,p.settleColdStart(id,"Asharu",false,0,2,true,true,true,false));
        assertEquals(-1,p.settleColdStart(id,"Asharu",false,0,2,true,true,true,false));
        assertTrue(p.cargoDelivered());assertTrue(p.shorePower());assertEquals(2,p.repairLosses());
        var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(p);}
        try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){p=(ShepherdRecoveryProgress)in.readObject();}
        var retry=p.missionId();assertTrue(p.begin(retry,"Asharu"));
        assertEquals(-1,p.settleColdStart(retry,"Asharu",true,22000,0,true,true,false,true));
        assertEquals(22000,p.settleColdStart(retry,"Asharu",true,22000,0,true,true,true,true));
        assertTrue(p.repaired());assertTrue(ShepherdRecoveryProgress.workingPassage(p.missionId()));assertEquals(2,p.repairLosses());
        assertEquals(-1,p.settleColdStart(retry,"Asharu",true,22000,0,true,true,true,true));
    }

    private ShepherdRecoveryProgress repaired() {
        var p=new ShepherdRecoveryProgress();var id=p.missionId();
        p.begin(id,"Asharu");p.settle(id,"Asharu",true,18000,0,true);
        id=p.missionId();p.begin(id,"Asharu");p.settleColdStart(id,"Asharu",true,22000,0,true,true,true,true);
        return p;
    }
    @Test void disabledDepartureRetainsRepairAndLossesThenAwardsOneIdentityAcrossLoad() throws Exception {
        var p=repaired();var id=p.missionId();assertTrue(p.begin(id,"Asharu"));
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",true,0,1,true,true,false,"ship",15));
        assertEquals(-1,p.settleWorkingPassage(id,"Garnir",false,0,1,false,true,true,null,0));
        assertEquals(0,p.settleWorkingPassage(id,"Asharu",false,0,3,false,true,true,null,0));
        assertTrue(p.claimSecured());assertTrue(p.repaired());assertEquals(3,p.departureLosses());
        assertEquals(1,p.departureFailures());assertTrue(p.lastDepartureDisabled());
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",false,0,3,false,true,true,null,0));
        id=p.missionId();assertTrue(p.begin(id,"Asharu"));
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",true,0,0,true,false,false,"ship",0));
        assertEquals(0,p.settleWorkingPassage(id,"Asharu",true,0,0,true,false,false,"ship",15));
        var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(p);}
        try(var in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))){p=(ShepherdRecoveryProgress)in.readObject();}
        assertTrue(p.departed());assertEquals("ship",p.awardedMemberId());assertEquals(15,p.willingCrewTransferred());
        assertNull(p.missionId());assertFalse(p.begin(id,"Asharu"));
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",true,0,0,true,false,false,"replacement",15));
    }
    @Test void abortedAndForgedDepartureCannotAward() {
        var p=repaired();var id=p.missionId();p.begin(id,"Asharu");
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",false,0,0,true,false,false,"ship",15));
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",true,0,0,true,false,true,"ship",15));
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",true,10,0,true,false,false,"ship",15));
        assertEquals(-1,p.settleWorkingPassage(id,"Asharu",true,0,9,true,false,false,"ship",15));
        assertEquals(0,p.settleWorkingPassage(id,"Asharu",false,0,0,false,false,true,null,0));
        assertFalse(p.departed());assertTrue(p.lastDepartureAborted());assertTrue(p.repaired());
    }
}
