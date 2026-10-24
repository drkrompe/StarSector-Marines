package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.dillon.starsectormarines.ui.retained.UiKey;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MechLabViewModelTest {

    private static final float EPSILON = 0.01f;
    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/company/company-resource-stock.mlx", "mod/data/ui/components/company/company-resource-cost.mlx", "mod/data/ui/components/marine-ops-page-nav.mlx",
            "mod/data/ui/components/mech-lab/mech-lab.mlx");

    @Test void compactWorkshopRejectsStaleFabricationAfterCapacityShrinks() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestResources resources = TestResources.stocked(2000);
        var model = new MechLabViewModel(new Reactor(), bay, resources);
        model.nextGantryAction().run();
        var order = model.catalogRows().get().stream().filter(row -> !row.actionDisabled()).findFirst().orElseThrow();
        int before = resources.available(Commodities.METALS);
        model.physicalGantries(1);
        assertEquals(1, model.gantryRows().get().size());
        assertEquals(1, model.gantryDeployments().size());
        assertEquals(1, model.gantryVariants().size());
        assertEquals(0, model.selectedGantryIndex());
        order.action().run();
        assertEquals(1, bay.activeSquad().mechs().size());
        assertEquals(before, resources.available(Commodities.METALS));
    }

    @Test
    void dragCommandsFitMoveAndReturnWeaponsWithoutDuplicatingInventory() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestResources resources = TestResources.stocked(2_000);
        AtomicInteger changes = new AtomicInteger();
        MechLabViewModel viewModel = new MechLabViewModel(
                new Reactor(), bay, resources, changes::incrementAndGet);
        viewModel.gantryRows().get().get(0).select().run();

        // Nothing in stores yet: a pattern alone cannot be dragged, its row fabricates first.
        assertFalse(viewModel.canDropCatalogWeapon(
                MechWeaponComponent.PULSE_LASER, SocketId.LEFT_ARM));
        viewModel.selectSlot(SocketId.LEFT_ARM);
        viewModel.catalogRows().get().stream()
                .filter(row -> row.weaponPreview() == MechWeaponComponent.PULSE_LASER)
                .findFirst().orElseThrow().action().run();
        assertTrue(viewModel.canDropCatalogWeapon(
                MechWeaponComponent.PULSE_LASER, SocketId.LEFT_ARM));
        assertTrue(viewModel.dropCatalogWeapon(
                MechWeaponComponent.PULSE_LASER, SocketId.LEFT_ARM));
        assertEquals(MechWeaponComponent.PULSE_LASER,
                viewModel.installedWeapon(SocketId.LEFT_ARM));

        assertTrue(viewModel.removeInstalledWeapon(SocketId.RIGHT_SHOULDER));
        assertTrue(viewModel.canMoveInstalledWeapon(
                SocketId.LEFT_SHOULDER, SocketId.RIGHT_SHOULDER));
        assertTrue(viewModel.moveInstalledWeapon(
                SocketId.LEFT_SHOULDER, SocketId.RIGHT_SHOULDER));
        assertNull(viewModel.installedWeapon(SocketId.LEFT_SHOULDER));
        assertEquals(MechWeaponComponent.SRM_15,
                viewModel.installedWeapon(SocketId.RIGHT_SHOULDER));
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.SRM_15.id));
        assertEquals(4, changes.get());
    }

    @Test
    void dragValidationRejectsOccupiedAndIncompatibleTargets() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.gantryRows().get().get(0).select().run();

        assertFalse(viewModel.canMoveInstalledWeapon(
                SocketId.LEFT_SHOULDER, SocketId.RIGHT_SHOULDER));
        assertFalse(viewModel.canMoveInstalledWeapon(
                SocketId.LEFT_ARM, SocketId.LEFT_SHOULDER));
        assertFalse(viewModel.canDropCatalogWeapon(
                MechWeaponComponent.SINGLE_HEAVY_CANNON, SocketId.LEFT_SHOULDER));
    }

    @Test
    void installCommandUsesFiniteMechBayStockAndReturnsCurrentComponent() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        viewModel.gantryRows().get().get(0).select().run();

        MechLabViewModel.CatalogRow accelerated = viewModel.catalogRows().get()
                .stream()
                .filter(row -> row.id().endsWith(
                        MissileReplenisherComponent.ACCELERATED_FEED.id()))
                .findFirst()
                .orElseThrow();
        assertFalse(accelerated.actionDisabled());
        accelerated.action().run();

        assertEquals(MissileReplenisherComponent.ACCELERATED_FEED.id(),
                mech.missileReplenisherId());
        assertEquals(1, bay.availableReplenisher(
                MissileReplenisherComponent.STANDARD.id()));
        assertEquals(0, bay.availableReplenisher(
                MissileReplenisherComponent.ACCELERATED_FEED.id()));
        assertTrue(viewModel.feedbackText().get().contains("installed"));
    }

    @Test
    void selectingTypedWeaponSocketDiscoversCompatibleFabricationPatterns() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        String originalReplenisher = mech.missileReplenisherId();
        viewModel.gantryRows().get().get(0).select().run();

        viewModel.leftSlotRows().get().stream()
                .filter(row -> row.id().endsWith("left_arm"))
                .findFirst().orElseThrow().select().run();

        assertTrue(viewModel.fittingFocused());
        assertTrue(viewModel.selectedSlotRule().get().contains("OMNI SOCKET"));
        assertTrue(viewModel.catalogRows().get().size() > 1);
        assertTrue(viewModel.catalogRows().get().stream()
                .anyMatch(row -> row.name().equals("Pulse laser")));
        assertTrue(viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Pulse laser"))
                .findFirst().orElseThrow().actionDisabled());
        assertEquals(originalReplenisher, mech.missileReplenisherId());
        assertTrue(viewModel.feedbackText().get().contains("fabricable"));

        viewModel.overviewAction().run();
        assertFalse(viewModel.fittingFocused());
        assertTrue(viewModel.feedbackText().get().contains("Lance overview"));
    }

    @Test
    void assetPickerIsASeparateInternalScreenAndSelectionReturnsToGantry() {
        MechLabViewModel viewModel = new MechLabViewModel(
                new Reactor(), MechBay.legacyStarterFixture());

        assertTrue(viewModel.pickerClasses().get().contains("hidden"));
        assertFalse(viewModel.workspaceClasses().get().contains("hidden"));
        viewModel.openAssetPickerAction().run();
        assertFalse(viewModel.pickerClasses().get().contains("hidden"));
        assertTrue(viewModel.workspaceClasses().get().contains("hidden"));

        viewModel.mechRows().get().get(0).select().run();
        assertTrue(viewModel.pickerClasses().get().contains("hidden"));
        assertFalse(viewModel.workspaceClasses().get().contains("hidden"));
        assertTrue(viewModel.fittingFocused());
        assertFalse(viewModel.catalogClasses().get().contains("hidden"));
        assertTrue(viewModel.overviewRailClasses().get().contains("hidden"));
    }

    @Test
    void newCampaignLanceOverviewStartsWithFourVacantGantries() {
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), new MechBay());

        assertFalse(viewModel.fittingFocused());
        assertNull(viewModel.selectedVariant());
        assertTrue(viewModel.catalogClasses().get().contains("hidden"));
        assertTrue(viewModel.slotRackClasses().get().contains("hidden"));
        assertTrue(viewModel.performanceClasses().get().contains("hidden"));
        assertTrue(viewModel.fittingHeaderClasses().get().contains("hidden"));
        assertFalse(viewModel.overviewRailClasses().get().contains("hidden"));
        assertEquals(4, viewModel.gantryRows().get().size());
        assertTrue(viewModel.gantryRows().get().get(0).disabled());
        assertTrue(viewModel.gantryRows().get().get(1).disabled());

        viewModel.selectGantryAction(0).run();
        assertTrue(viewModel.fittingFocused());
        assertNull(viewModel.selectedVariant());
        assertFalse(viewModel.catalogClasses().get().contains("hidden"));
        assertTrue(viewModel.slotRackClasses().get().contains("hidden"));
        assertTrue(viewModel.catalogRows().get().stream()
                .anyMatch(row -> row.name().equals("Hound chassis")));
    }

    @Test
    void gantryNavigatorVisitsAssignedAssetsAndVacantStations() {
        MechBay bay = MechBay.legacyStarterFixture();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id()));
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_03", "Warden 03", MechVariant.WARDEN,
                MechRole.LR_SUPPORT, MissileReplenisherComponent.STANDARD.id()));
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);

        viewModel.nextGantryAction().run();
        assertEquals(1, viewModel.selectedGantryIndex());
        assertEquals(MechVariant.HOUND, viewModel.selectedVariant());
        assertTrue(viewModel.fittingFocused());
        assertTrue(viewModel.activeGantryLabel().get().contains("PAD B"));
        assertEquals("Hound 02", viewModel.selectedMechName().get());

        viewModel.nextGantryAction().run();
        viewModel.nextGantryAction().run();
        assertEquals(3, viewModel.selectedGantryIndex());
        assertNull(viewModel.selectedVariant());
        assertTrue(viewModel.fittingFocused());
        assertFalse(viewModel.catalogClasses().get().contains("hidden"));
        assertTrue(viewModel.slotRackClasses().get().contains("hidden"));
        assertTrue(viewModel.activeGantryLabel().get().contains("VACANT"));

        viewModel.nextGantryAction().run();
        assertEquals(0, viewModel.selectedGantryIndex());
        viewModel.previousGantryAction().run();
        assertEquals(3, viewModel.selectedGantryIndex());
    }

    @Test
    void commodityIconsAndCargoCountsDriveWeaponAndChassisFabrication() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestResources resources = TestResources.stocked(2_000);
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay, resources);
        viewModel.gantryRows().get().get(0).select().run();
        viewModel.leftSlotRows().get().stream()
                .filter(row -> row.id().endsWith("left_arm"))
                .findFirst().orElseThrow().select().run();

        MechLabViewModel.CatalogRow pulse = viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Pulse laser"))
                .findFirst().orElseThrow();
        assertFalse(pulse.actionDisabled());
        assertEquals(3, pulse.materials().size());
        assertTrue(pulse.materials().stream().allMatch(row -> row.icon().startsWith(
                "graphics/icons/cargo/")));
        CompanyResources.Row cost = pulse.materials().get(0);
        String commodity = cost.id().substring(cost.id().lastIndexOf(':') + 1);
        assertEquals(String.valueOf(cost.required()), cost.label());
        assertEquals(2000, cost.available());
        assertEquals("MANUFACTURING COST", MechLabSpecSheets.materialSheet(cost).subtitle());
        assertEquals("2,000", stockLabel(viewModel, commodity));
        pulse.action().run();
        assertEquals(String.format("%,d", 2000 - cost.required()), stockLabel(viewModel, commodity));
        // Fabrication fills stores; fitting is the drag.
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.PULSE_LASER.id));
        assertTrue(viewModel.dropCatalogWeapon(MechWeaponComponent.PULSE_LASER, SocketId.LEFT_ARM));
        assertEquals(MechWeaponComponent.PULSE_LASER,
                bay.mechById(MechBay.STARTER_MECH_ID).leftArm());

        viewModel.nextGantryAction().run();
        assertTrue(viewModel.selectedMechName().get().contains("VACANT PAD"));
        MechLabViewModel.CatalogRow hound = viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Hound chassis"))
                .findFirst().orElseThrow();
        assertFalse(hound.actionDisabled());
        assertEquals(MechVariant.HOUND, hound.chassisPreview());
        assertEquals("mech-catalog:recipe.chassis.hound:preview", hound.previewId());
        assertFalse(hound.previewClasses().contains("hidden"));
        hound.select().run();
        assertEquals(1, bay.activeSquad().mechs().size(), "selecting a pattern only previews it");
        assertEquals(MechVariant.HOUND, viewModel.padPreview());
        viewModel.buildAction().run();
        assertEquals(2, bay.activeSquad().mechs().size());
        assertEquals(MechVariant.HOUND, viewModel.selectedVariant());
        assertTrue(viewModel.feedbackText().get().contains("standard roll-out fit"));
    }

    @Test
    void theSheetShowsTheShipsMechLabRefits() {
        MechBay bay = MechBay.legacyStarterFixture();
        ShipCondition shop = ShipCondition.battered().with(RefitTrack.MECH_ARMOR, RefitBand.IMPROVED_II)
                .with(RefitTrack.MECH_COOLANT, RefitBand.IMPROVED_I);
        MechLabViewModel plain = new MechLabViewModel(new Reactor(), bay);
        MechLabViewModel refitted = new MechLabViewModel(new Reactor(), bay, FabricationResources.NONE,
                () -> { }, () -> 0d, () -> shop);
        plain.selectGantryAction(0).run();
        refitted.selectGantryAction(0).run();
        float armor = plain.selectedFit().armorCapacity();

        assertEquals("", plain.shipRefitText().get());
        assertTrue(refitted.shipRefitText().get().contains("plate +10%"), refitted.shipRefitText().get());
        assertTrue(refitted.shipRefitText().get().contains("vents +10%"), refitted.shipRefitText().get());
        assertTrue(refitted.performanceMeters().get().get(0).value().contains(String.valueOf(Math.round(armor * 1.10f))),
                refitted.performanceMeters().get().get(0).value());
        assertEquals(plain.selectedDissipationPerSecond() * 1.10f, refitted.selectedDissipationPerSecond(), 1e-4f);
    }

    @Test
    void vehicleGantryShowsReadOnlyHullWithoutFittingControls() {
        MechBay bay = MechBay.legacyStarterFixture();
        CampaignVehicle vehicle = new CampaignVehicle(
                "support_vehicle_01", "Mule 01", VehicleType.ESCORT_APC);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, vehicle));
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);

        MechLabViewModel.GantryRow row = viewModel.gantryRows().get().get(1);
        assertFalse(row.disabled());
        assertEquals("Mule 01", row.name());
        assertTrue(row.detail().contains("Escort APC"));
        assertTrue(viewModel.gantryRows().get().get(2).disabled());
        assertEquals("2 / 4 assets", viewModel.squadRows().get().get(0).strength());
        assertTrue(viewModel.mechRows().get().stream()
                .anyMatch(mechRow -> mechRow.name().equals("Mule 01")));

        row.select().run();
        assertEquals(1, viewModel.selectedGantryIndex());
        assertEquals("Mule 01", viewModel.selectedMechName().get());
        assertTrue(viewModel.selectedMechIdentity().get().contains("Escort APC"));
        assertTrue(viewModel.selectedMechIdentity().get().contains("VEHICLE"));
        assertEquals(VehicleType.ESCORT_APC.hasTurretWeapon(),
                viewModel.selectedMechDoctrine().get().startsWith("TURRET"));
        assertFalse(viewModel.activeGantryLabel().get().contains("VACANT"));

        assertFalse(viewModel.performanceClasses().get().contains("hidden"));
        List<String> meters = viewModel.performanceMeters().get().stream()
                .map(MechLabViewModel.PerformanceMeter::label).toList();
        assertEquals(List.of("STRUCTURE", "ARMOR", "MOBILITY", "PASSENGERS"), meters);
        assertEquals(VehicleType.ESCORT_APC.capacity + " SEATS",
                viewModel.performanceMeters().get().get(3).value());
        assertTrue(viewModel.selectedSlotRule().get().contains("NO FLUX"));

        assertNull(viewModel.selectedVariant());
        assertEquals(1, viewModel.gantryDeployments().size());
        assertTrue(viewModel.slotRows().get().isEmpty());
        assertTrue(viewModel.slotRackClasses().get().contains("hidden"));
        assertTrue(viewModel.catalogRows().get().isEmpty());
        assertTrue(viewModel.categoryFilterPills().get().isEmpty());
        assertNull(viewModel.installedWeapon(SocketId.LEFT_ARM));
        assertNull(viewModel.socketDefinition(SocketId.LEFT_ARM));
        assertFalse(viewModel.canDropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER));
        assertFalse(viewModel.dropCatalogWeapon(MechWeaponComponent.SRM_5, SocketId.LEFT_SHOULDER));

        assertFalse(viewModel.callSignEditorClasses().get().contains("hidden"));
        viewModel.editCallSignAction().accept("Packhorse");
        assertFalse(viewModel.callSignRenameDisabled().get());
        viewModel.renameCallSignAction().run();
        assertEquals("Packhorse", vehicle.displayName());
        assertEquals("Packhorse", viewModel.gantryRows().get().get(1).name());
    }

    @Test
    void mechCallsignRenamesTheSelectedAssetAndRefusesBlankInput() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.selectGantryAction(0).run();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        MechWeaponComponent arms = mech.leftArm();

        viewModel.editCallSignAction().accept("   ");
        assertTrue(viewModel.callSignRenameDisabled().get());
        viewModel.renameCallSignAction().run();
        assertEquals("Bulwark 01", mech.displayName());

        viewModel.editCallSignAction().accept("  Ironside ");
        assertFalse(viewModel.callSignRenameDisabled().get());
        viewModel.renameCallSignAction().run();
        assertEquals("Ironside", mech.displayName());
        assertEquals("Ironside", viewModel.selectedMechName().get());
        assertEquals("Ironside", viewModel.gantryRows().get().get(0).name());
        assertEquals(MechBay.STARTER_MECH_ID, mech.id());
        assertEquals(arms, mech.leftArm());

        viewModel.editCallSignAction().accept("I".repeat(CampaignMech.CALLSIGN_MAX_LENGTH + 1));
        assertTrue(viewModel.callSignRenameDisabled().get());
        viewModel.renameCallSignAction().run();
        assertEquals("Ironside", mech.displayName());
        assertTrue(viewModel.feedbackClasses().get().contains("tone-danger"));
    }

    @Test
    void vacantGantryFabricatesAKnownVehiclePattern() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestResources resources = TestResources.stocked(2_000);
        AtomicInteger changes = new AtomicInteger();
        MechLabViewModel viewModel = new MechLabViewModel(
                new Reactor(), bay, resources, changes::incrementAndGet);
        viewModel.nextGantryAction().run();

        MechLabViewModel.CatalogRow apc = viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Escort APC"))
                .findFirst().orElseThrow();
        assertEquals("VEHICLE", apc.badge());
        assertFalse(apc.actionDisabled());
        assertEquals(3, apc.materials().size());
        apc.select().run();
        assertEquals(0, bay.activeSquad().vehicles().size(), "selecting a pattern only previews it");
        assertEquals(VehicleType.ESCORT_APC, viewModel.padPreview());
        viewModel.buildAction().run();

        assertEquals(1, bay.activeSquad().vehicles().size());
        assertEquals(2, bay.activeSquad().occupied());
        CampaignVehicle built = bay.activeSquad().vehicles().get(0);
        assertEquals(VehicleType.ESCORT_APC, built.type());
        assertEquals(built.displayName(), viewModel.selectedMechName().get());
        assertEquals(1, viewModel.selectedGantryIndex());
        assertTrue(viewModel.feedbackText().get().contains("fabricated"));
        assertTrue(resources.available(Commodities.METALS) < 2_000);
        assertEquals(1, changes.get());
        assertTrue(viewModel.catalogRows().get().isEmpty());
    }

    @Test
    void unknownVehiclePatternIsNotOfferedAndIsRefused() {
        MechBay bay = new MechBay();
        TestResources resources = TestResources.stocked(2_000);
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay, resources);
        viewModel.selectGantryAction(0).run();

        assertTrue(viewModel.catalogRows().get().stream()
                .noneMatch(row -> row.name().equals("Escort APC")));
        assertFalse(viewModel.fabricateVehicle(VehicleType.ESCORT_APC));
        assertEquals("This fabrication pattern has not been acquired.",
                viewModel.feedbackText().get());
        assertTrue(viewModel.feedbackClasses().get().contains("tone-danger"));
        assertEquals(0, bay.activeSquad().occupied());
        assertEquals(2_000, resources.available(Commodities.METALS));

        assertTrue(bay.learnPattern(MechPattern.of(VehicleType.ESCORT_APC).id()));
        viewModel.selectGantryAction(0).run();
        assertTrue(viewModel.catalogRows().get().stream()
                .anyMatch(row -> row.name().equals("Escort APC")));
    }

    @Test
    void castleSpotlightKeepsFittingControlsLiveAndResumesAfterLeaving() throws Exception {
        Reactor reactor = new Reactor();
        MechBay bay = new MechBay();
        bay.receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
        bay.beginLabLesson();
        MechLabViewModel model = new MechLabViewModel(reactor, bay);
        model.guideLabLesson();
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, "mech-lab", props(model))) {
            UiDocument document = PreparationGuide.document(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            var guideLoader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                    List.of("data/ui/components/company/preparation-guide.mlx"));
            var guide = new PreparationGuide(document, model::campaignLesson, () -> {}, guideLoader);
            document.layout(1920, 1080);
            var stage = instance.requireElement("mech-doll-canvas");
            float x = stage.box().borderBox().x(), y = stage.box().borderBox().y();
            float width = stage.box().borderBox().width(), height = stage.box().borderBox().height();
            guide.update();
            assertTrue(guide.visible());
            document.keyPressed(UiKey.ESCAPE, Set.of(), false);
            instance.flush();
            document.layout(1920, 1080);
            assertTrue(guide.visible(), "Escape must not mark physical fitting practice complete");
            assertEquals(x, stage.box().borderBox().x(), EPSILON);
            assertEquals(y, stage.box().borderBox().y(), EPSILON);
            assertEquals(width, stage.box().borderBox().width(), EPSILON);
            assertEquals(height, stage.box().borderBox().height(), EPSILON);
            // The spare is dragged onto the shoulder; the drop is what the lesson waits for.
            assertTrue(model.dropCatalogWeapon(MechWeaponComponent.SRM_5_FRAG, SocketId.LEFT_SHOULDER));
            assertEquals(3, bay.labLesson(), "Fitting the spare must advance the lesson through the spotlight");
            var reopened = new MechLabViewModel(new Reactor(), bay);
            reopened.resumeCampaignLesson();
            assertEquals(3, bay.labLesson());
            assertNotNull(reopened.campaignLesson());
            assertTrue(reopened.removeInstalledWeapon(SocketId.LEFT_SHOULDER));
            assertTrue(reopened.dropCatalogWeapon(MechWeaponComponent.SRM_5_FRAG, SocketId.LEFT_SHOULDER));
            assertEquals(4, bay.labLesson());
        }
    }

    @Test
    void shippedRoomBuildsWithinWideAndLowResolutionBounds() throws Exception {
        Reactor reactor = new Reactor();
        MechLabViewModel viewModel = new MechLabViewModel(reactor, new MechBay());
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "mech-lab", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());

            assertWithinRoot(document, instance, 1744f, 938f);
            assertWithinRoot(document, instance, 1163f, 625f);
            assertTrue(instance.requireElement("page-nav-mech-lab")
                    .hasClass("selected"));
            assertTrue(instance.requireElement("page-nav-mech-lab")
                    .hasClass("page-nav-current"));
        }
    }

    /**
     * Every chassis and every piece of hardware in the catalog is a name a
     * player can ask about, and hovering one opens that item's spec sheet
     * beside the paper-doll preview the same hover drives.
     */
    @Test
    void everyCatalogSubjectIsAskable() throws Exception {
        Reactor reactor = new Reactor();
        // The starter bay with a mount selected, because the catalog lists what
        // could go in the chosen socket -- and a binding test against an empty
        // catalog passes by measuring nothing.
        MechLabViewModel viewModel = new MechLabViewModel(
                reactor, MechBay.legacyStarterFixture());
        viewModel.mechRows().get().get(0).select().run();
        viewModel.slotRows().get().stream()
                .filter(row -> row.name().contains("SHOULDER"))
                .findFirst().orElseThrow().select().run();
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "mech-lab", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            List<MechLabViewModel.CatalogRow> rows = viewModel.catalogRows().get();
            MechLabSpecSheets.bindCatalogRows(binder, instance, rows);
            MechLabSpecSheets.bindMaterials(binder, instance, viewModel);
            document.layout(1920f, 1080f);

            int subjects = 0;
            for (MechLabViewModel.CatalogRow row : rows) {
                boolean subject = row.chassisPreview() != null || row.weaponPreview() != null;
                if (subject) subjects++;
                assertEquals(subject, binder.isBound(instance.requireElement(row.id())),
                        row.id() + " is bound the wrong way round");
            }
            assertTrue(subjects > 0, "the catalog offered nothing to describe");
            assertEquals(subjects + viewModel.materialStock().get().size() + rows.stream().mapToInt(row -> row.materials().size()).sum(), binder.size());
            for (CompanyResources.Row material : viewModel.materialStock().get()) {
                assertTrue(binder.isBound(instance.requireElement(material.id())));
            }
            for (MechLabViewModel.CatalogRow row : rows) {
                for (CompanyResources.Row material : row.materials()) {
                    assertTrue(binder.isBound(instance.requireElement(material.id())));
                }
            }

            UiElement name = instance.requireElement(
                    MechLabSpecSheets.firstSpecSheetAnchorId(rows));
            document.pointerMoved(centerX(name), centerY(name));
            binder.update();
            document.advance(0f);

            assertTrue(layer.visible());
            assertEquals(name.text(),
                    instance.requireElement(SpecSheetLayer.ELEMENT_ID + "-title").text());
        }
    }

    @Test
    void fabricateButtonExplainsItselfAndBuildsIntoStoresAt1080p() throws Exception {
        Reactor reactor = new Reactor();
        MechBay bay = MechBay.legacyStarterFixture();
        AtomicInteger commits = new AtomicInteger();
        MechLabViewModel viewModel = new MechLabViewModel(
                reactor, bay, TestResources.stocked(2000), commits::incrementAndGet);
        viewModel.gantryRows().get().get(0).select().run();
        viewModel.selectSlot(SocketId.LEFT_ARM);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, "mech-lab", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            MechLabSpecSheets.bindCatalogRows(binder, instance, viewModel.catalogRows().get());
            MechLabSpecSheets.bindActions(binder, instance, viewModel);
            document.layout(1920, 1080);
            UiElement fab = instance.requireElement("mech-catalog:" + MechWeaponComponent.PULSE_LASER.id + ":action");
            document.pointerMoved(centerX(fab), centerY(fab));
            binder.update();
            document.advance(0f);
            assertTrue(layer.visible(), "The fabricate button explains itself on hover");
            assertEquals("BUY", instance.requireElement(SpecSheetLayer.ELEMENT_ID + "-subtitle").text());
            document.pointerDown(centerX(fab), centerY(fab));
            document.pointerUp(centerX(fab), centerY(fab));
            instance.flush();
            assertEquals(1, commits.get());
            assertEquals(1, bay.availableWeapon(MechWeaponComponent.PULSE_LASER.id));
            assertNotEquals(MechWeaponComponent.PULSE_LASER, viewModel.installedWeapon(SocketId.LEFT_ARM),
                    "fabrication must not fit the part");
            assertTrue(instance.requireElement("mech-catalog:" + MechWeaponComponent.PULSE_LASER.id + ":action")
                    .hasClass("hidden"), "a spare in stores takes the build offer away");
        }
    }

    private static String stockLabel(MechLabViewModel viewModel, String commodity) {
        return viewModel.materialStock().get().stream().filter(row -> row.id().endsWith(":" + commodity))
                .findFirst().orElseThrow().label();
    }

    private static float centerX(UiElement element) {
        return element.box().borderBox().x() + element.box().borderBox().width() * 0.5f;
    }

    private static float centerY(UiElement element) {
        return element.box().borderBox().y() + element.box().borderBox().height() * 0.5f;
    }

    @Test
    void mechLabNavigationTextCenterBubblesToTheRouteAction() throws Exception {
        Reactor reactor = new Reactor();
        MechLabViewModel viewModel = new MechLabViewModel(reactor, new MechBay());
        AtomicInteger clicks = new AtomicInteger();
        Map<String, Object> props = props(viewModel);
        props.put("mechLabAction", (Runnable) clicks::incrementAndGet);
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(reactor, "mech-lab", props)) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(1920f, 1080f);
            UiElement route = instance.requireElement("page-nav-mech-lab");
            float centerX = route.box().borderBox().x()
                    + route.box().borderBox().width() * 0.5f;
            float centerY = route.box().borderBox().y()
                    + route.box().borderBox().height() * 0.5f;
            document.pointerMoved(centerX, centerY);
            assertTrue(route.hovered());
            assertTrue(document.pointerDown(centerX, centerY));
            assertTrue(document.pointerUp(centerX, centerY));
            assertEquals(1, clicks.get());
        }
    }

    @Test
    void spareCountsReadAsACappedXCount() {
        assertEquals("x0", MechLabViewModel.spareCount(0));
        assertEquals("x7", MechLabViewModel.spareCount(7));
        assertEquals("x99", MechLabViewModel.spareCount(99));
        assertEquals("x99", MechLabViewModel.spareCount(250), "a large store still reads x99");
    }

    @Test
    void categoryFiltersControlCatalogDisplay() {
        Reactor reactor = new Reactor();
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(reactor, bay);
        viewModel.gantryRows().get().get(0).select().run();

        // Opening a mech lists every part in stores, with a symbol per category and no "all".
        assertTrue(viewModel.browsingStores().get());
        assertNull(viewModel.selectedSocket(), "no mount is chosen while browsing");
        assertEquals(List.of("mech-filter:ballistic", "mech-filter:missile", "mech-filter:energy",
                        "mech-filter:engine", "mech-filter:capacitors", "mech-filter:utility"),
                viewModel.categoryFilterPills().get().stream().map(MechLabViewModel.FilterPill::id).toList());
        List<MechLabViewModel.CatalogRow> everything = viewModel.catalogRows().get();
        assertTrue(everything.stream().anyMatch(row -> row.weaponPreview() != null));
        assertTrue(everything.stream().anyMatch(row -> row.internalPreview() != null
                && row.internalPreview().role == MechInternal.Role.ENGINE));
        assertTrue(everything.stream().anyMatch(row -> row.internalPreview() != null
                && row.internalPreview().role == MechInternal.Role.CAPACITOR_BANK));

        viewModel.setFilter(MechLabViewModel.CategoryFilter.ENGINE);
        assertTrue(viewModel.catalogRows().get().stream().allMatch(row -> row.internalPreview() != null
                && row.internalPreview().role == MechInternal.Role.ENGINE));
        viewModel.setFilter(MechLabViewModel.CategoryFilter.MISSILE);
        assertFalse(viewModel.catalogRows().get().isEmpty());
        assertTrue(viewModel.catalogRows().get().stream().allMatch(row -> row.weaponPreview() != null
                && row.weaponPreview().hardpointType == MechWeaponComponent.HardpointType.MISSILE));
        // Choosing the chosen category again clears it.
        viewModel.setFilter(MechLabViewModel.CategoryFilter.MISSILE);
        assertEquals(MechLabViewModel.CategoryFilter.ALL, viewModel.filter());
        assertEquals(everything.size(), viewModel.catalogRows().get().size());

        // A mount narrows the list to what fits it; ALL PARTS returns to everything.
        viewModel.selectSlot(SocketId.CORE);
        assertFalse(viewModel.browsingStores().get());
        assertTrue(viewModel.categoryFilterPills().get().isEmpty(), "the engine mount decides the kind");
        assertTrue(viewModel.catalogRows().get().stream().allMatch(row -> row.internalPreview() != null
                && row.internalPreview().role == MechInternal.Role.ENGINE));
        assertFalse(viewModel.allPartsClasses().get().contains("hidden"));
        viewModel.showAllParts();
        assertTrue(viewModel.browsingStores().get());
        assertEquals(everything.size(), viewModel.catalogRows().get().size());
    }

    @Test
    void strippingWeaponReturnsComponentToCargoAndClearsSocket() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.gantryRows().get().get(0).select().run();

        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        viewModel.selectSlot(SocketId.LEFT_ARM);
        MechWeaponComponent initialWeapon = mech.leftArm();
        assertEquals(MechWeaponComponent.CHAINGUN, initialWeapon);

        int initialFree = bay.availableWeapon(initialWeapon.id);
        viewModel.stripWeapon(SocketId.LEFT_ARM);

        assertNull(mech.leftArm());
        assertEquals(initialFree + 1, bay.availableWeapon(initialWeapon.id));
        assertTrue(viewModel.feedbackText().get().contains("stripped and returned to cargo stores"));

        boolean foundArms = false;
        for (MechLabViewModel.SlotRow slot : viewModel.slotRows().get()) {
            if (slot.socketId() == SocketId.LEFT_ARM) {
                foundArms = true;
                assertEquals("EMPTY", slot.badge());
                assertFalse(slot.canStrip());
            }
        }
        assertTrue(foundArms);
    }

    @Test
    void hoveringCandidateWeaponProjectsGhostPerformanceMeters() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.gantryRows().get().get(0).select().run();
        viewModel.selectSlot(SocketId.LEFT_SHOULDER);

        for (MechLabViewModel.PerformanceMeter meter : viewModel.performanceMeters().get()) {
            assertTrue(meter.ghostClasses().contains("hidden"));
        }

        MechWeaponComponent candidate = MechWeaponComponent.LRM_5;
        viewModel.hoverWeapon(candidate);

        boolean foundGhostGain = false;
        boolean foundGhostLoss = false;
        for (MechLabViewModel.PerformanceMeter meter : viewModel.performanceMeters().get()) {
            if (meter.label().equals("MAX RANGE") && meter.ghostClasses().contains("gain")) {
                foundGhostGain = true;
            }
            if (meter.label().equals("MISSILES") && meter.ghostClasses().contains("loss")) {
                foundGhostLoss = true;
            }
        }
        assertTrue(foundGhostGain);
        assertTrue(foundGhostLoss);

        viewModel.clearHoverWeapon();
        for (MechLabViewModel.PerformanceMeter meter : viewModel.performanceMeters().get()) {
            assertTrue(meter.ghostClasses().contains("hidden"));
        }
    }

    @Test
    void internalSocketsListTheirRoleAndCommitThroughTheWorkshop() {
        MechBay bay = MechBay.legacyStarterFixture();
        AtomicInteger changes = new AtomicInteger();
        MechLabViewModel viewModel = new MechLabViewModel(
                new Reactor(), bay, TestResources.stocked(2_000), changes::incrementAndGet);
        viewModel.gantryRows().get().get(0).select().run();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        bay.learnPattern(MechPattern.of(MechInternal.UPRATED_ENGINE).id());

        for (SocketId socket : List.of(SocketId.CORE, SocketId.CAPACITORS, SocketId.MINI_FAB)) {
            assertFalse(viewModel.socketDefinition(socket).factoryLocked(), socket + " is still sealed");
            assertFalse(viewModel.canDropCatalogWeapon(MechWeaponComponent.SRM_5, socket),
                    "a weapon must never drop into " + socket);
        }

        viewModel.selectSlot(SocketId.CORE);
        List<MechLabViewModel.CatalogRow> engines = viewModel.catalogRows().get();
        assertFalse(engines.isEmpty());
        for (MechLabViewModel.CatalogRow row : engines) {
            assertNotNull(row.internalPreview());
            assertEquals(MechInternal.Role.ENGINE, row.internalPreview().role);
        }
        MechLabViewModel.CatalogRow stock = engines.stream()
                .filter(row -> row.internalPreview() == MechInternal.INDUSTRIAL_ENGINE).findFirst().orElseThrow();
        assertEquals(MechInternal.INDUSTRIAL_ENGINE, viewModel.installedInternal(SocketId.CORE));
        assertFalse(viewModel.canDropCatalogInternal(MechInternal.INDUSTRIAL_ENGINE, SocketId.CORE),
                "the fitted engine is already in its mount");

        // The row fabricates into stores; the spare is then dragged onto its own mount only.
        MechLabViewModel.CatalogRow uprated = engines.stream()
                .filter(row -> row.internalPreview() == MechInternal.UPRATED_ENGINE).findFirst().orElseThrow();
        assertFalse(uprated.actionDisabled(), uprated.actionHint());
        assertFalse(viewModel.canDropCatalogInternal(MechInternal.UPRATED_ENGINE, SocketId.CORE));
        uprated.action().run();
        assertEquals(1, bay.availableInternal(MechInternal.UPRATED_ENGINE.id));
        assertFalse(viewModel.canDropCatalogInternal(MechInternal.UPRATED_ENGINE, SocketId.CAPACITORS));
        assertTrue(viewModel.dropCatalogInternal(MechInternal.UPRATED_ENGINE, SocketId.CORE));
        assertEquals(MechInternal.UPRATED_ENGINE, mech.internal(MechInternal.Role.ENGINE));
        assertEquals(2, changes.get());
        assertTrue(viewModel.feedbackClasses().get().contains("tone-good"));
        assertEquals(mech.internal(MechInternal.Role.ENGINE).displayName(),
                viewModel.slotRows().get().stream().filter(row -> row.socketId() == SocketId.CORE)
                        .findFirst().orElseThrow().component());

        // Dragged off the doll, the engine goes back to stores and leaves its socket empty:
        // the mech is incomplete and off the drop manifest until an engine is fitted again.
        assertTrue(viewModel.removeInstalledInternal(SocketId.CORE), viewModel.feedbackText().get());
        assertNull(mech.internal(MechInternal.Role.ENGINE));
        assertEquals(1, bay.availableInternal(MechInternal.UPRATED_ENGINE.id));
        assertEquals(CampaignMech.Readiness.INCOMPLETE, mech.readiness(0f));
        assertFalse(viewModel.socketOccupied(SocketId.CORE));
        // The panel reports the gap rather than the stock engine the budget check stands in.
        MechLabViewModel.PerformanceMeter mobility = viewModel.performanceMeters().get().stream()
                .filter(meter -> meter.id().endsWith("mobility")).findFirst().orElseThrow();
        assertEquals("0 CELLS/S", mobility.value());
        assertTrue(mobility.fillStyle().contains("width: 0"), mobility.fillStyle());
        MechLabViewModel.PerformanceMeter dissipation = viewModel.performanceMeters().get().stream()
                .filter(meter -> meter.id().endsWith("dissipation")).findFirst().orElseThrow();
        assertEquals("0 FLUX/S", dissipation.value(), "nothing powers the vents");
        assertTrue(viewModel.budgetText().get().endsWith("/ 0"), viewModel.budgetText().get());
        assertEquals("slot-badge required", viewModel.slotRows().get().stream()
                .filter(row -> row.socketId() == SocketId.CORE).findFirst().orElseThrow().badgeClasses());
        assertFalse(viewModel.removeInstalledInternal(SocketId.CORE), "the socket is already empty");
        assertTrue(viewModel.dropCatalogInternal(MechInternal.UPRATED_ENGINE, SocketId.CORE));
        assertEquals(CampaignMech.Readiness.READY, mech.readiness(0f));

        viewModel.selectSlot(SocketId.CAPACITORS);
        assertTrue(viewModel.catalogRows().get().stream().allMatch(row ->
                row.internalPreview() != null && row.internalPreview().role == MechInternal.Role.CAPACITOR_BANK));
    }

    @Test
    void unlearnedInternalsWithNoStockAreAbsent() {
        MechBay bay = new MechBay();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech("support_mech_09", "Bulwark 09",
                MechVariant.BULWARK, MechRole.ARMORED_SUPPORT, MissileReplenisherComponent.STANDARD.id()));
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.gantryRows().get().get(0).select().run();
        viewModel.selectSlot(SocketId.CORE);
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            MechInternal internal = row.internalPreview();
            assertTrue(bay.canFabricate(internal) || bay.ownedInternal(internal.id) > 0,
                    internal + " is listed without a pattern or a copy");
        }
        assertTrue(viewModel.catalogRows().get().stream()
                .anyMatch(row -> row.internalPreview() == MechInternal.INDUSTRIAL_ENGINE));
    }

    @Test
    void platingIsChosenBesideTheSocketsAndPreviewsArmor() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(
                new Reactor(), bay, TestResources.stocked(2_000));
        viewModel.gantryRows().get().get(0).select().run();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);

        viewModel.selectPlating();
        assertTrue(viewModel.platingSelected());
        assertNull(viewModel.selectedSocket());
        assertTrue(viewModel.platingClasses().get().contains("selected"));
        assertFalse(viewModel.catalogRows().get().isEmpty());
        assertTrue(viewModel.catalogRows().get().stream().allMatch(row ->
                row.internalPreview() != null && row.internalPreview().role == MechInternal.Role.PLATING));

        MechLabViewModel.PerformanceMeter armor = meter(viewModel, "mech-meter:armor");
        assertEquals(Math.round(mech.fit().armorCapacity()) + " PLATE", armor.value());
        assertTrue(armor.ghostClasses().contains("hidden"));
        viewModel.hoverInternal(MechInternal.HEAVY_PLATING);
        assertTrue(meter(viewModel, "mech-meter:armor").ghostClasses().contains("gain"));
        assertTrue(viewModel.budgetText().get().contains("THIS FIT"), viewModel.budgetText().get());
        viewModel.clearHoverCandidates();
        assertFalse(viewModel.budgetText().get().contains("THIS FIT"));

        viewModel.selectSlot(SocketId.LEFT_ARM);
        assertFalse(viewModel.platingSelected());
        assertEquals(SocketId.LEFT_ARM, viewModel.selectedSocket());
    }

    @Test
    void budgetReadsEngineOutputAgainstFittedMass() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.gantryRows().get().get(0).select().run();
        MechFrameBudget.Fit fit = bay.mechById(MechBay.STARTER_MECH_ID).fit();
        String expected = fit.overBudget() ? "OVER BUDGET" : "MASS";
        assertTrue(viewModel.budgetText().get().startsWith(expected), viewModel.budgetText().get());
        assertEquals(fit.overBudget(), viewModel.budgetClasses().get().contains("tone-danger"));
        assertNotNull(meter(viewModel, "mech-meter:flux"));
        assertNotNull(meter(viewModel, "mech-meter:dissipation"));
        assertEquals(fit.dissipationPerSecond(), viewModel.selectedDissipationPerSecond(), EPSILON);
    }

    @Test
    void overBudgetRowsAreRefusedWithBothSidesOfTheBudget() {
        MechFrameBudget.Fit heavy = new MechFrameBudget.Fit(MechVariant.HOUND,
                MechWeaponComponent.SINGLE_HEAVY_CANNON, MechWeaponComponent.LRM_15,
                MechWeaponComponent.LRM_15, MechInternal.INDUSTRIAL_ENGINE,
                MechInternal.HEAVY_PLATING, MechInternal.MILITARY_CAPACITOR_BANK,
                MechInternal.AUXILIARY_VENT_ARRAY);
        assertTrue(heavy.overBudget(), "fixture fit is expected to exceed a Hound's engine");
        String message = MechLabViewModel.overBudgetFeedback(heavy);
        assertTrue(message.contains(Math.round(heavy.output()) + ""), message);
        assertFalse(message.startsWith("opening"), "string key was not resolved: " + message);

        // Every internal row agrees with the bay's own budget rule.
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(
                new Reactor(), bay, TestResources.stocked(2_000));
        for (int gantry = 0; gantry < bay.activeSquad().mechs().size(); gantry++) {
            viewModel.gantryRows().get().get(gantry).select().run();
            CampaignMech mech = bay.activeSquad().mechs().get(gantry);
            viewModel.selectPlating();
            for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
                MechInternal internal = row.internalPreview();
                boolean within = mech.internal(internal.role) == internal || bay.withinBudget(
                        mech.id(), mech.fit().withInternal(internal.role, internal));
                // A spare plate is fitted from its row, and only when the engine can carry it.
                if (row.actionLabel().equals("FIT")) assertEquals(!within, row.actionDisabled(), internal.id);
            }
        }
    }

    @Test
    void utilityVentArrayFitsAndStripsBackToEmpty() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(
                new Reactor(), bay, TestResources.stocked(2_000));
        viewModel.gantryRows().get().get(0).select().run();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        bay.learnPattern(MechPattern.of(MechInternal.AUXILIARY_VENT_ARRAY).id());
        viewModel.selectSlot(SocketId.MINI_FAB);
        assertNull(viewModel.installedInternal(SocketId.MINI_FAB));

        MechLabViewModel.CatalogRow ventArray = viewModel.catalogRows().get().stream()
                .filter(row -> row.internalPreview() == MechInternal.AUXILIARY_VENT_ARRAY)
                .findFirst().orElseThrow();
        assertTrue(viewModel.catalogRows().get().stream().anyMatch(row -> row.replenisherPreview() != null),
                "the utility socket still lists missile loaders");
        assertFalse(ventArray.actionDisabled(), ventArray.actionHint());
        ventArray.action().run();
        assertTrue(viewModel.dropCatalogInternal(MechInternal.AUXILIARY_VENT_ARRAY, SocketId.MINI_FAB));
        assertEquals(MechInternal.AUXILIARY_VENT_ARRAY, mech.internal(MechInternal.Role.VENT_ARRAY));
        assertEquals(MechInternal.AUXILIARY_VENT_ARRAY, viewModel.installedInternal(SocketId.MINI_FAB));

        MechLabViewModel.SlotRow utility = viewModel.slotRows().get().stream()
                .filter(row -> row.socketId() == SocketId.MINI_FAB).findFirst().orElseThrow();
        assertTrue(utility.canStrip());
        assertEquals(MechInternal.AUXILIARY_VENT_ARRAY.displayName(), utility.component());
        utility.stripAction().run();
        assertNull(mech.internal(MechInternal.Role.VENT_ARRAY));
        assertTrue(bay.availableInternal(MechInternal.AUXILIARY_VENT_ARRAY.id) > 0);
    }

    private static MechLabViewModel.PerformanceMeter meter(MechLabViewModel viewModel, String id) {
        return viewModel.performanceMeters().get().stream()
                .filter(meter -> meter.id().equals(id)).findFirst().orElse(null);
    }

    private static void assertWithinRoot(UiDocument document, MarkupInstance instance,
                                         float width, float height) {
        document.layout(width, height);
        UiElement root = instance.requireElement("mech-lab-root");
        UiElement body = instance.requireElement("mech-lab-body");
        UiElement inventory = instance.requireElement("mech-component-catalog");
        assertTrue(body.box().borderBox().right()
                <= root.box().contentBox().right() + EPSILON);
        assertTrue(body.box().borderBox().bottom()
                <= root.box().contentBox().bottom() + EPSILON);
        assertTrue(inventory.box().borderBox().right()
                <= body.box().contentBox().right() + EPSILON);
    }

    @Test
    void repairOrderSpendsTheBillAndHoldsTheMechUntilItsClockRunsOut() {
        MechBay bay = MechBay.legacyStarterFixture();
        damage(bay, MechBay.STARTER_MECH_ID);
        TestResources resources = TestResources.stocked(2_000);
        double[] clock = {10d};
        AtomicInteger changes = new AtomicInteger();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay, resources,
                changes::incrementAndGet, () -> clock[0]);
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);

        assertEquals("Damaged — repair before it can drop", viewModel.gantryRows().get().get(0).detail());
        viewModel.gantryRows().get().get(0).select().run();
        MechLabViewModel.Repair card = viewModel.repair().get();
        assertFalse(card.classes().contains("hidden"));
        assertTrue(card.sections().contains("Legs"));
        assertFalse(card.materials().isEmpty());
        long stockBefore = resources.stock.values().stream().mapToLong(Integer::longValue).sum();

        card.action().run();

        assertEquals(CampaignMech.Readiness.UNDER_REPAIR, mech.readiness(10f));
        assertNull(mech.damage());
        assertTrue(resources.stock.values().stream().mapToLong(Integer::longValue).sum() < stockBefore);
        assertTrue(viewModel.feedbackText().get().contains("under repair"));
        assertEquals(1, changes.get());
        assertTrue(viewModel.repair().get().status().startsWith("Under repair"));
        assertTrue(viewModel.repair().get().orderClasses().contains("hidden"));

        clock[0] = 10d + mech.repairDaysRemaining(10f) + 1d;
        viewModel.refreshTime();
        assertTrue(viewModel.repair().get().classes().contains("hidden"));
        assertEquals("OPEN PAD", viewModel.mechRows().get().get(0).status());
    }

    @Test
    void repairOrderRefusedOnAShortHoldChangesNothing() {
        MechBay bay = MechBay.legacyStarterFixture();
        damage(bay, MechBay.STARTER_MECH_ID);
        TestResources resources = TestResources.stocked(0);
        AtomicInteger changes = new AtomicInteger();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay, resources,
                changes::incrementAndGet, () -> 4d);
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        viewModel.gantryRows().get().get(0).select().run();

        viewModel.repair().get().action().run();

        assertEquals(CampaignMech.Readiness.DAMAGED, mech.readiness(4f));
        assertNotNull(mech.damage());
        assertEquals(0, changes.get());
        assertTrue(viewModel.feedbackText().get().contains("short of materials"));
        assertTrue(viewModel.feedbackClasses().get().contains("tone-danger"));
        assertFalse(viewModel.repair().get().classes().contains("hidden"));
    }

    /** Settles a battle that left the mech's legs half gone and its torso armor scratched. */
    private static void damage(MechBay bay, String mechId) {
        int sections = MechSection.values().length;
        boolean[] fitted = new boolean[sections];
        float[] structure = new float[sections];
        float[] armor = new float[sections];
        Arrays.fill(fitted, true);
        Arrays.fill(structure, 1f);
        Arrays.fill(armor, 1f);
        structure[MechSection.LEGS.ordinal()] = 0.5f;
        armor[MechSection.TORSO.ordinal()] = 0.6f;
        MechSections.Report report = new MechSections.Report("test", fitted, structure, armor,
                new boolean[sections], new MechWeaponComponent[MechMountSlot.values().length]);
        bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mechId, report, false)));
    }

    private static Map<String, Object> props(MechLabViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        // Layout evidence, so the heading only has to be a string of about the
        // right length; where the room actually is belongs to the ship.
        props.put("contextLabel", "CRUISER TROOP TRANSPORT / MIDSHIPS PORT / VEHICLE BAY");
        props.put("activeBayLabel", "BAY 01 / 01");
        props.put("bayNavigatorClasses", "bay-navigator hidden");
        props.put("previousBay", (Runnable) () -> { });
        props.put("nextBay", (Runnable) () -> { });
        viewModel.putTutorialProps(props);
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("gantryRows", viewModel.gantryRows());
        props.put("garageTitle", viewModel.garageTitle());
        props.put("garageStatus", viewModel.garageStatus());
        props.put("dragNoteTitle", viewModel.dragNoteTitle());
        props.put("dragNoteCopy", viewModel.dragNoteCopy());
        props.put("scrapLabel", viewModel.scrapLabel());
        props.put("scrapClasses", viewModel.scrapClasses());
        props.put("scrapAction", viewModel.scrapAction());
        viewModel.putBuildPanelProps(props);
        props.put("performanceMeters", viewModel.performanceMeters());
        viewModel.putInternalProps(props);
        viewModel.putRepairProps(props);
        props.put("leftSlotRows", viewModel.leftSlotRows());
        props.put("rightSlotRows", viewModel.rightSlotRows());
        props.put("slotRows", viewModel.slotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("selectedSlotRule", viewModel.selectedSlotRule());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("materialStock", viewModel.materialStock());
        props.put("pickerClasses", viewModel.pickerClasses());
        props.put("workspaceClasses", viewModel.workspaceClasses());
        props.put("fittingHeaderClasses", viewModel.fittingHeaderClasses());
        props.put("backToLance", viewModel.overviewAction());
        props.put("performanceClasses", viewModel.performanceClasses());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("slotRackClasses", viewModel.slotRackClasses());
        props.put("overviewRailClasses", viewModel.overviewRailClasses());
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("categoryFilterPills", viewModel.categoryFilterPills());
        props.put("categoryFilterClasses", viewModel.categoryFilterClasses());
        props.put("allPartsClasses", viewModel.allPartsClasses());
        props.put("showAllParts", (Runnable) viewModel::showAllParts);
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }

    private static final class TestResources implements FabricationResources {
        private final Map<String, Integer> stock = new HashMap<>();

        private static TestResources stocked(int quantity) {
            TestResources resources = new TestResources();
            resources.stock.put(Commodities.SUPPLIES, quantity);
            resources.stock.put(Commodities.HEAVY_MACHINERY, quantity);
            resources.stock.put(Commodities.METALS, quantity);
            resources.stock.put(Commodities.RARE_METALS, quantity);
            return resources;
        }

        @Override public int available(String commodityId) {
            return stock.getOrDefault(commodityId, 0);
        }

        @Override public String commodityName(String commodityId) {
            return commodityId.replace('_', ' ');
        }

        @Override public String commodityIcon(String commodityId) {
            return "graphics/icons/cargo/" + commodityId + ".png";
        }

        @Override public boolean spend(FabricationCost cost) {
            if (!canAfford(cost)) return false;
            for (FabricationCost.Line line : cost.lines()) {
                stock.merge(line.commodityId(), -line.quantity(), Integer::sum);
            }
            return true;
        }
    }
}
