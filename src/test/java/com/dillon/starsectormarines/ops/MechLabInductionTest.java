package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.*;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import com.dillon.starsectormarines.marine.*;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MechLabInductionTest {
    private static MissionOutcome victory() {
        return MissionOutcome.builder().missionSource(MissionSource.STORY).victory(true)
                .missionId(OpeningOperationKind.COUNTERATTACK.missionId)
                .waylineHound(new WaylineHoundRecovery.Report(true, true)).build();
    }

    @Test void recoveryGivesOneSpareAndFittingItEndsTheLessonAfterASave() throws Exception {
        MarineRoster roster = new MarineRoster();
        MissionResolver.recordStoryCompletion(roster, victory());
        MissionResolver.recordStoryCompletion(roster, victory());
        MechBay bay = roster.mechBay();
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.SRM_5_FRAG.id));
        assertEquals(1, bay.labLesson());
        var model = new MechLabViewModel(new Reactor(), bay);
        model.guideLabLesson();
        assertEquals(2, bay.labLesson());
        bay.pauseLabLesson(true);
        bay = reload(bay);
        assertTrue(bay.labLessonPaused());
        assertEquals(2, bay.labLesson());
        model = new MechLabViewModel(new Reactor(), bay);
        model.guideLabLesson();
        assertFalse(model.dropCatalogWeapon(MechWeaponComponent.SRM_5_FRAG, SocketId.ARMS));
        assertEquals(2, bay.labLesson());
        assertTrue(model.dropCatalogWeapon(MechWeaponComponent.SRM_5_FRAG, SocketId.LEFT_SHOULDER));
        assertEquals(5, bay.labLesson(), "Installing the rack is the whole lesson");
        assertNull(model.campaignLesson());
        assertEquals(MechWeaponComponent.SRM_5_FRAG, bay.activeDeployment().get(0).leftShoulder());
        bay.beginLabLesson();
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_5_FRAG.id));
        assertEquals(ScreenId.MECH_LAB, ResultsScreen.returnDestination(victory()));
    }

    @Test void aSaveInsideTheRetiredRefitStepsLoadsFinished() throws Exception {
        for (int retired = 3; retired <= 4; retired++) {
            MechBay bay = new MechBay();
            bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
            var field = MechBay.class.getDeclaredField("labLesson");
            field.setAccessible(true);
            field.setInt(bay, retired);
            assertEquals(5, reload(bay).labLesson());
        }
    }

    private static MechBay reload(MechBay bay) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(bay); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MechBay) in.readObject();
        }
    }

    @Test void olderOwnedWaywardGetsOneLessonPackageOnLoad() throws Exception {
        MechBay bay = MechBay.legacyStarterFixture();
        bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
        for (int load = 0; load < 2; load++) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) { out.writeObject(bay); }
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                bay = (MechBay) in.readObject();
            }
            assertEquals(1, bay.labLesson());
            assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_5_FRAG.id));
            assertTrue(bay.canFabricate(MechWeaponComponent.LRM_15));
        }
    }

    @Test void starterPatternsBlockFabricationButNeverConfiscateOwnedAdvancedParts() {
        MechBay bay = new MechBay();
        bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
        MechWorkshop workshop = new MechWorkshop(bay, FabricationResources.NONE);
        assertTrue(bay.canFabricate(MechWeaponComponent.SRM_5_FRAG));
        assertFalse(bay.canFabricate(MechWeaponComponent.LRM_5));
        assertEquals(MechWorkshop.Status.PATTERN_LOCKED, workshop.fitWeapon(WaylineHound.ID,
                MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.LRM_5).status());
        assertEquals(MechWorkshop.Status.PATTERN_LOCKED, workshop.fabricateChassis(
                MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK).status());
        bay.addWeapon(MechWeaponComponent.LRM_5.id, 1);
        assertTrue(workshop.fitWeapon(WaylineHound.ID, MechMountSlot.LEFT_SHOULDER,
                MechWeaponComponent.LRM_5).succeeded());
        assertTrue(MechBay.legacyStarterFixture().canFabricate(MechWeaponComponent.LRM_15));
    }

    @Test void skippingClosesTheLessonAndKeepsTheSpare() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            MechBay bay = new MechBay();
            bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
            bay.beginLabLesson();
            var model = new MechLabViewModel(new Reactor(), bay);
            model.guideLabLesson();
            model.campaignLesson().later().run();
            assertEquals(5, bay.labLesson());
            assertNull(model.campaignLesson());
            assertFalse(model.labGuideVisible());
            assertEquals(1, bay.availableWeapon(MechWeaponComponent.SRM_5_FRAG.id));
        }
    }
}
