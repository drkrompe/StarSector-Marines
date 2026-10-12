package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.ShipInterior;
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
                String name = (conversion ? "converted" : "unconverted") + "-" + seed;
                frames.add(new SnapshotArtifact(name + ".png", ShipDeckSnapshotSuite.renderPlan(map,
                        generator.getLastDeckProfile(), graph, "Cerberus " + name, ship.deckPlan(),
                        ship.minCrew() + "/" + ship.maxCrew() + " crew, " + ship.cargo() + " cargo", RoomFit.STANDARD)));
            }
        }
        return List.copyOf(frames);
    }
}
