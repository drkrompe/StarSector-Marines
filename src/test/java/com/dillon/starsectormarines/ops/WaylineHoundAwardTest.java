package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MechBay;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class WaylineHoundAwardTest {
    @Test void victoryRetainsTheSameRepairedChaingunOnlyChassisEvenIfLostOrNeverActivated() throws Exception {
        for (boolean activated : new boolean[]{false, true}) for (boolean disabled : new boolean[]{false, true}) {
            MarineRoster roster = new MarineRoster();
            var outcome = outcome(true, MissionSource.STORY, OpeningOperationKind.COUNTERATTACK.missionId,
                    new WaylineHoundRecovery.Report(activated, disabled));
            assertFalse(CompanyShipRestoration.mechBayRestored(roster));
            MissionResolver.recordStoryCompletion(roster, outcome);
            assertTrue(CompanyShipRestoration.mechBayRestored(roster));
            assertTrue(roster.hasCompletedStory(outcome.missionId));
            assertEquals(WaylineHound.deployment(), roster.mechBay().mechById(WaylineHound.ID).freezeForDeployment());
            assertEquals(1, roster.mechBay().ownedWeapon(MechWeaponComponent.NOSE_CHAINGUN.id));
            roster = roundTrip(roster);
            assertTrue(CompanyShipRestoration.mechBayRestored(roster));
            MissionResolver.recordStoryCompletion(roster, outcome);
            assertEquals(1, roster.mechBay().activeDeployment().size());
            assertEquals(1, roster.mechBay().ownedWeapon(MechWeaponComponent.NOSE_CHAINGUN.id));
            assertEquals(WaylineHound.deployment(), roster.mechBay().activeDeployment().get(0));
        }
    }

    @Test void failedRepeatableUnrelatedAndLegacyOutcomesCannotGrantAChassis() {
        var report = new WaylineHoundRecovery.Report(true, false);
        var counterattack = OpeningOperationKind.COUNTERATTACK.missionId;
        for (var outcome : new MissionOutcome[]{
                outcome(false, MissionSource.STORY, counterattack, report),
                outcome(true, MissionSource.RECURRING, counterattack, report),
                outcome(true, MissionSource.STORY, OpeningOperationKind.RELIEF.missionId, report),
                outcome(true, MissionSource.STORY, counterattack, null)}) {
            MarineRoster roster = new MarineRoster();
            MissionResolver.recordStoryCompletion(roster, outcome);
            assertNull(roster.mechBay().mechById(WaylineHound.ID));
        }
        MarineRoster completedSave = new MarineRoster();
        completedSave.markStoryComplete(counterattack);
        assertFalse(WaylineHoundAward.settle(completedSave, outcome(true, MissionSource.STORY, counterattack, report)));
        assertNull(completedSave.mechBay().mechById(WaylineHound.ID));
    }

    @Test void fullLanceKeepsItsSelectionAndGetsAnOverflowRecoveryGroup() {
        MarineRoster roster = new MarineRoster();
        MechBay bay = roster.mechBay();
        for (int i = 0; i < CampaignMechSquad.CAPACITY; i++)
            assertNotNull(bay.fabricateChassis(bay.activeSquad().id(), MechVariant.BULWARK));
        String selected = bay.activeSquad().id();
        MissionResolver.recordStoryCompletion(roster, outcome(true, MissionSource.STORY,
                OpeningOperationKind.COUNTERATTACK.missionId, new WaylineHoundRecovery.Report(true, true)));
        assertEquals(selected, bay.activeSquad().id());
        assertEquals(CampaignMechSquad.CAPACITY, bay.activeSquad().mechs().size());
        assertEquals(2, bay.squads().size());
        assertEquals(WaylineHound.deployment(), bay.mechById(WaylineHound.ID).freezeForDeployment());
    }

    @Test void legacyStarterIsNeverReplacedAndRepeatedReceiptCannotDuplicateParts() {
        MechBay bay = MechBay.legacyStarterFixture();
        CampaignMech starter = bay.mechById(MechBay.STARTER_MECH_ID);
        assertTrue(bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment())));
        assertFalse(bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment())));
        assertSame(starter, bay.mechById(MechBay.STARTER_MECH_ID));
        assertEquals(2, bay.activeDeployment().size());
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.NOSE_CHAINGUN.id));
    }

    private static MissionOutcome outcome(boolean victory, MissionSource source, String id,
            WaylineHoundRecovery.Report report) {
        return MissionOutcome.builder().missionId(id).missionSource(source).victory(victory).waylineHound(report).build();
    }

    private static MarineRoster roundTrip(MarineRoster roster) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(roster); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MarineRoster) in.readObject();
        }
    }
}
