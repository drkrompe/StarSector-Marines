package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The mission defenders' flow tier (`garrisonables.md`, mission defenders; {@link PlaceFlow}): on the Raid's production
 * map, the Raid's own defender commander holds six mobile squads far from a held place's building, and a sentry by
 * the building watches the approach to it. A party of marines,
 * too tough to be shot down so the question stays about command, is set down a long way short of the building, later
 * walks up to it, and later leaves.
 *
 * <p>Verdicts: the commander stages a reserve squad for the pressed area before it mans the building; a staged squad
 * stays free while it waits (an area defence, not a place); and every staged squad is given back once belief of the
 * party has cooled, within the flow's dwell and a few pulses. Whether a squad went forward, which squad manned, and
 * whether it was one the flow had staged are metrics: they are what the fight does, not what the machinery does.
 */
public final class MissionFlowScene implements BehaviorScene {

    private static final int PULSE_TICKS = 75;
    private static final int ENTER_TICK = 30;
    private static final int ADVANCE_TICK = 1500;
    private static final int LEAVE_TICK = 2700;
    private static final int TICKS = 6000;
    private static final int SQUADS = 6;
    private static final int SQUAD_SIZE = 6;
    private static final int RAIDERS = 4;
    private static final float TOUGH = 1_000_000f;
    private static final int MIN_POSTS = 4;
    /** Cells from the building within which a believed contact is the party. */
    private static final int BELIEF_CELLS = GarrisonFlow.MATCH_RADIUS_CELLS;
    private static final int RETURN_BOUND_TICKS = (PlaceFlow.COOL_PLANS + 4) * PULSE_TICKS;

    @Override public String id() { return "mission-flow"; }

