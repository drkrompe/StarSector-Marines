package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.*;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import com.dillon.starsectormarines.marine.*;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class MechLabInductionTest {
    private static MissionOutcome victory() {
        return MissionOutcome.builder().missionSource(MissionSource.STORY).victory(true)
                .missionId(OpeningOperationKind.COUNTERATTACK.missionId)
                .waylineHound(new WaylineHoundRecovery.Report(true, true)).build();
    }

    @Test void recoveryGivesOneSpareAndTutorialSurvivesSaveBetweenFittingAndRefitting() throws Exception {
        MarineRoster roster = new MarineRoster();
        MissionResolver.recordStoryCompletion(roster, victory());
        MissionResolver.recordStoryCompletion(roster, victory());
        MechBay bay = roster.mechBay();
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.SRM_5.id));
        assertEquals(1, bay.labLesson());
        var model = new MechLabViewModel(new Reactor(), bay);
        model.guideLabLesson();
        assertEquals(2, bay.labLesson());
        assertFalse(model.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.ARMS));
        assertEquals(2, bay.labLesson());
        assertTrue(model.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER));
        assertEquals(3, bay.labLesson());
        bay.pauseLabLesson(true);
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(bay); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            bay = (MechBay) in.readObject();
        }
        assertTrue(bay.labLessonPaused());
        model = new MechLabViewModel(new Reactor(), bay);
        model.guideLabLesson();
        model.stripWeapon(SocketId.LEFT_SHOULDER);
        assertTrue(model.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER));
        assertEquals(4, bay.labLesson());
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_5.id));
        assertEquals(MechWeaponComponent.SRM_5, bay.activeDeployment().get(0).leftShoulder());
        model.guideLabLesson();
        assertEquals(5, bay.labLesson());
        bay.beginLabLesson();
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_5.id));
        assertEquals(ScreenId.MECH_LAB, ResultsScreen.returnDestination(victory()));
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
            assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_5.id));
            assertTrue(bay.canFabricate(MechWeaponComponent.LRM_15));
        }
    }

    @Test void starterPatternsBlockFabricationButNeverConfiscateOwnedAdvancedParts() {
        MechBay bay = new MechBay();
        bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
        MechWorkshop workshop = new MechWorkshop(bay, FabricationResources.NONE);
        assertTrue(bay.canFabricate(MechWeaponComponent.NOSE_CHAINGUN));
        assertTrue(bay.canFabricate(MechWeaponComponent.SRM_5));
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

    @Test void strippingDoesNotCompleteLessonUntilRefitted() {
        MechBay bay = new MechBay();
        bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
        bay.beginLabLesson();
        var model = new MechLabViewModel(new Reactor(), bay);
        model.guideLabLesson();
        model.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER);
        model.stripWeapon(SocketId.LEFT_SHOULDER);
        assertEquals(3, bay.labLesson());
        model.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER);
        assertEquals(4, bay.labLesson());
    }
}
