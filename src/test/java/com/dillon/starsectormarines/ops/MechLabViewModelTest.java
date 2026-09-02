package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabViewModelTest {

    private static final float EPSILON = 0.01f;
    private static final List<String> COMPONENTS = List.of(
            "mod/data/ui/components/marine-ops-page-nav.mlx",
            "mod/data/ui/components/mech-lab/mech-lab.mlx");

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
                .filter(row -> row.id().endsWith("arms"))
                .findFirst().orElseThrow().select().run();

        assertTrue(viewModel.fittingFocused());
        assertTrue(viewModel.selectedSlotRule().get().contains("OMNI SOCKET"));
        assertTrue(viewModel.catalogRows().get().size() > 1);
        assertTrue(viewModel.catalogRows().get().stream()
                .anyMatch(row -> row.name().equals("Dual pulse lasers")));
        assertTrue(viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Dual pulse lasers"))
                .findFirst().orElseThrow().actionDisabled());
        assertEquals(originalReplenisher, mech.missileReplenisherId());
        assertTrue(viewModel.feedbackText().get().contains("fabricable"));

        viewModel.overviewAction().run();
        assertFalse(viewModel.fittingFocused());
        assertTrue(viewModel.feedbackText().get().contains("overview restored"));
    }

    @Test
    void houndCatalogShowsOversizedBallisticPatternsWithoutAllowingFit() {
        MechBay bay = MechBay.legacyStarterFixture();
        bay.addMech(MechBay.STARTER_SQUAD_ID, new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id()));
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.mechRows().get().stream()
                .filter(row -> row.name().startsWith("Hound"))
                .findFirst().orElseThrow().select().run();
        viewModel.slotRows().get().stream()
                .filter(row -> row.name().equals("ARM ASSEMBLY"))
                .findFirst().orElseThrow().select().run();

        MechLabViewModel.CatalogRow heavy = viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Heavy cannon"))
                .findFirst().orElseThrow();
        assertEquals("TOO LARGE", heavy.actionLabel());
        assertTrue(heavy.actionDisabled());
        assertEquals(MechWeaponComponent.SINGLE_HEAVY_CANNON, heavy.weaponPreview());
        assertTrue(heavy.detail().contains("3×2 GRID"));
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
                "support_mech_03", "Sirocco 03", MechVariant.SIROCCO,
                MechRole.LR_SUPPORT, MissileReplenisherComponent.STANDARD.id()));
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);

        viewModel.nextGantryAction().run();
        assertEquals(1, viewModel.selectedGantryIndex());
        assertEquals(MechVariant.HOUND, viewModel.selectedVariant());
        assertTrue(viewModel.fittingFocused());
        assertTrue(viewModel.activeGantryLabel().get().contains("GANTRY 02 / 04"));
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
                .filter(row -> row.id().endsWith("arms"))
                .findFirst().orElseThrow().select().run();

        MechLabViewModel.CatalogRow pulse = viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Dual pulse lasers"))
                .findFirst().orElseThrow();
        assertFalse(pulse.actionDisabled());
        assertEquals(4, pulse.materials().size());
        assertTrue(pulse.materials().stream().allMatch(row -> row.icon().startsWith(
                "graphics/icons/cargo/")));
        pulse.action().run();
        assertEquals(MechWeaponComponent.DUAL_PULSE_LASERS,
                bay.mechById(MechBay.STARTER_MECH_ID).arms());

        viewModel.nextGantryAction().run();
        assertTrue(viewModel.selectedMechName().get().contains("VACANT GANTRY"));
        MechLabViewModel.CatalogRow hound = viewModel.catalogRows().get().stream()
                .filter(row -> row.name().equals("Hound chassis"))
                .findFirst().orElseThrow();
        assertFalse(hound.actionDisabled());
        assertEquals(MechVariant.HOUND, hound.chassisPreview());
        assertEquals("mech-catalog:recipe.chassis.hound:preview", hound.previewId());
        assertFalse(hound.previewClasses().contains("hidden"));
        hound.action().run();
        assertEquals(2, bay.activeSquad().mechs().size());
        assertEquals(MechVariant.HOUND, viewModel.selectedVariant());
        assertTrue(viewModel.feedbackText().get().contains("standard roll-out fit"));
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
            document.layout(1744f, 938f);
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
    void categoryFiltersControlCatalogDisplay() {
        Reactor reactor = new Reactor();
        MechBay bay = new MechBay();
        MechLabViewModel viewModel = new MechLabViewModel(reactor, bay);
        viewModel.selectSlot(SocketId.ARMS);

        assertEquals(MechLabViewModel.CategoryFilter.ALL, viewModel.filter());
        int totalRows = viewModel.catalogRows().get().size();
        assertTrue(totalRows > 0);

        viewModel.setFilter(MechLabViewModel.CategoryFilter.BALLISTIC);
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            if (row.weaponPreview() != null) {
                assertEquals(MechWeaponComponent.HardpointType.BALLISTIC, row.weaponPreview().hardpointType);
            }
        }

        viewModel.setFilter(MechLabViewModel.CategoryFilter.MISSILE);
        for (MechLabViewModel.CatalogRow row : viewModel.catalogRows().get()) {
            if (row.weaponPreview() != null) {
                assertEquals(MechWeaponComponent.HardpointType.MISSILE, row.weaponPreview().hardpointType);
            }
        }

        viewModel.setFilter(MechLabViewModel.CategoryFilter.ALL);
        assertEquals(totalRows, viewModel.catalogRows().get().size());
    }

    @Test
    void strippingWeaponReturnsComponentToCargoAndClearsSocket() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        viewModel.gantryRows().get().get(0).select().run();

        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        viewModel.selectSlot(SocketId.ARMS);
        MechWeaponComponent initialWeapon = mech.arms();
        assertEquals(MechWeaponComponent.DUAL_CHAINGUNS, initialWeapon);

        int initialFree = bay.availableWeapon(initialWeapon.id);
        viewModel.stripWeapon(SocketId.ARMS);

        assertNull(mech.arms());
        assertEquals(initialFree + 1, bay.availableWeapon(initialWeapon.id));
        assertTrue(viewModel.feedbackText().get().contains("stripped and returned to cargo stores"));

        boolean foundArms = false;
        for (MechLabViewModel.SlotRow slot : viewModel.slotRows().get()) {
            if (slot.socketId() == SocketId.ARMS) {
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

    private static Map<String, Object> props(MechLabViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        // Layout evidence, so the heading only has to be a string of about the
        // right length; where the room actually is belongs to the ship.
        props.put("contextLabel", "CRUISER TROOP TRANSPORT / MIDSHIPS PORT / VEHICLE BAY");
        props.put("activeBayLabel", "BAY 01 / 01");
        props.put("bayNavigatorClasses", "bay-navigator hidden");
        props.put("previousBay", (Runnable) () -> { });
        props.put("nextBay", (Runnable) () -> { });
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("gantryRows", viewModel.gantryRows());
        props.put("activeGantryLabel", viewModel.activeGantryLabel());
        props.put("garageTitle", viewModel.garageTitle());
        props.put("selectedMechName", viewModel.selectedMechName());
        props.put("selectedMechIdentity", viewModel.selectedMechIdentity());
        props.put("selectedMechDoctrine", viewModel.selectedMechDoctrine());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("leftSlotRows", viewModel.leftSlotRows());
        props.put("rightSlotRows", viewModel.rightSlotRows());
        props.put("slotRows", viewModel.slotRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("selectedSlotRule", viewModel.selectedSlotRule());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("pickerClasses", viewModel.pickerClasses());
        props.put("workspaceClasses", viewModel.workspaceClasses());
        props.put("fittingHeaderClasses", viewModel.fittingHeaderClasses());
        props.put("performanceClasses", viewModel.performanceClasses());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("slotRackClasses", viewModel.slotRackClasses());
        props.put("overviewRailClasses", viewModel.overviewRailClasses());
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("previousGantry", viewModel.previousGantryAction());
        props.put("nextGantry", viewModel.nextGantryAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        props.put("categoryFilterPills", viewModel.categoryFilterPills());
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
