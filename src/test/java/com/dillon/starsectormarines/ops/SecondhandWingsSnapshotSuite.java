package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.HullOutline;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.campaign.start.SecondhandWingsCampaign.Offer;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.SecondhandWings;
import com.dillon.starsectormarines.ops.battleview.BoatDeckCanvas;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.json.JSONObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual room markup and campaign transactions; 1920x1080 at 100%, no live game implied. */
public final class SecondhandWingsSnapshotSuite implements SnapshotSuite {
    public String id() { return "secondhand-wings"; }
    public String label() { return "Second Wind / wrecks to working landers"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        var spec = new JSONObject(Files.readString(context.starsectorCore().resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        var hull = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED);
        var roster = new MarineRoster();
        var mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0f);
        roster.add(mira); roster.beginCompanyPreparation(mira);
        var boats = roster.boatDeck();
        boats.registerFoundingWrecks("second-wind");
        boats.reconcile("second-wind", ShuttleType.HERMES, 1);
        var reactor = new Reactor();
        var resources = new FabricationResources() {
            public int available(String id) { return 200; }
            public boolean spend(FabricationCost cost) { return true; }
            public String commodityIcon(String id) { return "graphics/icons/cargo/" + id + ".png"; }
        };
        var model = new BoatDeckViewModel(reactor, boats, resources, "Second Wind");
        var frames = new ArrayList<SnapshotArtifact>();
        model.selectBoatAction(0).run();
        frames.add(frame(context, hull, reactor, boats, model, null, "01-repair-first-hermes.png"));
        model.fabricateAction().run();
        frames.add(frame(context, hull, reactor, boats, model, null, "01b-first-hermes-repaired.png"));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        roster.mechBay().beginLabLesson();
        frames.add(frame(context, hull, reactor, boats, model, null, BoatBayRestorationModel.Row.hidden(), 0,
                CompanyNotices.unread(roster, BoatBayState.DAMAGED), "01c-unread-room-opportunities.png"));
        frames.add(missionNews(context, CompanyNotices.unread(roster, BoatBayState.DAMAGED)));
        AtomicInteger credits = new AtomicInteger(15000);
        boolean[] restored = {false};
        var wings = new SecondhandWingsModel(reactor, boats,
                () -> new Offer(!boats.secondhandWingsPurchased(), SecondhandWings.refusal(boats, true, true,
                        restored[0], true, true, credits.get()), credits.get()),
                review -> SecondhandWings.purchase(boats, review,
                        SecondhandWings.refusal(boats, true, true, restored[0], true, true, credits.get()),
                        n -> { if (credits.get() < n) return false; credits.addAndGet(-n); return true; }),
                model::refresh, () -> model.selectBoatAction(0).run());
        wings.open();
        frames.add(frame(context, hull, reactor, boats, model, wings, "02-castles-lead.png"));
        wings.row().get().action().run();
        frames.add(frame(context, hull, reactor, boats, model, wings, "03-separate-bay-required.png"));
        wings.dismiss();
        CompanyNotices.Notice.SECONDHAND_WINGS.review(roster);
        frames.add(frame(context, hull, reactor, boats, model, null, BoatBayRestorationModel.Row.hidden(), 0,
                CompanyNotices.unread(roster, BoatBayState.DAMAGED), "03a-other-news-remains.png"));
        model.backToDeckAction().run();
        frames.add(frame(context, hull, reactor, boats, model, null,
                new BoatBayRestorationModel.Row(true, true, credits.get(), null, () -> { }),
                1, "03b-inspect-second-bay.png"));
        // The existing bay transaction is represented explicitly: 8,000 credits, no boat reward.
        credits.addAndGet(-8000); restored[0] = true;
        hull = hull.withBoatBay(BoatBayState.RESTORED);
        boats.reconcile("second-wind", ShuttleType.HERMES, 2);
        model.refresh(); model.selectBoatAction(1).run();
        frames.add(frame(context, hull, reactor, boats, model, null, "04-second-hermes-wreck.png"));
        model.fabricateAction().run();
        frames.add(frame(context, hull, reactor, boats, model, null, "04b-second-hermes-repaired.png"));
        wings.open(); wings.row().get().action().run();
        frames.add(frame(context, hull, reactor, boats, model, wings, "05-reviewed-exchange.png"));
        wings.row().get().action().run();
        if (credits.get() != 2000 || boats.airworthy().size() != 2 || boats.pattern() != ShuttleType.AEROSHUTTLE)
            throw new AssertionError("Purchase did not apply exactly once");
        hull = hull.withAeroshuttleConversion(true);
        frames.add(frame(context, hull, reactor, boats, model, wings, "06-delivery.png"));
        wings.row().get().action().run();
        frames.add(frame(context, hull, reactor, boats, model, wings, "07-aeroshuttle-fittings.png"));
        return frames;
    }

