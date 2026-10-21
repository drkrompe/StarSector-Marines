package com.dillon.starsectormarines.battle.command;

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
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A procedural mission's counter-attack (`conquest-command.md`, a place the defender loses; {@link MissionCounterattack})
 * on the Raid's production map with the Raid's own defender commander: six mobile squads well back from a key building
 * beside the Raid target, whose born garrison is not there, and with no other posted building close enough that the
 * party would turn it too. A party of marines, too tough to be shot down so the
 * question stays about command, is set down in the building and stays.
 *
 * <p>Loops: <b>light</b>, four marines in the building, which half the defender can retake at its margin; and
 * <b>outmatched</b>, ninety, which it cannot (it goes at half of parity, so a party it believes twice what it can raise).
 *
 * <p>Verdicts, light: the building turns lost on belief; the side observes it (a probe goes, or a squad of its own
 * already watches) before it commits anybody to a muster; what it believes the place takes is sized from what it
 * observed, at the mission margin with a floor of one squad; the group launches together, every member on the same pulse; and it
 * never holds more than its share of the side. Outmatched: the side observes, does not launch, stands down, and forms
 * no group for that building again until its wait has run out. Whether the push wins the building back is a metric: the party cannot be killed.
 */
public final class MissionCounterattackScene implements BehaviorScene {

    private static final int TICKS = 7200;
    private static final int ENTER_TICK = 30;
    private static final String FIXTURE = "/battle-fixtures/raid-command-duel-v1.json";
    private static final int SEED_TRIES = 12;
    private static final int SQUADS = 6;
    private static final int SQUAD_SIZE = 6;
    private static final float TOUGH = 1_000_000f;
    private static final Pattern TAKES = Pattern.compile("believed to take (\\d+)");

    @Override public String id() { return "mission-counterattack"; }

    @Override public String label() {
        return "Mission counter-attack: a Raid's defender observes a lost key building, sizes a push from what it saw "
                + "and launches it together within its share, or stands down when outmatched";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        long seed = seed();
        return List.of(playOne(seed, "light", 4, frames), playOne(seed, "outmatched", 90, frames));
    }

