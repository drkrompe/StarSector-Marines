package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.BoatWorkshop;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.FabricationCost;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.nio.file.Files;
import org.json.JSONObject;
import java.util.List;

/** Real hull geometry and complete room program, including varied campaign seeds. */
public final class CerberusStartSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "cerberus-start"; }
    @Override public String label() { return "Cerberus company conversion"; }
    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        // Vanilla's Cerberus hull id lives in warhound.ship. Read its collision
        // polygon, exactly as the campaign resolver does through getShipFilePath.
        var spec = new JSONObject(Files.readString(context.starsectorCore().resolve("data/hulls/warhound.ship")));
        if (!"cerberus".equals(spec.getString("hullId"))) throw new AssertionError("Wrong hull");
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (boolean conversion : List.of(false, true)) {
            CompanyShip ship = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20,
                    50 + (conversion ? 18 : 0), 100 - (conversion ? 25 : 0),
                    outline.aspect(), outline, spec.getString("spriteName"), conversion);
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
                    if (map.gantries.stream().filter(g -> g.holds == Gantry.Holds.MACHINE).count() != 1)
                        throw new AssertionError("Conversion must have exactly one machine gantry");
                    if (map.gantries.stream().filter(g -> g.holds == Gantry.Holds.BOAT).count() != 1)
                        throw new AssertionError("Conversion must keep exactly one boat berth");
                    if (!graph.unplaced().isEmpty()) throw new AssertionError("Unplaced " + graph.unplaced());
                }
                if (conversion && seed == 42) frames.addAll(commissionedHermes(context, ship));
                String name = (conversion ? "converted" : "unconverted") + "-" + seed;
                frames.add(new SnapshotArtifact(name + ".png", ShipDeckSnapshotSuite.renderPlan(map,
                        generator.getLastDeckProfile(), graph, "Cerberus " + name, ship.deckPlan(),
                        ship.minCrew() + "/" + ship.maxCrew() + " crew, " + ship.cargo() + " cargo", RoomFit.STANDARD)));
            }
        }
        return List.copyOf(frames);
    }
    /** The starting hull, empty and then with the same berth commissioned. */
    private List<SnapshotArtifact> commissionedHermes(SnapshotContext context, CompanyShip hull) throws Exception {
        MarineRoster roster = new MarineRoster();
        MarineCaptain mira = new MarineCaptain("Mira Hale", null, Rank.LIEUTENANT, 0f);
        roster.add(mira);
        roster.beginCompanyPreparation(mira);
        BoatDeck boats = roster.boatDeck();
        boats.reconcile("cerberus", ShuttleType.HERMES, 1);
        Map<String, Integer> stock = new HashMap<>(Map.of("metals", 30, "heavy_machinery", 8, "supplies", 20));
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
        for (boolean commissioned : List.of(false, true)) {
            if (commissioned && !workshop.fabricate(0).succeeded()) throw new AssertionError("Hermes not commissioned");
            CompanyDeck deck = new CompanyDeck(hull, 42, null, List::of, List::of,
                    () -> new boolean[]{boats.boats().get(0) != null});
            try {
                while (!deck.ready()) Thread.onSpinWait();
                deck.advance(1f / 30f);
                var scene = deck.scene();
                var berths = scene.simulation().getAirfieldService().berths();
                if (berths.size() != (commissioned ? 1 : 0)) throw new AssertionError("Wrong rendered occupancy: commissioned=" + commissioned + ", berths=" + berths.size());
                if (commissioned && berths.get(0).airframe != ShuttleType.HERMES)
                    throw new AssertionError("Wrong commissioned airframe");
                var bay = deck.rooms(RoomPurpose.HANGAR).get(0);
                frames.add(new SnapshotArtifact(commissioned ? "hermes-commissioned.png" : "hermes-empty.png",
                        renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(bay, 2, 48),
                                EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.UNITS, RenderLayer.SHUTTLES)),
                                (bay.width() + 4) * 48, (bay.depth() + 4) * 48)));
            } finally { deck.dismiss(); }
        }
        return frames;
    }

}
