package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.WorkMotion;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
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
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Actual Valkyrie generation and crew, advanced physically for two minutes. */
public final class ShipDutySnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "ship-duty"; }
    @Override public String label() { return "Occupied ship duty stations and layered crew"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        // The larger recipe must still be packed and fitted at both ends of the hull range.
        for (String hullId : List.of("wolf", "conquest")) {
            var checkHull = new VanillaHullSilhouettes(context.starsectorCore()).read(hullId);
            if (checkHull == null) throw new IllegalStateException(hullId + " source hull required");
            var checkGenerator = new ShipDeckGenerator();
            var checkDeck = checkGenerator.generateDeck(DeckSizing.planFor(checkHull.hullClass(), checkHull.role(),
                    checkHull.minCrew(), checkHull.maxCrew(), checkHull.cargo(), checkHull.silhouette().aspect()),
                    42, checkHull.silhouette());
            var command = checkGenerator.getLastDeckGraph().compartments().stream()
                    .filter(room -> room.purpose() == RoomPurpose.BRIDGE).findFirst().orElseThrow();
            long consoles = checkDeck.fixtureTasks.stream().filter(task -> task.affordance() == Affordance.WATCH
                    && command.contains(task.cellX(), task.cellY())).count();
            long guards = checkDeck.fixtureTasks.stream().filter(task -> task.affordance() == Affordance.GUARD
                    && command.contains(task.cellX(), task.cellY())).count();
            if (consoles != 9 || guards != 1)
                throw new IllegalStateException(hullId + " CIC lost its watch or entrance: " + consoles + "/" + guards);
            System.out.println("ship-duty: " + hullId + " packs CIC with nine consoles and one guard post");
        }
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
            // Machines in the bay, so its technicians have something to weld.
            var bay = scene.room(RoomPurpose.VEHICLE_BAY);
            if (bay != null) scene.occupyGantries(Collections.nCopies(scene.berthsIn(bay).size(), MechVariant.BULWARK));
            long[] hired = scene.manDeck(Integer.MAX_VALUE, hull.minCrew());
            if (scene.manDeck(Integer.MAX_VALUE, hull.minCrew()).length != 0)
                throw new IllegalStateException("Repeated manning duplicated population");
            if (scene.dutyCrew().values().stream().filter(post -> post.role() == CrewRole.SECURITY).count() != 1)
                throw new IllegalStateException("Command entrance has no occupied guard post");
            if (scene.dutyCrew().size() > hull.minCrew())
                throw new IllegalStateException("Guard exceeded duty complement");
            long commandWatch = scene.dutyCrew().values().stream()
                    .filter(post -> post.role() == CrewRole.BRIDGE_WATCH && post.room().purpose() == RoomPurpose.BRIDGE).count();
            long engineWatch = scene.dutyCrew().values().stream()
                    .filter(post -> post.role() == CrewRole.ENGINE_WATCH).count();
            if (commandWatch != 5 || engineWatch != 2)
                throw new IllegalStateException("CIC side consoles or engineering understaffed: " + commandWatch + "/" + engineWatch);
            var sim = scene.simulation();
            for (String trade : List.of("cook-", "armourer-", "machinist-", "storekeeper-")) {
                boolean staffed = false;
                for (long actor : hired)
                    if (!scene.dutyCrew().containsKey(actor) && sim.identity().name(actor).startsWith(trade)) staffed = true;
                if (!staffed) throw new IllegalStateException("CIC allocation removed roaming trade " + trade);
            }
            if (named.length != 2 || named[0] == 0 || named[1] == 0
                    || !sim.identity().name(named[0]).equals("Morgan Vale"))
                throw new IllegalStateException("Named roster lost");
            System.out.println("ship-duty: " + hired.length + " ship crew; " + scene.dutyCrew().size()
                    + " on duty; " + commandWatch + " command operators; " + engineWatch
                    + " engineering; 1 guard; 2 named marines; minCrew=" + hull.minCrew());
            var drain = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            Map<CrewRole, RoomPurpose> workplaces = Map.of(CrewRole.ENGINE_WATCH, RoomPurpose.ENGINE_ROOM,
                    CrewRole.MEDIC, RoomPurpose.PATIENT_WARD, CrewRole.COOK, RoomPurpose.MESS_HALL,
                    CrewRole.ARMOURER, RoomPurpose.ARMORY, CrewRole.MACHINIST, RoomPurpose.PARTS_CAGE,
                    CrewRole.STOREKEEPER, RoomPurpose.STOCKROOM);
            Map<Long, CrewRole> roles = new HashMap<>();
            Map<CrewRole, Set<String>> visited = new EnumMap<>(CrewRole.class);
            for (long actor : hired) {
                var duty = scene.dutyCrew().get(actor);
                if (duty != null) roles.put(actor, duty.role());
                else for (CrewRole role : CrewRole.values())
                    if (sim.identity().name(actor).startsWith(role.name().toLowerCase(Locale.ROOT) + "-")) roles.put(actor, role);
            }
            for (int tick = 0; tick <= 3600; tick++) {
                if (tick > 0) sim.advance(BattleSimulation.TICK_DT);
                // Station guarantees hold past several dwell renewals, not just at spawn.
                if (tick % 30 == 0) for (var entry : scene.dutyCrew().entrySet()) {
                    var post = entry.getValue();
                    if (post.role() != CrewRole.BRIDGE_WATCH && post.role() != CrewRole.ENGINE_WATCH
                            && post.role() != CrewRole.SECURITY) continue;
                    long actor = entry.getKey();
                    if (post.role() == CrewRole.SECURITY || post.role() == CrewRole.BRIDGE_WATCH) {
                        var stand = post.route().stops().get(0);
                        if (sim.world().cellX(actor) != (int) stand.worldX()
                                || sim.world().cellY(actor) != (int) stand.worldY())
                            throw new IllegalStateException("Command watch left post at tick " + tick + ": " + post.route().id());
                    }
                    if (!post.room().contains(sim.world().cellX(actor), sim.world().cellY(actor))
                            || (post.route().stops().size() == 1 && sim.ambientTasks().jobInHand(actor) == null))
                        throw new IllegalStateException("Unattended essential station at tick " + tick + ": " + post.route().id());
                }
                if (tick % 30 == 0) for (var hand : roles.entrySet()) {
                    RoomPurpose workplace = workplaces.get(hand.getValue());
                    long actor = hand.getKey();
                    var point = sim.taskPoints().claimedPoint(actor);
                    if (workplace == null || point == null || sim.ambientTasks().jobInHand(actor) == null) continue;
                    if (rooms.compartments().stream().anyMatch(room -> room.purpose() == workplace
                            && room.contains(point.cellX(), point.cellY())))
                        visited.computeIfAbsent(hand.getValue(), key -> new HashSet<>()).add(point.id());
                }
                if (tick != 0 && tick != 1800 && tick != 3600) continue;
                for (var room : rooms.compartments()) {
                    boolean core = room.purpose() == RoomPurpose.BRIDGE || room.purpose() == RoomPurpose.ENGINE_ROOM;
                    boolean staffed = scene.dutyCrew().values().stream().anyMatch(post -> post.room().id() == room.id());
                    boolean workplace = workplaces.containsValue(room.purpose()) && rooms.compartments().stream()
                            .noneMatch(other -> other.purpose() == room.purpose()
                                    && other.width() * other.depth() > room.width() * room.depth());
                    boolean motions = room.purpose() == RoomPurpose.GYMNASIUM || room.purpose() == RoomPurpose.VEHICLE_BAY;
                    if (!core && !workplace && !motions && (tick != 3600 || !staffed)) continue;
                    for (int scale : new int[]{24, 64}) images.add(new SnapshotArtifact(
                            "ship-duty-" + room.purpose().name().toLowerCase() + "-" + room.id() + "-" + tick / 30 + "s-" + scale + "px.png",
                            drain.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(room, 2, scale),
                                    EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.UNITS)),
                                    (room.width() + 4) * scale, (room.depth() + 4) * scale)));
                }
            }
            for (CrewRole role : workplaces.keySet()) {
                int stations = visited.getOrDefault(role, Set.of()).size();
                System.out.println("ship-duty: " + role + " worked " + stations + " distinct home-room stations");
                if (stations < 2) throw new IllegalStateException(role + " did not work a room round: " + stations);
            }
        }
        images.addAll(motionBoard(context));
        return images;
    }

    /** The motions the board shows, in reading order. */
    private static final List<WorkMotion> BOARD = List.of(WorkMotion.WELD, WorkMotion.CARRY, WorkMotion.TREAT,
            WorkMotion.STIR, WorkMotion.CHOP, WorkMotion.WRENCH, WorkMotion.RUN, WorkMotion.ROW,
            WorkMotion.PRESS, WorkMotion.LIFT, WorkMotion.CONSOLE);
    private static final int CLOSE_PX = 128, CLOSE_TILE = 320, GAME_PX = 24, GAME_TILE = 168, BOARD_FRAMES = 45;
    /** How long the ship is watched for every motion to turn up: six minutes of its day. */
    private static final int BOARD_SEARCH_TICKS = 6 * 60 * 30;

    /**
     * One worker per work motion, followed for a few seconds: a close-up of each
     * and the same worker at game zoom beneath, on one 1920x1080 frame. Which
     * motion an actor shows is read off its layered flags, the renderer's own
     * input, so the board shows what the compositor was asked to draw.
     */
    private static List<SnapshotArtifact> motionBoard(SnapshotContext context) throws Exception {
        // A capital's gym is the full ship gym, with the rower and the bench press.
        var hull = new VanillaHullSilhouettes(context.starsectorCore()).read("conquest");
        var generator = new ShipDeckGenerator();
        var deck = generator.generateDeck(DeckSizing.planFor(hull.hullClass(), hull.role(),
                hull.minCrew(), hull.maxCrew(), hull.cargo(), hull.silhouette().aspect()), 42, hull.silhouette());
        var drain = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        try (var scene = new ShipDeckBattleScene(deck, generator.getLastDeckGraph(), 42, null)) {
            var bay = scene.room(RoomPurpose.VEHICLE_BAY);
            if (bay != null) scene.occupyGantries(Collections.nCopies(scene.berthsIn(bay).size(), MechVariant.BULWARK));
            scene.manDeck(Integer.MAX_VALUE, hull.minCrew());
            return motionBoard(scene, drain);
        }
    }

    private static List<SnapshotArtifact> motionBoard(ShipDeckBattleScene scene, HeadlessUiRenderer drain) {
        var sim = scene.simulation();
        // Each motion is filmed from the moment somebody is first seen doing it,
        // following that one worker; a tile is a clip of its own, so a board
        // does not need every job on the ship to be worked at the same instant.
        Map<WorkMotion, Long> actors = new EnumMap<>(WorkMotion.class);
        Map<WorkMotion, List<BufferedImage[]>> clips = new EnumMap<>(WorkMotion.class);
        for (int tick = 0; tick < BOARD_SEARCH_TICKS; tick++) {
            if (tick % 2 == 0) {
                // Keep following the same worker while they keep at it, and
                // change to somebody else only once they have stopped.
                Map<WorkMotion, Long> now = showing(scene);
                for (var seen : now.entrySet()) {
                    Long current = actors.get(seen.getKey());
                    if (current == null || !now.containsValue(current)) actors.put(seen.getKey(), seen.getValue());
                }
                for (var entry : actors.entrySet()) {
                    List<BufferedImage[]> clip = clips.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());
                    if (clip.size() >= BOARD_FRAMES) continue;
                    // Framed on the body as drawn: a gym user is drawn up the machine from the cell they stand on.
                    long actor = entry.getValue();
                    var components = sim.getBattleComponents();
                    float mount = entry.getKey().mountCells();
                    double facing = Math.toRadians(sim.getEntityWorld().getFloat(actor, components.LAYERED_ANIMATION,
                            BattleComponents.LAYERED_FACING_DEGREES));
                    float x = sim.world().x(actor) - (float) Math.sin(facing) * mount;
                    float y = sim.world().y(actor) + (float) Math.cos(facing) * mount;
                    clip.add(new BufferedImage[]{
                            drain.renderHostPass(scene.pass(new ShipDeckBattleScene.DeckView(x, y, CLOSE_PX),
                                    BOARD_LAYERS), CLOSE_TILE, CLOSE_TILE),
                            drain.renderHostPass(scene.pass(new ShipDeckBattleScene.DeckView(x, y, GAME_PX),
                                    BOARD_LAYERS), GAME_TILE, GAME_TILE)});
                }
                if (clips.size() == BOARD.size()
                        && clips.values().stream().allMatch(clip -> clip.size() >= BOARD_FRAMES)) break;
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        System.out.println("ship-duty: motion board shows " + actors.keySet() + "; never seen "
                + BOARD.stream().filter(m -> !actors.containsKey(m)).toList());
        List<BufferedImage> frames = new ArrayList<>();
        for (int frame = 0; frame < BOARD_FRAMES; frame++) {
            var board = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
            var g = board.createGraphics();
            g.setColor(new Color(0x0b, 0x10, 0x16));
            g.fillRect(0, 0, 1920, 1080);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.setColor(new Color(0xd8, 0xdc, 0xd0));
            for (int i = 0; i < BOARD.size(); i++) {
                WorkMotion motion = BOARD.get(i);
                int closeX = (i % 6) * CLOSE_TILE, closeY = (i / 6) * (CLOSE_TILE + 30);
                int gameX = 12 + i * (GAME_TILE + 6), gameY = 2 * (CLOSE_TILE + 30) + 60;
                List<BufferedImage[]> clip = clips.getOrDefault(motion, List.of());
                String label = clip.isEmpty() ? motion.name() + " (not seen)" : motion.name();
                g.drawString(label, closeX + 10, closeY + CLOSE_TILE + 22);
                g.drawString(motion.name(), gameX, gameY - 8);
                if (clip.isEmpty()) continue;
                BufferedImage[] shot = clip.get(Math.min(frame, clip.size() - 1));
                g.drawImage(shot[0], closeX, closeY, null);
                g.drawImage(shot[1], gameX, gameY, null);
            }
            g.drawString("Top: close-up, " + CLOSE_PX + " px/cell.  Bottom: the same worker at game zoom, "
                    + GAME_PX + " px/cell.  Each tile follows one worker from when the motion was first seen.",
                    12, 1060);
            g.dispose();
            frames.add(board);
        }
        return List.of(new SnapshotArtifact("ship-duty-motion-board.png", frames.get(BOARD_FRAMES / 2)),
                SnapshotArtifact.colorAnimation("ship-duty-motion-board.gif", frames, 66));
    }

    private static final EnumSet<RenderLayer> BOARD_LAYERS =
            EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.VEHICLES, RenderLayer.UNITS);

    /** The first actor showing each board motion this tick. */
    private static Map<WorkMotion, Long> showing(ShipDeckBattleScene scene) {
        var sim = scene.simulation();
        BattleComponents components = sim.getBattleComponents();
        Map<WorkMotion, Long> found = new EnumMap<>(WorkMotion.class);
        for (long actor : sim.ambientTasks().assigned()) {
            if (!sim.getEntityWorld().has(actor, components.LAYERED_ANIMATION)) continue;
            int flags = sim.getEntityWorld().getInt(actor, components.LAYERED_ANIMATION, BattleComponents.LAYERED_FLAGS);
            WorkMotion motion = (flags & LayeredAppearance.FLAG_TREATING) != 0 ? WorkMotion.TREAT
                    : LayeredAppearance.workMotion(flags);
            if ((flags & LayeredAppearance.FLAG_AT_STATION) != 0 && motion == WorkMotion.NONE) {
                motion = WorkMotion.CONSOLE;
            }
            if (BOARD.contains(motion)) found.putIfAbsent(motion, actor);
        }
        return found;
    }
}
