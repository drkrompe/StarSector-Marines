package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.marine.MarineRoster;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredPlayerLiftTest {
    private ShuttleMission craft(int ordinal, int... sizes) {
        var m=new ShuttleMission(0,0,0,0,0,0,0,6);m.manifestOrdinal=ordinal;
        m.cycleLoadouts=new MarineLoadout[sizes.length][];
        for(int i=0;i<sizes.length;i++) {m.cycleLoadouts[i]=new MarineLoadout[sizes[i]];Arrays.fill(m.cycleLoadouts[i],MarineLoadout.COMBATANT);}
        m.marineLoadout=m.cycleLoadouts[0];return m;
    }
    @Test void sortsPhysicalCraftAndKeepsEveryIssuedIdentityIncludingPartialLastTrip() {
        var roster=new MarineRoster();roster.ensureActiveSoldiers(17);var deployment=CampaignMarineDeployment.freeze(roster,17);
        var first=craft(0,6);var second=craft(1,6,5);
        deployment.applyToAuthoredPlayerLift(List.of(second,first));
        for(int i=0;i<6;i++)assertEquals(deployment.seat(i).campaignSoldierId,first.cycleLoadouts[0][i].campaignSoldierId);
        for(int i=0;i<6;i++)assertEquals(deployment.seat(i+6).campaignSoldierId,second.cycleLoadouts[0][i].campaignSoldierId);
        for(int i=0;i<5;i++)assertEquals(deployment.seat(i+12).campaignSoldierId,second.cycleLoadouts[1][i].campaignSoldierId);
        assertEquals(deployment.seat(0).primaryWeaponId,first.marineLoadout[0].primaryWeaponId);
        assertNotNull(first.marineLoadout[0].campaignSquad);
    }
    @Test void refusesAnonymousFallbackOrDuplicateOrdinalBeforeMutatingSeats() {
        var roster=new MarineRoster();roster.ensureActiveSoldiers(12);var deployment=CampaignMarineDeployment.freeze(roster,12);
        var shortCraft=craft(0,6);assertThrows(IllegalStateException.class,()->deployment.applyToAuthoredPlayerLift(List.of(shortCraft)));
        assertNull(shortCraft.marineLoadout[0].campaignSoldierId);
        assertThrows(IllegalStateException.class,()->deployment.applyToAuthoredPlayerLift(List.of(craft(0,6),craft(0,6))));
    }
}
