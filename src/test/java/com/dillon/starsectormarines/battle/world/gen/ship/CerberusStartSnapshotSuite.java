package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.dillon.starsectormarines.ops.UiSnapshotSuite;
import com.dillon.starsectormarines.ops.WorkshopRestorationModel;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration.Offer;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
import com.dillon.starsectormarines.ops.ShipConditionModel;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.battle.world.gen.ship.stage.ShipConditionStage;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.ArrayDeque;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Real hull geometry and complete room program, including varied campaign seeds. */
public final class CerberusStartSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "cerberus-start"; }
    @Override public String label() { return "Cerberus company conversion"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        // Vanilla's Cerberus hull id lives in warhound.ship. Read its collision
        // polygon, exactly as the campaign resolver does through getShipFilePath.
        var spec = new JSONObject(Files.readString(context.starsectorCore().resolve("data/hulls/warhound.ship")));
        if (!"cerberus".equals(spec.getString("hullId"))) throw new AssertionError("Wrong hull");
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (BoatBayState state : List.of(BoatBayState.DAMAGED, BoatBayState.RESTORED)) {
            for (boolean conversion : List.of(false, true)) {
                CompanyShip ship = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20,
                        50 + (conversion ? 18 : 0), 100 - (conversion ? 25 : 0),
                        outline.aspect(), outline, spec.getString("spriteName"), conversion, state);
                if (conversion && state == BoatBayState.DAMAGED) frames.addAll(condition(context, ship));
                if (ship.quarters() != conversion) throw new AssertionError("Frigate eligibility");
                for (long seed : conversion ? new long[]{42, 81, 417, 2026, 987654} : new long[]{42}) {
                    var generator = new ShipDeckGenerator();
                    var map = generator.generateDeck(ship.deckPlan(), seed, ship.outline());
                    var graph = generator.getLastDeckGraph();
                    var interior = ShipInterior.of(ship, graph);
                    if (conversion) {
                        for (var purpose : List.of(RoomPurpose.BRIDGE, RoomPurpose.BARRACKS, RoomPurpose.ARMORY,
                                RoomPurpose.HANGAR, RoomPurpose.VEHICLE_BAY))
                            if (!interior.has(purpose)) throw new AssertionError("Missing " + purpose + " seed " + seed);
                        if (interior.facility(RoomPurpose.BARRACKS).capacity() < 12)
                            throw new AssertionError("Squad cannot berth");
                        if (map.gantries.stream().filter(g -> g.holds == Gantry.Holds.MACHINE).count() != 4)
                            throw new AssertionError("Restored original hull must have four machine gantries");
                        if (map.gantries.stream().filter(g -> g.holds == Gantry.Holds.BOAT).count() != (state == BoatBayState.DAMAGED ? 1 : 2))
                            throw new AssertionError("Working berth count must match condition");
                        if (!graph.unplaced().isEmpty()) throw new AssertionError("Unplaced " + graph.unplaced());
                    }
                    if (conversion && state == BoatBayState.DAMAGED) {
                        var restored = ship.withBoatBay(BoatBayState.RESTORED);
                        var other = new ShipDeckGenerator();
                        var restoredMap = other.generateDeck(restored.deckPlan(), seed, restored.outline());
                        var after = other.getLastDeckGraph();
                        if (!layout(graph).equals(layout(after))) throw new AssertionError("Repair moved a compartment");
                        if (!wear(map).equals(wear(restoredMap))) throw new AssertionError("Bay repair changed lasting scars");
                        if (ship.deckPlan().frames() != restored.deckPlan().frames()
                                || ship.deckPlan().height() != restored.deckPlan().height())
                            throw new AssertionError("Repair resized the hull");
                        if (graph.apertures().size() != 1 || after.apertures().size() != 2)
                            throw new AssertionError("Repair must open exactly one sealed launch door");
                        var sealed = graph.compartments().stream().filter(r -> graph.damaged(r.id())).findFirst().orElseThrow();
                        for (var door : sealed.doors()) {
                            if (map.grid.isWalkable(door.x(), door.y()))
                                throw new AssertionError("Damaged bay hatch remains open");
                            if (map.topology.isWall(door.x(), door.y())
                                    || !"ship-floor-trim.passage".equals(map.topology.getSurfaceId(door.x(), door.y())))
                                throw new AssertionError("Sealed bay hatch must render over deck, not a wall block");
                            if (!map.grid.blocksStructuralLineOfSightAt(map.grid.index(door.x(), door.y())))
                                throw new AssertionError("Sealed bay hatch must remain opaque");
                            if (map.doodads.stream().noneMatch(d -> d.cellX == door.x() && d.cellY == door.y()
                                    && "ship-sealed-hatches.pressure-hatch".equals(d.definitionId())))
                                throw new AssertionError("Sealed entrance has no visible hatch");
                            if (!restoredMap.grid.isWalkable(door.x(), door.y()))
                                throw new AssertionError("Repair did not reopen the personnel hatch");
                        }
                        if (restoredMap.doodads.stream().anyMatch(d -> "ship-sealed-hatches.pressure-hatch".equals(d.definitionId())))
                            throw new AssertionError("Restored bay retained a sealed-hatch sprite");
                    }
                    if (conversion && seed == 42) {
                        if (state == BoatBayState.DAMAGED) {
                            frames.addAll(commissionedHermes(context, ship));
                            frames.addAll(workshop(context, ship));
                        }
                        CompanyDeck deck = CompanyDeck.home(ship, seed, null, List::of, List::of,
                                () -> new boolean[]{true, false});
                        try {
                            while (!deck.ready()) Thread.onSpinWait();
                            deck.advance(1f / 30f);
                            if (deck.rooms().apertures().size() != (state == BoatBayState.DAMAGED ? 1 : 2))
                                throw new AssertionError("Home deck cache returned the wrong bay condition");
                            var bay = deck.rooms(RoomPurpose.HANGAR).get(1);
                            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
                            frames.add(new SnapshotArtifact("bay-" + state.name().toLowerCase() + "-detail.png",
                                    renderer.renderHostPass(deck.scene().pass(ShipDeckBattleScene.DeckView.over(bay, 2, 64),
                                            EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.UNITS, RenderLayer.SHUTTLES)),
                                            (bay.width() + 4) * 64, (bay.depth() + 4) * 64)));
                            for (float scale : new float[]{1f, 1.5f}) {
                                frames.add(new SnapshotArtifact("bay-" + state.name().toLowerCase() + "-" + scale + ".png",
                                        UiSnapshotSuite.renderBayRestoration(context, deck, state == BoatBayState.RESTORED, 12000, true, scale)));
                                if (state == BoatBayState.DAMAGED)
                                    frames.add(new SnapshotArtifact("bay-locked-" + scale + ".png",
                                            UiSnapshotSuite.renderBayRestoration(context, deck, false, 4000, false, scale)));
                            }
                        } finally { deck.dismiss(); }
                    }
                    String name = (conversion ? "converted" : "unconverted") + "-" + state.name().toLowerCase() + "-" + seed;
                    frames.add(new SnapshotArtifact(name + ".png", ShipDeckSnapshotSuite.renderPlan(map,
                            generator.getLastDeckProfile(), graph, "Cerberus " + name, ship.deckPlan(),
                            ship.minCrew() + "/" + ship.maxCrew() + " crew, " + ship.cargo() + " cargo", RoomFit.STANDARD)));
                }
            }
        }
        return List.copyOf(frames);
    }
    private List<SnapshotArtifact> condition(SnapshotContext context, CompanyShip hull) throws Exception {
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (long seed : new long[]{42, 81, 417, 2026, 987654}) {
            List<String> baseline = null;
            for (int phase : new int[]{0, 1, 2}) {
                boolean restored = phase > 0;
                var state = new ShipRestoration(true, restored, phase == 2, phase == 2, phase == 2);
                var ship = hull.withWorkshop(WorkshopState.BURIED).withRestoration(state);
                var deck = CompanyDeck.home(ship, seed, null, List::of, List::of, () -> new boolean[]{true});
                try {
                    while (!deck.ready()) Thread.onSpinWait();
                    deck.advance(1f / 30f);
                    var graph = deck.rooms();
                    assertPreparationAccess(deck.map(), graph);
                    if (baseline == null) baseline = layout(graph);
                    else if (!baseline.equals(layout(graph))) throw new AssertionError("Condition repairs moved rooms");
                    long gantries = deck.map().gantries.stream().filter(g -> g.holds == Gantry.Holds.MACHINE).count();
                    if (gantries != (restored ? 1 : 0)) throw new AssertionError("Sealed Mech Lab offers a gantry");
                    if (deck.has(RoomPurpose.VEHICLE_BAY) != restored) throw new AssertionError("Mech Lab navigation disagrees with condition");
                    var mess = deck.room(RoomPurpose.MESS_HALL);
                    if (deck.has(RoomPurpose.MESS_HALL) != state.messHall()) throw new AssertionError("Wrong mess availability");
                    boolean meals = deck.map().fixtureTasks.stream().anyMatch(t -> mess.contains(t.cellX(), t.cellY()));
                    if (meals != state.messHall()) throw new AssertionError("Mess tasks disagree with repair");
                    int messCapacity = ShipInterior.of(ship, graph).facility(RoomPurpose.MESS_HALL).capacity();
                    if (messCapacity != (state.messHall() ? 90 : 0)) throw new AssertionError("Wrong working mess capacity");
                    int billets = ShipInterior.of(ship, graph).facility(RoomPurpose.BARRACKS).capacity();
                    if (graph.damaged(deck.room(RoomPurpose.BARRACKS).id())) throw new AssertionError("Default Barracks opens sealed quarters");
                    if (billets != (phase == 2 ? 54 : 36)) throw new AssertionError("Wrong usable billet count: " + billets);
                    for (var room : graph.compartments()) if (ShipConditionStage.sealed(graph, room, state)) {
                        if (deck.map().fixtureTasks.stream().anyMatch(t -> room.contains(t.cellX(), t.cellY()))) throw new AssertionError("Sealed room offers work");
                        for (int y = room.top(); y <= room.bottom(); y++) for (int x = room.left(); x <= room.right(); x++)
                            if (room.contains(x, y) && deck.map().grid.isWalkable(x, y)) throw new AssertionError("Crew can enter sealed room");
                    }
                    if (seed == 42) {
                        Reactor reactor = new Reactor();
                        var model = new ShipConditionModel(reactor, () -> deck,
                                work -> new CompanyShipRestoration.Offer(state, 12000, null),
                                work -> "Snapshot only", () -> new CompanyBayRestoration.Offer(true, 12000, null), () -> "Snapshot only",
                                room -> {}, () -> {});
                        String name = phase == 2 ? "restored" : phase == 1 ? "mission-two" : "damaged";
                        frames.add(new SnapshotArtifact("condition-" + name + "-ship.png",
                                UiSnapshotSuite.renderShipCondition(context, deck, model, reactor, 1f, null)));
                        model.select(deck.room(RoomPurpose.VEHICLE_BAY).id());
                        for (float scale : new float[]{1f, 1.5f}) frames.add(new SnapshotArtifact("condition-" + name + "-mech-" + scale + ".png",
                                UiSnapshotSuite.renderShipCondition(context, deck, model, reactor, scale, RoomPurpose.VEHICLE_BAY)));
                        model.select(mess.id());
                        for (float scale : new float[]{1f, 1.5f}) frames.add(new SnapshotArtifact("condition-" + name + "-mess-" + scale + ".png",
                                UiSnapshotSuite.renderShipCondition(context, deck, model, reactor, scale, RoomPurpose.MESS_HALL)));
                        model.select(ShipConditionStage.spareBillets(graph).get(0).id());
                        frames.add(new SnapshotArtifact("condition-" + name + "-billets.png",
                                UiSnapshotSuite.renderShipCondition(context, deck, model, reactor, 1f, null)));
                    }
                } finally { deck.dismiss(); }
            }
        }
        return frames;
    }

    private List<SnapshotArtifact> workshop(SnapshotContext context, CompanyShip hull) throws Exception {
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (long seed : new long[]{42, 81, 2026}) {
            List<String> baseline = null;
            List<String> lastingWear = null;
            for (WorkshopState state : List.of(WorkshopState.BURIED, WorkshopState.CLEARED,
                    WorkshopState.REPAIRED, WorkshopState.EQUIPPED)) {
                CompanyShip ship = hull.withWorkshop(state);
                CompanyDeck deck = CompanyDeck.home(ship, seed, null, List::of, List::of, () -> new boolean[]{true});
                try {
                    while (!deck.ready()) Thread.onSpinWait();
                    deck.advance(1f / 30f);
                    var graph = deck.rooms();
                    assertPreparationAccess(deck.map(), graph);
                    if (lastingWear == null) lastingWear = wear(deck.map());
                    else if (!lastingWear.equals(wear(deck.map()))) throw new AssertionError("Workshop repair changed lasting scars");
                    if (baseline == null) baseline = layout(graph);
                    else if (!baseline.equals(layout(graph))) throw new AssertionError("Workshop stage moved rooms");
                    var room = deck.room(RoomPurpose.PARTS_CAGE);
                    var tasks = deck.map().fixtureTasks.stream().filter(t -> room.contains(t.cellX(), t.cellY())).toList();
                    if (!state.operational() && !tasks.isEmpty()) throw new AssertionError("Broken shop still offers work");
                    if (state.operational() && tasks.isEmpty()) throw new AssertionError("Restored shop has no work");
                    if (state == WorkshopState.REPAIRED && tasks.stream().anyMatch(t -> t.affordance() == Affordance.FABRICATE))
                        throw new AssertionError("Unequipped machines offer precision fabrication");
                    boolean debris = deck.map().doodads.stream().anyMatch(d -> room.contains(d.cellX, d.cellY)
                            && "workshop-damage.collapse".equals(d.definitionId()));
                    if (debris != (state == WorkshopState.BURIED)) throw new AssertionError("Cached room has wrong debris state");
                    boolean breach = deck.map().doodads.stream().anyMatch(d -> room.contains(d.cellX, d.cellY)
                            && "workshop-damage.breach".equals(d.definitionId()));
                    if (breach != (state == WorkshopState.BURIED)) throw new AssertionError("Wrong pressure condition");
                    if (breach) for (int y = room.top(); y <= room.bottom(); y++)
                        for (int x = room.left(); x <= room.right(); x++)
                            if (room.contains(x, y) && deck.map().grid.isWalkable(x, y))
                                throw new AssertionError("Crew can enter exposed workshop");
                    if (seed == 42) {
                        // Same hull, seed, occupancy, and simulation time in every condition.
                        // Keep the second boat bay damaged so only the workshop changes.
                        for (boolean close : new boolean[]{false, true}) frames.add(new SnapshotArtifact(
                                "workshop-" + state.name().toLowerCase() + (close ? "-detail.png" : "-ship.png"),
                                UiSnapshotSuite.renderWorkshopRestoration(context, deck,
                                        WorkshopRestorationModel.Row.hidden(), 1f, close)));
                        var row = new WorkshopRestorationModel.Row(new Offer(state, 12000,
                                state == WorkshopState.EQUIPPED ? "Workshop fully equipped." : null,
                                state.operational() ? null : "Repair the workshop first.", 2, 10), true, false, "", () -> {}, () -> {}, () -> {});
                        for (float scale : new float[]{1f, 1.5f}) frames.add(new SnapshotArtifact(
                                "workshop-" + state.name().toLowerCase() + "-" + scale + ".png",
                                UiSnapshotSuite.renderWorkshopRestoration(context, deck, row, scale)));
                        if (state == WorkshopState.BURIED) {
                            // One complete local lighting cycle, sampled through the production deck clock.
                            for (int frame = 0; frame < 44; frame++) {
                                deck.scene().advanceTo(.25f * frame + .1f);
                                frames.add(new SnapshotArtifact("lighting-" + String.format("%02d", frame) + ".png",
                                        UiSnapshotSuite.renderWorkshopRestoration(context, deck,
                                                WorkshopRestorationModel.Row.hidden(), 1f, true)));
                            }
                        }
                    }
                } finally { deck.dismiss(); }
            }
        }
        return frames;
    }

    static void assertPreparationAccess(MapResult map, DeckGraph graph) {
        int width = map.grid.getWidth(), height = map.grid.getHeight();
        boolean[][] reached = new boolean[width][height];
        var start = graph.largest(RoomPurpose.BRIDGE).doors().stream()
                .filter(d -> map.grid.isWalkable(d.x(), d.y())).findFirst().orElseThrow();
        var queue = new ArrayDeque<int[]>(); queue.add(new int[]{start.x(), start.y()});
        reached[start.x()][start.y()] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = cell[0] + step[0], y = cell[1] + step[1];
                if (x < 0 || y < 0 || x >= width || y >= height || reached[x][y] || !map.grid.isWalkable(x, y)) continue;
                reached[x][y] = true; queue.addLast(new int[]{x, y});
            }
        }
        for (var room : graph.compartments()) {
            if (graph.damaged(room.id()) || !List.of(RoomPurpose.BARRACKS, RoomPurpose.ARMORY,
                    RoomPurpose.HANGAR, RoomPurpose.VEHICLE_BAY).contains(room.purpose())) continue;
            if (room.doors().stream().noneMatch(d -> reached[d.x()][d.y()]))
                throw new AssertionError("Restoration isolated preparation room " + room.id() + " " + room.purpose());
        }
    }

    private static List<String> wear(MapResult map) {
        return map.doodads.stream().filter(d -> d.definitionId() != null && d.definitionId().startsWith("ship-wear."))
                .map(d -> d.definitionId() + ":" + d.cellX + ":" + d.cellY + ":" + d.quarterTurns + ":" + d.mirrored).toList();
    }

    private static List<String> layout(DeckGraph graph) {
        return graph.compartments().stream().map(r -> r.id() + ":" + r.originX() + ":" + r.originY()
                + ":" + r.width() + ":" + r.depth() + ":" + r.purpose() + ":" + r.pose() + ":" + r.doors()).toList();
    }
    /** The starting hull, empty and then with the same berth commissioned. */
    private List<SnapshotArtifact> commissionedHermes(SnapshotContext context, CompanyShip hull) throws Exception {
        MarineRoster roster = new MarineRoster();
        MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0f);
        roster.add(mira);
        roster.beginCompanyPreparation(mira);
        BoatDeck boats = roster.boatDeck();
        boats.reconcile("cerberus", ShuttleType.HERMES, 1);
        Map<String, Integer> stock = new HashMap<>(Map.of("metals", 60, "heavy_machinery", 16, "supplies", 40));
        var workshop = new BoatWorkshop(boats, new FabricationResources() {
            @Override public int available(String id) { return stock.getOrDefault(id, 0); }
            @Override public String commodityName(String id) { return id; }
            @Override public String commodityIcon(String id) { return ""; }
            @Override public boolean spend(FabricationCost cost) {
                if (!canAfford(cost)) return false;
                for (var line : cost.lines()) stock.compute(line.commodityId(), (id, held) -> held - line.quantity());
                return true;
            }
        });
        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (int occupied = 0; occupied <= 2; occupied++) {
            if (occupied == 2) {
                hull = hull.withBoatBay(BoatBayState.RESTORED);
                boats.reconcile("cerberus", ShuttleType.HERMES, 2);
            }
            if (occupied > 0 && !workshop.fabricate(occupied - 1).succeeded())
                throw new AssertionError("Hermes not commissioned in berth " + occupied);
            CompanyDeck deck = CompanyDeck.home(hull, 42, null, List::of, List::of,
                    () -> new boolean[]{boats.boats().get(0) != null, boats.boats().size() > 1 && boats.boats().get(1) != null});
            try {
                while (!deck.ready()) Thread.onSpinWait();
                deck.advance(1f / 30f);
                var scene = deck.scene();
                var berths = scene.simulation().getAirfieldService().berths();
                if (berths.size() != occupied) throw new AssertionError("Wrong rendered occupancy: expected=" + occupied + ", berths=" + berths.size());
                if (berths.stream().anyMatch(b -> b.airframe != ShuttleType.HERMES))
                    throw new AssertionError("Wrong commissioned airframe");
                var bay = deck.rooms(RoomPurpose.HANGAR).get(occupied == 2 ? 1 : 0);
                frames.add(new SnapshotArtifact(occupied == 2 ? "hermes-second-commissioned.png" : occupied == 1 ? "hermes-commissioned.png" : "hermes-empty.png",
                        renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(bay, 2, 48),
                                EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.UNITS, RenderLayer.SHUTTLES)),
                                (bay.width() + 4) * 48, (bay.depth() + 4) * 48)));
            } finally { deck.dismiss(); }
        }
        return frames;
    }

}
