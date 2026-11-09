package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/**
 * Fleet facilities' standard deck (`fleet-facilities.md`) at the sizes the fleet can buy: every
 * room placed and reachable, exactly the bought gantries, berths and bunkrooms in service, nothing
 * dressed as wrecked, and the Mech Lab, boat bay and barracks as their screens crop them.
 */
public final class FleetFacilitiesSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "fleet-facilities"; }
    @Override public String label() { return "Fleet facilities' mock rooms"; }

    private static final List<FleetRooms> SIZES = List.of(
            new FleetRooms(0, 1, 4), new FleetRooms(0, 2, 4), new FleetRooms(1, 1, 4), new FleetRooms(2, 2, 6),
            new FleetRooms(4, 2, 6), new FleetRooms(4, 3, 8));

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        CompanyManagementGuide.install(new JSONObject(Files.readString(context.modRoot().resolve(CompanyManagementGuide.PATH))));
        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (FleetRooms size : SIZES) {
            for (long seed : new long[]{42, 81, 417}) {
                var ship = CompanyShip.fleet(size);
                var mechs = Collections.nCopies(size.gantries(), MechVariant.HOUND);
                var deck = CompanyDeck.home(ship, seed, null, () -> mechs, List::of, () -> new boolean[]{true, true, true});
                try {
                    while (!deck.ready()) Thread.onSpinWait();
                    deck.advance(1f / 30f);
                    var map = deck.map();
                    var graph = deck.rooms();
                    if (!graph.unplaced().isEmpty()) throw new AssertionError(size + " unplaced " + graph.unplaced());
                    CerberusStartSnapshotSuite.assertPreparationAccess(map, graph);
                    long machines = map.gantries.stream().filter(g -> g.holds == Gantry.Holds.MACHINE).count();
                    long boats = map.gantries.stream().filter(g -> g.holds == Gantry.Holds.BOAT).count();
                    long bunkrooms = graph.compartments().stream().filter(r -> r.purpose() == RoomPurpose.BARRACKS).count();
                    if (machines != size.gantries()) throw new AssertionError(size + " working gantries " + machines);
                    if (boats != size.boatBerths()) throw new AssertionError(size + " boat berths " + boats);
                    if (ShipsBoats.aboard(ship).size() != size.boatBerths()) throw new AssertionError(size + " lift disagrees with the deck");
                    if (bunkrooms != size.bunkrooms()) throw new AssertionError(size + " bunkrooms " + bunkrooms);
                    if (deck.has(RoomPurpose.VEHICLE_BAY) != size.gantries() > 0) throw new AssertionError(size + " Mech Lab access");
                    if (map.doodads.stream().anyMatch(d -> d.definitionId() != null && d.definitionId().startsWith("workshop-damage")))
                        throw new AssertionError(size + " dressed as wrecked");
                    for (var task : map.fixtureTasks)
                        if (!map.grid.isWalkable(task.cellX(), task.cellY())) throw new AssertionError(size + " work on closed floor");
                    System.out.println("FLEET seed=" + seed + " gantries=" + machines + " berths=" + boats
                            + " bunkrooms=" + bunkrooms + " deck=" + ship.deckPlan().frames() + "x" + ship.deckPlan().height() + " PASS");
                    if (seed != 42) continue;
                    ShipDeckBattleScene scene = deck.scene();
                    scene.advanceTo(30f);
                    String tag = size.gantries() + "g-" + size.boatBerths() + "b-" + size.bunkrooms() + "r";
                    if (size.gantries() > 0) frames.add(closeUp(renderer, scene, graph.largest(RoomPurpose.VEHICLE_BAY), tag + "-mech-lab.png",
                            "Mech Lab / " + size.gantries() + " of 4 gantries working"));
                    int bay = 0;
                    for (var room : graph.compartments()) if (room.purpose() == RoomPurpose.HANGAR)
                        frames.add(closeUp(renderer, scene, room, tag + "-boat-bay-" + (++bay) + ".png",
                                "Boat bay " + bay + " of " + size.boatBerths()));
                    frames.add(closeUp(renderer, scene, graph.largest(RoomPurpose.BARRACKS), tag + "-barracks.png",
                            "Barracks / one of " + size.bunkrooms() + " bunkrooms"));
                } finally { deck.dismiss(); }
            }
        }
        return List.copyOf(frames);
    }

    private static SnapshotArtifact closeUp(HeadlessUiRenderer renderer, ShipDeckBattleScene scene,
                                            DeckGraph.Compartment room, String path, String caption) {
        float cell = Math.min(72f, Math.min(1920f / (room.width() + 12), 1080f / (room.depth() + 12)));
        BufferedImage image = renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(room, 6, cell)), 1920, 1080);
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