    /** The Raid's production construction, as its commander evidence builds it, at a seed offset from its fixture. */
    private static BattleSimulation build(long offset) throws Exception {
        String json;
        try (InputStream stream = MissionCounterattackScene.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing Raid fixture: " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSONObject object = new JSONObject(json);
        object.put("seed", object.getLong("seed") + offset);
        return BattleFixtureJson.fromJson(object).build();
    }

    /** The first seed offset whose target has a key building beside it with room in and round it. */
    private static long seed() throws Exception {
        for (long offset = 0; offset < SEED_TRIES; offset++) {
            try (BattleSimulation source = build(offset)) {
                if (pick(source) != null) return offset;
            }
        }
        return 0;
    }

    private record Pick(TacticalNode building, int[] inside, int[] home) { }

    private static Pick pick(BattleSimulation source) {
        RaidObjective raid = raid(source);
        List<TacticalNode> nodes = source.getTacticalMap() == null ? List.of() : source.getTacticalMap().all();
        for (TacticalNode candidate : candidates(nodes, raid)) {
            int[] in = MissionEnvelopeScene.ground(source.getGrid(), candidate, candidate.anchorX, candidate.anchorY, 0,
                    2, null);
            int[] back = in == null ? null
                    : MissionEnvelopeScene.ground(source.getGrid(), candidate, in[0], in[1], 30, 60, null);
            if (back != null) return new Pick(candidate, in, back);
        }
        return null;
    }

    private static RaidObjective raid(BattleSimulation source) {
        for (Objective objective : source.getObjectives()) {
            if (objective instanceof RaidObjective r) return r;
        }
        return null;
    }

    private SceneReport playOne(long seed, String loop, int holders, FrameSink frames) throws Exception {
        try (BattleSimulation source = build(seed)) {
            NavigationGrid grid = source.getGrid();
            List<TacticalNode> nodes = source.getTacticalMap() == null ? List.of() : source.getTacticalMap().all();
            RaidObjective raid = raid(source);
            Pick pick = pick(source);
            if (raid == null || pick == null) {
                return new SceneReport(id(), loop, 0, List.of(Verdict.of("ground-found", false,
                        "no Raid seed offset below " + SEED_TRIES + " has a key building beside its target with room "
                                + "inside it and open ground back from it")), Map.of());
            }
            TacticalNode chosen = pick.building();
            int[] inside = pick.inside();
            int[] home = pick.home();
            MissionPlaces.Site site = MissionPlaces.building(new MissionPlaces.Building(chosen.anchorX, chosen.anchorY,
                    chosen.left, chosen.top, chosen.right, chosen.bottom, chosen.mustHold), MissionPlaces.BUILDING_WORTH);

            SceneBuilder builder = SceneBuilder.over(grid, source.getTopology()).missionCompletion(false);
            for (int i = 0; i < SQUADS; i++) {
                builder.squad("defenders-" + i).faction(Faction.DEFENDER).type(UnitType.MILITIA).size(SQUAD_SIZE)
                        .at(home[0] + 2 * (i % 3), home[1] + 2 * (i / 3)).primary("weapon.smg").health(TOUGH).done();
            }
            builder.squad("party").faction(Faction.MARINE).size(holders).at(inside[0], inside[1])
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
            long[] party = world.members("party");
            int[] in = inside;
            ScriptedPlayer player = ScriptedPlayer.on(world);
            player.at(ENTER_TICK, s -> {
                for (int i = 0; i < party.length; i++) {
                    s.world().setCellPos(party[i], in[0] + (i % 3) - 1, in[1] + (i / 3) % 3 - 1);
                }
            });
            Trace trace = new Trace(pool, command.strategyId(), site.key());
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick,
                            trace.state))
                    .run(TICKS);
            return report(loop, holders, site.key(), trace);
        }
    }

    /** The posted defender buildings beside the Raid target that do not hold it, nearest first. */
    private static List<TacticalNode> candidates(List<TacticalNode> nodes, RaidObjective raid) {
        List<TacticalNode> out = new ArrayList<>();
        if (raid == null) return out;
        int x = raid.targetCellX();
        int y = raid.targetCellY();
        for (TacticalNode node : nodes) {
            if (node.defaultGuard != Faction.DEFENDER || HeldPlaces.isUnposted(node)) continue;
            if (node.left <= x && x <= node.right && node.top <= y && y <= node.bottom) continue;
            if (gap(node, x, y) > MissionPlaces.KEY_REACH_CELLS) continue;
            if (!apart(node, nodes)) continue;
            out.add(node);
        }
        out.sort(Comparator.<TacticalNode>comparingInt(n -> gap(n, x, y)).thenComparingInt(n -> n.anchorX)
                .thenComparingInt(n -> n.anchorY));
        return out;
    }

    /**
     * Whether no other posted building is near enough that a party in this one would stand "on" it too: each place
     * counts a believed marine within {@link MissionPlaces#ON_CELLS} of its ground, and the party spreads a cell.
     */
    private static boolean apart(TacticalNode node, List<TacticalNode> nodes) {
        int clear = 2 * MissionPlaces.ON_CELLS + 2;
        for (TacticalNode other : nodes) {
            if (other == node || other.defaultGuard != Faction.DEFENDER || HeldPlaces.isUnposted(other)) continue;
            int dx = Math.max(other.left - node.right, node.left - other.right);
            int dy = Math.max(other.top - node.bottom, node.top - other.bottom);
            if (Math.max(dx, dy) <= clear) return false;
        }
        return true;
    }

    private static int gap(TacticalNode node, int x, int y) {
        return Math.max(Math.max(node.left - x, x - node.right), Math.max(node.top - y, y - node.bottom));
    }

    private SceneReport report(String loop, int holders, String key, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        int cap = PlaceCounterattack.Doctrine.mission(MissionCounterattack.share, MissionCounterattack.floor,
                MissionCounterattack.margin, MissionCounterattack.commitShare, MissionCounterattack.maxLaunches).cap(SQUADS);
        verdicts.add(Verdict.of("place-lost", t.lostTick >= 0,
                key + " turned lost at tick " + t.lostTick + " (" + holders + " marines put in it at tick " + ENTER_TICK + ")"));
        verdicts.add(Verdict.of("observed-before-commit",
                t.observedTick >= 0 && (t.musterTick < 0 || t.observedTick <= t.musterTick),
                "observed at tick " + t.observedTick + " (" + t.observedWhy + "), first muster at tick " + t.musterTick));
        verdicts.add(Verdict.of("within-share", t.maxGroup <= cap,
                "the group held at most " + t.maxGroup + " squads; its share of " + SQUADS + " is " + cap));
        int floor = MissionCounterattack.floor;
        if (loop.equals("light")) {
            int expectLo = Math.max(floor, (int) Math.floor(MissionCounterattack.margin * (holders - 1)));
            verdicts.add(Verdict.of("sized-from-observed", t.required >= expectLo
                            && t.required <= Math.max(floor, (int) Math.ceil(MissionCounterattack.margin * (holders + 1))) + 4,
                    "the muster believed the building takes " + t.required + " with " + holders
                            + " marines in it (margin " + MissionCounterattack.margin + ", floor " + floor + ")"));
            verdicts.add(Verdict.of("launched-together", t.launchTick >= 0 && t.launchedTogether,
                    "launched at tick " + t.launchTick + "; every member's first launch order on that pulse: "
                            + t.launchedTogether));
        } else {
            verdicts.add(Verdict.of("not-attempted", t.launchTick < 0 && t.standDownTick >= 0,
                    "stood down at tick " + t.standDownTick + ", launched at tick " + t.launchTick));
            verdicts.add(Verdict.of("place-waits", t.standDownTick >= 0 && t.reformedWhileWaiting == 0,
                    t.reformedWhileWaiting + " notes forming, mustering or launching a group for " + key + " within "
                            + PlaceCounterattack.waitTicks(1) + " ticks of standing down " + t.reformedNote));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("lostTick", t.lostTick);
        metrics.put("observedTick", t.observedTick);
        metrics.put("musterTick", t.musterTick);
        metrics.put("launchTick", t.launchTick);
        metrics.put("standDownTick", t.standDownTick);
        metrics.put("required", t.required);
        metrics.put("maxGroup", t.maxGroup);
        metrics.put("retookTick", t.retookTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final Set<Integer> pool;
        final String strategy;
        int lostTick = -1;
        int observedTick = -1;
        String observedWhy = "";
        int musterTick = -1;
        int launchTick = -1;
        int standDownTick = -1;
        int retookTick = -1;
        int required = -1;
        int maxGroup;
        boolean launchedTogether = true;
        /** Notes that formed, mustered or launched a group for the place while it waited after standing down. */
        int reformedWhileWaiting;
        String reformedNote = "";
        final String key;
        final Set<Integer> launched = new TreeSet<>();
        int lastNotesTick = -1;
        String state = "";

        Trace(Set<Integer> pool, String strategy, String key) {
            this.pool = pool;
            this.strategy = strategy;
            this.key = key;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.DEFENDER);
            if (snapshot == null) return;
            if (snapshot.tick() != lastNotesTick) {
                lastNotesTick = snapshot.tick();
                notes(snapshot.objectiveSummaries(), lastNotesTick);
            }
            int group = 0;
            int launchedNow = 0;
            for (int id : pool) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                CommandDirective directive = snapshot.directiveFor(id);
                if (directive == null || !strategy.equals(directive.issuer())) continue;
                String reason = directive.reason();
                if (!reason.startsWith(MissionCounterattack.REASON_PREFIX)) continue;
                group++;
                if (reason.endsWith("LAUNCHED")) {
                    launchedNow++;
                    if (launched.add(id) && launchTick >= 0 && snapshot.tick() != launchTick) {
                        launchedTogether = false;
                    }
                }
            }
            maxGroup = Math.max(maxGroup, group);
            state = group + " in the counter-attack, " + launchedNow + " launched";
        }

        private void notes(List<String> lines, int tick) {
            for (String line : lines) {
                // Only this place's notes: a party that spills onto the buildings beside it turns them too.
                if (!line.contains(key + " ") && !line.contains(key + ":")) continue;
                if (line.startsWith("mission place ") && line.contains(" is lost") && lostTick < 0) lostTick = tick;
                if (line.contains(" probes: squad ") && observedTick < 0) {
                    observedWhy = "a probe was sent";
                }
                if (line.contains(" observed it (") && observedTick < 0) {
                    observedTick = tick;
                    observedWhy = observedWhy.isEmpty() ? "watched" : observedWhy;
                }
                if (line.contains(" musters: ") && musterTick < 0) {
                    musterTick = tick;
                    if (observedTick < 0) {
                        // Decided straight from an observation its own watchers already made.
                        observedTick = tick;
                        observedWhy = "already watched";
                    }
                    Matcher m = TAKES.matcher(line);
                    if (m.find()) required = Integer.parseInt(m.group(1));
                }
                if (line.contains(" launches (") && launchTick < 0) launchTick = tick;
                if (line.contains(" stands down (outmatched") && standDownTick < 0) {
                    standDownTick = tick;
                    if (observedTick < 0) {
                        observedTick = tick;
                        observedWhy = "already watched";
                    }
                }
                if (line.contains(" retook it at tick ") && retookTick < 0) retookTick = tick;
                if (standDownTick >= 0 && tick > standDownTick && tick < standDownTick + PlaceCounterattack.waitTicks(1)
                        && (line.startsWith("counter-attack reserve for " + key + " ")
                        || line.startsWith("counter-attack for " + key + " ") && (line.contains(" musters: ")
                        || line.contains(" launches (")))) {
                    reformedWhileWaiting++;
                    if (reformedNote.isEmpty()) reformedNote = tick + ": " + line;
                }
            }
        }
    }
}
