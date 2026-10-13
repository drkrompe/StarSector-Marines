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
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Opt-in physical fit and restoration evidence inside Second Wind's original envelope. */
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
            for (int count = 0; count <= 4; count++) {
                var state = new ShipRestoration(true, count > 0, false, false, false, Math.max(0, count - 1));
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
        return List.copyOf(frames);
    }
}
