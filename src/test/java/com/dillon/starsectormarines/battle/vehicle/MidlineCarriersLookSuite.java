package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * How the midline row's articulated carriers look in play, for the owner rather than for a verdict: the look preset
 * on real generated ground ({@link LookReview}). Nothing is judged and no battle is played out.
 *
 * <ul>
 *   <li>{@code carrier-line}: the light, medium and heavy carriers in file, driving east toward a pinned, unarmed
 *       scout car and swinging north round it, so their guns come round and their modules bend at the hinges.</li>
 *   <li>{@code carrier-turn}: each carrier ordered to a point behind it, so it turns hard and its train bends to the
 *       hinge's stop and straightens again.</li>
 *   <li>{@code carrier-reverse-<weight>}: each carrier driven by hand, as direct control drives it: full left lock
 *       forward, so its train bends round, then straight back, so the cab pushes the bent modules further round until
 *       the hinge rams stop them.</li>
 *   <li>{@code carrier-wrecks}: the three wrecked where they stand.</li>
 * </ul>
 * Each at play zoom and close. Opt-in: a look study, not part of {@code all}.
 */
public final class MidlineCarriersLookSuite implements SnapshotSuite {

    static final int W = 60, H = 34;
    static final float DEEP = 5000f;
    static final VehicleType[] LINE = {VehicleType.LIGHT_CARRIER, VehicleType.MEDIUM_CARRIER, VehicleType.HEAVY_CARRIER};
    static final String[] NAMES = {"light", "medium", "heavy"};

    @Override public String id() { return "midline-carriers"; }

    @Override public String label() { return "Midline carriers look: in file, turning and wrecked, play zoom and close"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        out.addAll(LookReview.record(context.modRoot(), line()));
        out.addAll(LookReview.record(context.modRoot(), turn()));
        for (int i = 0; i < LINE.length; i++) out.addAll(LookReview.record(context.modRoot(), reverse(i)));
        out.addAll(LookReview.record(context.modRoot(), wrecks()));
        return out;
    }

    static LookReview.Shot line() {
        return LookReview.Shot.of("carrier-line", W, H, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            float[] targetAt = placement.openNear(W - 8f, H / 2f);
            long target = placement.subject("target", sim.convoy().spawn(VehicleType.SCOUT_CAR, Faction.DEFENDER,
                    VehicleMission.deployed(targetAt[0], targetAt[1])));
            sim.convoy().body(target).teleport(targetAt[0], targetAt[1], 0f);
            sim.world().setMaxHp(target, DEEP);
            sim.world().setHp(target, DEEP);
            pin(sim, target);
            for (int i = 0; i < LINE.length; i++) {
                long hull = spawn(sim, placement, i, 6f + i * 6f, H / 2f - 6f + i * 2f, -90f);
                float[] goal = placement.openNear(W / 2f + 10f, 4f + i * 3f);
                sim.getVehicleMoveOrderService().requestMove(hull, (int) goal[0], (int) goal[1]);
            }
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "light", "medium", "heavy"),
                LookReview.View.follow("close-line", placement, 26f, "light", "medium", "heavy"),
                LookReview.View.follow("close-heavy", placement, 12f, "heavy")), 720);
    }

    static LookReview.Shot turn() {
        return LookReview.Shot.of("carrier-turn", W, H, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            for (int i = 0; i < LINE.length; i++) {
                float y = 7f + i * 10f;
                long hull = spawn(sim, placement, i, W / 2f + 4f, y, -90f);
                // A point behind it and to one side: a hard turn round to it.
                float[] goal = placement.openNear(W / 2f - 6f, y + 4f);
                sim.getVehicleMoveOrderService().requestMove(hull, (int) goal[0], (int) goal[1]);
            }
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "light", "medium", "heavy"),
                LookReview.View.follow("close-light", placement, 10f, "light"),
                LookReview.View.follow("close-medium", placement, 12f, "medium"),
                LookReview.View.follow("close-heavy", placement, 14f, "heavy")), 600);
    }

    /** Ticks of full left lock forward before the hull backs up, and ticks backing up. */
    static final int LOCK_TICKS = 90, BACK_TICKS = 120;

    static LookReview.Shot reverse(int i) {
        return LookReview.Shot.of("carrier-reverse-" + NAMES[i], W, H, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            long hull = spawn(sim, placement, i, W / 2f - 6f, H / 2f - 4f, -90f);
            if (!sim.directControl().enter(hull)) throw new IllegalStateException("direct control refused the carrier");
            int[] tick = {0};
            placement.beforeTick(s -> {
                int t = tick[0]++;
                // Throttle is the intent's y, steering its x: full lock left forward, then straight back.
                ManualIntent drive = t < LOCK_TICKS ? new ManualIntent(-1f, 1f, Float.NaN, Float.NaN, false, false)
                        : t < LOCK_TICKS + BACK_TICKS ? new ManualIntent(0f, -1f, Float.NaN, Float.NaN, false, false)
                        : ManualIntent.NEUTRAL;
                s.directControl().submit(drive);
            });
            return sim;
        }, placement -> List.of(LookReview.View.follow("close", placement, 10f + 2f * i, NAMES[i]),
                LookReview.View.followAtPlay(placement, NAMES[i])), LOCK_TICKS + BACK_TICKS + 12)
                .withStills(LOCK_TICKS / 6, (LOCK_TICKS + BACK_TICKS / 3) / 6, (LOCK_TICKS + BACK_TICKS) / 6);
    }

    static LookReview.Shot wrecks() {
        return LookReview.Shot.of("carrier-wrecks", 40, 24, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            for (int i = 0; i < LINE.length; i++) {
                float[] at = placement.openNear(20f, 6f + i * 6f);
                long hull = sim.convoy().spawn(LINE[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
                sim.convoy().body(hull).teleport(at[0], at[1], -90f);
                placement.subject(NAMES[i], hull);
                sim.applyDamage(hull, 10000, 10000);
            }
            return sim;
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "light", "medium", "heavy"),
                LookReview.View.follow("close", placement, 16f, "light", "medium", "heavy")), 240);
    }

    private static long spawn(BattleSimulation sim, LookReview.Placement placement, int i, float x, float y,
                              float facing) {
        float[] at = placement.openNear(x, y);
        long hull = sim.convoy().spawn(LINE[i], Faction.MARINE, VehicleMission.deployed(at[0], at[1]));
        sim.convoy().body(hull).teleport(at[0], at[1], facing);
        sim.world().setMaxHp(hull, DEEP);
        sim.world().setHp(hull, DEEP);
        placement.subject(NAMES[i], hull);
        return hull;
    }

    /** Holds a hull where it stands: a paused authored route keeps it from driving. */
    private static void pin(BattleSimulation sim, long id) {
        VehicleMission mission = sim.convoy().mission(id);
        GroundBody body = sim.convoy().body(id);
        mission.authoredRouteX = new float[] {body.x};
        mission.authoredRouteY = new float[] {body.y};
        mission.authoredRoutePaused = true;
    }
}
