package com.dillon.starsectormarines.campaign;

import java.io.*;
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
        assertTrue(p.claimSecured());assertEquals(1,p.inspectionLosses());assertNull(p.missionId());
        assertEquals(-1,p.settle(retry,"Asharu",true,18000,0,true));
        assertFalse(p.begin(first,"Asharu"));
    }
}
