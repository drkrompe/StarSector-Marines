package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.ApertureHold;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.ReactiveManningService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Building envelopes beyond Conquest (`garrisonables.md`, building envelopes): a squad in the GARRISON stance mans a
 * held place's building on the production map of each procedural mission (Raid, Assault, Sabotage, Extraction, civilian
 * rescue) and of a story mission. Each loop generates the mission's map, keeps its terrain and its held places, and
 * plays one marine squad beside the held place with the most posts, with a tough, stationary enemy party in sight of it
 * outside the building. The mission's own forces are left out, so the question stays about the stance and the place.
 *
 * <p>Verdicts: the map offers its held places as envelopes, the side believes the party pressing the building, the
 * stance holds a building by the occupy click's own hold order within a few pulses, and its holders stand to at the
 * apertures. Which building it took and when are metrics.
 */
public final class MissionEnvelopeScene implements BehaviorScene {

    private static final int PULSE_TICKS = 75;
    private static final int MAN_BOUND_TICKS = 6 * PULSE_TICKS;
    private static final int TICKS = 1800;
    private static final int SQUAD_SIZE = 6;
    private static final int RAIDERS = 4;
    private static final float TOUGH = 1_000_000f;
    private static final int MIN_POSTS = 4;

    private record Mission(String loop, Supplier<BattleSimulation> build) {}

