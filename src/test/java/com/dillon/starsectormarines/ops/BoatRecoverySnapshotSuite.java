package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.HullOutline;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.battleview.*;
import com.dillon.starsectormarines.testsupport.*;
import com.dillon.starsectormarines.tools.snapshot.*;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.json.JSONObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Production recovery panel and resumable portrait guidance, always 1920x1080 at 100%. */
public final class BoatRecoverySnapshotSuite implements SnapshotSuite {
    public String id() { return "boat-recovery"; }
    public String label() { return "Shuttle recovery and Castle guidance"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        var spec = new JSONObject(Files.readString(context.starsectorCore().resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        var hull = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED);
        var frames = new ArrayList<SnapshotArtifact>();
        for (String mode : List.of("damaged", "shortage", "castle", "ready", "expanded", "overview")) {
            try (var strings = new HeadlessStrings(context.modRoot(), text -> mode.equals("expanded")
                    ? OpeningLocalizationSnapshotSuite.expand(text) : text)) {
                var roster = new MarineRoster(); var boats = roster.boatDeck();
                boats.reconcile("home", ShuttleType.HERMES, 1);
                String id = boats.boats().get(0).id();
                boats.settleRecovery("return", Map.of(id, .75f), List.of(), 10f);
                roster.campaignLessons().observeBoatDamage(id);
                float day = mode.equals("ready") ? 12f : 10f;
                var resources = new FabricationResources() {
                    public int available(String commodity) { return mode.equals("shortage") ? 0 : 15_000_000; }
                    public String commodityIcon(String commodity) { return "graphics/icons/cargo/" + commodity + ".png"; }
                    public boolean spend(FabricationCost cost) { return canAfford(cost); }
                };
                var reactor = new Reactor();
                var model = new BoatDeckViewModel(reactor, boats, resources, "Second Wind", () -> {}, () -> day);
                if (!mode.equals("overview")) model.inspect(id);
                var ship = CompanyDeck.home(hull, 42, null, List::of, List::of, () -> new boolean[]{true})
                        .groundedBoats(() -> new boolean[]{day < 11.5f});
                try {
                    long deadline = System.nanoTime() + 60_000_000_000L;
                    while (!ship.ready()) {
                        if (System.nanoTime() > deadline) throw new AssertionError("Deck did not prepare");
                        Thread.sleep(10);
                    }
                    ship.advance(1f / 30f);
                    var berth = ship.scene().simulation().getAirfieldService().berths().get(0);
                    if (berth.grounded != (day < 11.5f) || (day < 11.5f && berth.airworthy()))
                        throw new AssertionError("Grounded aircraft is eligible for ambient sorties");
                    var bay = ship.rooms(RoomPurpose.HANGAR).get(0);
                    var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(
                            "data/ui/components/company/company-resource-stock.mlx",
                            "data/ui/components/company/company-resource-cost.mlx",
                            "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/boat-deck/boat-deck.mlx"));
                    try (var markup = loader.reloadAndBuild(reactor, "boat-deck", UiSnapshotSuite.props(model, ship, bay, 1))) {
                        var document = PreparationGuide.document(markup.root()).theme(MarineOpsThemes.standard());
                        markup.styles().forEach(document::addStyleSheet);
                        document.canvases().set(markup.requireElement("boat-deck-canvas"),
                                new BoatDeckCanvas(ship, () -> bay, model::selectedBerthIndex, () -> 0, n -> n == 0).recovery(model::berthRecovery));
                        document.layout(1920, 1080);
                        BoatDeckCanvas.fitSurface(markup.requireElement("boat-deck-canvas"));
                        if (mode.equals("castle") || mode.equals("ready")) {
                            var guide = new PreparationGuide(document, () -> CampaignLessons.boats(roster, model), () -> {},
                                    new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                                            List.of("data/ui/components/company/preparation-guide.mlx")));
                            guide.update();
                        }
                        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                                new HeadlessBattleSceneRenderer(context.modRoot(), true));
                        frames.add(new SnapshotArtifact(mode + ".png", renderer.renderRelative(document, 1920, 1080, 1f,
                                MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
                    }
                } finally { ship.dismiss(); }
            }
        }
        return frames;
    }
}
