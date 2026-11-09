package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The derelict security network's director with walkers (`derelict-security-network.md`, slice 2), four loops on open
 * ground with no flocks, so the walkers are their zone's own eyes.
 *
 * <p>{@code relay}: three zones linked in a chain; a fire team stands inside the first, in its walker's sight. The
 * first wakes, the second goes alert on the relay, the third (two hops) stays dormant. {@code pin}: a team inside a
 * line frame's perimeter; the frame comes to it. {@code pin-outside}: the same team past the perimeter, in sight; the
 * frame holds its post. {@code contingency}: the vault is breached with nobody near; the line frame walks out to the
 * egress and holds it; the siege frame fires from its post and the vault frame stays on the vault.
 *
 * <p>Verdicts are the director's rules seen in play: states, orders and where the walkers went. Who wins is a reading.
 */
public final class DerelictNetworkScene implements BehaviorScene {

    public static final String RELAY = "relay";
    public static final String PIN = "pin";
    public static final String PIN_OUTSIDE = "pin-outside";
    public static final String CONTINGENCY = "contingency";

    static final int WIDTH = 128;
    static final int HEIGHT = 56;
    static final int ROW_Y = 28;
    static final int POST_X = 20;
    static final int PERIMETER = 14;
    static final int TICKS = 900;
    static final int BREACH_AT = 60;
    static final int EGRESS_X = 44;
    /** The walker crosses about 0.8 cells a second: long enough for it to reach the egress. */
    static final int CONTINGENCY_TICKS = 1800;
    private static final String RIFLE = "weapon.field-rifle";

    private final int frameEvery;

    public DerelictNetworkScene() {
        this(10);
    }

