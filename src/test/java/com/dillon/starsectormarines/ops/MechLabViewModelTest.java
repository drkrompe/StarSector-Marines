package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MechBay;
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
        MechBay bay = new MechBay();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);

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
    void selectingTypedWeaponSocketIsInspectionOnlyUntilWeaponAuthorityExists() {
        MechBay bay = new MechBay();
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), bay);
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        String originalReplenisher = mech.missileReplenisherId();

        viewModel.leftSlotRows().get().stream()
                .filter(row -> row.id().endsWith("arms"))
                .findFirst().orElseThrow().select().run();

        assertTrue(viewModel.fittingFocused());
        assertTrue(viewModel.selectedSlotRule().get().contains("BALLISTIC SOCKET"));
        assertEquals(1, viewModel.catalogRows().get().size());
        assertTrue(viewModel.catalogRows().get().get(0).actionDisabled());
        assertEquals(originalReplenisher, mech.missileReplenisherId());
        assertTrue(viewModel.feedbackText().get().contains("Inspection only"));

        viewModel.overviewAction().run();
        assertFalse(viewModel.fittingFocused());
        assertTrue(viewModel.feedbackText().get().contains("overview restored"));
    }

    @Test
    void assetPickerIsASeparateInternalScreenAndSelectionReturnsToGantry() {
        MechLabViewModel viewModel = new MechLabViewModel(new Reactor(), new MechBay());

        assertTrue(viewModel.pickerClasses().get().contains("hidden"));
        assertFalse(viewModel.workspaceClasses().get().contains("hidden"));
        viewModel.openAssetPickerAction().run();
        assertFalse(viewModel.pickerClasses().get().contains("hidden"));
        assertTrue(viewModel.workspaceClasses().get().contains("hidden"));

        viewModel.mechRows().get().get(0).select().run();
        assertTrue(viewModel.pickerClasses().get().contains("hidden"));
        assertFalse(viewModel.workspaceClasses().get().contains("hidden"));
    }

    @Test
    void gantryNavigatorVisitsAssignedAssetsAndVacantStations() {
        MechBay bay = new MechBay();
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
        assertTrue(viewModel.activeGantryLabel().get().contains("GANTRY 02 / 04"));
        assertEquals("Hound 02", viewModel.selectedMechName().get());

        viewModel.nextGantryAction().run();
        viewModel.nextGantryAction().run();
        assertEquals(3, viewModel.selectedGantryIndex());
        assertNull(viewModel.selectedVariant());
        assertTrue(viewModel.activeGantryLabel().get().contains("VACANT"));

        viewModel.nextGantryAction().run();
        assertEquals(0, viewModel.selectedGantryIndex());
        viewModel.previousGantryAction().run();
        assertEquals(3, viewModel.selectedGantryIndex());
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
        props.put("labSummary", viewModel.labSummary());
        props.put("squadRows", viewModel.squadRows());
        props.put("mechRows", viewModel.mechRows());
        props.put("activeGantryLabel", viewModel.activeGantryLabel());
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
        props.put("openAssetPicker", viewModel.openAssetPickerAction());
        props.put("closeAssetPicker", viewModel.closeAssetPickerAction());
        props.put("previousGantry", viewModel.previousGantryAction());
        props.put("nextGantry", viewModel.nextGantryAction());
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.MECH_LAB,
                () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }
}
