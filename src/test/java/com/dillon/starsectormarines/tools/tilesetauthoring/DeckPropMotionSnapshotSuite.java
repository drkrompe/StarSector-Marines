package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.FixtureUse;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.gen.ship.VanillaHullSilhouettes;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The deck's moving props on the generated, manned Valkyrie: one 1920x1080 review GIF per room that has any, framed
 * on the room and played on the deck's own clock. The first half runs with the ship's power up, the second with it
 * at a deficit, so a power-gated prop is seen running down; a worked machine moves only while somebody is at it, and
 * the console line says how often that was.
 */
public final class DeckPropMotionSnapshotSuite implements SnapshotSuite {
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final float WARM_UP_SECONDS = 40f;
    private static final float FRAME_SECONDS = 0.125f;
    private static final int FRAMES = 112;
    /** How long a room may wait for somebody to start work on one of its worked machines. */
    private static final float MAX_WAIT_SECONDS = 900f;
    private static final Set<RoomPurpose> ROOMS = Set.of(RoomPurpose.GYMNASIUM, RoomPurpose.LOADING_BAY,
            RoomPurpose.ENGINE_ROOM, RoomPurpose.VEHICLE_BAY);

    @Override public String id() { return "deck-props"; }
    @Override public String label() { return "Moving deck props, gated by use and by power"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        TileRegistry registry = TileRegistry.installed();
        var hull = new VanillaHullSilhouettes(context.starsectorCore()).read("valkyrie");
        if (hull == null) throw new IllegalStateException("Valkyrie source hull required");
        var generator = new ShipDeckGenerator();
        var deck = generator.generateDeck(DeckSizing.planFor(hull.hullClass(), hull.role(),
                hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.silhouette().aspect()), 42, hull.silhouette());
        DeckGraph rooms = generator.getLastDeckGraph();
        List<SnapshotArtifact> images = new ArrayList<>();
        try (var scene = new ShipDeckBattleScene(deck, rooms, 42, null)) {
            boolean first = true;
            for (var room : rooms.compartments()) if (room.purpose() == RoomPurpose.BARRACKS) {
                scene.muster(room, first ? List.of(new MarineSoldier("prop-marine-1", "Morgan Vale",
                        SoldierAptitude.STEADY)) : List.of());
                first = false;
            }
            // Every other berth holds a machine, so a crane over a worked bay runs beside one over an empty bay.
            List<MechVariant> lance = new ArrayList<>();
            for (var room : rooms.compartments()) if (room.purpose() == RoomPurpose.VEHICLE_BAY)
                for (int i = 0; i < scene.berthsIn(room).size(); i++) lance.add(i % 2 == 0 ? MechVariant.BULWARK : null);
            scene.occupyGantries(lance);
            // The ship's one storekeeper favours main stores, so the provisions bay gets a hand of its own here.
            for (var room : rooms.compartments()) if (room.purpose() == RoomPurpose.LOADING_BAY)
                scene.staff(room, CrewRole.STOREKEEPER, 1);
            scene.manDeck(Integer.MAX_VALUE, hull.minCrew());
            var sim = scene.simulation();

            // One room of each kind that carries a moving prop: the one with the most of them.
            Map<RoomPurpose, DeckGraph.Compartment> chosen = new LinkedHashMap<>();
            Map<DeckGraph.Compartment, List<Doodad>> props = new LinkedHashMap<>();
            for (var room : rooms.compartments()) {
                if (!ROOMS.contains(room.purpose())) continue;
                List<Doodad> moving = new ArrayList<>();
                for (Doodad d : sim.getDoodads())
                    if (registry.motion(d.definitionId()) != null && room.contains(d.cellX, d.cellY)) moving.add(d);
                if (moving.isEmpty()) continue;
                props.put(room, moving);
                var best = chosen.get(room.purpose());
                if (best == null || props.get(best).size() < moving.size()) chosen.put(room.purpose(), room);
            }
            for (RoomPurpose purpose : ROOMS)
                if (!chosen.containsKey(purpose)) System.out.println("deck-props: no " + purpose + " carries a moving prop");

            var drain = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            EnumSet<RenderLayer> layers = EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.UNITS,
                    RenderLayer.ROOFS, RenderLayer.VEHICLES);
            Map<DeckGraph.Compartment, List<BufferedImage>> frames = new LinkedHashMap<>();
            Map<Doodad, Integer> worked = new LinkedHashMap<>();
            float t = WARM_UP_SECONDS;
            scene.advanceTo(t);
            for (var room : chosen.values()) {
                // Each room is recorded from the moment somebody is at work on one of its worked machines, if any.
                List<Doodad> gated = props.get(room).stream()
                        .filter(d -> registry.motion(d.definitionId()).runs.needsUse()).toList();
                // Every kind of worked machine in the room is seen at work, and recording starts as the last kind starts:
                // a gym is recorded with its rower going, not only its treadmill.
                Set<String> unseen = new HashSet<>();
                for (Doodad d : gated) unseen.add(d.definitionId());
                float waited = 0f;
                while (!unseen.isEmpty() && waited < MAX_WAIT_SECONDS) {
                    for (Doodad d : gated) if (FixtureUse.inUse(sim, d)) unseen.remove(d.definitionId());
                    if (unseen.isEmpty()) break;
                    t += 1f;
                    waited += 1f;
                    scene.advanceTo(t);
                }
                scene.dress(CoreDressing.RESTORED);
                List<BufferedImage> roomFrames = new ArrayList<>();
                var view = ShipDeckBattleScene.DeckView.fitting(room.left() - 2, room.top() - 2,
                        room.width() + 4, room.depth() + 4, WIDTH, HEIGHT);
                for (int frame = 0; frame < FRAMES; frame++) {
                    if (frame == FRAMES / 2) scene.dress(CoreDressing.RESTORED.with(ShipCore.POWER, false));
                    t += FRAME_SECONDS;
                    scene.advanceTo(t);
                    roomFrames.add(drain.renderHostPass(scene.pass(view, layers), WIDTH, HEIGHT));
                    for (Doodad d : props.get(room))
                        if (FixtureUse.inUse(sim, d)) worked.merge(d, 1, Integer::sum);
                }
                frames.put(room, roomFrames);
                System.out.println("deck-props: " + room.purpose() + " recorded from t=" + (int) (t - FRAMES * FRAME_SECONDS)
                        + "s after waiting " + (int) waited + "s for work");
            }
            // Staged: a lone exerciser always takes the nearest free station, so the rower is only ever reached when
            // the others are busy. Hold every other exercise stand with a placeholder claim and record the rower.
            for (var room : chosen.values()) {
                Doodad rower = props.get(room).stream().filter(d -> d.definitionId().equals("ship-gym.rower"))
                        .findFirst().orElse(null);
                if (rower == null) continue;
                var points = sim.taskPoints();
                var own = FixtureUse.pointsOn(points, rower);
                long placeholder = -1_000_000L;
                List<Long> held = new ArrayList<>();
                for (var point : List.copyOf(points.points())) {
                    if (own.contains(point) || own.stream().noneMatch(o -> o.group().equals(point.group()))
                            || points.claimant(point.id()) != 0L) continue;
                    points.claimAt(--placeholder, point.group(), point.worldX(), point.worldY());
                    held.add(placeholder);
                }
                float waited = 0f;
                while (!FixtureUse.inUse(sim, rower) && waited < MAX_WAIT_SECONDS) {
                    t += 1f;
                    waited += 1f;
                    scene.advanceTo(t);
                }
                scene.dress(CoreDressing.RESTORED);
                var view = ShipDeckBattleScene.DeckView.fitting(room.left() - 2, room.top() - 2,
                        room.width() + 4, room.depth() + 4, WIDTH, HEIGHT);
                List<BufferedImage> staged = new ArrayList<>();
                int rowed = 0;
                for (int frame = 0; frame < FRAMES / 2; frame++) {
                    t += FRAME_SECONDS;
                    scene.advanceTo(t);
                    staged.add(drain.renderHostPass(scene.pass(view, layers), WIDTH, HEIGHT));
                    if (FixtureUse.inUse(sim, rower)) rowed++;
                }
                for (long actor : held) points.release(actor);
                System.out.println("deck-props: staged rower, other stands held: worked in " + rowed + " of "
                        + FRAMES / 2 + " frames after waiting " + (int) waited + "s");
                images.add(SnapshotArtifact.colorAnimation("deck-props-" + room.purpose().name().toLowerCase()
                        + "-" + room.id() + "-rower-staged.gif", staged, Math.round(FRAME_SECONDS * 1000)));
            }
            for (var room : chosen.values()) {
                for (Doodad d : props.get(room)) {
                    PropMotion motion = registry.motion(d.definitionId());
                    System.out.println("deck-props: " + room.purpose() + " " + d.definitionId() + " at "
                            + d.cellX + "," + d.cellY + " runs " + motion.runs + "; "
                            + FixtureUse.pointsOn(sim.taskPoints(), d).stream().map(p -> p.group()).toList()
                            + " worked in "
                            + worked.getOrDefault(d, 0) + " of " + FRAMES + " frames");
                }
                images.add(SnapshotArtifact.colorAnimation("deck-props-" + room.purpose().name().toLowerCase()
                        + "-" + room.id() + ".gif", frames.get(room), Math.round(FRAME_SECONDS * 1000)));
            }
        }
        return images;
    }
}
