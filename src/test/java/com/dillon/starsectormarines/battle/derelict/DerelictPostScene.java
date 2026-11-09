package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
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
 * A derelict security zone's folded post (`derelict-security-network.md`, slice 7), two loops.
 *
 * <p>{@code wake}: a fire team stands in the open in front of a post, in its range and sight, while the zone's network
 * is not yet running; the post neither takes a target nor fires. From {@link #NETWORK_FROM} the network runs, the
 * team in its sight wakes the zone, and the post unfolds and fires.
 *
 * <p>{@code unseen}: the network runs from the start but the team stands behind a wall; nothing stirs the zone and
 * the post stays folded.
 *
 * <p>Verdicts are the gate: no post round while its zone is not awake, rounds once it is. Who wins is a reading.
 */
public final class DerelictPostScene implements BehaviorScene {

    public static final String WAKE = "wake";
    public static final String UNSEEN = "unseen";

    static final int WIDTH = 96;
    static final int HEIGHT = 56;
    static final int POST_X = 30, POST_Y = 28;
    static final int TEAM_X = 46;
    /** The wall that hides the team in the unseen loop. */
    static final int WALL_X = 40;
    static final int NETWORK_FROM = 300;
    static final int TICKS = 900;
    private static final String RIFLE = "weapon.field-rifle";

    private final int frameEvery;

    public DerelictPostScene() {
        this(10);
    }

    public DerelictPostScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "derelict-post"; }

    @Override public String label() {
        return "Derelict post: a folded gun holds fire with a fire team in its sight until its zone wakes, then fires; "
                + "unseen, it stays folded";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(loop(WAKE, frames), loop(UNSEEN, frames));
    }

    private static SceneBuilder holdouts(SceneBuilder b) {
        return b.wall(WIDTH - 9, HEIGHT - 9, WIDTH - 2, HEIGHT - 9).wall(WIDTH - 9, HEIGHT - 2, WIDTH - 2, HEIGHT - 2)
                .wall(WIDTH - 9, HEIGHT - 9, WIDTH - 9, HEIGHT - 2).wall(WIDTH - 2, HEIGHT - 9, WIDTH - 2, HEIGHT - 2)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, WIDTH - 5, HEIGHT - 5)
                .wall(WIDTH - 9, 1, WIDTH - 2, 1).wall(WIDTH - 9, 8, WIDTH - 2, 8)
                .wall(WIDTH - 9, 1, WIDTH - 9, 8).wall(WIDTH - 2, 1, WIDTH - 2, 8)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, WIDTH - 5, 4);
    }

    private SceneReport loop(String id, FrameSink frames) {
        boolean unseen = UNSEEN.equals(id);
        SceneBuilder b = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (unseen) b.wall(WALL_X, 10, WALL_X, 46);
        holdouts(b);
        b.squad("team").faction(Faction.MARINE).size(4).inFile(TEAM_X, 26, 0, 1).primary(RIFLE).stationary().done();
        SceneWorld world = b.build();
        BattleSimulation sim = world.sim();

        SecurityNetwork network = new SecurityNetwork().add(new SecurityZone(0, POST_X, POST_Y, 24));
        DerelictSecurityDirector director = new DerelictSecurityDirector(network, List.of(), List.of(), 0, 0);
        long post = DerelictSecurity.standPost(sim, "derelict-post", 0, POST_X, POST_Y, director);
        int networkFrom = unseen ? 0 : NETWORK_FROM;
        if (networkFrom == 0) sim.installDerelictSecurity(director);

        int[] roundsFolded = {0};
        int[] roundsAwake = {0};
        int[] wokeAt = {-1};
        int[] poweredWhileNotAwake = {0};
        SceneRun.of(world).observe((s, tick) -> {
            if (tick == networkFrom && networkFrom > 0) s.installDerelictSecurity(director);
            boolean awake = network.zone(0).state() == SecurityZone.State.AWAKE;
            if (awake && wokeAt[0] < 0) wokeAt[0] = tick;
            if (!awake && s.resolveUnit(post) != 0L && !s.turretState().unpowered(post)) poweredWhileNotAwake[0]++;
            for (ShotEvent shot : s.getShotsThisFrame()) {
                if (shot.shooterId != post) continue;
                if (awake) roundsAwake[0]++;
                else roundsFolded[0]++;
            }
        }).frames(frames, id, frameEvery, tick -> id + " / tick " + tick + " / zone "
                + network.zone(0).state()).run(TICKS);
        boolean standing = sim.resolveUnit(post) != 0L;
        sim.close();

        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("folded-holds-fire", roundsFolded[0] == 0 && poweredWhileNotAwake[0] == 0,
                roundsFolded[0] + " post rounds and " + poweredWhileNotAwake[0]
                        + " powered ticks while the zone was not awake"));
        if (unseen) {
            v.add(Verdict.of("stays-folded", wokeAt[0] < 0 && roundsAwake[0] == 0,
                    "zone " + (wokeAt[0] < 0 ? "never woke" : "woke at tick " + wokeAt[0])));
        } else {
            v.add(Verdict.of("unfolds-on-wake", wokeAt[0] >= NETWORK_FROM && roundsAwake[0] > 0,
                    "zone woke at tick " + wokeAt[0] + ", " + roundsAwake[0] + " post rounds after"));
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("wokeAt", wokeAt[0]);
        m.put("roundsAwake", roundsAwake[0]);
        m.put("postStanding", standing ? 1 : 0);
        return new SceneReport(id(), id, TICKS, v, m);
    }
}
