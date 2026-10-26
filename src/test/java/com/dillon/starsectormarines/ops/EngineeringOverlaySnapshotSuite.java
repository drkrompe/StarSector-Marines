package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullOutline;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipRestoration;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitOrder;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import com.dillon.starsectormarines.campaign.refit.ShipRefits;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
import com.dillon.starsectormarines.testsupport.HeadlessCommodityPresentation;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipViewCanvas;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.json.JSONObject;

import java.nio.file.Files;
import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in production-document evidence: actual room anchors, hover, pinning and reviewed orders. */
public final class EngineeringOverlaySnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "engineering-overlays"; }
    @Override public String label() { return "Engineering room overlays"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception { return render(context, false); }

    List<SnapshotArtifact> render(SnapshotContext context, boolean resourceBills) throws Exception {
        if (TileRegistry.installed() == null) {
            var tiles = new TileRegistry();
            for (String path : TileRegistry.BUILTIN_TILESETS)
                tiles.ingestSheet(new JSONObject(Files.readString(context.modRoot().resolve(path))));
            tiles.validateReferences();
            TileRegistry.install(tiles);
        }
        if (GenMappingRegistry.installed() == null) {
            var mappings = new GenMappingRegistry();
            for (String path : GenMappingRegistry.BUILTIN_MAPPINGS)
                mappings.ingest(new JSONObject(Files.readString(context.modRoot().resolve(path))));
            mappings.validateReferences();
            GenMappingRegistry.install(mappings);
        }
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        var spec = new JSONObject(Files.readString(context.starsectorCore().resolve("data/hulls/warhound.ship")));
        var source = spec.getJSONArray("bounds");
        float[] polygon = new float[source.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) source.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        var hull = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED)
                .withWorkshop(WorkshopState.BURIED).withRestoration(ShipRestoration.WRECK);
        var ship = CompanyDeck.home(hull, 42, null, List::of, List::of, () -> new boolean[]{true});
        try {
            while (!ship.ready()) Thread.onSpinWait();
            ship.advance(1f / 30f);
            return render(context, ship, resourceBills);
        } finally { ship.dismiss(); }
    }

    private List<SnapshotArtifact> render(SnapshotContext context, CompanyDeck ship, boolean resourceBills) throws Exception {
        var reactor = new Reactor();
        var goods = HeadlessCommodityPresentation.from(context.starsectorCore());
        int[] payments = {0};
        // Mutable so the reveal can land a real tier: life support, the one this hold can afford.
        ShipCondition[] refit = {REFIT};
        ship.dress(CoreDressing.restored(REFIT.restoredCores()));
        var model = new ShipConditionModel(reactor, () -> ship,
                work -> new CompanyShipRestoration.Offer(ship.ship().restoration(), 20000, null),
                work -> { payments[0]++; return "Evidence: no campaign mutation"; },
                () -> new CompanyBayRestoration.Offer(true, 20000, null), () -> "Evidence only",
                () -> refit[0], order -> new ShipRefits.Offer(refit[0], 20000, CARGO,
                        ShipRefits.refusal(order, refit[0], true, true, true, true, 20000, CARGO, goods::commodityName)),
                order -> {
                    if (order != RefitOrder.LIFE_SUPPORT_RESTORED) { payments[0]++; return "Evidence: no campaign mutation"; }
                    refit[0] = refit[0].with(order.track, order.band);
                    ship.dress(CoreDressing.restored(refit[0].restoredCores()), RefitReveal.WAVE);
                    return null;
                }, goods,
                ignored -> {}, () -> {});
        var offer = new CompanyWorkshopRestoration.Offer[]{new CompanyWorkshopRestoration.Offer(WorkshopState.BURIED, 20000, null, null, 3, 20)};
        var workshop = new WorkshopRestorationModel(reactor,
                () -> offer[0],
                state -> "Evidence only", () -> "Evidence only", () -> {}, () -> {}, goods);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(
                "data/ui/components/company/company-resource-stock.mlx",
            "data/ui/components/company/company-resource-cost.mlx", "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/ship-view.mlx"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("survey", model.row()); props.put("workshop", workshop.row());
        props.put("roomTitle", "ENGINEERING // SHIP CONDITION");
        props.put("roomCopy", "Hover to inspect; click to pin. Right-drag to pan; wheel to zoom.");
        props.put("contextLabel", "SHIP SERVICES");
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.SHIP_VIEW,
                purpose -> ship.has(purpose) ? MarineOpsPageNav.Aboard.YES : MarineOpsPageNav.Aboard.NO,
                () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
        try (var markup = loader.reloadAndBuild(reactor, "ship-view", props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            var canvas = new ShipViewCanvas(ship);
            document.canvases().set(markup.requireElement("ship-view-deck"), canvas);
            var overlay = new ShipConditionOverlay(document, markup, model);
            document.onCancel(overlay::dismiss);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            List<SnapshotArtifact> frames = new ArrayList<>();
            renderer.render(document, 1920, 1080); // Prime the actual camera and font metrics.
            overlay.update(0f, ship, canvas);
            frames.add(new SnapshotArtifact("01-overview.png", renderer.render(document, 1920, 1080)));
            var mess = ship.rooms().largest(RoomPurpose.MESS_HALL);
            UiElement marker = markup.requireElement("survey-room-" + mess.id());
            require(marker.opacity() == 1f, "Room marker is invisible");
            require(marker.background().getAlpha() == 0 && marker.backgroundImage() == null,
                    "Shared button theme restored an opaque tile behind the room glyph");
            Rect anchor = marker.box().borderBox();
            require(anchor.width() <= 22.1f, "Room glyph grew into an obstructive tile");
            document.pointerMoved(anchor.x() + anchor.width() / 2f, anchor.y() + anchor.height() / 2f);
            overlay.update(0.1f, ship, canvas);
            UiElement card = markup.requireElement("ship-condition");
            require(card.opacity() > 0f && model.selectedRoomId() == mess.id(), "Hover did not inspect the mess");
            frames.add(new SnapshotArtifact("02-hover-mess.png", renderer.render(document, 1920, 1080)));
            Rect box = card.box().borderBox();
            document.pointerMoved(box.x() + 20f, box.y() + 20f);
            overlay.update(0.5f, ship, canvas);
            require(card.opacity() > 0f, "Card collapsed crossing from room to controls");
            require(overlay.blocksCamera(box.x() + 20f, box.y() + 20f), "Card leaks camera input");
            click(document, markup.requireElement("ship-condition-action"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(payments[0] == 0, "Review spent credits");
            document.pointerMoved(20f, 100f);
            overlay.update(1f, ship, canvas);
            require(card.opacity() > 0f, "Interacting with a hover card did not pin it");
            frames.add(new SnapshotArtifact("03-pinned-review.png", renderer.render(document, 1920, 1080)));
            click(document, markup.requireElement("ship-condition-action"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(overlay.revealing() && payments[0] == 0, "A refit was paid for before the deck could show it");
            overlay.dismiss(); // Escape hurries the reveal to its curtain, where the order is paid.
            for (int i = 0; i < 100 && overlay.revealing(); i++) { overlay.update(0.1f, ship, canvas); markup.flush(); }
            require(!overlay.revealing() && payments[0] == 1, "Authorization failed or repeated");
            click(document, markup.requireElement("survey-close"));
            overlay.update(0f, ship, canvas);
            require(card.opacity() == 0f, "Close did not dismiss the inspection");
            require(markup.requireElement("systems-strip").opacity() > 0f, "The deck lost its core-system chips");
            click(document, markup.requireElement("systems-chip-power"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(model.systemsOpen() && markup.requireElement("ship-systems").opacity() > 0f, "A core chip did not open the systems tree");
            require(card.opacity() > 0f, "The systems tree opened without its detail card");
            require(markup.requireElement("survey-needs").opacity() > 0f, "The next Power tier did not list what it needs");
            frames.add(new SnapshotArtifact("04-systems-power.png", renderer.render(document, 1920, 1080)));
            click(document, markup.requireElement("systems-flux-header"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(model.row().get().get("reason").equals(ShipRefits.refusal(RefitOrder.FLUX_RESTORED, REFIT, true, true, true, true, 20000, CARGO, goods::commodityName)),
                    "A locked combat tier did not name what it waits on");
            frames.add(new SnapshotArtifact("04-systems-flux-locked.png", renderer.render(document, 1920, 1080)));
            click(document, markup.requireElement("systems-hull-header"));
            markup.flush(); overlay.update(0f, ship, canvas);
            click(document, markup.requireElement("ship-condition-action"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(payments[0] == 1, "Reviewing a refit tier spent credits");
            frames.add(new SnapshotArtifact("04-systems-hull-review.png", renderer.render(document, 1920, 1080)));
            frames.addAll(reveal(document, markup, model, overlay, renderer, ship, canvas));
            click(document, markup.requireElement("systems-back"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(!model.systemsOpen() && markup.requireElement("ship-systems").opacity() == 0f && card.opacity() == 0f,
                    "Back to deck left the systems tree up: open=" + model.systemsOpen() + " tree=" + markup.requireElement("ship-systems").opacity() + " card=" + card.opacity());
            overlay.select(ship.rooms().largest(RoomPurpose.PARTS_CAGE).id());
            overlay.update(0f, ship, canvas);
            frames.add(new SnapshotArtifact("05-workshop.png", renderer.render(document, 1920, 1080)));
            require(markup.requireElement("workshop-action").box().borderBox().width() <= 220f, "Workshop action grew into a full-width bar");
            click(document, markup.requireElement("workshop-action"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(workshop.row().get().reviewed(), "Workshop review is unreachable");
            if (resourceBills) {
                offer[0] = new CompanyWorkshopRestoration.Offer(WorkshopState.BURIED, 41, "Insufficient credits", null, 3, 20);
                workshop.refresh(); markup.flush(); overlay.update(0f, ship, canvas);
                frames.add(new SnapshotArtifact("06-workshop-short.png", renderer.render(document, 1920, 1080)));
                offer[0] = new CompanyWorkshopRestoration.Offer(WorkshopState.REPAIRED, 15000000, null, null, 15000000, 15000000);
                workshop.refresh(); markup.flush(); overlay.update(0f, ship, canvas);
                document.advance(.2f);
                require(markup.requireElement("engineering-credit-stock:icon").svgImage() != null, "Credit icon lost during refresh");
                require(markup.requireElement("engineering-credit-stock:label").text().equals("15,000,000"), "Large stock did not refresh");
                frames.add(new SnapshotArtifact("07-reclamation.png", renderer.render(document, 1920, 1080)));
                // Tooled, with the reclamation line on offer as the fourth stage.
                offer[0] = new CompanyWorkshopRestoration.Offer(WorkshopState.EQUIPPED, 20000, null, null, 3, 20);
                workshop.refresh(); markup.flush(); overlay.update(0f, ship, canvas);
                frames.add(new SnapshotArtifact("08-reclamation-line.png", renderer.render(document, 1920, 1080)));
                return List.copyOf(frames);
            }
            // The hull's reserves are on the plan, sealed and inspectable, before
            // any refit opens them.
            var reserves = ship.rooms().compartments().stream().filter(r -> ship.rooms().reserved(r.id())).toList();
            require(reserves.size() == 5, "Second Wind should keep five sealed reserves, found " + reserves.size());
            for (var reserve : reserves) markup.requireElement("survey-room-" + reserve.id());
            var range = ship.rooms().largest(RoomPurpose.FIRING_RANGE);
            require(range != null && ship.rooms().reserved(range.id()) && !ship.has(RoomPurpose.FIRING_RANGE),
                    "The reserved range must be on the plan and out of service");
            int shown = 0;
            for (var reserve : reserves) {
                overlay.select(reserve.id()); overlay.update(0f, ship, canvas);
                require(model.selectedRoomId() == reserve.id(), "A reserve could not be inspected: " + reserve.purpose());
                frames.add(new SnapshotArtifact(String.format("07-reserve-%d-%s.png", ++shown,
                        reserve.purpose().name().toLowerCase().replace('_', '-')), renderer.render(document, 1920, 1080)));
            }
            overlay.select(mess.id()); overlay.update(0f, ship, canvas);
            // Zoom on the mess itself, wherever the hull keeps it, so the
            // projection is checked on a marker that stays on the stage.
            Rect messMarker = markup.requireElement("survey-room-" + mess.id()).box().borderBox();
            float messX = messMarker.x() + messMarker.width() / 2f, messY = messMarker.y() + messMarker.height() / 2f;
            var deckMetrics = document.canvasMetrics(markup.requireElement("ship-view-deck"), 1f);
            canvas.camera().zoomAt(2f, canvas.toCameraX(deckMetrics.toCanvasX(messX)),
                    canvas.toCameraY(deckMetrics.toCanvasY(messY)));
            canvas.camera().panByCells(6f, 4f);
            overlay.update(0f, ship, canvas);
            frames.add(new SnapshotArtifact("06-pan-zoom.png", renderer.render(document, 1920, 1080)));
            Rect projected = canvas.markerBounds(mess, document.canvasMetrics(markup.requireElement("ship-view-deck"), 1f));
            require(Math.abs(marker.box().borderBox().x() - projected.x()) < 0.2f, "Marker drifted from camera projection");
            renderer.renderRelative(document, 1920, 1080, 1.5f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
            overlay.update(0f, ship, canvas);
            box = card.box().borderBox();
            Rect stage = markup.requireElement("ship-view-stage").box().contentBox();
            require(box.x() >= stage.x() && box.right() <= stage.right() && box.bottom() <= stage.bottom(), "Scaled card escapes deck");
            System.out.println("ENGINEERING hover/pin/close/review/payment/camera/150%-bounds PASS");
            return List.copyOf(frames);
        }
    }

    /** Mid-way through the cores: power and structure restored, everything else as found. */
    /** A mid-campaign hold: metals to spare, a little machinery, and no AI cores yet. */
    private static final Map<String, Integer> CARGO = Map.of(Commodities.METALS, 140,
            Commodities.HEAVY_MACHINERY, 6, Commodities.RARE_METALS, 4);

    private static final ShipCondition REFIT = ShipCondition.battered()
            .with(RefitTrack.POWER, RefitBand.RESTORED).with(RefitTrack.STRUCTURE, RefitBand.RESTORED);

    /**
     * Authorizes life support and plays the reveal out at a steady frame rate: the camera flies to
     * the barracks, the order is paid as it arrives, and the scrubbers' haze clears across the deck
     * as a wave before the result is shown. No curtain: the deck is the same deck throughout.
     */
    private static List<SnapshotArtifact> reveal(UiDocument document, MarkupInstance markup, ShipConditionModel model,
                                                 ShipConditionOverlay overlay, HeadlessUiRenderer renderer,
                                                 CompanyDeck ship, ShipViewCanvas canvas) throws Exception {
        click(document, markup.requireElement("systems-life-support-header"));
        markup.flush(); overlay.update(0f, ship, canvas);
        click(document, markup.requireElement("ship-condition-action"));
        markup.flush(); overlay.update(0f, ship, canvas);
        click(document, markup.requireElement("ship-condition-action"));
        markup.flush(); overlay.update(0f, ship, canvas);
        require(overlay.revealing(), "Authorizing a refit did not stage it on the deck");
        List<SnapshotArtifact> stills = new ArrayList<>();
        List<BufferedImage> movie = new ArrayList<>();
        BufferedImage previous = null;
        float dt = 1f / 12f;
        boolean flew = false, before = false, dark = false, shown = false, curtained = false;
        for (int frame = 0; frame < 200 && overlay.revealing(); frame++) {
            ship.advance(dt);
            boolean wasWaving = ship.dressingWave();
            overlay.update(dt, ship, canvas);
            markup.flush();
            BufferedImage image = renderer.render(document, 1920, 1080);
            movie.add(downscale(image));
            float curtain = markup.requireElement("reveal-curtain").opacity();
            float caption = markup.requireElement("reveal-caption").opacity();
            if (!flew && frame == 8) { stills.add(new SnapshotArtifact("08-reveal-1-flight.png", image)); flew = true; }
            curtained |= curtain > 0f;
            if (!before && !wasWaving && ship.dressingWave()) {
                // The last frame before the wave set out: the room as it was, framed as it will be.
                stills.add(new SnapshotArtifact("08-reveal-2-before.png", previous));
                before = true;
            }
            previous = image;
            if (!dark && ship.dressingWave() && ship.dressingProgress() >= 0.5f) {
                stills.add(new SnapshotArtifact("08-reveal-3-wave.png", image)); dark = true;
            }
            if (!shown && !ship.dressingWave() && dark && caption >= 0.99f && markup.requireElement("reveal-frame").opacity() > 0f) {
                stills.add(new SnapshotArtifact("08-reveal-4-after.png", image)); shown = true;
            }
        }
        require(!overlay.revealing(), "The reveal never finished");
        require(flew && before && dark && shown, "The reveal skipped a beat: flight " + flew + ", before " + before + ", wave " + dark + ", result " + shown);
        require(!curtained, "A core's wave was hidden behind the curtain");
        require(model.systemsOpen(), "The tree did not come back after the reveal");
        markup.flush(); overlay.update(0f, ship, canvas);
        stills.add(new SnapshotArtifact("08-reveal-5-back-to-tree.png", renderer.render(document, 1920, 1080)));
        stills.add(SnapshotArtifact.colorAnimation("08-reveal.gif", movie, 83));
        return stills;
    }

    /** Half size keeps a seven-second animation a reasonable file; the stills carry full resolution. */
    private static BufferedImage downscale(BufferedImage image) {
        var small = new BufferedImage(image.getWidth() / 2, image.getHeight() / 2, BufferedImage.TYPE_INT_RGB);
        var g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, small.getWidth(), small.getHeight(), null);
        g.dispose();
        return small;
    }

    private static void click(UiDocument document, UiElement element) {
        Rect box = element.box().borderBox();
        float x = box.x() + box.width() / 2f, y = box.y() + box.height() / 2f;
        require(document.elementAt(x, y) == element, "Control is clipped or obscured: " + element.id());
        document.pointerMoved(x, y); document.pointerDown(x, y); document.pointerUp(x, y);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
