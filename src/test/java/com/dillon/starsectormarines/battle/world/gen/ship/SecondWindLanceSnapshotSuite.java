package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.dillon.starsectormarines.ops.ShipConditionModel;
import com.dillon.starsectormarines.ops.UiSnapshotSuite;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Opt-in physical fit and restoration evidence inside Second Wind's original
 * envelope: the four-berth lance bay, and her refit reserves sealed and opened.
 */
public final class SecondWindLanceSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "second-wind-lance"; }
    @Override public String label() { return "Second Wind four-berth restoration"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        var spec = new JSONObject(Files.readString(context.starsectorCore().resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        var hull = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED)
                .withWorkshop(WorkshopState.BURIED);
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (long seed : new long[]{42, 81, 417, 2026, 987654}) {
            List<DeckGraph.Compartment> layout = null;
            List<String> previousBerths = List.of();
            for (int count : new int[]{0, ShipRestoration.RECOVERED_MECH_BERTHS, 3, 4}) {
                var state = new ShipRestoration(true, count > 0, false, false, false, Math.max(0, count - 2));
                var ship = hull.withRestoration(state);
                var plan = ship.deckPlan();
                // These are the pre-expansion envelope measured with the actual Cerberus outline.
                if (plan.frames() != 106 || plan.height() != 79) throw new AssertionError("Hull enlarged to fit the bay");
                var mechs = Collections.nCopies(count, MechVariant.HOUND);
                var deck = CompanyDeck.home(ship, seed, null, () -> mechs, List::of, () -> new boolean[]{true});
                try {
                    while (!deck.ready()) Thread.onSpinWait();
                    deck.advance(1f / 30f);
                    var map = deck.map();
                    var graph = deck.rooms();
                    var room = graph.largest(RoomPurpose.VEHICLE_BAY);
                    if (layout == null) layout = graph.compartments();
                    else if (!layout.equals(graph.compartments())) throw new AssertionError("Repair moved rooms or doors");
                    if (!graph.unplaced().isEmpty()) throw new AssertionError("Unplaced " + graph.unplaced());
                    CerberusStartSnapshotSuite.assertPreparationAccess(map, graph);
                    var berths = map.gantries.stream().filter(g -> g.holds == Gantry.Holds.MACHINE).toList();
                    if (berths.size() != count) throw new AssertionError("Wrong physical capacity");
                    if (ShipInterior.of(ship, graph).facility(RoomPurpose.VEHICLE_BAY).capacity() != count)
                        throw new AssertionError("Ship comparison disagrees with working fixtures");
                    if (deck.has(RoomPurpose.VEHICLE_BAY) != (count > 0)) throw new AssertionError("Wrong Mech Lab access");
                    var identities = berths.stream().map(g -> g.left() + ":" + g.bottom() + ":" + g.facing).toList();
                    if (!identities.subList(0, previousBerths.size()).equals(previousBerths)) throw new AssertionError("Repair moved an occupied berth");
                    previousBerths = identities;
                    if (map.gantries.stream().filter(g -> g.holds == Gantry.Holds.BOAT).count() != 1)
                        throw new AssertionError("Mech repair changed shuttle capacity");
                    long wrecks = map.doodads.stream().filter(d -> room.contains(d.cellX, d.cellY)
                            && "workshop-damage.mill".equals(d.definitionId())).count();
                    if (count > 0 && wrecks != 4 - count) throw new AssertionError("Wrong visible berth wrecks");
                    for (var task : map.fixtureTasks) {
                        if (!map.grid.isWalkable(task.cellX(), task.cellY())) throw new AssertionError("Work left on closed floor");
                        if (task.berth() >= map.gantries.size()) throw new AssertionError("Stale service berth index");
                    }
                    System.out.println("LANCE seed=" + seed + " working=" + count + "/4 hull=106x79 requiredRooms=PASS access=PASS");
                    if (seed == 42) {
                        var reactor = new Reactor();
                        var model = new ShipConditionModel(reactor, () -> deck,
                                work -> new CompanyShipRestoration.Offer(state, 20000,
                                        CompanyShipRestoration.refusal(work, state, true, true, true, true, 20000)),
                                work -> "Snapshot only", () -> new CompanyBayRestoration.Offer(true, 20000, null),
                                () -> "Snapshot only", ignored -> {}, () -> {});
                        model.select(room.id());
                        frames.add(new SnapshotArtifact("berths-" + count + "-ship.png",
                                UiSnapshotSuite.renderShipCondition(context, deck, model, reactor, 1f, null)));
                        frames.add(new SnapshotArtifact("berths-" + count + "-detail.png",
                                UiSnapshotSuite.renderShipCondition(context, deck, model, reactor, 1f, RoomPurpose.VEHICLE_BAY)));
                    }
                } finally { deck.dismiss(); }
            }
        }
        reserves(context, hull, frames);
        return List.copyOf(frames);
    }

    /** Deck clock for the reserve close-ups: long enough for the crew to walk to the opened rooms. */
    private static final float RESERVE_CLOCK = 45f;

    /**
     * Every reserve opened against every reserve sealed, on the same layouts:
     * no room, door or berth moves, the opened rooms are in service and
     * publish work, and the frames show Engineering's list both ways and each
     * reserve close up, sealed and open, with every core restored so the
     * furniture reads and the crew at the deck clock above.
     */
    private static void reserves(SnapshotContext context, CompanyShip hull, List<SnapshotArtifact> frames) throws Exception {
        var restored = new ShipRestoration(true, true, true, true, true, 2);
        var open = restored.withOpenReserves(EnumSet.allOf(ReservedFacility.class));
        for (long seed : new long[]{42, 81, 417}) {
            List<DeckGraph.Compartment> sealedLayout = null;
            List<String> sealedBerths = null;
            for (var state : List.of(restored, open)) {
                var ship = hull.withRestoration(state);
                var mechs = Collections.nCopies(4, MechVariant.HOUND);
                var deck = CompanyDeck.home(ship, seed, null, () -> mechs, List::of, () -> new boolean[]{true});
                try {
                    while (!deck.ready()) Thread.onSpinWait();
                    deck.advance(1f / 30f);
                    var map = deck.map();
                    var graph = deck.rooms();
                    if (!graph.unplaced().isEmpty()) throw new AssertionError("Unplaced " + graph.unplaced());
                    var berths = map.gantries.stream().map(g -> g.holds + "@" + g.left() + ":" + g.bottom() + ":" + g.facing).toList();
                    if (sealedLayout == null) { sealedLayout = graph.compartments(); sealedBerths = berths; }
                    else if (!sealedLayout.equals(graph.compartments())) throw new AssertionError("Opening a reserve moved rooms or doors");
                    else if (!sealedBerths.equals(berths)) throw new AssertionError("Opening a reserve moved a berth");
                    CerberusStartSnapshotSuite.assertPreparationAccess(map, graph);
                    boolean opened = state == open;
                    StringBuilder line = new StringBuilder("RESERVES seed=" + seed + (opened ? " open" : " sealed"));
                    for (var room : graph.compartments()) {
                        var facility = graph.reserve(room.id());
                        if (facility == null) continue;
                        if (graph.damaged(room.id()) == opened) throw new AssertionError(facility + " service state wrong");
                        long work = map.fixtureTasks.stream().filter(t -> room.contains(t.cellX(), t.cellY())).count();
                        if (opened == (work == 0)) throw new AssertionError(facility + " work wrong: " + work);
                        if (deck.has(room.purpose()) != opened && graph.largest(room.purpose()) == room)
                            throw new AssertionError(facility + " access wrong");
                        line.append(' ').append(facility).append('=').append(work);
                    }
                    System.out.println(line);
                    if (seed != 42) continue;
                    var reactor = new Reactor();
                    var model = new ShipConditionModel(reactor, () -> deck,
                            work -> new CompanyShipRestoration.Offer(state, 20000,
                                    CompanyShipRestoration.refusal(work, state, true, true, true, true, 20000)),
                            work -> "Snapshot only", () -> new CompanyBayRestoration.Offer(true, 20000, null),
                            () -> "Snapshot only", ignored -> {}, () -> {});
                    String name = opened ? "open" : "sealed";
                    frames.add(new SnapshotArtifact("reserves-" + name + "-ship.png",
                            UiSnapshotSuite.renderShipCondition(context, deck, model, reactor, 1f, null)));
                    var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                            new HeadlessBattleSceneRenderer(context.modRoot(), true));
                    ShipDeckBattleScene scene = deck.scene();
                    scene.dress(CoreDressing.RESTORED);
                    scene.advanceTo(RESERVE_CLOCK);
                    for (var room : graph.compartments()) {
                        var facility = graph.reserve(room.id());
                        if (facility == null) continue;
                        float cell = Math.min(72f, Math.min(1920f / (room.width() + 12), 1080f / (room.depth() + 12)));
                        frames.add(closeUp(renderer, scene, ShipDeckBattleScene.DeckView.over(room, 6, cell),
                                "reserve-" + facility.name().toLowerCase().replace('_', '-') + "-" + name + ".png",
                                facility.name().replace('_', ' ') + " " + room.width() + "x" + room.depth()
                                        + (opened ? " / opened" : " / sealed")));
                    }
                    if (opened) frames.addAll(doorsInPassing(renderer, scene));
                } finally { deck.dismiss(); }
            }
        }
    }

    /**
     * A compartment door caught opening for someone, then standing open while
     * they pass: the deck's own crew walking its own doorways, never a posed door.
     */
    private static List<SnapshotArtifact> doorsInPassing(HeadlessUiRenderer renderer, ShipDeckBattleScene scene) {
        var found = new ArrayList<SnapshotArtifact>();
        float clock = scene.simulatedSeconds();
        String[] names = {"door-opening.png", "door-open.png"};
        float[][] bands = {{0.3f, 0.8f}, {1f, 1f}};
        for (int stage = 0; stage < 2; stage++) {
            RoomDoor caught = null;
            for (int step = 0; step < 2400 && caught == null; step++) {
                clock += 0.05f;
                scene.advanceTo(clock);
                for (RoomDoor door : scene.roomDoors()) {
                    float t = door.openness();
                    if (!door.locked() && t >= bands[stage][0] && t <= bands[stage][1]) { caught = door; break; }
                }
            }
            if (caught == null) throw new AssertionError("No crew opened a compartment door in the evidence window");
            found.add(closeUp(renderer, scene, new ShipDeckBattleScene.DeckView(caught.centerX(), caught.centerY(), 72f),
                    names[stage], String.format(Locale.ROOT, "COMPARTMENT DOOR / %.0f%% open", caught.openness() * 100f)));
        }
        return found;
    }

    private static SnapshotArtifact closeUp(HeadlessUiRenderer renderer, ShipDeckBattleScene scene,
                                            ShipDeckBattleScene.DeckView view, String path, String caption) {
        BufferedImage image = renderer.renderHostPass(scene.pass(view), 1920, 1080);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        int textWidth = g.getFontMetrics().stringWidth(caption);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(16, 16, textWidth + 24, 38);
        g.setColor(new Color(235, 228, 205));
        g.drawString(caption, 28, 43);
        g.dispose();
        return new SnapshotArtifact(path, image);
    }
}
