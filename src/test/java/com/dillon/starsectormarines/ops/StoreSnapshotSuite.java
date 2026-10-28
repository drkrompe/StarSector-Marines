package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatParts;
import com.dillon.starsectormarines.marine.EquipmentStore;
import com.dillon.starsectormarines.marine.StoreSlot;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.LoadoutCards;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;

/** The company store page over a fixed shelf, 1920x1080 at 100% UI scale. */
public final class StoreSnapshotSuite implements SnapshotSuite {
    public String id() { return "store"; }
    public String label() { return "Company store / shelf and purchase"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (var strings = new HeadlessStrings(context.modRoot())) {
            HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
            // A loadout card is built from the catalogs the Armory reads, medicine among them.
            DiskRegistries.install();
            HeadlessSvgAssets.install(context.modRoot());
            return List.of(
                    new SnapshotArtifact("store-shelf.png", frame(context, false, null, false)),
                    new SnapshotArtifact("store-after-purchase.png", frame(context, true, null, false)),
                    new SnapshotArtifact("store-mechs-category.png",
                            frame(context, false, "SECTION:MECHS", false)),
                    new SnapshotArtifact("store-vehicle-chassis.png",
                            frame(context, false, "SLOT:MECH_CHASSIS", true)),
                    new SnapshotArtifact("store-boats-category.png",
                            frame(context, false, "SECTION:BOATS", false)),
                    new SnapshotArtifact("store-armor-slot.png",
                            frame(context, false, "SLOT:ARMOR_LOADOUT", false)));
        }
    }

    private static BufferedImage frame(SnapshotContext context, boolean afterPurchase, String filter,
                                       boolean vehicleChassis)
            throws Exception {
        Reactor reactor = new Reactor();
        MarineArmory armory = new MarineArmory();
        List<LoadoutCard> cards = LoadoutCards.all();
        List<EquipmentStore.Offer> shelf = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            shelf.add(new EquipmentStore.Offer(EquipmentStore.Kind.LOADOUT, cards.get(i).id(),
                    i == 0 ? EquipmentStore.Shelf.LISTED : EquipmentStore.Shelf.MARKET, 6500));
        }
        shelf.add(new EquipmentStore.Offer(EquipmentStore.Kind.LOADOUT, cards.get(3).id(),
                EquipmentStore.Shelf.LICENSE, 6500));
        // One mech or vehicle pattern for every slot it can fill.
        Set<StoreSlot> seen = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) {
            // The chassis row is a mech's frame, or with vehicleChassis a vehicle hull.
            if (vehicleChassis && pattern.kind() == MechPattern.Kind.CHASSIS) continue;
            EquipmentStore.Offer offer = new EquipmentStore.Offer(EquipmentStore.Kind.MECH_PATTERN, pattern.id(),
                    EquipmentStore.Shelf.MARKET, 6500);
            if (seen.add(offer.slot())) shelf.add(offer);
        }
        // One shuttle part for every slot it can fill.
        for (String part : BoatParts.offerable()) {
            EquipmentStore.Offer offer = new EquipmentStore.Offer(EquipmentStore.Kind.BOAT_PART, part,
                    EquipmentStore.Shelf.MARKET, 6500);
            if (seen.add(offer.slot())) shelf.add(offer);
        }
        for (LoadoutCard card : cards) {
            if (card.kind() == LoadoutCard.Kind.SHEET) {
                shelf.add(new EquipmentStore.Offer(EquipmentStore.Kind.LOADOUT, card.id(),
                        EquipmentStore.Shelf.MARKET, 6500));
                break;
            }
        }
        long[] credits = {afterPurchase ? 20_000 : 9_000};
        FabricationResources purse = new FabricationResources() {
            @Override public long credits() { return credits[0]; }
            @Override public boolean atMarket() { return true; }
            @Override public boolean spend(FabricationCost cost) {
                credits[0] -= cost.credits();
                return true;
            }
        };
        StoreViewModel viewModel = new StoreViewModel(reactor, () -> shelf, () -> new EquipmentStore.Holdings(armory, new MechBay(), null),
                purse, () -> "Jangala");
        if (afterPurchase) viewModel.buySelected();
        if (filter != null) viewModel.selectFilter(filter);

        Map<String, Object> props = StoreScreen.props(viewModel);
        var saved = MarineOpsPageNav.storeOpen;
        MarineOpsPageNav.storeOpen = () -> true;
        try {
            Runnable none = () -> { };
            MarineOpsPageNav.put(props, MarineOpsPageNav.Page.STORE, MarineOpsPageNav.ANY_SHIP,
                    none, none, none, none, none, none, none, none);
            var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                    StoreScreen.COMPONENT_PATHS);
            try (var markup = loader.reloadAndBuild(reactor, "company-store", props)) {
                MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.STORE, MarineOpsPageNav.ANY_SHIP);
                var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
                markup.styles().forEach(document::addStyleSheet);
                SpecSheetLayer.install(document);
                StoreScreen.bindSymbols(document, markup, viewModel);
                StoreScreen.bindCard(document, null, markup, viewModel);
                var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                        new HeadlessBattleSceneRenderer(context.modRoot(), true));
                return renderer.renderRelative(document, 1920, 1080, 1f,
                        MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            }
        } finally {
            MarineOpsPageNav.storeOpen = saved;
        }
    }
}
