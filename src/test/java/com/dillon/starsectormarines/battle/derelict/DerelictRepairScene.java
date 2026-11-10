package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.FlockLivery;
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
 * A derelict zone's repair drone (`derelict-security-network.md`, slice 6), three loops on one zone: a node, a
 * damaged folded post twenty cells off, and the zone's repair drone at the node.
 *
 * <p>{@code mend}: the drone flies to the post and works it back up. {@code node-down}: the node is destroyed first;
 * the drone stays home and the post stays as it was. {@code under-fire}: the drone is hit on its way and breaks off
 * home, keeps there while it stands down, then goes out again.
 */
public final class DerelictRepairScene implements BehaviorScene {

    public static final String MEND = "mend";
    public static final String NODE_DOWN = "node-down";
    public static final String UNDER_FIRE = "under-fire";

    static final int WIDTH = 96;
    static final int HEIGHT = 56;
    static final int NODE_X = 20, POST_X = 40, ROW_Y = 28;
    static final int TICKS = 1200;
    /** When the under-fire loop hits the drone, partway out. */
    static final int HIT_AT = 90;

    private final int frameEvery;

    public DerelictRepairScene() {
        this(10);
    }

    public DerelictRepairScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "derelict-repair"; }

    @Override public String label() {
        return "Derelict repair drone: works a damaged post back up from its node, stays home with the node down, "
                + "breaks off home when hit";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(loop(MEND, frames), loop(NODE_DOWN, frames), loop(UNDER_FIRE, frames));
    }

    private static SceneBuilder holdouts(SceneBuilder b) {
        return b.wall(WIDTH - 9, HEIGHT - 9, WIDTH - 2, HEIGHT - 9).wall(WIDTH - 9, HEIGHT - 2, WIDTH - 2, HEIGHT - 2)
                .wall(WIDTH - 9, HEIGHT - 9, WIDTH - 9, HEIGHT - 2).wall(WIDTH - 2, HEIGHT - 9, WIDTH - 2, HEIGHT - 2)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, WIDTH - 5, HEIGHT - 5)
                .wall(WIDTH - 9, 1, WIDTH - 2, 1).wall(WIDTH - 9, 8, WIDTH - 2, 8)
                .wall(WIDTH - 9, 1, WIDTH - 9, 8).wall(WIDTH - 2, 1, WIDTH - 2, 8)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, WIDTH - 5, 4);
    }

    private static float durability(BattleSimulation sim, long id) {
        return sim.resolveUnit(id) == 0L ? 0f : sim.world().hp(id) + sim.world().armor(id);
    }

    private SceneReport loop(String id, FrameSink frames) {
        SceneWorld world = holdouts(SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)).build();
        BattleSimulation sim = world.sim();
        SecurityNetwork network = new SecurityNetwork().add(new SecurityZone(0, NODE_X, ROW_Y, 30));
        DerelictSecurityDirector director = new DerelictSecurityDirector(network, List.of(), List.of(), 0, 0);
        long node = sim.spawn(DerelictNode.create("node", Faction.DEFENDER, NODE_X, ROW_Y));
        director.standNode(0, node);
        long post = DerelictSecurity.standPost(sim, "post", 0, POST_X, ROW_Y, director);
        long drone = DerelictSecurity.standRepairDrone(sim, 0, director, FlockLivery.DOMAIN);
        sim.installDerelictSecurity(director);
        sim.applyExternalDamage(post, 90f, 100f);
        if (NODE_DOWN.equals(id)) sim.applyExternalDamage(node, 1.0e6f, 1.0e6f);
        float damaged = durability(sim, post);
        float whole = sim.world().maxHp(post) + sim.world().maxArmor(post);

        float[] farthest = {0f};
        float[] atHit = {-1f};
        float[] farthestAfterHit = {0f};
        float[] outAgain = {0f};
        SceneRun.of(world).observe((s, tick) -> {
            if (s.resolveUnit(drone) == 0L) return;
            float out = Math.abs(s.world().x(drone) - (NODE_X + 0.5f));
            farthest[0] = Math.max(farthest[0], out);
            if (UNDER_FIRE.equals(id) && tick == HIT_AT) {
                atHit[0] = out;
                s.applyExternalDamage(drone, 4f, 100f);
            }
            // Home within four seconds of the hit, and kept there while it stands down.
            if (atHit[0] >= 0f && tick >= HIT_AT + 240 && tick < HIT_AT + RepairDrone.STAND_DOWN_TICKS) {
                farthestAfterHit[0] = Math.max(farthestAfterHit[0], out);
            }
            if (atHit[0] >= 0f && tick >= HIT_AT + RepairDrone.STAND_DOWN_TICKS) {
                outAgain[0] = Math.max(outAgain[0], out);
            }
        }).frames(frames, id, frameEvery, tick -> id + " / tick " + tick + " / stock "
                + Math.round(director.repairStock(0))).run(TICKS);
        float end = durability(sim, post);
        float stock = director.repairStock(0);
        boolean droneUp = sim.resolveUnit(drone) != 0L;
        sim.close();

        List<Verdict> v = new ArrayList<>();
        switch (id) {
            case MEND -> {
                v.add(Verdict.of("flies-out", farthest[0] >= POST_X - NODE_X - 2,
                        "drone reached " + round(farthest[0]) + " cells from its node"));
                v.add(Verdict.of("mends", end > damaged + 20f,
                        "post " + round(damaged) + " -> " + round(end) + " of " + round(whole)));
            }
            case NODE_DOWN -> {
                v.add(Verdict.of("stays-home", farthest[0] < 2f,
                        "drone at most " + round(farthest[0]) + " cells from its node"));
                v.add(Verdict.of("no-mend", end <= damaged + 0.01f, "post " + round(damaged) + " -> " + round(end)));
            }
            default -> {
                v.add(Verdict.of("breaks-off-home", droneUp && atHit[0] > 2f && farthestAfterHit[0] < 1f,
                        "hit " + round(atHit[0]) + " cells out; at most " + round(farthestAfterHit[0])
                                + " cells out while standing down"));
                v.add(Verdict.of("goes-out-again", outAgain[0] > 5f,
                        "after standing down it went " + round(outAgain[0]) + " cells out again"));
            }
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("postDamaged", round(damaged));
        m.put("postEnd", round(end));
        m.put("stockLeft", round(stock));
        return new SceneReport(id(), id, TICKS, v, m);
    }

    private static float round(float value) {
        return Math.round(value * 10f) / 10f;
    }
}
