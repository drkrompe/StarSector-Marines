package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentStore;
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
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The company store page over a fixed shelf, 1920x1080 at 100% UI scale. */
public final class StoreSnapshotSuite implements SnapshotSuite {
    public String id() { return "store"; }
    public String label() { return "Company store / shelf and purchase"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (var strings = new HeadlessStrings(context.modRoot())) {
            HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
            HeadlessSvgAssets.install(context.modRoot());
            return List.of(
                    new SnapshotArtifact("store-shelf.png", frame(context, false)),
                    new SnapshotArtifact("store-after-purchase.png", frame(context, true)));
        }
    }

    private static BufferedImage frame(SnapshotContext context, boolean afterPurchase) throws Exception {
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
        shelf.add(new EquipmentStore.Offer(EquipmentStore.Kind.MECH_PATTERN,
                MechPattern.offerable().get(0).id(), EquipmentStore.Shelf.MARKET, 6500));
        long[] credits = {afterPurchase ? 20_000 : 9_000};
        FabricationResources purse = new FabricationResources() {
            @Override public long credits() { return credits[0]; }
            @Override public boolean atMarket() { return true; }
            @Override public boolean spend(FabricationCost cost) {
                credits[0] -= cost.credits();
                return true;
            }
        };
        StoreViewModel viewModel = new StoreViewModel(reactor, () -> shelf, () -> armory, MechBay::new,
                purse, () -> "Jangala");
        if (afterPurchase) viewModel.buySelected();

        Map<String, Object> props = StoreScreen.props(viewModel);
        var saved = MarineOpsPageNav.storeOpen;
        MarineOpsPageNav.storeOpen = () -> true;
        try {
            Runnable none = () -> { };
            MarineOpsPageNav.put(props, MarineOpsPageNav.Page.STORE, MarineOpsPageNav.ANY_SHIP,
                    none, none, none, none, none, none, none, none);
            var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(
                    "data/ui/components/company/company-resource-stock.mlx",
                    "data/ui/components/company/company-resource-cost.mlx",
                    "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/company-store.mlx"));
            try (var markup = loader.reloadAndBuild(reactor, "company-store", props)) {
                MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.STORE, MarineOpsPageNav.ANY_SHIP);
                var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
                markup.styles().forEach(document::addStyleSheet);
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