    @Override public String label() {
        return "Mission flow: a Raid's defender commander stages its reserve toward a pressed area before it mans the "
                + "building there, keeps it free while it waits, and gives it back when the area is quiet";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        return List.of(playOne("raid", frames));
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        try (BattleSimulation source = BattleSetup.createRaid(15311)) {
            NavigationGrid grid = source.getGrid();
            List<TacticalNode> nodes = source.getTacticalMap() == null ? List.of() : source.getTacticalMap().all();
            GarrisonableRegistry offered = source.garrisonables();
            RaidObjective raid = null;
            for (Objective objective : source.getObjectives()) {
                if (objective instanceof RaidObjective r) raid = r;
            }
            TacticalNode chosen = choose(nodes, offered, raid);
            if (chosen == null || raid == null) {
                return new SceneReport(id(), loop, 0, List.of(Verdict.of("envelope-found", false,
                        "no held place with " + MIN_POSTS + " posts")), Map.of());
            }
            int centre = offered.envelopeOf(chosen);
            int[] near = MissionEnvelopeScene.ground(grid, chosen, offered.centreX(centre), offered.centreY(centre), 3, 6,
                    null);
            int[] approach = near == null ? null : MissionEnvelopeScene.ground(grid, chosen, near[0], near[1], 18, 26, null);
            int[] home = near == null ? null : MissionEnvelopeScene.ground(grid, chosen, near[0], near[1], 40, 55, null);
            int[] lookout = approach == null ? null
                    : MissionEnvelopeScene.ground(grid, chosen, near[0], near[1], 2, 8, approach);
            if (near == null || approach == null || home == null || lookout == null) {
                return new SceneReport(id(), loop, 0, List.of(Verdict.of("ground-found", false,
                        "no open ground round " + chosen.kind + " at " + chosen.anchorX + "," + chosen.anchorY)), Map.of());
            }
            // Out of everybody's way: the corner farthest from the pool, so nothing there is a contact of its own.
            int[] farMarine = MissionEnvelopeScene.far(grid, home[0], home[1]);

            SceneBuilder builder = SceneBuilder.over(grid, source.getTopology()).missionCompletion(false);
            builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, farMarine[0], farMarine[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            // A sentry by the building, not in the pool, is the defender's eyes on the approach.
            builder.unit("lookout", Faction.DEFENDER, UnitType.MARINE, lookout[0], lookout[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            for (int i = 0; i < SQUADS; i++) {
                builder.squad("defenders-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(SQUAD_SIZE)
                        .at(home[0] + 2 * (i % 3), home[1] + 2 * (i / 3)).primary("weapon.smg").health(TOUGH).done();
            }
            builder.squad("party").faction(Faction.MARINE).size(RAIDERS).at(farMarine[0], farMarine[1] - 3)
                    .primary("weapon.smg").health(TOUGH).stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.setTacticalMap(new TacticalMap(new ArrayList<>(nodes)));
            sim.random().setSeed(20261021L);
            Set<Integer> pool = new TreeSet<>();
            for (int i = 0; i < SQUADS; i++) pool.add(world.squadId("defenders-" + i));
            sim.addObjective(new RaidObjective(raid.targetId(), raid.targetName(), raid.targetCellX(),
                    raid.targetCellY(), sim.getZoneGraph().zoneIdAt(raid.targetCellX(), raid.targetCellY()), -1, -1));
            RaidDefenderCommand command = new RaidDefenderCommand(pool);
            sim.setAutonomousCommander(Faction.DEFENDER, command, RaidDefenderCommandDisclosure.INSTANCE);
            for (int id : pool) {
                sim.claimSquadCommand(id, CommandAuthority.MISSION_COMMAND, command.strategyId(), "scene mobile security");
            }

            long[] raiders = world.members("party");
            ScriptedPlayer player = ScriptedPlayer.on(world);
            player.at(ENTER_TICK, s -> place(s, raiders, approach));
            player.at(ADVANCE_TICK, s -> place(s, raiders, near));
            player.at(LEAVE_TICK, s -> place(s, raiders, new int[]{farMarine[0], farMarine[1] - 3}));
            Trace trace = new Trace(pool, chosen, command.strategyId());
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                            loop, tick, trace.caption()))
                    .run(TICKS);
            Map<String, Integer> figures = command.reactiveDefenceFigures();
            return report(loop, trace, chosen, figures);
        }
    }

    private static void place(BattleSimulation s, long[] raiders, int[] at) {
        for (int i = 0; i < raiders.length; i++) s.world().setCellPos(raiders[i], at[0] + (i % 2), at[1] + (i / 2));
    }

    /** The held place nearest the Raid target with at least {@link #MIN_POSTS} posts. */
    private static TacticalNode choose(List<TacticalNode> nodes, GarrisonableRegistry offered, RaidObjective raid) {
        if (raid == null) return null;
        TacticalNode best = null;
        long bestScore = Long.MAX_VALUE;
        for (TacticalNode node : nodes) {
            int g = offered.envelopeOf(node);
            if (g < 0 || offered.size(g) < MIN_POSTS) continue;
            long dx = node.anchorX - raid.targetCellX();
            long dy = node.anchorY - raid.targetCellY();
            if (dx * dx + dy * dy < bestScore) {
                best = node;
                bestScore = dx * dx + dy * dy;
            }
        }
        return best;
    }

    private SceneReport report(String loop, Trace t, TacticalNode chosen, Map<String, Integer> figures) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("reserve-staged-before-manning",
                t.firstStageTick >= 0 && (t.firstManTick < 0 || t.firstStageTick < t.firstManTick),
                "first squad staged for the area at tick " + t.firstStageTick + ", the " + chosen.kind
                        + " first manned at tick " + t.firstManTick));
        verdicts.add(Verdict.of("staged-squad-stays-free", t.stagedSamples > 0 && t.stagedOnPlace == 0,
                t.stagedSamples + " staged squad-samples, " + t.stagedOnPlace
                        + " of them carrying a place's order rather than an area defence"));
        verdicts.add(Verdict.of("returned-when-quiet",
                t.coolTick >= 0 && t.lastStagedTick >= 0 && t.lastStagedTick - t.coolTick <= RETURN_BOUND_TICKS,
                "the party left at tick " + LEAVE_TICK + ", belief of it cooled at tick " + t.coolTick
                        + ", the last squad stopped waiting at tick " + t.lastStagedTick + " (bound "
                        + RETURN_BOUND_TICKS + ")"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstBelievedTick", t.firstBelievedTick);
        metrics.put("firstStageTick", t.firstStageTick);
        metrics.put("firstForwardTick", t.firstForwardTick);
        metrics.put("firstManTick", t.firstManTick);
        metrics.put("maxStaged", t.maxStaged);
        metrics.put("coolTick", t.coolTick);
        metrics.put("lastStagedTick", t.lastStagedTick);
        for (String key : List.of("stageOrders", "forwardOrders", "stageReleases", "manOrders", "manFromStage",
                "arrivedFirst", "contactFirst")) {
            metrics.put(key, figures.getOrDefault(key, -1));
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Set<Integer> pool;
        final TacticalNode chosen;
        final String strategy;
        int firstStageTick = -1;
        int firstForwardTick = -1;
        int firstManTick = -1;
        int lastStagedTick = -1;
        int coolTick = -1;
        int firstBelievedTick = -1;
        int maxStaged;
        int stagedSamples;
        int stagedOnPlace;
        private String state = "";

        Trace(Set<Integer> pool, TacticalNode chosen, String strategy) {
            this.pool = pool;
            this.chosen = chosen;
            this.strategy = strategy;
        }

        String caption() { return state; }

        @Override public void observe(BattleSimulation sim, int tick) {
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
            int staged = 0;
            int holding = 0;
            for (int id : pool) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(id);
                if (directive == null || !strategy.equals(directive.issuer())) continue;
                String reason = directive.reason();
                ObjectiveAssignment order = squad.assignedObjective;
                boolean waits = MissionGarrisons.Kind.STAGE.reason().equals(reason)
                        || MissionGarrisons.Kind.FORWARD.reason().equals(reason);
                if (waits) {
                    staged++;
                    stagedSamples++;
                    if (order != null && order.mansPlace()) stagedOnPlace++;
                    if (firstStageTick < 0) firstStageTick = tick;
                    if (MissionGarrisons.Kind.FORWARD.reason().equals(reason) && firstForwardTick < 0) {
                        firstForwardTick = tick;
                    }
                }
                if (order != null && order.isEnvelope() && GarrisonableRegistry.sameBuilding(order.targetNode(), chosen)) {
                    holding++;
                }
            }
            if (holding > 0 && firstManTick < 0) firstManTick = tick;
            if (staged > 0) lastStagedTick = tick;
            maxStaged = Math.max(maxStaged, staged);
            boolean believed = believesParty(sim);
            if (believed && firstBelievedTick < 0) firstBelievedTick = tick;
            if (tick >= LEAVE_TICK && coolTick < 0 && !believed) coolTick = tick;
            state = staged + " waiting, " + holding + " holding the " + chosen.kind;
        }

        private boolean believesParty(BattleSimulation sim) {
            CommanderInfluenceSnapshot picture = sim.peekCommanderInfluence(Faction.DEFENDER);
            if (picture == null) return false;
            for (CommanderContact contact : picture.contacts()) {
                if (contact.confidence() * contact.strength() < GarrisonDirector.MIN_CONTACT_WEIGHT) continue;
                int gap = Math.max(Math.max(chosen.left - contact.cellX(), contact.cellX() - chosen.right),
                        Math.max(chosen.top - contact.cellY(), contact.cellY() - chosen.bottom));
                if (gap <= BELIEF_CELLS) return true;
            }
            return false;
        }
    }
}
