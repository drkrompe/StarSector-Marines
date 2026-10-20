package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
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
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
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
import java.util.function.Supplier;

/**
 * Mission defenders man garrisonables reactively (`garrisonables.md`, Beyond Conquest): on the production map of a
 * Raid and of an Assault, the mission's own defender commander — {@link RaidDefenderCommand},
 * {@link AssaultDefenderCommand} — is given three mobile squads beside a held place's building, and a party of marines,
 * too tough to be shot down so the question stays about command, is set down in front of it and later walks away.
 *
 * <p>Verdicts: the building is an envelope; the defender believes the party pressing it; the commander mans it with
 * its own envelope order within a few pulses; a holder stands to at the apertures; and once the party has gone and
 * the defender's belief of it has cooled, the commander gives the building back within the dwell and a few pulses.
 * Which squad went, when, and how many held at once are metrics.
 */
public final class MissionDefenceScene implements BehaviorScene {

    private static final int PULSE_TICKS = 75;
    private static final int MAN_BOUND_TICKS = 6 * PULSE_TICKS;
    private static final int RELEASE_BOUND_TICKS = (GarrisonDirector.DWELL_PULSES + 3) * PULSE_TICKS;
    private static final int ENTER_TICK = 30;
    private static final int LEAVE_TICK = 1500;
    private static final int TICKS = 4200;
    private static final int SQUADS = 3;
    private static final int SQUAD_SIZE = 6;
    private static final int RAIDERS = 4;
    private static final float TOUGH = 1_000_000f;
    private static final int MIN_POSTS = 4;

    private record Mission(String loop, Supplier<BattleSimulation> build) {}

    private static List<Mission> missions() {
        List<ShuttleAssignment> lift = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        return List.of(
                new Mission("raid", () -> BattleSetup.createRaid(15311)),
                new Mission("assault", () -> BattleSetup.createPlaceholder(15312, lift)));
    }

    @Override public String id() { return "mission-defence"; }

    @Override public String label() {
        return "Mission defence: a Raid's and an Assault's own defender commander man a pressed building from their "
                + "mobile squads and give it back when the pressure passes";
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
            RaidObjective raid = null;
            for (Objective objective : source.getObjectives()) {
                if (objective instanceof RaidObjective r) raid = r;
            }
            TacticalNode chosen = choose(nodes, offered, raid);
            if (chosen == null) {
                return new SceneReport(id(), mission.loop(), 0, List.of(Verdict.of("envelope-found", false,
                        "no held place with " + MIN_POSTS + " posts")), Map.of());
            }
            int centre = offered.envelopeOf(chosen);
            int[] near = MissionEnvelopeScene.ground(grid, chosen, offered.centreX(centre), offered.centreY(centre), 3, 6,
                    null);
            int[] party = near == null ? null
                    : MissionEnvelopeScene.ground(grid, chosen, near[0], near[1], 10, 16, near);
            int[] back = near == null ? null
                    : MissionEnvelopeScene.ground(grid, chosen, near[0], near[1], 14, 22, null);
            if (near == null || party == null || back == null) {
                return new SceneReport(id(), mission.loop(), 0, List.of(Verdict.of("ground-found", false,
                        "no open ground beside " + chosen.kind + " at " + chosen.anchorX + "," + chosen.anchorY)),
                        Map.of());
            }
            int[] farMarine = MissionEnvelopeScene.far(grid, party[0], party[1]);
            int[] farDefender = MissionEnvelopeScene.far(grid, near[0], near[1]);

            SceneBuilder builder = SceneBuilder.over(grid, source.getTopology()).missionCompletion(false);
            builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, farMarine[0], farMarine[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, farDefender[0], farDefender[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            for (int i = 0; i < SQUADS; i++) {
                int[] at = i == 0 ? near : back;
                builder.squad("defenders-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(SQUAD_SIZE)
                        .at(at[0] + 2 * (i % 2), at[1] + (i / 2)).primary("weapon.smg").health(TOUGH).done();
            }
            // The party waits at the far corner until it is set down in front of the building.
            builder.squad("party").faction(Faction.MARINE).size(RAIDERS).at(farMarine[0], farMarine[1] - 3)
                    .primary("weapon.smg").health(TOUGH).stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.setTacticalMap(new TacticalMap(new ArrayList<>(nodes)));
            sim.random().setSeed(20261020L);
            Set<Integer> pool = new TreeSet<>();
            for (int i = 0; i < SQUADS; i++) pool.add(world.squadId("defenders-" + i));
            String strategy;
            if (raid != null) {
                sim.addObjective(new RaidObjective(raid.targetId(), raid.targetName(), raid.targetCellX(),
                        raid.targetCellY(), sim.getZoneGraph().zoneIdAt(raid.targetCellX(), raid.targetCellY()),
                        -1, -1));
                RaidDefenderCommand command = new RaidDefenderCommand(pool);
                strategy = command.strategyId();
                sim.setAutonomousCommander(Faction.DEFENDER, command, RaidDefenderCommandDisclosure.INSTANCE);
            } else {
                AssaultDefenderCommand command = new AssaultDefenderCommand(pool);
                strategy = command.strategyId();
                sim.setAutonomousCommander(Faction.DEFENDER, command, AssaultDefenderCommandDisclosure.INSTANCE);
            }
            for (int id : pool) {
                sim.claimSquadCommand(id, CommandAuthority.MISSION_COMMAND, strategy, "scene mobile security");
            }

            long[] raiders = world.members("party");
            ScriptedPlayer player = ScriptedPlayer.on(world);
            player.at(ENTER_TICK, s -> {
                for (int i = 0; i < raiders.length; i++) {
                    s.world().setCellPos(raiders[i], party[0] + (i % 2), party[1] + (i / 2));
                }
            });
            player.at(LEAVE_TICK, s -> {
                for (int i = 0; i < raiders.length; i++) {
                    s.world().setCellPos(raiders[i], farMarine[0] + (i % 2), farMarine[1] - 3 + (i / 2));
                }
            });
            Trace trace = new Trace(pool, chosen, strategy);
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, mission.loop(), 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                            mission.loop(), tick, trace.caption()))
                    .run(TICKS);
            return report(mission.loop(), trace, chosen, offered.size(centre));
        }
    }

