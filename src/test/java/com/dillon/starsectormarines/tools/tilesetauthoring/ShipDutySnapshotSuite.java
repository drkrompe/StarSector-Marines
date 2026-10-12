package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.gen.ship.VanillaHullSilhouettes;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Actual Valkyrie generation and crew, advanced physically for two minutes. */
public final class ShipDutySnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ship-duty"; }
    @Override public String label() { return "Occupied ship duty stations and layered crew"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        var hull = new VanillaHullSilhouettes(context.starsectorCore()).read("valkyrie");
        if (hull == null) throw new IllegalStateException("Valkyrie source hull required");
        var generator = new ShipDeckGenerator();
        var deck = generator.generateDeck(DeckSizing.planFor(hull.hullClass(), hull.role(),
                hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.silhouette().aspect()), 42, hull.silhouette());
        var rooms = generator.getLastDeckGraph();
        var images = new ArrayList<SnapshotArtifact>();
        try (var scene = new ShipDeckBattleScene(deck, rooms, 42, null)) {
            // Named roster fixture, not anonymous marines conjured to fill the ship.
            var roll = List.of(new MarineSoldier("duty-marine-1", "Morgan Vale", SoldierAptitude.STEADY),
                    new MarineSoldier("duty-marine-2", "Alex Chen", SoldierAptitude.STEADY));
            boolean first = true;
            long[] named = new long[0];
            for (var room : rooms.compartments()) if (room.purpose() == RoomPurpose.BARRACKS) {
                long[] present = scene.muster(room, first ? roll : List.of());
                if (first) named = present;
                first = false;
            }
            long[] hired = scene.manDeck(Integer.MAX_VALUE, hull.minCrew());
            if (scene.manDeck(Integer.MAX_VALUE, hull.minCrew()).length != 0)
                throw new IllegalStateException("Repeated manning duplicated population");
            var sim = scene.simulation();
            if (named.length != 2 || named[0] == 0 || named[1] == 0
                    || !sim.identity().name(named[0]).equals("Morgan Vale"))
                throw new IllegalStateException("Named roster lost");
            System.out.println("ship-duty: " + hired.length + " ship crew; " + scene.dutyCrew().size()
                    + " on duty; 2 named marines; minCrew=" + hull.minCrew());
            var drain = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            for (int tick = 0; tick <= 3600; tick++) {
                if (tick > 0) sim.advance(BattleSimulation.TICK_DT);
                // Station guarantees hold past several dwell renewals, not just at spawn.
                if (tick % 30 == 0) for (var entry : scene.dutyCrew().entrySet()) {
                    var post = entry.getValue();
                    if (post.role() != CrewRole.BRIDGE_WATCH && post.role() != CrewRole.ENGINE_WATCH) continue;
                    long actor = entry.getKey();
                    if (!post.room().contains(sim.world().cellX(actor), sim.world().cellY(actor))
                            || sim.ambientTasks().jobInHand(actor) == null)
                        throw new IllegalStateException("Unattended essential station at tick " + tick + ": " + post.route().id());
                }
                if (tick != 0 && tick != 1800 && tick != 3600) continue;
                for (var room : rooms.compartments()) {
                    boolean core = room.purpose() == RoomPurpose.BRIDGE || room.purpose() == RoomPurpose.ENGINE_ROOM;
                    boolean staffed = scene.dutyCrew().values().stream().anyMatch(post -> post.room().id() == room.id());
                    if (!core && (tick != 3600 || !staffed)) continue;
                    for (int scale : new int[]{24, 64}) images.add(new SnapshotArtifact(
                            "ship-duty-" + room.purpose().name().toLowerCase() + "-" + room.id() + "-" + tick / 30 + "s-" + scale + "px.png",
                            drain.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(room, 2, scale),
                                    EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.UNITS)),
                                    (room.width() + 4) * scale, (room.depth() + 4) * scale)));
                }
            }
        }
        return images;
    }
}
