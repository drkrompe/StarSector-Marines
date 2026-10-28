package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.fitting.FittingMeter;
import com.dillon.starsectormarines.ops.fitting.FittingFilterPill;
import com.dillon.starsectormarines.ops.fitting.FittingComponents;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.BoatGun;
import com.dillon.starsectormarines.marine.BoatHardpoint;
import com.dillon.starsectormarines.ops.battleview.BoatDoll;
import com.dillon.starsectormarines.marine.TestPurse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoatDeckViewModelTest {

    @Test
    void theHermesOffersEverySocketAndAFrameMeter() {
        BoatDeck boats = new BoatDeck();
        boats.reconcile("courier", ShuttleType.HERMES, 1);
        BoatDeckViewModel model = new BoatDeckViewModel(
                new Reactor(), boats, new TestPurse(60_000), "Second Wind");
        model.selectBoatAction(0).run();
        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            model.selectSlotAction(slot).run();
            long offered = BoatFitting.catalog(slot).stream().filter(f -> f.supports(ShuttleType.HERMES)).count();
            assertEquals(offered, model.catalogRows().get().size(), slot.name());
        }
        assertEquals("0 / 10", meter(model, "FRAME").value());
    }

    @Test
    void aBusClassBoatOffersTheLiftSlotAndItsCradles() {
        BoatDeck boats = new BoatDeck();
        boats.reconcile("freighter", ShuttleType.BUFFALO, 1);
        BoatDeckViewModel model = new BoatDeckViewModel(
                new Reactor(), boats, new TestPurse(60_000), "Buffalo");
        model.selectBoatAction(0).run();

        assertTrue(model.filterPills().get().stream().anyMatch(pill -> pill.id().equals("boat-filter:lift")));
        model.selectSlotAction(BoatFittingSlot.LIFT).run();
        assertEquals(4, model.catalogRows().get().size());
        assertEquals(BoatFitting.STANDARD_LIFT.displayName(), dollSlot(model, BoatDoll.slotKey(BoatFittingSlot.LIFT)).fitted());
    }

    @Test
    void aGunDraggedOntoAHardpointArmsTheDoll() {
        BoatDeckViewModel model = new BoatDeckViewModel(
                new Reactor(), deck(), new TestPurse(60_000), "Valkyrie");
        model.selectBoatAction(0).run();
        model.selectDollSlot(BoatDoll.HARDPOINT_KEY + 0);

        assertEquals(Strings.get("boatDeckStoresGuns"), model.selectedSlotTitle().get());
        assertEquals(BoatGun.catalog().size(), model.catalogRows().get().size());
        String mount = BoatDoll.HARDPOINT_KEY + 0;
        assertFalse(model.canDropOnSocket(BoatDoll.Kind.HARDPOINT, BoatGun.HEAVY_MG.id(), mount),
                "the yard gun is already on");
        assertFalse(model.canDropOnSocket(BoatDoll.Kind.HARDPOINT, BoatGun.ARBALEST.id(), mount),
                "none in stores yet");
        row(model.catalogRows().get(), BoatGun.ARBALEST.id()).card().action().run();
        assertFalse(model.canDropOnSocket(BoatDoll.Kind.HARDPOINT, BoatGun.ARBALEST.id(),
                BoatDoll.slotKey(BoatFittingSlot.DRIVE)), "a gun does not go in the drive");
        model.dropOnSocket(BoatDoll.Kind.HARDPOINT, BoatGun.ARBALEST.id(), mount);

        BoatDoll.Slot hardpoint = model.doll().get().slots().stream()
                .filter(slot -> slot.kind() == BoatDoll.Kind.HARDPOINT).findFirst().orElseThrow();
        assertEquals(BoatGun.ARBALEST.displayName(), hardpoint.fitted());
        assertTrue(hardpoint.selected());
    }

    /** A part dropped on the stores comes off the boat as a spare that can be picked up again. */
    @Test
    void aFittedPartLiftedOffGoesBackToStores() {
        BoatDeck deck = deck();
        BoatDeckViewModel model = new BoatDeckViewModel(
                new Reactor(), deck, new TestPurse(60_000), "Valkyrie");
        model.selectBoatAction(0).run();
        String drive = BoatDoll.slotKey(BoatFittingSlot.DRIVE);
        assertTrue(model.canLiftOff(drive), "the stock drive comes off like any part");
        assertFalse(model.canPickUp(BoatDoll.Kind.DRIVE, BoatFitting.TUNED_DRIVE.id()));
        row(model.catalogRows().get(), BoatFitting.TUNED_DRIVE.id()).card().action().run();
        model.dropOnSocket(BoatDoll.Kind.DRIVE, BoatFitting.TUNED_DRIVE.id(), drive);

        assertTrue(model.canLiftOff(drive));
        model.returnToStores(drive);

        assertNull(deck.boats().get(0).drive(), "the socket stands empty");
        assertEquals(1, deck.spares(BoatFitting.TUNED_DRIVE.id()));
        assertEquals(1, deck.spares(BoatFitting.STANDARD_DRIVE.id()), "the stock drive came off at the refit");
        assertTrue(model.canPickUp(BoatDoll.Kind.DRIVE, BoatFitting.TUNED_DRIVE.id()));
        assertTrue(model.feedbackText().get().contains("stores"), model.feedbackText().get());
        BoatDoll.Slot socket = model.doll().get().slots().stream()
                .filter(slot -> slot.key().equals(drive)).findFirst().orElseThrow();
        assertTrue(socket.missing(), "an empty drive warns: she cannot fly without one");
        assertFalse(deck.boats().get(0).flightReady(0f));
    }

    @Test
    void theStoresShowEveryPartUntilAFilterOrASocketNarrowsThem() {
        BoatDeckViewModel model = new BoatDeckViewModel(
                new Reactor(), deck(), new TestPurse(60_000), "Valkyrie");
        model.selectBoatAction(0).run();

        List<BoatDoll.Kind> kinds = model.catalogRows().get().stream()
                .map(BoatDeckViewModel.CatalogRow::kind).distinct().toList();
        assertEquals(List.of(BoatDoll.Kind.HARDPOINT, BoatDoll.Kind.PLATING, BoatDoll.Kind.DRIVE,
                BoatDoll.Kind.CABIN, BoatDoll.Kind.POWER, BoatDoll.Kind.LIFT, BoatDoll.Kind.EMITTER), kinds);
        model.selectSlotAction(BoatFittingSlot.DRIVE).run();
        assertTrue(model.catalogRows().get().stream().allMatch(row -> row.kind() == BoatDoll.Kind.DRIVE));
        model.filterPills().get().get(0).select().run();
        assertEquals(kinds, model.catalogRows().get().stream()
                .map(BoatDeckViewModel.CatalogRow::kind).distinct().toList());
    }

    private static final float EPSILON = 0.01f;
    private static final int BERTHS = 6;
    private static final List<String> COMPONENTS = FittingComponents.with(List.of(
            "data/ui/components/company/company-resource-stock.mlx", "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx",
            "data/ui/components/boat-deck/boat-deck.mlx")).stream().map(path -> "mod/" + path).toList();

    @Test
    void aFreshDeckShowsOneCardPerBerthAtStandardFit() {
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck(), new TestPurse(60_000), "Valkyrie");

        assertFalse(viewModel.fittingFocused());
        assertEquals(-1, viewModel.selectedBerthIndex());
        assertEquals(BERTHS, viewModel.boatRows().get().size());
        for (BoatDeckViewModel.BoatRow row : viewModel.boatRows().get()) {
            assertEquals(ShuttleType.AEROSHUTTLE.displayName(), row.pattern());
            assertEquals(BoatFitting.STANDARD_PLATING.displayName(), row.plating());
            assertEquals(BoatFitting.STANDARD_DRIVE.displayName(), row.drive());
        }
        assertTrue(viewModel.deckSummary().get().startsWith("6 boats aboard"),
                viewModel.deckSummary().get());
        assertTrue(viewModel.deckSummary().get().contains("carried by Valkyrie"));
        assertTrue(viewModel.overviewClasses().get().contains("boat-overview"));
        assertFalse(viewModel.overviewClasses().get().contains("hidden"));
        assertTrue(viewModel.fittingClasses().get().contains("hidden"));
    }

    @Test
    void selectingABerthOpensThatBoatOverItsInstalledFittings() {
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck(), new TestPurse(60_000), "Valkyrie");

        viewModel.boatRows().get().get(1).select().run();

        assertTrue(viewModel.fittingFocused());
        assertEquals(1, viewModel.selectedBerthIndex());
        assertTrue(viewModel.selectedBoatIdentity().get().contains("BERTH 02"),
                viewModel.selectedBoatIdentity().get());
        assertTrue(viewModel.overviewClasses().get().contains("hidden"));
        assertFalse(viewModel.fittingClasses().get().contains("hidden"));

        List<BoatDeckViewModel.ReadinessRow> readiness = viewModel.readinessRows().get();
        assertTrue(readiness.get(readiness.size() - 1).id().endsWith(":frame"), "her frame closes the list");
        assertTrue(readiness.stream().noneMatch(row -> row.classes().contains("readiness-danger")),
                "a boat at standard fit is missing nothing");
        assertEquals(List.of("boat-filter:all", "boat-filter:hardpoint", "boat-filter:plating", "boat-filter:drive",
                        "boat-filter:cabin", "boat-filter:power", "boat-filter:lift", "boat-filter:emitter"),
                viewModel.filterPills().get().stream().map(FittingFilterPill::id).toList());
    }

    @Test
    void buyingADriveSpendsItsWholeBillAndDroppingItMovesTheMountAndTheMeters() {
        BoatDeck deck = deck();
        TestPurse resources = new TestPurse(60_000);
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck, resources, "Valkyrie");
        viewModel.boatRows().get().get(0).select().run();
        BoatDeckViewModel.CatalogRow drive = row(viewModel.catalogRows().get(), BoatFitting.TUNED_DRIVE.id());
        long price = drive.materials().get(0).required();
        drive.card().action().run();

        viewModel.dropOnSocket(BoatDoll.Kind.DRIVE, BoatFitting.TUNED_DRIVE.id(),
                BoatDoll.slotKey(BoatFittingSlot.DRIVE));

        assertEquals(BoatFitting.TUNED_DRIVE.id(), deck.boats().get(0).drive().id());
        assertEquals(60_000 - price, resources.credits);
        assertEquals(BoatFitting.TUNED_DRIVE.displayName(),
                dollSlot(viewModel, BoatDoll.slotKey(BoatFittingSlot.DRIVE)).fitted());
        assertEquals(BoatFitting.TUNED_DRIVE.displayName(), viewModel.boatRows().get().get(0).drive());
        // 10 cells/s of pattern at the drive's 1.20, read as gain past the pattern's own figure.
        assertEquals("12 CELLS/S", meter(viewModel, "SPEED").value());
        assertEquals("meter-gain", meter(viewModel, "SPEED").gainClasses());
        assertTrue(viewModel.feedbackText().get().contains("fitted to"));
        assertTrue(viewModel.feedbackClasses().get().contains("tone-good"));
    }

    @Test
    void aPriceTheCompanyCannotCoverIsRefusedAndShowsTheShortfall() {
        BoatDeck deck = deck();
        TestPurse resources = new TestPurse(3);
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck, resources, "Valkyrie");
        viewModel.boatRows().get().get(0).select().run();

        BoatDeckViewModel.CatalogRow reinforced = row(viewModel.catalogRows().get(),
                BoatFitting.REINFORCED_PLATING.id());
        String plating = BoatDoll.slotKey(BoatFittingSlot.PLATING);
        assertFalse(viewModel.canDropOnSocket(reinforced.kind(), reinforced.itemId(), plating));
        // The card says it is short by the credits row's own chip, not by a word.
        assertEquals(1, reinforced.materials().size());
        assertTrue(reinforced.materials().get(0).shortfall() > 0);

        reinforced.card().action().run();
        assertTrue(viewModel.feedbackClasses().get().contains("tone-danger"));
        assertTrue(viewModel.feedbackText().get().contains("short"));
        viewModel.dropOnSocket(reinforced.kind(), reinforced.itemId(), plating);

        assertEquals(0, deck.spares(BoatFitting.REINFORCED_PLATING.id()));
        assertEquals(BoatFitting.STANDARD_PLATING.id(), deck.boats().get(0).plating().id());
        assertEquals(3, resources.credits);
        assertEquals("60 HP", meter(viewModel, "HULL").value());
    }

    @Test
    void backToDeckReturnsToTheOverviewWithoutTouchingTheBoats() {
        BoatDeck deck = deck();
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck, new TestPurse(60_000), "Valkyrie");
        viewModel.boatRows().get().get(2).select().run();
        assertTrue(viewModel.fittingFocused());

        viewModel.backToDeckAction().run();

        assertFalse(viewModel.fittingFocused());
        assertEquals(-1, viewModel.selectedBerthIndex());
        assertEquals(BERTHS, viewModel.boatRows().get().size());
        assertEquals(BoatFitting.STANDARD_PLATING.id(), deck.boats().get(2).plating().id());
    }

    @Test
    void theSummaryCountsEmptyBerths() {
        BoatDeck deck = deckOneBoatDown();
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck, new TestPurse(60_000), "Valkyrie");

        String summary = viewModel.deckSummary().get();
        assertTrue(summary.startsWith("5 boats aboard"), summary);
        assertTrue(summary.contains("1 berth empty"), summary);

        deck.lose(List.of(deck.boats().get(0).id()));
        viewModel.refresh();
        assertTrue(viewModel.deckSummary().get().contains("2 berths empty"),
                viewModel.deckSummary().get());
    }

    @Test
    void selectingAVacantBerthOpensFabricationNotFitting() {
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deckOneBoatDown(), new TestPurse(60_000), "Valkyrie");

        viewModel.boatRows().get().get(2).select().run();

        assertTrue(viewModel.fabricationFocused());
        assertFalse(viewModel.fittingFocused());
        assertEquals(2, viewModel.selectedBerthIndex());
        assertTrue(viewModel.overviewClasses().get().contains("hidden"));
        assertTrue(viewModel.fittingClasses().get().contains("hidden"));
        assertFalse(viewModel.fabricationClasses().get().contains("hidden"));
        assertEquals("BERTH 03", viewModel.fabricationBerthLabel().get());
        assertEquals(ShuttleType.AEROSHUTTLE.displayName(),
                viewModel.fabricationPatternName().get());
        assertEquals("PURPOSE-BUILT LANDER", viewModel.fabricationCopy().get());
        assertEquals(1, viewModel.fabricationMaterials().get().size());
        assertTrue(viewModel.fabricationMaterials().get().get(0).id().endsWith(":material:credits"));
        assertEquals("", viewModel.fabricationReason().get());
        assertFalse(viewModel.fabricationBlocked().get());

        viewModel.backToDeckAction().run();

        assertFalse(viewModel.fabricationFocused());
        assertEquals(-1, viewModel.selectedBerthIndex());
        assertFalse(viewModel.overviewClasses().get().contains("hidden"));
    }

    @Test
    void fabricatingSpendsTheBillStandsTheBoatAndOpensItsFitting() {
        BoatDeck deck = deckOneBoatDown();
        TestPurse resources = new TestPurse(60_000);
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck, resources, "Valkyrie");
        viewModel.boatRows().get().get(2).select().run();
        long price = viewModel.fabricationMaterials().get().get(0).required();

        viewModel.fabricateAction().run();

        assertEquals(60_000 - price, resources.credits);
        assertEquals(ShuttleType.AEROSHUTTLE, deck.boats().get(2).pattern());
        assertEquals(BoatFitting.STANDARD_PLATING.id(), deck.boats().get(2).plating().id());
        assertEquals(BoatFitting.STANDARD_DRIVE.id(), deck.boats().get(2).drive().id());
        assertTrue(deck.vacantBerths().isEmpty());

        // The player is still looking at berth three; what is standing in it
        // has changed, so the room is now that boat's fitting pane.
        assertEquals(2, viewModel.selectedBerthIndex());
        assertTrue(viewModel.fittingFocused());
        assertFalse(viewModel.fabricationFocused());
        assertFalse(viewModel.fittingClasses().get().contains("hidden"));
        assertTrue(viewModel.fabricationClasses().get().contains("hidden"));
        assertEquals(deck.boats().get(2).displayName(), viewModel.selectedBoatName().get());
        assertEquals(deck.boats().get(2).displayName(),
                viewModel.boatRows().get().get(2).name());
        assertTrue(viewModel.deckSummary().get().startsWith("6 boats aboard"),
                viewModel.deckSummary().get());
        assertTrue(viewModel.feedbackClasses().get().contains("tone-good"));
        assertTrue(viewModel.feedbackText().get().contains("BERTH 03"),
                viewModel.feedbackText().get());
    }

    @Test
    void aShortPurseDisablesFabricationAndSaysCredits() {
        BoatDeck deck = deckOneBoatDown();
        TestPurse resources = new TestPurse(41);
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                new Reactor(), deck, resources, "Valkyrie");
        viewModel.boatRows().get().get(2).select().run();

        assertTrue(viewModel.fabricationBlocked().get());
        assertEquals("SHORT CREDITS", viewModel.fabricationReason().get());
        assertTrue(viewModel.fabricationActionClasses().get().contains("blocked"),
                viewModel.fabricationActionClasses().get());

        viewModel.fabricateAction().run();

        assertNull(deck.boats().get(2));
        assertEquals(41, resources.credits);
        assertTrue(viewModel.fabricationFocused());
        assertTrue(viewModel.feedbackClasses().get().contains("tone-danger"));
        assertTrue(viewModel.feedbackText().get().contains("short"),
                viewModel.feedbackText().get());
    }

    @Test
    void buyingNeedsAPortEvenWhenTheCreditsAreThere() {
        TestPurse purse = new TestPurse(60_000);
        BoatDeckViewModel viewModel = new BoatDeckViewModel(new Reactor(), deck(), purse, "Valkyrie");
        viewModel.boatRows().get().get(0).select().run();
        String drive = BoatFitting.TUNED_DRIVE.id();

        assertFalse(row(viewModel.catalogRows().get(), drive).card().actionDisabled());
        assertEquals(Strings.get("companyResourcePortDocked"), portRow(viewModel).label());

        purse.away();
        viewModel.refresh();
        viewModel.boatRows().get().get(0).select().run();

        var card = row(viewModel.catalogRows().get(), drive).card();
        assertTrue(card.actionDisabled());
        assertEquals(Strings.get("fabricationNoPort"), card.actionHint());
        assertEquals(Strings.get("companyResourcePortAway"), portRow(viewModel).label());
        card.action().run();
        assertEquals(60_000, purse.credits);
    }

    private static CompanyResources.Row portRow(BoatDeckViewModel viewModel) {
        return viewModel.materialStock().get().stream()
                .filter(row -> row.id().equals("boat-material-stock:port")).findFirst().orElseThrow();
    }

    @Test
    void shippedRoomBuildsWithinWideAndLowResolutionBoundsAndOwnsItsRoute() throws Exception {
        Reactor reactor = new Reactor();
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                reactor, deck(), new TestPurse(60_000), "Valkyrie");
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();

        try (MarkupInstance instance = loader.build(
                reactor, "boat-deck", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());

            assertWithinRoot(document, instance, 1744f, 938f);
            assertWithinRoot(document, instance, 1163f, 625f);
            assertTrue(instance.requireElement("page-nav-boats").hasClass("selected"));
            assertTrue(instance.requireElement("page-nav-boats")
                    .hasClass("page-nav-current"));
        }
    }

    @Test
    void berthAndBackControlsNavigateWithPointerAt1080p() throws Exception {
        Reactor reactor = new Reactor();
        BoatDeckViewModel viewModel = new BoatDeckViewModel(
                reactor, deckOneBoatDown(), new TestPurse(60_000), "Valkyrie");
        MarkupLoader loader = new MarkupLoader(
                path -> Files.readString(Path.of(path)), COMPONENTS);
        loader.reload();
        try (MarkupInstance instance = loader.build(reactor, "boat-deck", props(viewModel))) {
            UiDocument document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            document.layout(1920, 1080);
            click(document, instance, instance.requireElement(viewModel.boatRows().get().get(0).id()));
            assertTrue(viewModel.fittingFocused());
            click(document, instance, instance.requireElement("boat-back-to-deck"));
            assertFalse(viewModel.fittingFocused());
            click(document, instance, instance.requireElement(viewModel.boatRows().get().get(2).id()));
            assertTrue(viewModel.fabricationFocused());
            click(document, instance, instance.requireElement("boat-fabrication-back"));
            assertFalse(viewModel.fabricationFocused());
        }
    }

    private static void click(UiDocument document, MarkupInstance instance, UiElement element) {
        var bounds = element.box().borderBox();
        float x = bounds.x() + bounds.width() * .5f;
        float y = bounds.y() + bounds.height() * .5f;
        document.pointerMoved(x, y);
        document.pointerDown(x, y);
        document.pointerUp(x, y);
        instance.flush();
        document.advance(0f);
    }

    private static void assertWithinRoot(UiDocument document, MarkupInstance instance,
                                         float width, float height) {
        document.layout(width, height);
        UiElement root = instance.requireElement("boat-deck-root");
        UiElement body = instance.requireElement("boat-deck-body");
        UiElement overview = instance.requireElement("boat-overview");
        assertTrue(body.box().borderBox().right()
                <= root.box().contentBox().right() + EPSILON);
        assertTrue(body.box().borderBox().bottom()
                <= root.box().contentBox().bottom() + EPSILON);
        assertTrue(overview.box().borderBox().right()
                <= body.box().contentBox().right() + EPSILON);
    }

    private static BoatDeck deck() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("transport", ShuttleType.AEROSHUTTLE, BERTHS);
        return deck;
    }

    /** The same deck with the third berth's boat shot down. */
    private static BoatDeck deckOneBoatDown() {
        BoatDeck deck = deck();
        deck.lose(List.of(deck.boats().get(2).id()));
        return deck;
    }

    private static BoatDeckViewModel.CatalogRow row(
            List<BoatDeckViewModel.CatalogRow> rows, String fittingId) {
        return rows.stream()
                .filter(row -> row.id().endsWith(fittingId))
                .findFirst().orElseThrow();
    }

    private static FittingMeter meter(BoatDeckViewModel viewModel,
                                                            String label) {
        return viewModel.performanceMeters().get().stream()
                .filter(row -> row.label().equals(label))
                .findFirst().orElseThrow();
    }

    static Map<String, Object> props(BoatDeckViewModel viewModel) {
        Map<String, Object> props = new LinkedHashMap<>();
        // Layout evidence, so the heading only has to be a string of about the
        // right length; where the room actually is belongs to the ship.
        props.put("contextLabel", "CRUISER TROOP TRANSPORT / MIDSHIPS PORT / HANGAR");
        props.put("activeBayLabel", "BAY 01 / 01");
        props.put("bayNavigatorClasses", "bay-navigator hidden");
        props.put("previousBay", (Runnable) () -> { });
        props.put("nextBay", (Runnable) () -> { });
        props.put("deckSummary", viewModel.deckSummary());
        props.put("materialStock", viewModel.materialStock());
        props.put("boatRows", viewModel.boatRows());
        props.put("recovery", viewModel.recovery());
        props.put("selectedBoatName", viewModel.selectedBoatName());
        props.put("selectedBoatIdentity", viewModel.selectedBoatIdentity());
        props.put("performanceMeters", viewModel.performanceMeters());
        props.put("readinessRows", viewModel.readinessRows());
        props.put("selectedSlotTitle", viewModel.selectedSlotTitle());
        props.put("selectedSlotCopy", viewModel.selectedSlotCopy());
        props.put("catalogRows", viewModel.catalogRows());
        props.put("catalogClasses", viewModel.catalogClasses());
        props.put("filterPills", viewModel.filterPills());
        props.put("filterClasses", viewModel.filterClasses());
        props.put("backClasses", viewModel.backClasses());
        props.put("stageTitle", viewModel.stageTitle());
        props.put("stageStatus", viewModel.stageStatus());
        props.put("overviewClasses", viewModel.overviewClasses());
        props.put("fittingClasses", viewModel.fittingClasses());
        props.put("fabricationClasses", viewModel.fabricationClasses());
        props.put("fabricationBerthLabel", viewModel.fabricationBerthLabel());
        props.put("fabricationPatternName", viewModel.fabricationPatternName());
        props.put("fabricationCopy", viewModel.fabricationCopy());
        props.put("fabricationMaterials", viewModel.fabricationMaterials());
        props.put("fabricationActionClasses", viewModel.fabricationActionClasses());
        props.put("fabricationBlocked", viewModel.fabricationBlocked());
        props.put("fabricationReason", viewModel.fabricationReason());
        props.put("fabricationActionLabel", viewModel.fabricationActionLabel());
        props.put("bayNoteClasses", "hidden");
        props.put("fabricate", viewModel.fabricateAction());
        props.put("backToDeck", viewModel.backToDeckAction());
        props.put("scrapLabel", viewModel.scrapLabel());
        props.put("scrapClasses", viewModel.scrapClasses());
        props.put("scrapAction", viewModel.scrapAction());
        viewModel.putReserveProps(props);
        props.put("feedbackText", viewModel.feedbackText());
        props.put("feedbackClasses", viewModel.feedbackClasses());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.BOAT_DECK,
                MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return props;
    }
    /** A missing required part heads the readiness list in red, and clicking it opens that socket. */
    @Test
    void aMissingRequiredPartHeadsTheReadinessListAndOpensItsSocket() {
        BoatDeck deck = deck();
        BoatDeckViewModel model = new BoatDeckViewModel(
                new Reactor(), deck, TestPurse.rich(), "Valkyrie");
        model.selectBoatAction(0).run();
        model.returnToStores(BoatDoll.slotKey(BoatFittingSlot.DRIVE));
        model.returnToStores(BoatDoll.slotKey(BoatFittingSlot.PLATING));
        model.selectDollSlot(BoatDoll.slotKey(BoatFittingSlot.CABIN));

        List<BoatDeckViewModel.ReadinessRow> rows = model.readinessRows().get();
        assertTrue(rows.get(0).classes().contains("readiness-danger"), rows.get(0).classes());
        assertTrue(rows.get(1).id().endsWith(":bare-hull"), rows.get(1).id());
        rows.get(0).select().run();
        assertTrue(dollSlot(model, BoatDoll.slotKey(BoatFittingSlot.DRIVE)).selected());
    }

    /**
     * A hardpoint takes two 2x1 guns by position: the board reads them as a pod, each comes off or
     * moves on its own, and the readiness counts empty mounts and the guns on the rest.
     */
    @Test
    void twoHalfGunsDroppedOnOneHardpointReadAsAPod() {
        BoatDeck boats = new BoatDeck();
        boats.reconcile("courier", ShuttleType.HERMES, 1);
        BoatDeckViewModel model = new BoatDeckViewModel(
                new Reactor(), boats, new TestPurse(60_000), "Second Wind");
        model.selectBoatAction(0).run();
        model.selectDollSlot(BoatDoll.HARDPOINT_KEY + 1);
        String laser = BoatGun.PULSE_LASER.id();
        String top = BoatDoll.positionKey(1, BoatHardpoint.TOP);
        String bottom = BoatDoll.positionKey(1, BoatHardpoint.BOTTOM);

        row(model.catalogRows().get(), laser).card().action().run();
        model.dropOnSocket(BoatDoll.Kind.HARDPOINT, laser, top);
        row(model.catalogRows().get(), laser).card().action().run();
        assertTrue(model.canDropOnSocket(BoatDoll.Kind.HARDPOINT, laser, bottom), "the bottom is open for a second");
        model.dropOnSocket(BoatDoll.Kind.HARDPOINT, laser, bottom);

        BoatDoll.Slot pod = dollSlot(model, BoatDoll.HARDPOINT_KEY + 1);
        assertEquals(laser, pod.itemId());
        assertEquals(laser, pod.podItemId());
        assertTrue(pod.selected(), "a position's key opens its hardpoint");
        assertEquals(laser, model.installedIn(bottom));
        BoatDeckViewModel.ReadinessRow bare = model.readinessRows().get().stream()
                .filter(row -> row.id().endsWith(":bare-mounts")).findFirst().orElseThrow();
        assertEquals(Strings.format("boatReadinessBareMounts", 1, 3), bare.title());
        assertEquals(Strings.format("boatReadinessBareMountsDetail", 3, 2), bare.detail(),
                "a pod is two guns on one mount");

        String spare = BoatDoll.positionKey(2, BoatHardpoint.TOP);
        assertTrue(model.canMoveBetweenSockets(bottom, spare));
        assertFalse(model.canMoveBetweenSockets(top, bottom), "a laser does not move onto the one beside it");
        model.moveBetweenSockets(bottom, spare);
        assertNull(dollSlot(model, BoatDoll.HARDPOINT_KEY + 1).podItemId());
        assertEquals(laser, dollSlot(model, BoatDoll.HARDPOINT_KEY + 2).itemId());
        model.returnToStores(spare);
        assertEquals(1, boats.spares(laser));
        assertEquals(laser, dollSlot(model, BoatDoll.HARDPOINT_KEY + 1).itemId(), "the other laser stays on");
    }

    private static BoatDoll.Slot dollSlot(BoatDeckViewModel model, String key) {
        return model.doll().get().slots().stream().filter(slot -> slot.key().equals(key)).findFirst().orElseThrow();
    }
}