    /** On a Raid the held place nearest the target, elsewhere the one with the most posts; at least {@link #MIN_POSTS}. */
    private static TacticalNode choose(List<TacticalNode> nodes, GarrisonableRegistry offered, RaidObjective raid) {
        TacticalNode best = null;
        long bestScore = Long.MAX_VALUE;
        for (TacticalNode node : nodes) {
            int g = offered.envelopeOf(node);
            if (g < 0 || offered.size(g) < MIN_POSTS) continue;
            long score;
            if (raid != null) {
                long dx = node.anchorX - raid.targetCellX();
                long dy = node.anchorY - raid.targetCellY();
                score = dx * dx + dy * dy;
            } else {
                score = -offered.size(g);
            }
            if (score < bestScore) {
                best = node;
                bestScore = score;
            }
        }
        return best;
    }

    private SceneReport report(String loop, Trace t, TacticalNode chosen, int posts) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("envelope-offered", t.envelope >= 0,
                "the " + chosen.kind + " at " + chosen.anchorX + "," + chosen.anchorY + " offers " + posts + " posts"));
        verdicts.add(Verdict.of("pressure-believed", t.hotTick >= 0,
                "the defender believed the party pressing the building from tick " + t.hotTick));
        verdicts.add(Verdict.of("building-manned-by-the-mission-commander",
                t.hotTick >= 0 && t.firstManTick >= 0 && t.firstManTick - t.hotTick <= MAN_BOUND_TICKS,
                "pressed from tick " + t.hotTick + ", first held by the commander's envelope order at tick "
                        + t.firstManTick + " (bound " + MAN_BOUND_TICKS + ")"));
        verdicts.add(Verdict.of("holders-stand-to", t.firstStoodTo >= 0,
                "a holder first stood to at the building's apertures at tick " + t.firstStoodTo));
        verdicts.add(Verdict.of("given-back-when-the-pressure-passes",
                t.coolTick >= 0 && t.releaseTick >= 0 && t.releaseTick - t.coolTick <= RELEASE_BOUND_TICKS,
                "the party left at tick " + LEAVE_TICK + ", belief on the building cooled at tick " + t.coolTick
                        + ", the building was given back at tick " + t.releaseTick + " (bound " + RELEASE_BOUND_TICKS
                        + ")"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("posts", posts);
        metrics.put("pressedTick", t.hotTick);
        metrics.put("firstManTick", t.firstManTick);
        metrics.put("firstStoodToTick", t.firstStoodTo);
        metrics.put("maxHolding", t.maxHolding);
        metrics.put("coolTick", t.coolTick);
        metrics.put("releaseTick", t.releaseTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Set<Integer> pool;
        final TacticalNode chosen;
        final String strategy;
        int envelope = -1;
        int hotTick = -1;
        int firstManTick = -1;
        int firstStoodTo = -1;
        int coolTick = -1;
        int releaseTick = -1;
        int maxHolding;
        private String state = "";

        Trace(Set<Integer> pool, TacticalNode chosen, String strategy) {
            this.pool = pool;
            this.chosen = chosen;
            this.strategy = strategy;
        }

        String caption() { return state; }

        @Override public void observe(BattleSimulation sim, int tick) {
            GarrisonableRegistry places = sim.garrisonables();
            if (envelope < 0) envelope = places.envelopeOf(chosen);
            if (envelope < 0) return;
            CommanderInfluenceSnapshot picture = sim.peekCommanderInfluence(Faction.DEFENDER);
            float pressure = picture == null ? 0f : pressure(picture, places, envelope);
            if (hotTick < 0 && tick < LEAVE_TICK && pressure >= GarrisonDirector.START_PRESSURE) hotTick = tick;
            if (tick >= LEAVE_TICK && coolTick < 0 && pressure < GarrisonDirector.RELEASE_PRESSURE) coolTick = tick;
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
            int holding = 0;
            for (int id : pool) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                ObjectiveAssignment order = squad.assignedObjective;
                CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(id);
                boolean ours = directive != null && strategy.equals(directive.issuer());
                if (!ours || order == null || !order.isEnvelope()
                        || !GarrisonableRegistry.sameBuilding(order.targetNode(), chosen)) continue;
                holding++;
                if (firstStoodTo < 0 && standsTo(squad)) firstStoodTo = tick;
            }
            if (holding > 0 && firstManTick < 0) firstManTick = tick;
            if (holding == 0 && firstManTick >= 0 && tick >= LEAVE_TICK && releaseTick < 0) releaseTick = tick;
            maxHolding = Math.max(maxHolding, holding);
            state = holding > 0 ? holding + " holding the " + chosen.kind : "pool on its duties";
        }

        private static float pressure(CommanderInfluenceSnapshot picture, GarrisonableRegistry places, int place) {
            int bw = places.blockWidth();
            float best = 0f;
            for (int block : places.coveredBlocks(place)) best = Math.max(best, picture.hostileAt(block % bw, block / bw));
            return best;
        }

        private static boolean standsTo(Squad squad) {
            SquadPlan plan = squad.currentPlan;
            if (plan == null || plan.isComplete()) return false;
            SquadPlan.Step step = plan.currentStep();
            return step != null && step.action instanceof ApertureHold;
        }
    }
}