    private SnapshotArtifact frame(SnapshotContext context, CompanyShip hull, Reactor reactor,
                                   BoatDeck boats, BoatDeckViewModel model, SecondhandWingsModel wings,
                                   String name) throws Exception {
        return frame(context, hull, reactor, boats, model, wings, BoatBayRestorationModel.Row.hidden(),
                Math.max(0, model.selectedBerthIndex()), name);
    }

    private SnapshotArtifact frame(SnapshotContext context, CompanyShip hull, Reactor reactor,
                                   BoatDeck boats, BoatDeckViewModel model, SecondhandWingsModel wings,
                                   BoatBayRestorationModel.Row restoration, int selected, String name) throws Exception {
        return frame(context, hull, reactor, boats, model, wings, restoration, selected, List.of(), name);
    }

    private SnapshotArtifact frame(SnapshotContext context, CompanyShip hull, Reactor reactor,
                                   BoatDeck boats, BoatDeckViewModel model, SecondhandWingsModel wings,
                                   BoatBayRestorationModel.Row restoration, int selected,
                                   List<CompanyNotices.Notice> notices, String name) throws Exception {
        var ship = CompanyDeck.home(hull, 42, null, List::of, List::of, () -> {
            boolean[] occupied = new boolean[boats.berths()];
            for (int i = 0; i < occupied.length; i++) occupied[i] = boats.boats().get(i) != null;
            return occupied;
        }, () -> {
            boolean[] wrecks = new boolean[boats.berths()];
            for (int i = 0; i < wrecks.length; i++) wrecks[i] = boats.wreckInBerth(i);
            return wrecks;
        });
        try {
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (!ship.ready()) {
                if (System.nanoTime() > deadline) throw new AssertionError("Deck did not become ready");
                Thread.sleep(10);
            }
            ship.advance(1f / 30f);
            var airfield = ship.scene().simulation().getAirfieldService();
            long expectedWrecks = boats.vacantBerths().stream().filter(boats::wreckInBerth).count();
            if (airfield.berths().stream().filter(b -> b.wreckOnPad).count() != expectedWrecks)
                throw new AssertionError("Original wreck presentation disagrees with campaign");
            if (airfield.berths().stream().anyMatch(b -> b.wreckOnPad && (b.airworthy() || b.airframeId != 0)))
                throw new AssertionError("Wreck became an active aircraft");
            if (airfield.berths().stream().anyMatch(b -> b.airframe != boats.pattern()))
                throw new AssertionError("Cached hangar still carries the wrong airframe");
            var bay = ship.rooms(RoomPurpose.HANGAR).get(selected);
            var props = UiSnapshotSuite.props(model, ship, bay, 2);
            props.put("activeBayLabel", selected == 0 ? "BAY 01 / 02"
                    : restoration.visible() ? "02/02 SEALED" : "BAY 02 / 02");
            props.put("restoration", restoration);
            if (wings != null) { wings.refresh(); props.put("wings", wings.row()); }
            var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(
                    "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/boat-deck/boat-deck.mlx"));
            loader.reload();
            try (var markup = loader.build(reactor, "boat-deck", props)) {
                var document = new UiDocument(markup.root());
                markup.styles().forEach(document::addStyleSheet);
                document.theme(MarineOpsThemes.standard());
                MarineOpsPageNav.refresh(markup, MarineOpsPageNav.Page.BOAT_DECK, MarineOpsPageNav.ANY_SHIP, notices);
                document.canvases().set(markup.requireElement("boat-deck-canvas"), new BoatDeckCanvas(ship, () -> bay,
                        model::selectedBerthIndex, () -> selected,
                        n -> n >= 0 && n < boats.berths() && boats.boats().get(n) != null));
                if (wings != null) TutorialModal.bind(document, markup, wings::visible,
                        "wings-modal", "boat-deck-root", wings::dismiss, () -> { });
                var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                        new HeadlessBattleSceneRenderer(context.modRoot(), true));
                return new SnapshotArtifact(name, renderer.renderRelative(document, 1920, 1080, 1f,
                        MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
            }
        } finally { ship.dismiss(); }
    }

    private SnapshotArtifact missionNews(SnapshotContext context, List<CompanyNotices.Notice> notices) throws Exception {
        var props = MissionSelectScreen.previewProps();
        MissionSelectScreen.putCompanyNews(props, notices, () -> { });
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        try (var markup = loader.reloadAndBuild(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            return new SnapshotArtifact("01d-contract-board-news.png", renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }
}
