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
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in production-document evidence: actual room anchors, hover, pinning and reviewed orders. */
public final class EngineeringOverlaySnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "engineering-overlays"; }
    @Override public String label() { return "Engineering room overlays"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
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
            return render(context, ship);
        } finally { ship.dismiss(); }
    }

    private List<SnapshotArtifact> render(SnapshotContext context, CompanyDeck ship) throws Exception {
        var reactor = new Reactor();
        int[] payments = {0};
        var model = new ShipConditionModel(reactor, () -> ship,
                work -> new CompanyShipRestoration.Offer(ship.ship().restoration(), 20000, null),
                work -> { payments[0]++; return "Evidence: no campaign mutation"; },
                () -> new CompanyBayRestoration.Offer(true, 20000, null), () -> "Evidence only",
                ignored -> {}, () -> {});
        var workshop = new WorkshopRestorationModel(reactor,
                () -> new CompanyWorkshopRestoration.Offer(WorkshopState.BURIED, 20000, null, null, 3, 20),
                state -> "Evidence only", () -> "Evidence only", () -> {}, () -> {});
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(
                "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/company/ship-view.mlx"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("survey", model.row()); props.put("workshop", workshop.row());
        props.put("equipmentAction", (Runnable) () -> {});
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
            Rect anchor = marker.box().borderBox();
            document.pointerMoved(anchor.x() + 17f, anchor.y() + 17f);
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
            require(payments[0] == 1, "Authorization failed or repeated");
            click(document, markup.requireElement("survey-close"));
            overlay.update(0f, ship, canvas);
            require(card.opacity() == 0f, "Close did not dismiss the inspection");
            overlay.select(-1); overlay.update(0f, ship, canvas);
            frames.add(new SnapshotArtifact("04-distribution.png", renderer.render(document, 1920, 1080)));
            overlay.select(ship.rooms().largest(RoomPurpose.PARTS_CAGE).id());
            overlay.update(0f, ship, canvas);
            frames.add(new SnapshotArtifact("05-workshop.png", renderer.render(document, 1920, 1080)));
            require(markup.requireElement("workshop-action").box().borderBox().width() <= 220f, "Workshop action grew into a full-width bar");
            click(document, markup.requireElement("workshop-action"));
            markup.flush(); overlay.update(0f, ship, canvas);
            require(workshop.row().get().reviewed(), "Workshop review is unreachable");
            overlay.select(mess.id()); overlay.update(0f, ship, canvas);
            canvas.camera().zoomAt(2f, canvas.toCameraX(800f), canvas.toCameraY(450f));
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