    private static List<Mission> missions() {
        List<ShuttleAssignment> lift = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        TargetProfile rock = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD);
        return List.of(
                new Mission("raid", () -> BattleSetup.createRaid(15311)),
                new Mission("assault", () -> BattleSetup.createPlaceholder(15312, lift)),
                new Mission("sabotage", () -> BattleSetup.createSabotage(15313, lift, false)),
                new Mission("extraction", () -> BattleSetup.createExtraction(15314)),
                new Mission("rescue", () -> BattleSetup.createCivilianRescue(15315, lift, false, RiskLevel.MEDIUM)),
                new Mission("yard-owners", () -> BattleSetup.createYardOwners(15303, lift, rock)));
    }

    @Override public String id() { return "mission-envelopes"; }

    @Override public String label() {
        return "Mission envelopes: the GARRISON stance mans a held place's building on each procedural mission's map "
                + "and a story map";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (Mission mission : missions()) reports.add(playOne(mission, frames));
        return reports;
    }

    private SceneReport playOne(Mission mission, FrameSink frames) throws Exception {
        try (BattleSimulation source = mission.build().get()) {
            NavigationGrid grid = source.getGrid();
            List<TacticalNode> nodes = source.getTacticalMap() == null ? List.of() : source.getTacticalMap().all();
            GarrisonableRegistry offered = source.garrisonables();
            int envelopes = 0;
            int posts = 0;
            TacticalNode chosen = null;
            int chosenPosts = 0;
            for (TacticalNode node : nodes) {
                int g = offered.envelopeOf(node);
                if (g < 0) continue;
                envelopes++;
                posts += offered.size(g);
                if (offered.size(g) > chosenPosts && offered.size(g) >= MIN_POSTS) {
                    chosen = node;
                    chosenPosts = offered.size(g);
                }
            }
            if (chosen == null) {
                return new SceneReport(id(), mission.loop(), 0, List.of(Verdict.of("envelopes-offered", false,
                        envelopes + " envelopes on " + nodes.size() + " held places, none with " + MIN_POSTS + " posts")),
                        Map.of("envelopes", envelopes));
            }
            int centre = offered.envelopeOf(chosen);
            int[] start = ground(grid, chosen, offered.centreX(centre), offered.centreY(centre), 3, 6, null);
            int[] party = start == null ? null : ground(grid, chosen, start[0], start[1], 10, 16, start);
            if (start == null || party == null) {
                return new SceneReport(id(), mission.loop(), 0, List.of(Verdict.of("ground-found", false,
                        "no open ground beside " + chosen.kind + " at " + chosen.anchorX + "," + chosen.anchorY)),
                        Map.of("envelopes", envelopes));
            }
            int[] farMarine = far(grid, party[0], party[1]);
            int[] farDefender = far(grid, start[0], start[1]);

            SceneBuilder builder = SceneBuilder.over(grid, source.getTopology()).missionCompletion(false);
            builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, farMarine[0], farMarine[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, farDefender[0], farDefender[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            builder.squad("marines").faction(Faction.MARINE).size(SQUAD_SIZE).at(start[0] + 2, start[1])
                    .primary("weapon.smg").health(TOUGH).done();
            builder.squad("party").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(RAIDERS)
                    .at(party[0] + 2, party[1]).primary("weapon.smg").health(TOUGH).stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.setTacticalMap(new TacticalMap(new ArrayList<>(nodes)));
            sim.random().setSeed(20261020L);
            int squadId = world.squadId("marines");
            sim.getReactiveManning().setEnabled(squadId, true);

            Trace trace = new Trace(squadId, chosen);
            SceneRun.of(world).observe(trace)
                    .frames(frames, mission.loop(), 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                            mission.loop(), tick, trace.caption()))
                    .run(TICKS);

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("envelopes-offered", envelopes > 0 && trace.envelope >= 0,
                    envelopes + " envelopes (" + posts + " posts) on " + nodes.size() + " held places; the chosen "
                            + chosen.kind + " has " + chosenPosts + " posts"));
            verdicts.add(Verdict.of("pressure-believed", trace.hotTick >= 0,
                    "the marines believed the party pressing the building from tick " + trace.hotTick));
            verdicts.add(Verdict.of("building-held-within-a-few-pulses",
                    trace.hotTick >= 0 && trace.firstHeld >= 0 && trace.firstHeld - trace.hotTick <= MAN_BOUND_TICKS,
                    "pressed from tick " + trace.hotTick + ", a building first held at tick " + trace.firstHeld
                            + " (bound " + MAN_BOUND_TICKS + "); the chosen building: " + trace.heldChosen));
            verdicts.add(Verdict.of("held-by-the-click-order", trace.byClickOrder,
                    "the stance held it with the player's hold order: " + trace.byClickOrder));
            verdicts.add(Verdict.of("holders-stand-to", trace.firstStoodTo >= 0,
                    "a holder first stood to at the building's apertures at tick " + trace.firstStoodTo));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("heldPlaces", nodes.size());
            metrics.put("envelopes", envelopes);
            metrics.put("posts", posts);
            metrics.put("chosenPosts", chosenPosts);
            metrics.put("pressedTick", trace.hotTick);
            metrics.put("firstHeldTick", trace.firstHeld);
            metrics.put("firstStoodToTick", trace.firstStoodTo);
            return new SceneReport(id(), mission.loop(), TICKS, verdicts, metrics);
        }
    }

    /**
     * Open ground reached on foot from {@code (fromX, fromY)} whose gap to the building's box is within
     * {@code [minGap, maxGap]}, with room for a squad's cluster (four by two), and in sight of {@code sees} if given.
     */
    static int[] ground(NavigationGrid grid, TacticalNode building, int fromX, int fromY, int minGap, int maxGap,
                                int[] sees) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{fromX, fromY});
        seen[fromY * w + fromX] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int gap = Math.max(Math.max(building.left - c[0], c[0] - building.right),
                    Math.max(building.top - c[1], c[1] - building.bottom));
            if (gap >= minGap && gap <= maxGap && roomy(grid, c[0], c[1])
                    && (sees == null || grid.hasLineOfSight(c[0], c[1], sees[0], sees[1]))) return c;
            if (gap > maxGap + 4) continue;
            for (int[] s : steps) {
                int x = c[0] + s[0];
                int y = c[1] + s[1];
                if (x < 0 || y < 0 || x >= w || y >= h || seen[y * w + x] || !grid.isWalkable(x, y)) continue;
                seen[y * w + x] = true;
                queue.add(new int[]{x, y});
            }
        }
        return null;
    }

    private static boolean roomy(NavigationGrid grid, int x, int y) {
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 3; dx++) {
                if (!grid.inBounds(x + dx, y + dy) || !grid.isWalkable(x + dx, y + dy)) return false;
            }
        }
        return true;
    }

    /** A walkable cell far from {@code (x, y)}: a corner of the map's open ground, out of the scene's way. */
    static int[] far(NavigationGrid grid, int x, int y) {
        int[] best = null;
        long bestDistance = -1;
        for (int cy = 0; cy < grid.getHeight(); cy += 4) {
            for (int cx = 0; cx < grid.getWidth(); cx += 4) {
                if (!grid.isWalkable(cx, cy)) continue;
                long d = (long) (cx - x) * (cx - x) + (long) (cy - y) * (cy - y);
                if (d > bestDistance) {
                    bestDistance = d;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best;
    }

    private static final class Trace implements TickObserver {
        final int squadId;
        final TacticalNode chosen;
        int envelope = -1;
        int hotTick = -1;
        int firstHeld = -1;
        int firstStoodTo = -1;
        boolean heldChosen;
        boolean byClickOrder;
        private String state = "";

        Trace(int squadId, TacticalNode chosen) {
            this.squadId = squadId;
            this.chosen = chosen;
        }

        String caption() { return state; }

        @Override public void observe(BattleSimulation sim, int tick) {
            GarrisonableRegistry places = sim.garrisonables();
            if (envelope < 0) envelope = places.envelopeOf(chosen);
            if (envelope < 0) return;
            Squad squad = sim.getSquad(squadId);
            if (squad == null || squad.aliveMembers <= 0) return;
            ObjectiveAssignment order = squad.assignedObjective;
            boolean holds = order != null && order.kind() == AssignmentKind.HOLD_NODE && order.targetNode() != null
                    && places.envelopeOf(order.targetNode()) >= 0;
            // The stance mans the most pressed building in its watch, which need not be the one the party was set
            // down beside: pressure is read on that one and on whichever building it holds.
            CommanderInfluenceSnapshot picture = sim.peekCommanderInfluence(Faction.MARINE);
            if (picture != null && hotTick < 0) {
                if (pressed(picture, places, envelope)
                        || holds && pressed(picture, places, places.envelopeOf(order.targetNode()))) hotTick = tick;
            }
            state = holds ? "holding " + order.targetNode().kind : "watching";
            if (!holds) return;
            if (firstHeld < 0) firstHeld = tick;
            if (GarrisonableRegistry.sameBuilding(order.targetNode(), chosen)) heldChosen = true;
            ReactiveManningService.View view = sim.getReactiveManning().view(squadId);
            if (view.status() == ReactiveManningService.Status.HOLDING_BUILDING
                    && sim.getSquadMoveOrderService().activeOrder(squadId) != null) byClickOrder = true;
            if (firstStoodTo < 0 && standsTo(squad)) firstStoodTo = tick;
        }

        private static boolean pressed(CommanderInfluenceSnapshot picture, GarrisonableRegistry places, int place) {
            int bw = places.blockWidth();
            for (int block : places.coveredBlocks(place)) {
                if (picture.hostileAt(block % bw, block / bw) >= GarrisonDirector.START_PRESSURE) return true;
            }
            return false;
        }

        private static boolean standsTo(Squad squad) {
            SquadPlan plan = squad.currentPlan;
            if (plan == null || plan.isComplete()) return false;
            SquadPlan.Step step = plan.currentStep();
            return step != null && step.action instanceof ApertureHold;
        }
    }
}