    public DerelictNetworkScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "derelict-network"; }

    @Override public String label() {
        return "Derelict network: a wake relays one hop, a line frame comes to a contact inside its perimeter and "
                + "holds for one outside it, and on the vault breach walks to the egress while the vault frame stays";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(relay(frames), pin(PIN, POST_X + 10, frames), pin(PIN_OUTSIDE, POST_X + PERIMETER + 6, frames),
                contingency(frames));
    }

    private static SceneBuilder holdouts(SceneBuilder b) {
        return b.wall(WIDTH - 9, HEIGHT - 9, WIDTH - 2, HEIGHT - 9).wall(WIDTH - 9, HEIGHT - 2, WIDTH - 2, HEIGHT - 2)
                .wall(WIDTH - 9, HEIGHT - 9, WIDTH - 9, HEIGHT - 2).wall(WIDTH - 2, HEIGHT - 9, WIDTH - 2, HEIGHT - 2)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, WIDTH - 5, HEIGHT - 5)
                .wall(WIDTH - 9, 1, WIDTH - 2, 1).wall(WIDTH - 9, 8, WIDTH - 2, 8)
                .wall(WIDTH - 9, 1, WIDTH - 9, 8).wall(WIDTH - 2, 1, WIDTH - 2, 8)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, WIDTH - 5, 4);
    }

    /** A walker of {@code frame} at its post in zone {@code zoneId}, under the network's command. */
    private static DerelictSecurityDirector.Walker walker(BattleSimulation sim, DerelictWalkerFrame frame, int zoneId,
                                                          int x, int y) {
        int squad = DerelictSecurity.spawn(sim, frame, x, y);
        sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, DerelictSecurityDirector.ISSUER, "scene");
        return new DerelictSecurityDirector.Walker(squad, frame, zoneId, x, y);
    }

    private static float x(BattleSimulation sim, int squad) {
        return sim.squadMemberCount(squad) == 0 ? Float.NaN : sim.world().x(sim.squadMemberAt(squad, 0));
    }

    private SceneReport relay(FrameSink frames) {
        SceneBuilder b = holdouts(SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false));
        b.squad("team").faction(Faction.MARINE).size(4).inFile(POST_X + 8, ROW_Y - 2, 0, 1).primary(RIFLE)
                .stationary().done();
        SceneWorld world = b.build();
        BattleSimulation sim = world.sim();
        SecurityNetwork network = new SecurityNetwork()
                .add(new SecurityZone(0, POST_X, ROW_Y, PERIMETER))
                .add(new SecurityZone(1, POST_X + 30, ROW_Y, PERIMETER))
                .add(new SecurityZone(2, POST_X + 60, ROW_Y, PERIMETER))
                .link(0, 1).link(1, 2);
        DerelictSecurityDirector director = new DerelictSecurityDirector(network,
                List.of(walker(sim, DerelictWalkerFrame.LINE, 0, POST_X, ROW_Y)), List.of(), 0, 0);
        sim.installDerelictSecurity(director);
        int[] firstAwake = {-1};
        SceneRun.of(world).observe((s, tick) -> {
            if (firstAwake[0] < 0 && network.zone(0).state() == SecurityZone.State.AWAKE) firstAwake[0] = tick;
        }).frames(frames, RELAY, frameEvery, tick -> RELAY + " / tick " + tick + " / " + network.zone(0).state()
                + " " + network.zone(1).state() + " " + network.zone(2).state()).run(300);
        SecurityZone.State a = network.zone(0).state();
        SecurityZone.State second = network.zone(1).state();
        SecurityZone.State third = network.zone(2).state();
        sim.close();

        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("wakes", a == SecurityZone.State.AWAKE, "first zone " + a + " from tick " + firstAwake[0]));
        v.add(Verdict.of("relays-one-hop", second == SecurityZone.State.ALERT && third == SecurityZone.State.DORMANT,
                "second zone " + second + ", third " + third));
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("wokeAt", firstAwake[0]);
        return new SceneReport(id(), RELAY, 300, v, m);
    }

    private SceneReport pin(String id, int teamX, FrameSink frames) {
        boolean inside = PIN.equals(id);
        SceneBuilder b = holdouts(SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false));
        b.squad("team").faction(Faction.MARINE).size(4).inFile(teamX, ROW_Y - 2, 0, 1).primary(RIFLE).stationary()
                .done();
        SceneWorld world = b.build();
        BattleSimulation sim = world.sim();
        SecurityNetwork network = new SecurityNetwork().add(new SecurityZone(0, POST_X, ROW_Y, PERIMETER));
        DerelictSecurityDirector.Walker line = walker(sim, DerelictWalkerFrame.LINE, 0, POST_X, ROW_Y);
        DerelictSecurityDirector director = new DerelictSecurityDirector(network, List.of(line), List.of(), 0, 0);
        sim.installDerelictSecurity(director);
        float start = x(sim, line.squadId());
        float[] farthest = {0f};
        boolean[] closed = {false};
        boolean[] engaged = {false};
        SceneRun.of(world).observe((s, tick) -> {
            float now = x(s, line.squadId());
            if (!Float.isNaN(now)) farthest[0] = Math.max(farthest[0], now - start);
            WalkerPosture.Order order = director.orderOf(line.squadId());
            if (order != null && order.kind() == WalkerPosture.Kind.CLOSE) closed[0] = true;
            if (s.squadMemberCount(line.squadId()) > 0 && s.targetOf(s.squadMemberAt(line.squadId(), 0)) != 0L) {
                engaged[0] = true;
            }
        }).frames(frames, id, frameEvery, tick -> id + " / tick " + tick + " / zone " + network.zone(0).state()
                + " / " + director.orderOf(line.squadId())).run(TICKS);
        SecurityZone.State state = network.zone(0).state();
        sim.close();

        List<Verdict> v = new ArrayList<>();
        if (inside) {
            v.add(Verdict.of("comes-to-pin", closed[0] && (farthest[0] >= 3f || engaged[0]),
                    "close order " + closed[0] + ", walked " + Math.round(farthest[0])
                            + " cells toward the team, engaged " + engaged[0]));
        } else {
            v.add(Verdict.of("holds-post", !closed[0] && farthest[0] < 3f,
                    "close order " + closed[0] + ", at most " + Math.round(farthest[0]) + " cells off its post, zone "
                            + state));
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("walkedCells", Math.round(farthest[0]));
        return new SceneReport(id(), id, TICKS, v, m);
    }

    private SceneReport contingency(FrameSink frames) {
        SceneWorld world = holdouts(SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)).build();
        BattleSimulation sim = world.sim();
        SecurityNetwork network = new SecurityNetwork().add(new SecurityZone(0, POST_X, ROW_Y, PERIMETER));
        DerelictSecurityDirector.Walker line = walker(sim, DerelictWalkerFrame.LINE, 0, POST_X, ROW_Y);
        DerelictSecurityDirector.Walker vault = walker(sim, DerelictWalkerFrame.VAULT, 0, POST_X, ROW_Y - 10);
        DerelictSecurityDirector.Walker siege = walker(sim, DerelictWalkerFrame.SIEGE, 0, POST_X, ROW_Y + 10);
        DerelictSecurityDirector director = new DerelictSecurityDirector(network, List.of(line, vault, siege),
                List.<int[]>of(new int[] {EGRESS_X, ROW_Y}), POST_X, ROW_Y - 10);
        sim.installDerelictSecurity(director);
        float vaultStartX = x(sim, vault.squadId());
        float[] vaultFarthest = {0f};
        SceneRun.of(world).observe((s, tick) -> {
            if (tick == BREACH_AT) network.breachVault(tick);
            float now = x(s, vault.squadId());
            if (!Float.isNaN(now)) vaultFarthest[0] = Math.max(vaultFarthest[0], Math.abs(now - vaultStartX));
        }).frames(frames, CONTINGENCY, frameEvery, tick -> CONTINGENCY + " / tick " + tick + " / "
                + director.orderOf(line.squadId())).run(CONTINGENCY_TICKS);
        float lineEnd = x(sim, line.squadId());
        float siegeEnd = x(sim, siege.squadId());
        WalkerPosture.Order lineOrder = director.orderOf(line.squadId());
        sim.close();

        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("line-holds-egress", lineOrder != null && lineOrder.kind() == WalkerPosture.Kind.HOLD_EGRESS
                        && Math.abs(lineEnd - EGRESS_X) <= DerelictSecurityDirector.EGRESS_RADIUS + 2,
                "line frame " + (lineOrder == null ? "no order" : lineOrder.kind()) + " at x " + Math.round(lineEnd)
                        + " (egress x " + EGRESS_X + ")"));
        v.add(Verdict.of("siege-frame-stays", Math.abs(siegeEnd - (POST_X + 0.5f)) < 3f,
                "siege frame at x " + Math.round(siegeEnd) + ", firing from its post (post x " + POST_X + ")"));
        v.add(Verdict.of("vault-frame-stays", vaultFarthest[0] < 3f,
                "vault frame at most " + Math.round(vaultFarthest[0]) + " cells off its post"));
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("lineEndX", Math.round(lineEnd));
        return new SceneReport(id(), CONTINGENCY, CONTINGENCY_TICKS, v, m);
    }
}
